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

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.ddmlib.IDevice;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.api.client.util.Maps;
import com.google.api.client.util.Sets;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import com.google.api.services.testing.model.AndroidDevice;
import com.google.api.services.testing.model.Device;
import com.google.api.services.testing.model.TestMatrix;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gct.testing.android.CloudConfiguration;
import com.google.gct.testing.android.CloudMatrixTestRunningState;
import com.google.gct.testing.config.GoogleCloudTestingDeveloperConfigurable;
import com.google.gct.testing.config.GoogleCloudTestingDeveloperSettings;
import com.google.gct.testing.dimension.*;
import com.google.gct.testing.launcher.CloudAuthenticator;
import com.google.gct.testing.launcher.CloudTestsLauncher;
import com.google.gct.testing.results.GoogleCloudTestConsoleProperties;
import com.google.gct.testing.results.GoogleCloudTestListener;
import com.google.gct.testing.results.GoogleCloudTestResultsConnectionUtil;
import com.google.gct.testing.results.GoogleCloudTestingResultParser;
import com.google.gct.testing.vnc.BlankVncViewer;
import com.google.gct.testing.vnc.BlankVncViewerCallback;
import com.google.gct.testing.vnc.VncKeepAliveThreadImpl;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.InetAddress;
import java.util.*;

import static com.google.gct.testing.CloudTestingUtils.checkJavaVersion;
import static com.jcraft.jsch.KeyPair.RSA;

public final class CloudConfigurationHelper {

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

  private CloudConfigurationHelper() { } // Not instantiable.

  public static Map<String, List<? extends CloudTestingType>> getAllDimensionTypes() {
    Map<String, List<? extends CloudTestingType>> dimensionTypes = new HashMap<String, List<? extends CloudTestingType>>();
    dimensionTypes.put(DeviceDimension.DISPLAY_NAME, DeviceDimension.getFullDomain());
    dimensionTypes.put(ApiDimension.DISPLAY_NAME, ApiDimension.getFullDomain());
    dimensionTypes.put(LanguageDimension.DISPLAY_NAME, LanguageDimension.getFullDomain());
    dimensionTypes.put(OrientationDimension.DISPLAY_NAME, OrientationDimension.getFullDomain());
    return dimensionTypes;
  }

