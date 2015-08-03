/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.gct.testing;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.BaseArtifact;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.run.CloudConfiguration;
import com.android.tools.idea.run.CloudConfiguration.Kind;
import com.android.tools.idea.run.CloudConfigurationProvider;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.stats.UsageTracker;
import com.google.api.client.util.Maps;
import com.google.api.client.util.Sets;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Buckets;
import com.google.api.services.storage.model.StorageObject;
import com.google.api.services.testing.model.AndroidDevice;
import com.google.api.services.testing.model.Device;
import com.google.api.services.testing.model.TestMatrix;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gct.testing.config.GoogleCloudTestingDeveloperConfigurable;
import com.google.gct.testing.config.GoogleCloudTestingDeveloperSettings;
import com.google.gct.testing.dimension.*;
import com.google.gct.testing.launcher.CloudAuthenticator;
import com.google.gct.testing.launcher.CloudTestsLauncher;
import com.google.gct.testing.results.GoogleCloudTestListener;
import com.google.gct.testing.results.GoogleCloudTestResultsConnectionUtil;
import com.google.gct.testing.results.GoogleCloudTestingResultParser;
import com.google.gct.testing.util.CloudTestingTracking;
import com.google.gct.testing.vnc.BlankVncViewer;
import com.google.gct.testing.vnc.BlankVncViewerCallback;
import com.google.gct.testing.vnc.VncKeepAliveThreadImpl;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.run.AndroidRunningState;
import org.jetbrains.android.run.testing.AndroidTestConsoleProperties;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.tools.idea.run.CloudConfiguration.Kind.MATRIX;
import static com.android.tools.idea.run.CloudConfiguration.Kind.SINGLE_DEVICE;
import static com.google.gct.testing.CloudTestingUtils.checkJavaVersion;
import static com.google.gct.testing.launcher.CloudAuthenticator.getTest;

public class CloudConfigurationProviderImpl extends CloudConfigurationProvider {

  private static final String TEST_RUN_ID_PREFIX = "GoogleCloudTest:";
  private static final Map<String, CloudConfigurationImpl> testRunIdToCloudConfiguration = new HashMap<String, CloudConfigurationImpl>();
  private static final Map<String, CloudResultsAdapter> testRunIdToCloudResultsAdapter = new HashMap<String, CloudResultsAdapter>();
  // Do not use MultiMap to ensure proper reuse of serial numbers (IP:port).
  private static final Map<String, String> serialNumberToConfigurationInstance = Maps.newHashMap();

  public static final Icon DEFAULT_ICON = AndroidIcons.AndroidFile;

  public static final Function<CloudConfiguration, CloudConfigurationImpl> CLONE_CONFIGURATIONS =
    new Function<CloudConfiguration, CloudConfigurationImpl>() {
      @Override
      public CloudConfigurationImpl apply(CloudConfiguration configuration) {
        return ((CloudConfigurationImpl) configuration).clone();
      }
    };

  public static final Function<CloudConfiguration, CloudConfigurationImpl> CAST_CONFIGURATIONS =
    new Function<CloudConfiguration, CloudConfigurationImpl>() {
      @Override
      public CloudConfigurationImpl apply(CloudConfiguration configuration) {
        return (CloudConfigurationImpl) configuration;
      }
    };


  private static volatile String lastCloudProjectId;

  private static final Set<GhostCloudDevice> ghostCloudDevices = Sets.newHashSet();

  private static CloudConfigurationProviderImpl instance = null;

  public static Map<String, List<? extends CloudTestingType>> getAllDimensionTypes() {
    Map<String, List<? extends CloudTestingType>> dimensionTypes = new HashMap<String, List<? extends CloudTestingType>>();
    dimensionTypes.put(DeviceDimension.DISPLAY_NAME, DeviceDimension.getFullDomain());
    dimensionTypes.put(ApiDimension.DISPLAY_NAME, ApiDimension.getFullDomain());
    dimensionTypes.put(LanguageDimension.DISPLAY_NAME, LanguageDimension.getFullDomain());
    dimensionTypes.put(OrientationDimension.DISPLAY_NAME, OrientationDimension.getFullDomain());
    return dimensionTypes;
  }

  public static CloudConfigurationProviderImpl getInstance() {
    if (instance == null) {
      instance = new CloudConfigurationProviderImpl();
    }
    return instance;
  }

  @NotNull
  @Override
  public List<? extends CloudConfiguration> getCloudConfigurations(@NotNull AndroidFacet facet, @NotNull final Kind configurationKind) {
    try {
      CloudAuthenticator.prepareCredential();
    } catch(Exception e) {
      return Lists.newArrayList();
    }

    List<CloudPersistentConfiguration> cloudPersistentConfigurations = Lists.newArrayList(Iterables.filter(
      CloudCustomPersistentConfigurations.getInstance(facet.getModule()).getState().myCloudPersistentConfigurations,
      new Predicate<CloudPersistentConfiguration>() {
        @Override
        public boolean apply(@Nullable CloudPersistentConfiguration configuration) {
          return configuration != null && configuration.kind == configurationKind;
        }
      }));
    return Lists.newArrayList(Iterables.concat(deserializeConfigurations(cloudPersistentConfigurations, true, facet),
                                               getDefaultConfigurations(facet, configurationKind)));
  }

  private List<? extends CloudConfiguration> getDefaultConfigurations(AndroidFacet facet, Kind kind) {
    if (kind == SINGLE_DEVICE) {
      CloudConfigurationImpl defaultConfiguration =
        new CloudConfigurationImpl(CloudConfigurationImpl.DEFAULT_DEVICE_CONFIGURATION_ID, "", SINGLE_DEVICE, AndroidIcons.Display, facet);
      defaultConfiguration.apiDimension.enableDefault();
      ImmutableList<CloudTestingType> enabledApis = defaultConfiguration.apiDimension.getEnabledTypes();
      if (enabledApis.isEmpty()) {
        return ImmutableList.of();
      }
      defaultConfiguration.deviceDimension.enableDefault(enabledApis.get(0).getId());
      defaultConfiguration.languageDimension.enableDefault();
      defaultConfiguration.orientationDimension.enableDefault();

      ImmutableList<CloudTestingType> enabledDevices = defaultConfiguration.deviceDimension.getEnabledTypes();
      if (enabledDevices.isEmpty() || defaultConfiguration.languageDimension.getEnabledTypes().isEmpty()
          || defaultConfiguration.orientationDimension.getEnabledTypes().isEmpty()) {
        return ImmutableList.of();
      }

      defaultConfiguration.setName(enabledDevices.get(0).getConfigurationDialogDisplayName() + " API " + enabledApis.get(0).getId());
      defaultConfiguration.setNonEditable();
      return ImmutableList.of(defaultConfiguration);
    }

    CloudConfigurationImpl allConfiguration =
      new CloudConfigurationImpl(CloudConfigurationImpl.ALL_CONFIGURATION_ID, "All Compatible", MATRIX, AndroidIcons.Display, facet);
    allConfiguration.deviceDimension.enableAll();
    allConfiguration.apiDimension.enableAll();
    allConfiguration.languageDimension.enableAll();
    allConfiguration.orientationDimension.enableAll();
    allConfiguration.setNonEditable();
    return ImmutableList.of(allConfiguration);
    //TODO: For now, there are no default configurations to store/read from the persistent storage (i.e., an xml file).
    //List<CloudPersistentConfiguration> myCloudPersistentConfigurations =
    //  CloudDefaultPersistentConfigurations.getInstance(facet.getModule()).getState().myCloudPersistentConfigurations;
    //return ImmutableList.copyOf(deserializeConfigurations(myCloudPersistentConfigurations, false, facet));
  }

  @NotNull
  public List<? extends CloudConfiguration> getAllCloudConfigurations(@NotNull AndroidFacet facet) {
    try {
      CloudAuthenticator.prepareCredential();
    } catch(Exception e) {
      return Lists.newArrayList();
    }

    List<CloudPersistentConfiguration> cloudPersistentConfigurations =
      CloudCustomPersistentConfigurations.getInstance(facet.getModule()).getState().myCloudPersistentConfigurations;
    return Lists.newArrayList(Iterables.concat(deserializeConfigurations(cloudPersistentConfigurations, true, facet),
                                               getDefaultConfigurations(facet, MATRIX),
                                               getDefaultConfigurations(facet, SINGLE_DEVICE)));
  }