  @NotNull
  public static List<? extends CloudConfiguration> getCloudConfigurations(@NotNull AndroidFacet facet, @NotNull final CloudConfiguration.Kind configurationKind) {
    try {
      CloudAuthenticator.getInstance().prepareCredential();
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

  @VisibleForTesting
  public static List<? extends CloudConfiguration> getDefaultConfigurations(AndroidFacet facet, CloudConfiguration.Kind kind) {
    if (kind == CloudConfiguration.Kind.SINGLE_DEVICE) {
      CloudConfigurationImpl defaultConfiguration =
        new CloudConfigurationImpl(CloudConfigurationImpl.DEFAULT_DEVICE_CONFIGURATION_ID, "", CloudConfiguration.Kind.SINGLE_DEVICE, AndroidIcons.Display, facet);
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

    CloudConfigurationImpl defaultConfiguration =
      new CloudConfigurationImpl(CloudConfigurationImpl.DEFAULT_MATRIX_CONFIGURATION_ID, "Sample configuration", CloudConfiguration.Kind.MATRIX, AndroidIcons.Display, facet);
    defaultConfiguration.deviceDimension.enable(DeviceDimension.getFullDomain(), Arrays.asList("Nexus6", "hammerhead", "mako"));
    defaultConfiguration.apiDimension.enable(ApiDimension.getFullDomain(), Arrays.asList("19", "21", "22", "23"));
    ImmutableList<CloudTestingType> enabledApis = defaultConfiguration.apiDimension.getEnabledTypes();
    // Make sure we enable at most 3 latest APIs.
    if (enabledApis.size() > 3) {
      for (CloudTestingType enabledApi : enabledApis) {
        if (enabledApi.getId().equals("19")) { // Disable the oldest API.
          defaultConfiguration.apiDimension.disable(enabledApi);
          break;
        }
      }
    }
    defaultConfiguration.languageDimension.enableDefault();
    defaultConfiguration.orientationDimension.enableAll();
    defaultConfiguration.setNonEditable();

    CloudConfigurationImpl defaultSparkConfiguration =
      new CloudConfigurationImpl(CloudConfigurationImpl.DEFAULT_FREE_TIER_MATRIX_CONFIGURATION_ID, "Sample Spark configuration", CloudConfiguration.Kind.MATRIX, AndroidIcons.Display, facet);
    defaultSparkConfiguration.deviceDimension.enable(DeviceDimension.getFullDomain(), Arrays.asList("Nexus9", "shamu"));
    defaultSparkConfiguration.apiDimension.enable(ApiDimension.getFullDomain(), Arrays.asList("22", "23"));
    defaultSparkConfiguration.languageDimension.enableDefault();
    defaultSparkConfiguration.orientationDimension.enableDefault();
    defaultSparkConfiguration.setNonEditable();

    return ImmutableList.of(defaultSparkConfiguration, defaultConfiguration);
  }

  @NotNull
  public static List<? extends CloudConfiguration> getAllCloudConfigurations(@NotNull AndroidFacet facet) {
    try {
      CloudAuthenticator.getInstance().prepareCredential();
    } catch(Exception e) {
      return Lists.newArrayList();
    }

    List<CloudPersistentConfiguration> cloudPersistentConfigurations =
      CloudCustomPersistentConfigurations.getInstance(facet.getModule()).getState().myCloudPersistentConfigurations;
    return Lists.newArrayList(Iterables.concat(deserializeConfigurations(cloudPersistentConfigurations, true, facet),
                                               getDefaultConfigurations(facet, CloudConfiguration.Kind.MATRIX),
                                               getDefaultConfigurations(facet, CloudConfiguration.Kind.SINGLE_DEVICE)));
  }

  @Nullable
  public static CloudConfiguration openMatrixConfigurationDialog(@NotNull AndroidFacet currentFacet,
                                                                 @Nullable CloudConfiguration selectedConfiguration,
                                                                 @NotNull CloudConfiguration.Kind configurationKind) {

    final CloudConfiguration.Kind selectedConfigurationKind =
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
  private static String getDefaultBucketName(@NotNull Project project, @NotNull String cloudProjectId) {
    try {
      return CloudAuthenticator.getInstance().getToolresults().projects().initializeSettings(cloudProjectId).execute().getDefaultBucket();
    } catch (Exception e) {
      CloudTestingUtils
        .showErrorMessage(project, "Firebase test configuration is invalid",
                          "Failed to get the default bucket name! Please select a project you are authorized to use.\n"
                          + "Exception while getting the default bucket name\n\n" + e.getMessage());
      return null;
    }
  }

  public static void launchCloudDevice(int selectedConfigurationId, @NotNull String cloudProjectId, @NotNull AndroidFacet facet) {
    UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                     .setCategory(EventCategory.CLOUD_TESTING)
                                     .setKind(EventKind.CLOUD_TESTING_LAUNCH_CLOUD_DEVICE));

    CloudConfigurationImpl cloudConfiguration = CloudTestingUtils.getConfigurationById(selectedConfigurationId, facet);

    if (cloudConfiguration.getKind() != CloudConfiguration.Kind.SINGLE_DEVICE) {
      // Should handle only single device configurations.
      return;
    }

    lastCloudProjectId = cloudProjectId;
    String configurationInstance = cloudConfiguration.computeConfigurationInstances(ConfigurationInstance.ENCODED_NAME_DELIMITER).get(0);
    launchCloudDevice(configurationInstance);
  }

  public static void launchCloudDevice(String configurationInstance) {
    if (!checkJavaVersion()) {
      return;
    }

    String publicKey;
    JSch jsch = new JSch();
    try {
      publicKey = generateSshKeys(jsch);
    } catch (Exception e) {
      CloudTestingUtils.showErrorMessage(null, "Error launching a firebase device", "Failed to launch a firebase device!\n" +
                                                                                 "Exception while generating ssh keys\n\n" +
                                                                                 e.getMessage());
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
      createdDevice =
        CloudAuthenticator.getInstance().getTest().projects().devices().create(cloudProjectId, device).setSshPublicKey(publicKey).execute();
    } catch (Exception e) {
      CloudTestingUtils.showErrorMessage(null, "Error launching a firebase device", "Failed to launch a firebase device!\n" +
                                                                                 "Exception while launching a firebase device\n\n" +
                                                                                 e.getMessage());
      return;
    }
    if (createdDevice == null) {
      CloudTestingUtils.showErrorMessage(null, "Error launching a firebase device", "Failed to launch a firebase device!\n" +
                                                                                 "Could not access firebase device\n\n");
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
          CloudAuthenticator.getInstance().getTest().projects().devices().delete(cloudProjectId, deviceId).execute();
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
    File androidSdkPath = IdeSdks.getInstance().getAndroidSdkPath();
    assert androidSdkPath != null;
    String sdkPath = androidSdkPath.getAbsolutePath() + "/platform-tools";
    File workingDir = new File(sdkPath);
    try {
      while (System.currentTimeMillis() < stopTime) {
        synchronized (ghostCloudDevices) {
          if (!ghostCloudDevices.contains(ghostCloudDevice)) {
            // Blank VNC Viewer was closed, so stop waiting for the device.
            return;
          }
        }
        createdDevice = CloudAuthenticator.getInstance().getTest().projects().devices().get(cloudProjectId, deviceId).execute();
        System.out.println("Polling for device... (time: " + System.currentTimeMillis() + ", status: " + createdDevice.getState() + ")");
        if (createdDevice.getState().equals("DEVICE_ERROR")) {
          CloudTestingUtils.showErrorMessage(null, "Error launching a firebase device", "Failed to launch a firebase device!\n" +
                                                                                     "The polled firebase device has ERROR state\n\n");
          return;
        }
        if (createdDevice.getState().equals("READY")) {
          String ipAddress = createdDevice.getDeviceDetails().getConnectionInfo().getIpAddress();
          Integer adbPort = createdDevice.getDeviceDetails().getConnectionInfo().getAdbPort();
          Integer vncPort = createdDevice.getDeviceDetails().getConnectionInfo().getVncPort();
          Integer sshPort = createdDevice.getDeviceDetails().getConnectionInfo().getSshPort();
          String vncPassword = createdDevice.getDeviceDetails().getConnectionInfo().getVncPassword();

          Session session;
          try {
            session = connectSession(jsch, ipAddress, sshPort);
          } catch (Exception e) {
            CloudTestingUtils.showErrorMessage(null, "Error launching a firebase device", "Failed to launch a firebase device!\n" +
                                                                                       "Exception while connecting through SSH\n\n" +
                                                                                       e.getMessage());
            return;
          }

          int adbLocalPort;
          try {
            adbLocalPort = session.setPortForwardingL(0, "localhost", adbPort);
          } catch (Exception e) {
            CloudTestingUtils.showErrorMessage(null, "Error launching a firebase device", "Failed to launch a firebase device!\n" +
                                                                                       "Exception while tunneling through SSH\n\n" +
                                                                                       e.getMessage());
            return;
          }

          int vncLocalPort;
          try {
            vncLocalPort = session.setPortForwardingL(0, "localhost", vncPort);
          } catch (Exception e) {
            CloudTestingUtils.showErrorMessage(null, "Error launching a firebase device", "Failed to launch a firebase device!\n" +
                                                                                       "Exception while tunneling through SSH\n\n" +
                                                                                       e.getMessage());
            return;
          }

          String deviceAddress = "localhost:" + adbLocalPort;
          System.out.println("Device ready with IP address:port " + deviceAddress);
          File adbFile = new File(workingDir, System.getProperty("os.name").toLowerCase().indexOf("win") != -1 ? "adb.exe" : "adb");
          Runtime runtime = Runtime.getRuntime();
          Process connect = runtime.exec(new String[]{adbFile.getAbsolutePath(), "connect", deviceAddress}, null, workingDir);
          connect.waitFor();
          serialNumberToConfigurationInstance.put(deviceAddress, configurationInstance);
          // Do not wait for "finally" to remove the ghost device
          // to minimize the time both a ghost device and an actual firebase device are present in the devices table.
          synchronized (ghostCloudDevices) {
            ghostCloudDevices.remove(ghostCloudDevice);
          }
          // Do not wait for "finally" to close the blank window to avoid showing both blank and real VNC windows at the same time.
          try { // Use try just in case something goes wrong.
            blankVncViewer.closeWindow();
            blankVncViewer = null;
          } catch (Exception e) {
            e.printStackTrace();
          }
          // Make sure the device is unlocked.
          Process unlock = runtime.exec(
            new String[]{adbFile.getAbsolutePath(), "-s", deviceAddress, "wait-for-device", "shell", "input", "keyevent", "82"}, null, workingDir);
          unlock.waitFor();
          // Open the VNC window for the firebase device.
          String[] viewerArgs = new String[]{"-port=" + vncLocalPort, "-host=localhost", "-password=" + vncPassword, "-fullScreen=false"};
          VncKeepAliveThreadImpl.startVnc(viewerArgs, configurationName, cloudProjectId, deviceId, deviceAddress, workingDir);
          return;
        }
        Thread.sleep(POLLING_INTERVAL);
      }
      CloudTestingUtils.showErrorMessage(null, "Timed out connecting to a firebase device", "Timed out connecting to a firebase device!\n" +
                                                                                         "Timed out connecting to a firebase device:\n\n" +
                                                                                         deviceId);
    } catch (Exception e) {
      showCloudDevicePollingError(e, deviceId);
    } finally {
      synchronized (ghostCloudDevices) {
        ghostCloudDevices.remove(ghostCloudDevice);
      }
      if (blankVncViewer != null) {
        try { // Use try just in case something goes wrong.
          blankVncViewer.closeWindow();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static String getConfigurationInstanceForSerialNumber(String serialNumber) {
    return serialNumberToConfigurationInstance.get(serialNumber);
  }

  @NotNull
  public static Collection<IDevice> getLaunchingCloudDevices() {
    synchronized (ghostCloudDevices) {
      HashSet<IDevice> launchingCloudDevices = Sets.newHashSet();
      launchingCloudDevices.addAll(ghostCloudDevices);
      return launchingCloudDevices;
    }
  }

  @Nullable
  public static Icon getCloudDeviceIcon() {
    return CloudTestingUtils.CLOUD_DEVICE_ICON;
  }

  @Nullable
  public static String getCloudDeviceConfiguration(IDevice device) {
    String encodedConfigurationInstance = device instanceof GhostCloudDevice
                                          ? ((GhostCloudDevice)device).getEncodedConfigurationInstance()
                                          : serialNumberToConfigurationInstance.get(device.getSerialNumber());
    if (encodedConfigurationInstance != null) {
      return ConfigurationInstance.parseFromEncodedString(encodedConfigurationInstance).getResultsViewerDisplayString();
    }
    return null;
  }

  private static void showCloudDevicePollingError(Exception e, String deviceId) {
    CloudTestingUtils.showErrorMessage(null, "Error polling for a firebase device", "Failed to connect to a firebase device!\n" +
                                                                                 "Exception while polling for a firebase device\n\n" +
                                                                                 deviceId +
                                                                                 e.getMessage());
  }

  public static ExecutionResult executeCloudMatrixTests(
    int selectedConfigurationId, String cloudProjectId, CloudMatrixTestRunningState runningState, Executor executor)
    throws ExecutionException {
    UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                     .setCategory(EventCategory.CLOUD_TESTING)
                                     .setKind(EventKind.CLOUD_TESTING_RUN_TEST_MATRIX));

    Project project = runningState.getFacet().getModule().getProject();

    String bucketName = getDefaultBucketName(project, cloudProjectId);

    if (bucketName == null) {
      // Cloud project is invalid, nothing to do.
      return null;
    }

    CloudConfigurationImpl cloudConfiguration = CloudTestingUtils.getConfigurationById(selectedConfigurationId, runningState.getFacet());

    if (cloudConfiguration.getKind() != CloudConfiguration.Kind.MATRIX) {
      // Should handle only matrix configurations.
      return null;
    }

    lastCloudProjectId = cloudProjectId;

    AndroidTestRunConfiguration testRunConfiguration = runningState.getConfiguration();
    GoogleCloudTestConsoleProperties properties = new GoogleCloudTestConsoleProperties(testRunConfiguration, executor);
    CloudMatrixExecutionCancellator matrixExecutionCancellator = new CloudMatrixExecutionCancellator();
    ConsoleView console = GoogleCloudTestResultsConnectionUtil.createAndAttachConsole(
      "Firebase Testing", runningState.getProcessHandler(), properties, runningState.getEnvironment(), matrixExecutionCancellator);
    Disposer.register(project, console);

    GoogleCloudTestingResultParser
      cloudResultParser = new GoogleCloudTestingResultParser("Firebase Test Run", new GoogleCloudTestListener(runningState));

    List<String> expectedConfigurationInstances =
      cloudConfiguration.computeConfigurationInstances(ConfigurationInstance.DISPLAY_NAME_DELIMITER);
    for (String configurationInstance : expectedConfigurationInstances) {
      cloudResultParser.getTestRunListener().testConfigurationScheduled(configurationInstance);
    }
    GoogleCloudTestingDeveloperConfigurable.GoogleCloudTestingDeveloperState googleCloudTestingDeveloperState =
      GoogleCloudTestingDeveloperSettings.getInstance(project).getState();
    if (!googleCloudTestingDeveloperState.shouldUseFakeBucket) {
      performTestsInCloud(cloudConfiguration, cloudProjectId, bucketName, runningState, cloudResultParser, matrixExecutionCancellator);
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

  static boolean isCloudOptionEnabledRemotely(boolean isDebugging) {
    final String publicBucketName = "cloud-testing-plugin-enablement";
    final String triggerFileName = isDebugging ? "DEBUGGING_ENABLED" : "TESTING_ENABLED";
    try {
      Storage.Objects.List objects = CloudAuthenticator.getInstance().getPublicStorage().objects().list(publicBucketName);
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

  private static void performTestsInCloud(final CloudConfigurationImpl cloudTestingConfiguration, final String cloudProjectId,
                                          final String bucketName, final CloudMatrixTestRunningState runningState,
                                          final GoogleCloudTestingResultParser cloudResultParser,
                                          final CloudMatrixExecutionCancellator matrixExecutionCancellator) {
    if (cloudTestingConfiguration != null && cloudTestingConfiguration.getDeviceConfigurationCount() > 0) {
      final List<String> expectedConfigurationInstances =
        cloudTestingConfiguration.computeConfigurationInstances(ConfigurationInstance.DISPLAY_NAME_DELIMITER);
      new Thread(new Runnable() {
        @Override
        public void run() {
          AndroidTestRunConfiguration testRunConfiguration = runningState.getConfiguration();

          if (matrixExecutionCancellator.isCancelled()) {
            return;
          }
          runningState.getProcessHandler().notifyTextAvailable(
            prepareProgressString("Using Cloud Storage Bucket " + bucketName + " ...", ""), ProcessOutputTypes.STDOUT);

          if (matrixExecutionCancellator.isCancelled()) {
            return;
          }

          // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
          AndroidModuleModel androidModel = AndroidModuleModel.get(runningState.getFacet());
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

          AndroidArtifact testArtifactInfo = androidModel.getAndroidTestArtifactInSelectedVariant();

          if (testArtifactInfo == null) {
            CloudTestingUtils.showErrorMessage(runningState.getFacet().getModule().getProject(), "Error uploading APKs",
                                               "Could not find your Android test artifact!\n");
            return;
          }

          List<AndroidArtifactOutput> testOutputs = Lists.newArrayList(testArtifactInfo.getOutputs());
          if (testOutputs.isEmpty()) {
            CloudTestingUtils.showErrorMessage(runningState.getFacet().getModule().getProject(), "Error finding test APK",
                                               "Could not find your test APK!\n");
            return;
          }
          File testApk = testOutputs.get(0).getMainOutputFile().getOutputFile();

          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Uploading app APK ...", ""),
                                                               ProcessOutputTypes.STDOUT);
          String appApkName = CloudTestsLauncher.uploadFile(bucketName, appApk).getName();

          if (matrixExecutionCancellator.isCancelled()) {
            return;
          }
          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Uploading test APK ...", ""),
                                                               ProcessOutputTypes.STDOUT);
          String testApkName = CloudTestsLauncher.uploadFile(bucketName, testApk).getName();

          if (matrixExecutionCancellator.isCancelled()) {
            return;
          }
          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Submitting tests to Firebase Test Lab ...", ""),
                                                               ProcessOutputTypes.STDOUT);
          String testSpecification = CloudTestingUtils.prepareTestSpecification(testRunConfiguration);

          TestMatrix testMatrix = CloudTestsLauncher
            .triggerTestApi(cloudProjectId, getBucketGcsPath(bucketName), getApkGcsPath(bucketName, appApkName),
                            getApkGcsPath(bucketName, testApkName), testSpecification, testRunConfiguration.INSTRUMENTATION_RUNNER_CLASS,
                            cloudTestingConfiguration);

          if (testMatrix != null) {
            runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Validating APKs ...", "\n\n"),
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

  private static String getBucketGcsPath(String bucketName) {
    return "gs://" + bucketName;
  }

  private static String getApkGcsPath(String bucketName, String apkName) {
    return "gs://" + bucketName + "/" + apkName;
  }

  private static void addCloudResultsAdapter(String testRunId, CloudResultsAdapter cloudResultsAdapter) {
    if (testRunIdToCloudResultsAdapter.get(testRunId) != null) {
      throw new IllegalStateException("Cannot add more than one firebase results adapter for test run id: " + testRunId);
    }
    testRunIdToCloudResultsAdapter.put(testRunId, cloudResultsAdapter);
  }

  private static void addCloudConfiguration(String testRunId, CloudConfigurationImpl cloudConfiguration) {
    if (testRunIdToCloudConfiguration.get(testRunId) != null) {
      throw new IllegalStateException("Cannot add more than one firebase configuration for test run id: " + testRunId);
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

  private static String generateSshKeys(JSch jsch) throws JSchException {
    KeyPair keyPair = KeyPair.genKeyPair(jsch, RSA, 2048);

    // Setting 'comment' is by convention only. Pass an empty string if this code breaks on some OS.
    String comment = System.getProperty("user.home");
    if (comment == null) {
      comment = "";
    } else {
      try {
        comment = comment + InetAddress.getLocalHost().getHostName();
      }
      catch (Exception e) {
        // ignore
      }
    }

    ByteArrayOutputStream privateKeyArray = new ByteArrayOutputStream();
    keyPair.writePrivateKey(privateKeyArray);
    ByteArrayOutputStream publicKeyArray = new ByteArrayOutputStream();
    keyPair.writePublicKey(publicKeyArray, comment);

    jsch.addIdentity("root", privateKeyArray.toByteArray(), publicKeyArray.toByteArray(), null);

    keyPair.dispose();

    return "root:" + new String(publicKeyArray.toByteArray());
  }

  /**
   * Returns the session after connecting.
   */
  private static Session connectSession(JSch jsch, String rhost, int sshPort) throws Exception {
    Session session = jsch.getSession("root", rhost, sshPort);
    Properties config = new Properties();
    config.put("StrictHostKeyChecking", "no");
    session.setConfig(config);
    session.setTimeout(30*1000); // 30 seconds.

    try {
      session.connect();
    } catch (JSchException e) {
      throw new RuntimeException(String.format("%s@%s:%d: Error connecting to session.", "root", rhost, sshPort), e);
    }
    return session;
  }
}