  @Nullable
  @Override
  public CloudConfiguration openMatrixConfigurationDialog(@NotNull AndroidFacet currentFacet,
                                                          @Nullable CloudConfiguration selectedConfiguration,
                                                          @NotNull Kind configurationKind) {

    final Kind selectedConfigurationKind =
      selectedConfiguration == null ? configurationKind : ((CloudConfigurationImpl)selectedConfiguration).getKind();

    Module currentModule = currentFacet.getModule();
    List<? extends CloudConfiguration> testingConfigurations = getCloudConfigurations(currentFacet, selectedConfigurationKind);

    List<CloudConfigurationImpl> castDefaultConfigurations =
      Lists.newArrayList(Iterables.transform(Iterables.filter(testingConfigurations, new Predicate<CloudConfiguration>(){
        @Override
        public boolean apply(CloudConfiguration testingConfiguration) {
          return !testingConfiguration.isEditable();
        }
      }), CAST_CONFIGURATIONS));

    List<CloudConfigurationImpl> copyCustomConfigurations =
      Lists.newArrayList(Iterables.transform(Iterables.filter(testingConfigurations, new Predicate<CloudConfiguration>(){
        @Override
        public boolean apply(CloudConfiguration testingConfiguration) {
          return testingConfiguration.isEditable();
        }
      }), CLONE_CONFIGURATIONS));

    CloudConfigurationChooserDialog dialog =
      new CloudConfigurationChooserDialog(currentModule, copyCustomConfigurations, castDefaultConfigurations,
                                          (CloudConfigurationImpl) selectedConfiguration, selectedConfigurationKind);

    dialog.show();
    if (dialog.isOK()) {
      CloudCustomPersistentConfigurations persistentConfigurations = CloudCustomPersistentConfigurations.getInstance(currentModule);
      CloudPersistentState customState = persistentConfigurations.getState();

      // Keep all un-edited configurations (i.e., configurations of other kinds).
      customState.myCloudPersistentConfigurations =
        Lists.newArrayList(Iterables.filter(customState.myCloudPersistentConfigurations, new Predicate<CloudPersistentConfiguration>() {
          @Override
          public boolean apply(@Nullable CloudPersistentConfiguration configuration) {
            return configuration != null && configuration.kind != selectedConfigurationKind;
          }
        }));

      // Persist the edited configurations.
      customState.myCloudPersistentConfigurations.addAll(Lists.newArrayList(
        Iterables.transform(copyCustomConfigurations, new Function<CloudConfigurationImpl, CloudPersistentConfiguration>() {
          @Override
          public CloudPersistentConfiguration apply(CloudConfigurationImpl configuration) {
            return configuration.getPersistentConfiguration();
          }
        })));

      persistentConfigurations.loadState(customState);

      return dialog.getSelectedConfiguration();
    }
    return null;
  }

  @Nullable
  @Override
  public String openCloudProjectConfigurationDialog(@NotNull Project project, @Nullable String projectId) {
    CloudProjectChooserDialog dialog = new CloudProjectChooserDialog(project, projectId);

    dialog.show();

    if (dialog.isOK()) {
      return dialog.getSelectedProject();
    }
    return null;
  }

  private static boolean validateCloudProject(@NotNull Project project, @NotNull String cloudProjectId) {
    // Check that we can properly connect to the backend.
    Buckets buckets = null;
    String message = null;
    try {
      Storage.Buckets.List listBuckets = CloudAuthenticator.getStorage().buckets().list(cloudProjectId);
      buckets = listBuckets.execute();
    } catch (Exception e) {
      message = e.getMessage();
      // ignore
    } finally {
      if (buckets == null) {
        CloudTestingUtils
          .showErrorMessage(project, "Cloud test configuration is invalid",
                            "Failed to authorize to Google Cloud project! Please select a project you are authorized to use.\n"
                            + "Exception while performing a pre-trigger sanity check\n\n" + message);
        return false;
      }
    }
    return true;
  }

  @Override
  public void launchCloudDevice(int selectedConfigurationId, @NotNull String cloudProjectId, @NotNull AndroidFacet facet) {
    UsageTracker.getInstance()
      .trackEvent(CloudTestingTracking.CLOUD_TESTING, CloudTestingTracking.LAUNCH_CLOUD_DEVICE, CloudTestingTracking.SESSION_LABEL, null);

    CloudConfigurationImpl cloudConfiguration = CloudTestingUtils.getConfigurationById(selectedConfigurationId, facet);

    if (cloudConfiguration.getKind() != Kind.SINGLE_DEVICE) {
      // Should handle only single device configurations.
      return;
    }

    lastCloudProjectId = cloudProjectId;
    String configurationInstance = cloudConfiguration.computeConfigurationInstances(ConfigurationInstance.ENCODED_NAME_DELIMITER).get(0);
    launchCloudDevice(configurationInstance);
  }

  public void launchCloudDevice(String configurationInstance) {
    if (!checkJavaVersion()) {
      return;
    }
    final String cloudProjectId = lastCloudProjectId;
    String[] dimensionValues = configurationInstance.split("-");
    Device device = new Device().setAndroidDevice(
      new AndroidDevice()
        .setAndroidModelId(dimensionValues[0])
        .setAndroidVersionId(dimensionValues[1])
        .setLocale(dimensionValues[2])
        .setOrientation(dimensionValues[3]));

    Device createdDevice = null;
    try {
      createdDevice = getTest().projects().devices().create(cloudProjectId, device).execute();
    }
    catch (IOException e) {
      CloudTestingUtils.showErrorMessage(null, "Error launching a cloud device", "Failed to launch a cloud device!\n" +
                                                                                 "Exception while launching a cloud device\n\n" +
                                                                                 e.getMessage());
      return;
    }
    if (createdDevice == null) {
      CloudTestingUtils.showErrorMessage(null, "Error launching a cloud device", "Failed to launch a cloud device!\n" +
                                                                                 "The returned cloud device is null\n\n");
    }

    final String deviceId = createdDevice.getId();
    final GhostCloudDevice ghostCloudDevice = new GhostCloudDevice(createdDevice);
    synchronized (ghostCloudDevices) {
      ghostCloudDevices.add(ghostCloudDevice);
    }
    String configurationName =
      ConfigurationInstance.parseFromEncodedString(ghostCloudDevice.getEncodedConfigurationInstance()).getResultsViewerDisplayString();
    BlankVncViewer blankVncViewer = BlankVncViewer.showBlankVncViewer(configurationName, new BlankVncViewerCallback() {
      @Override
      public void viewerClosed() {
        try {
          getTest().projects().devices().delete(cloudProjectId, deviceId).execute();
        } catch (Exception e) {
          e.printStackTrace();
        }
        synchronized (ghostCloudDevices) {
          ghostCloudDevices.remove(ghostCloudDevice);
        }
      }
    });
    final long POLLING_INTERVAL = 10 * 1000; // 10 seconds
    final long INITIAL_TIMEOUT = 10 * 60 * 1000; // 10 minutes
    long stopTime = System.currentTimeMillis() + INITIAL_TIMEOUT;
    String sdkPath = IdeSdks.getAndroidSdkPath().getAbsolutePath() + "/platform-tools";
    File workingDir = new File(sdkPath);
    try {
      while (System.currentTimeMillis() < stopTime) {
        synchronized (ghostCloudDevices) {
          if (!ghostCloudDevices.contains(ghostCloudDevice)) {
            // Blank VNC Viewer was closed, so stop waiting for the device.
            return;
          }
        }
        createdDevice = getTest().projects().devices().get(cloudProjectId, deviceId).execute();
        System.out.println("Polling for device... (time: " + System.currentTimeMillis() + ", status: " + createdDevice.getState() + ")");
        if (createdDevice.getState().equals("DEVICE_ERROR")) {
          CloudTestingUtils.showErrorMessage(null, "Error launching a cloud device", "Failed to launch a cloud device!\n" +
                                                                                     "The polled cloud device has ERROR state\n\n");
          return;
        }
        if (createdDevice.getState().equals("READY")) {
          //if (createdDevice.getDeviceDetails().getConnectionInfo() != null) {
          String ipAddress = createdDevice.getDeviceDetails().getConnectionInfo().getIpAddress();
          Integer adbPort = createdDevice.getDeviceDetails().getConnectionInfo().getAdbPort();
          Integer vncPort = createdDevice.getDeviceDetails().getConnectionInfo().getVncPort();
          String vncPassword = createdDevice.getDeviceDetails().getConnectionInfo().getVncPassword();
          //TODO: Remove this temporary password.
          if ("abc".length() > 1) {
            vncPassword = "zeecloud";
          }
          String deviceAddress = ipAddress + ":" + adbPort;
          System.out.println("Device ready with IP address:port " + deviceAddress);
          Runtime rt = Runtime.getRuntime();
          Process connect = rt.exec("./adb connect " + deviceAddress, null, workingDir);
          connect.waitFor();
          serialNumberToConfigurationInstance.put(deviceAddress, configurationInstance);
          // Do not wait for "finally" to remove the ghost device
          // to minimize the time both a ghost device and an actual cloud device are present in the devices table.
          synchronized (ghostCloudDevices) {
            ghostCloudDevices.remove(ghostCloudDevice);
          }
          try { // Use try just in case something goes wrong.
            blankVncViewer.closeWindow();
          } catch (Exception e) {
            e.printStackTrace();
          }
          // Make sure the device is unlocked.
          Process unlock = rt.exec("./adb -s " + deviceAddress + " wait-for-device shell input keyevent 82" , null, workingDir);
          unlock.waitFor();
          // Open the VNC window for the cloud device.
          String[] viewerArgs = new String[]{"-port=" + vncPort, "-host=" + ipAddress, "-password=" + vncPassword, "-fullScreen=false"};
          VncKeepAliveThreadImpl.startVnc(viewerArgs, configurationName, cloudProjectId, deviceId, deviceAddress, workingDir);
          return;
        }
        Thread.sleep(POLLING_INTERVAL);
      }
      CloudTestingUtils.showErrorMessage(null, "Timed out connecting to a cloud device", "Timed out connecting to a cloud device!\n" +
                                                                                         "Timed out connecting to a cloud device:\n\n" +
                                                                                         deviceId);
    } catch (IOException e) {
      showCloudDevicePollingError(e, deviceId);
    } catch (InterruptedException e) {
      showCloudDevicePollingError(e, deviceId);
    } finally {
      synchronized (ghostCloudDevices) {
        ghostCloudDevices.remove(ghostCloudDevice);
      }
    }
  }

  public static String getConfigurationInstanceForSerialNumber(String serialNumber) {
    return serialNumberToConfigurationInstance.get(serialNumber);
  }

  @NotNull
  @Override
  public Collection<IDevice> getLaunchingCloudDevices() {
    synchronized (ghostCloudDevices) {
      HashSet<IDevice> launchingCloudDevices = Sets.newHashSet();
      launchingCloudDevices.addAll(ghostCloudDevices);
      return launchingCloudDevices;
    }
  }

  @Nullable
  @Override
  public Icon getCloudDeviceIcon() {
    return CloudTestingUtils.CLOUD_DEVICE_ICON;
  }

  @Nullable
  @Override
  public String getCloudDeviceConfiguration(IDevice device) {
    String encodedConfigurationInstance = device instanceof GhostCloudDevice
                                          ? ((GhostCloudDevice)device).getEncodedConfigurationInstance()
                                          : serialNumberToConfigurationInstance.get(device.getSerialNumber());
    if (encodedConfigurationInstance != null) {
      return ConfigurationInstance.parseFromEncodedString(encodedConfigurationInstance).getResultsViewerDisplayString();
    }
    return null;
  }

  private void showCloudDevicePollingError(Exception e, String deviceId) {
    CloudTestingUtils.showErrorMessage(null, "Error polling for a cloud device", "Failed to connect to a cloud device!\n" +
                                                                                 "Exception while polling for a cloud device\n\n" +
                                                                                 deviceId +
                                                                                 e.getMessage());
  }

  @Override
  public ExecutionResult executeCloudMatrixTests(int selectedConfigurationId, String cloudProjectId, AndroidRunningState runningState,
                                                 Executor executor) throws ExecutionException {
    UsageTracker.getInstance()
      .trackEvent(CloudTestingTracking.CLOUD_TESTING, CloudTestingTracking.RUN_TEST_MATRIX, CloudTestingTracking.SESSION_LABEL, null);

    Project project = runningState.getFacet().getModule().getProject();

    if (!validateCloudProject(project, cloudProjectId)) {
      // Cloud project is invalid, nothing to do.
      return null;
    }

    CloudConfigurationImpl cloudConfiguration = CloudTestingUtils.getConfigurationById(selectedConfigurationId, runningState.getFacet());

    if (cloudConfiguration.getKind() != Kind.MATRIX) {
      // Should handle only matrix configurations.
      return null;
    }

    lastCloudProjectId = cloudProjectId;

    AndroidTestRunConfiguration testRunConfiguration = (AndroidTestRunConfiguration) runningState.getConfiguration();
    AndroidTestConsoleProperties properties = new AndroidTestConsoleProperties(testRunConfiguration, executor);
    CloudMatrixExecutionCancellator matrixExecutionCancellator = new CloudMatrixExecutionCancellator();
    ConsoleView console = GoogleCloudTestResultsConnectionUtil.createAndAttachConsole(
      "Cloud Testing", runningState.getProcessHandler(), properties, runningState.getEnvironment(), matrixExecutionCancellator);
    Disposer.register(project, console);

    GoogleCloudTestingResultParser
      cloudResultParser = new GoogleCloudTestingResultParser("Cloud Test Run", new GoogleCloudTestListener(runningState));

    List<String> expectedConfigurationInstances =
      cloudConfiguration.computeConfigurationInstances(ConfigurationInstance.DISPLAY_NAME_DELIMITER);
    for (String configurationInstance : expectedConfigurationInstances) {
      cloudResultParser.getTestRunListener().testConfigurationScheduled(configurationInstance);
    }
    GoogleCloudTestingDeveloperConfigurable.GoogleCloudTestingDeveloperState googleCloudTestingDeveloperState =
      GoogleCloudTestingDeveloperSettings.getInstance(project).getState();
    if (!googleCloudTestingDeveloperState.shouldUseFakeBucket) {
      performTestsInCloud(cloudConfiguration, cloudProjectId, runningState, cloudResultParser, matrixExecutionCancellator);
    }
    else {
      String testRunId = TEST_RUN_ID_PREFIX + googleCloudTestingDeveloperState.fakeBucketName + System.currentTimeMillis();
      CloudResultsAdapter cloudResultsAdapter =
        new CloudResultsAdapter(cloudProjectId, googleCloudTestingDeveloperState.fakeBucketName, runningState.getProcessHandler(),
                                cloudResultParser, expectedConfigurationInstances, testRunId, null, null);
      addCloudConfiguration(testRunId, cloudConfiguration);
      addCloudResultsAdapter(testRunId, cloudResultsAdapter);
      cloudResultsAdapter.startPolling();
    }
    return new DefaultExecutionResult(console, runningState.getProcessHandler());
  }

  @Override
  protected boolean canBeEnabled() {
    final String publicBucketName = "cloud-testing-plugin-enablement";
    final String triggerFileName = "ENABLED";
    try {
      Storage.Objects.List objects = CloudAuthenticator.getPublicStorage().objects().list(publicBucketName);
      List<StorageObject> storageObjects = objects.execute().getItems();
      if (storageObjects != null) {
        for (StorageObject storageObject : storageObjects) {
          if (triggerFileName.equals(storageObject.getName())) {
            return true;
          }
        }
      }
    } catch (Exception e) {
      // ignore
    }
    return false;
  }

  private void performTestsInCloud(final CloudConfigurationImpl cloudTestingConfiguration, final String cloudProjectId,
                                   final AndroidRunningState runningState, final GoogleCloudTestingResultParser cloudResultParser,
                                   final CloudMatrixExecutionCancellator matrixExecutionCancellator) {
    if (cloudTestingConfiguration != null && cloudTestingConfiguration.getDeviceConfigurationCount() > 0) {
      final List<String> expectedConfigurationInstances =
        cloudTestingConfiguration.computeConfigurationInstances(ConfigurationInstance.DISPLAY_NAME_DELIMITER);
      new Thread(new Runnable() {
        @Override
        public void run() {
          AndroidTestRunConfiguration testRunConfiguration = (AndroidTestRunConfiguration) runningState.getConfiguration();
          String moduleName = runningState.getFacet().getModule().getName();
          String bucketName = "build-" + moduleName.toLowerCase() + "-" + System.currentTimeMillis();
          String appPackage = runningState.getFacet().getAndroidModuleInfo().getPackage();
          String testPackage = appPackage + ".test";

          if (matrixExecutionCancellator.isCancelled()) {
            return;
          }
          runningState.getProcessHandler().notifyTextAvailable(
            prepareProgressString("Creating Cloud Storage bucket " + bucketName + "...", ""), ProcessOutputTypes.STDOUT);
          CloudTestsLauncher.createBucket(cloudProjectId, bucketName);

          if (matrixExecutionCancellator.isCancelled()) {
            return;
          }

          IdeaAndroidProject androidModel = runningState.getFacet().getAndroidModel();
          if (androidModel == null) {
            CloudTestingUtils.showErrorMessage(runningState.getFacet().getModule().getProject(), "Error uploading APKs",
                                               "Your project is not an idea android project!\n");
            return;
          }

          AndroidArtifact mainArtifact = androidModel.getSelectedVariant().getMainArtifact();
          List<AndroidArtifactOutput> mainOutputs = Lists.newArrayList(mainArtifact.getOutputs());
          if (mainOutputs.isEmpty()) {
            CloudTestingUtils.showErrorMessage(runningState.getFacet().getModule().getProject(), "Error finding app APK",
                                               "Could not find your app APK!\n");
            return;
          }
          File appApk = mainOutputs.get(0).getMainOutputFile().getOutputFile();

          BaseArtifact testArtifactInfo = androidModel.findSelectedTestArtifactInSelectedVariant();
          if (!(testArtifactInfo instanceof AndroidArtifact)) {
            CloudTestingUtils.showErrorMessage(runningState.getFacet().getModule().getProject(), "Error uploading APKs",
                                               "Your test artifact is not an android artifact!\n");
            return;
          }

          List<AndroidArtifactOutput> testOutputs = Lists.newArrayList(((AndroidArtifact)testArtifactInfo).getOutputs());
          if (testOutputs.isEmpty()) {
            CloudTestingUtils.showErrorMessage(runningState.getFacet().getModule().getProject(), "Error finding test APK",
                                               "Could not find your test APK!\n");
            return;
          }
          File testApk = testOutputs.get(0).getMainOutputFile().getOutputFile();

          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Uploading app APK...", ""),
                                                               ProcessOutputTypes.STDOUT);
          String appApkName = CloudTestsLauncher.uploadFile(bucketName, appApk).getName();

          if (matrixExecutionCancellator.isCancelled()) {
            return;
          }
          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Uploading test APK...", ""),
                                                               ProcessOutputTypes.STDOUT);
          String testApkName = CloudTestsLauncher.uploadFile(bucketName, testApk).getName();

          if (matrixExecutionCancellator.isCancelled()) {
            return;
          }
          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Invoking cloud test API...", ""),
                                                               ProcessOutputTypes.STDOUT);
          String testSpecification = CloudTestingUtils.prepareTestSpecification(testRunConfiguration);

          TestMatrix testMatrix = CloudTestsLauncher
            .triggerTestApi(cloudProjectId, getBucketGcsPath(bucketName), getApkGcsPath(bucketName, appApkName),
                            getApkGcsPath(bucketName, testApkName), testSpecification, testRunConfiguration.INSTRUMENTATION_RUNNER_CLASS,
                            cloudTestingConfiguration, appPackage, testPackage);

          if (testMatrix != null) {
            runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Validating APKs...", "\n\n"),
                                                                 ProcessOutputTypes.STDOUT);
            matrixExecutionCancellator.setCloudProjectId(cloudProjectId);
            matrixExecutionCancellator.setTestMatrixId(testMatrix.getTestMatrixId());
            String testRunId = TEST_RUN_ID_PREFIX + bucketName;
            CloudResultsAdapter cloudResultsAdapter =
              new CloudResultsAdapter(cloudProjectId, bucketName, runningState.getProcessHandler(), cloudResultParser,
                                      expectedConfigurationInstances, testRunId, testMatrix, matrixExecutionCancellator);
            addCloudConfiguration(testRunId, cloudTestingConfiguration);
            addCloudResultsAdapter(testRunId, cloudResultsAdapter);
            cloudResultsAdapter.startPolling();
          }
        }

        private String prepareProgressString(String progressMessage, String suffix) {
          return CloudTestingUtils.shouldShowProgressTimestamps()
                 ? progressMessage + "\t" + System.currentTimeMillis() + "\n" + suffix
                 : progressMessage + "\n" + suffix;
        }
      }).start();
    }
  }

  //private String listAllApks(List<String> apkPaths) {
  //  List<String> allApks = new ArrayList<String>();
  //  for (String apkPath : apkPaths) {
  //    allApks.addAll(Arrays.asList(new File(apkPath).list(new FilenameFilter() {
  //      @Override
  //      public boolean accept(File dir, String name) {
  //        return name.endsWith(".apk");
  //      }
  //    })));
  //  }
  //  String apkList = "";
  //  for (String apk : allApks) {
  //    apkList += apk + "\n";
  //  }
  //  return apkList;
  //}
  //
  //private File findAppropriateApk(List<String> apkPaths, final boolean isTestApk) {
  //  List<File> allApkFiles = new ArrayList<File>();
  //  for (String apkPath : apkPaths) {
  //    allApkFiles.addAll(Arrays.asList(new File(apkPath).listFiles(new FilenameFilter() {
  //      @Override
  //      public boolean accept(File dir, String name) {
  //        return name.endsWith(".apk") && (isTestApk ? name.contains("-test") : !name.contains("-test"));
  //      }
  //    })));
  //  }
  //  if (allApkFiles.size() == 0) {
  //    return null;
  //  }
  //
  //  return Collections.max(allApkFiles, new Comparator<File>() {
  //    @Override
  //    public int compare(File file1, File file2) {
  //      return (int)(file1.lastModified() - file2.lastModified());
  //    }
  //  });
  //}

  //private File findAppropriateApk(String apkPath, String moduleName, final boolean isTestApk) {
  //  File apkFolder = new File(apkPath);
  //  HashSet<String> apks = Sets.newHashSet(apkFolder.list());
  //  String[] buildTypes = new String[] {"release", "debug"};
  //  String[] alignments = new String[] {"", "-unaligned"};
  //  String[] variations = new String[] {"full-", "", "x86-"}; // We do not support 'arm' and 'e2e' at the moment.
  //
  //  for (String buildType : buildTypes) {
  //    for (String alignment : alignments) {
  //      for (String variation : variations) {
  //        String fileName = moduleName + "-" + variation + buildType + (isTestApk ? "-test" : "") + alignment + ".apk";
  //        if (apks.contains(fileName)) {
  //          return new File(apkFolder, fileName);
  //        }
  //      }
  //    }
  //  }
  //  return null;
  //}

  //private List<String> getApkPaths(AndroidRunningState runningState) {
  //  String buildPath = runningState.getFacet().getModule().getModuleFile().getParent().getPath() + "/build";
  //  List<String> apkPaths = new LinkedList<String>();
  //  addPathIfExists(apkPaths, buildPath + "/apk/");
  //  addPathIfExists(apkPaths, buildPath + "/outputs/apk/");
  //  return apkPaths;
  //}

  private void addPathIfExists(List<String> apkPaths, String apkPath) {
    if (new File(apkPath).exists()) {
      apkPaths.add(apkPath);
    }
  }

  private String getBucketGcsPath(String bucketName) {
    return "gs://" + bucketName;
  }

  private String getApkGcsPath(String bucketName, String apkName) {
    return "gs://" + bucketName + "/" + apkName;
  }

  private static void addCloudResultsAdapter(String testRunId, CloudResultsAdapter cloudResultsAdapter) {
    if (testRunIdToCloudResultsAdapter.get(testRunId) != null) {
      throw new IllegalStateException("Cannot add more than one cloud results adapter for test run id: " + testRunId);
    }
    testRunIdToCloudResultsAdapter.put(testRunId, cloudResultsAdapter);
  }

  private static void addCloudConfiguration(String testRunId, CloudConfigurationImpl cloudConfiguration) {
    if (testRunIdToCloudConfiguration.get(testRunId) != null) {
      throw new IllegalStateException("Cannot add more than one cloud configuration for test run id: " + testRunId);
    }
    testRunIdToCloudConfiguration.put(testRunId, cloudConfiguration);
  }

  public static CloudConfigurationImpl getSelectedCloudConfiguration(String testRunId) {
    return testRunIdToCloudConfiguration.get(testRunId);
  }

  public static CloudResultsAdapter getCloudResultsAdapter(String testRunId) {
    return testRunIdToCloudResultsAdapter.get(testRunId);
  }

  public static List<CloudConfigurationImpl> deserializeConfigurations(
    final List<CloudPersistentConfiguration> persistentConfigurations, boolean isEditable, AndroidFacet facet) {
    List<CloudConfigurationImpl> googleCloudTestingConfigurations = new LinkedList<CloudConfigurationImpl>();
    for (CloudPersistentConfiguration persistentConfiguration : persistentConfigurations) {
      Icon icon = getIcon(persistentConfiguration.name, isEditable);
      CloudConfigurationImpl configuration =
        new CloudConfigurationImpl(persistentConfiguration.id, persistentConfiguration.name, persistentConfiguration.kind, icon, facet);
      configuration.deviceDimension.enable(DeviceDimension.getFullDomain(), persistentConfiguration.devices);
      configuration.apiDimension.enable(ApiDimension.getFullDomain(), persistentConfiguration.apiLevels);
      configuration.languageDimension.enable(LanguageDimension.getFullDomain(), persistentConfiguration.languages);
      configuration.orientationDimension.enable(OrientationDimension.getFullDomain(), persistentConfiguration.orientations);
      if (!isEditable) {
        configuration.setNonEditable();
      }
      googleCloudTestingConfigurations.add(configuration);
    }
    return googleCloudTestingConfigurations;
  }

  private static Icon getIcon(String configurationName, boolean isEditable) {
    if (isEditable) {
      return AndroidIcons.AndroidFile;
    }
    if (configurationName.equals("All Available")) {
      return AndroidIcons.Display;
    }
    return AndroidIcons.Portrait;
  }
}
