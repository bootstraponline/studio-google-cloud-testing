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

import com.android.tools.idea.run.CloudConfiguration;
import com.android.tools.idea.run.CloudConfiguration.Kind;
import com.android.tools.idea.run.CloudConfigurationProvider;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Buckets;
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
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessHandler;
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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;

import static com.android.tools.idea.run.CloudConfiguration.Kind.MATRIX;
import static com.android.tools.idea.run.CloudConfiguration.Kind.SINGLE_DEVICE;
import static com.google.gct.testing.launcher.CloudAuthenticator.getTest;

public class CloudConfigurationProviderImpl extends CloudConfigurationProvider {

  private static final String TEST_RUN_ID_PREFIX = "GoogleCloudTest:";
  private static final Map<String, CloudConfigurationImpl> testRunIdToCloudConfiguration = new HashMap<String, CloudConfigurationImpl>();
  private static final Map<String, CloudResultsAdapter> testRunIdToCloudResultsAdapter = new HashMap<String, CloudResultsAdapter>();

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

  public static Map<String, List<? extends CloudTestingType>> getAllDimensionTypes() {
    Map<String, List<? extends CloudTestingType>> dimensionTypes = new HashMap<String, List<? extends CloudTestingType>>();
    dimensionTypes.put(DeviceDimension.DISPLAY_NAME, DeviceDimension.getFullDomain());
    dimensionTypes.put(ApiDimension.DISPLAY_NAME, ApiDimension.getFullDomain());
    dimensionTypes.put(LanguageDimension.DISPLAY_NAME, LanguageDimension.getFullDomain());
    dimensionTypes.put(OrientationDimension.DISPLAY_NAME, OrientationDimension.getFullDomain());
    return dimensionTypes;
  }

  @NotNull
  @Override
  public List<? extends CloudConfiguration> getCloudConfigurations(@NotNull AndroidFacet facet, @NotNull final Kind configurationKind) {

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
      CloudConfigurationImpl defaultConfiguration = new CloudConfigurationImpl(
        CloudConfigurationImpl.DEFAULT_DEVICE_CONFIGURATION_ID, "Default Device", SINGLE_DEVICE, AndroidIcons.Display, facet);
      defaultConfiguration.deviceDimension.enable(DeviceDimension.getDefaultDevice());
      defaultConfiguration.apiDimension.enable(ApiDimension.getDefaultApi());
      defaultConfiguration.languageDimension.enable(LanguageDimension.getDefaultLanguage());
      defaultConfiguration.orientationDimension.enable(OrientationDimension.getDefaultOrientation());
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
    } catch (Throwable t) {
      message = t.getMessage();
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

    CloudConfigurationImpl cloudConfiguration = CloudTestingUtils.getConfigurationById(selectedConfigurationId, facet);

    if (cloudConfiguration.getKind() != Kind.SINGLE_DEVICE) {
      // Should handle only single device configurations.
      return;
    }

    String[] dimensionValues =
      cloudConfiguration.computeConfigurationInstances(ConfigurationInstance.ENCODED_NAME_DELIMITER).get(0).split("-");
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

    final long POLLING_INTERVAL = 10 * 1000; // 10 seconds
    final long INITIAL_TIMEOUT = 10 * 60 * 1000; // 10 minutes
    long stopTime = System.currentTimeMillis() + INITIAL_TIMEOUT;
    String sdkPath = IdeSdks.getAndroidSdkPath().getAbsolutePath() + "/platform-tools";
    File dir = new File(sdkPath);
    while (System.currentTimeMillis() < stopTime) {
      try {
        createdDevice = getTest().projects().devices().get(cloudProjectId, createdDevice.getId()).execute();
        System.out.println("Polling for device... (time: " + System.currentTimeMillis() + ", status: " + createdDevice.getState() + ")");
        if (createdDevice.getState().equals("READY")) {
        //if (createdDevice.getDeviceDetails().getConnectionInfo() != null) {
          String ipAddress = createdDevice.getDeviceDetails().getConnectionInfo().getIpAddress();
          Integer adbPort = createdDevice.getDeviceDetails().getConnectionInfo().getAdbPort();
          System.out.println("Device ready with IP address:port " + ipAddress + ":" + adbPort);
          Runtime rt = Runtime.getRuntime();
          //Process startServer = rt.exec("./adb start-server", null, dir);
          //startServer.waitFor();
          Process connect = rt.exec("./adb connect " + ipAddress + ":" + adbPort, null, dir);
          connect.waitFor();
          return;
        }
        Thread.sleep(POLLING_INTERVAL);
      } catch (IOException e) {
        showCloudDevicePollingError(e, createdDevice.getId());
      } catch (InterruptedException e) {
        showCloudDevicePollingError(e, createdDevice.getId());
      }
    }
    CloudTestingUtils.showErrorMessage(null, "Timed out connecting to a cloud device", "Timed out connecting to a cloud device!\n" +
                                                                                       "Timed out connecting to a cloud device:\n\n" +
                                                                                       createdDevice.getId());
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

    AndroidTestRunConfiguration testRunConfiguration = (AndroidTestRunConfiguration) runningState.getConfiguration();
    AndroidTestConsoleProperties properties =
      new AndroidTestConsoleProperties(testRunConfiguration, executor);
    ConsoleView console = GoogleCloudTestResultsConnectionUtil
      .createAndAttachConsole("Cloud Testing", runningState.getProcessHandler(), properties, runningState.getEnvironment());
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
      performTestsInCloud(cloudConfiguration, cloudProjectId, runningState, cloudResultParser);
    }
    else {
      String testRunId = TEST_RUN_ID_PREFIX + googleCloudTestingDeveloperState.fakeBucketName + System.currentTimeMillis();
      CloudResultsAdapter cloudResultsAdapter =
        new CloudResultsAdapter(cloudProjectId, googleCloudTestingDeveloperState.fakeBucketName, cloudResultParser,
                                expectedConfigurationInstances, testRunId, null);
      addCloudConfiguration(testRunId, cloudConfiguration);
      addCloudResultsAdapter(testRunId, cloudResultsAdapter);
      cloudResultsAdapter.startPolling();
    }
    return new DefaultExecutionResult(console, runningState.getProcessHandler());
  }

  private void performTestsInCloud(final CloudConfigurationImpl cloudTestingConfiguration, final String cloudProjectId,
                                   final AndroidRunningState runningState, final GoogleCloudTestingResultParser cloudResultParser) {
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

          runningState.getProcessHandler().notifyTextAvailable(
            prepareProgressString("Creating Cloud Storage bucket " + bucketName + "...", ""), ProcessOutputTypes.STDOUT);
          CloudTestsLauncher.createBucket(cloudProjectId, bucketName);

          List<String> apkPaths = getApkPaths(runningState);
          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Uploading app APK...", ""),
                                                               ProcessOutputTypes.STDOUT);
          File appApk = findAppropriateApk(apkPaths, false);
          if (appApk == null) {
            CloudTestingUtils.showErrorMessage(runningState.getFacet().getModule().getProject(), "Error uploading app APK",
                                               "Failed to find a supported app APK format!\n" +
                                               "There is no supported app APK among the existing ones\n\n" + listAllApks(apkPaths));
            return;
          }
          String appApkName = CloudTestsLauncher.uploadFile(bucketName, appApk).getName();

          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Uploading test APK...", ""),
                                                               ProcessOutputTypes.STDOUT);
          File testApk = findAppropriateApk(apkPaths, true);
          if (testApk == null) {
            CloudTestingUtils.showErrorMessage(runningState.getFacet().getModule().getProject(), "Error uploading test APK",
                                               "Failed to find a supported test APK format!\n" +
                                               "There is no supported test APK among the existing ones\n\n" + listAllApks(apkPaths));
            return;
          }
          String testApkName = CloudTestsLauncher.uploadFile(bucketName, testApk).getName();

          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Invoking cloud test API...", "\n"),
                                                               ProcessOutputTypes.STDOUT);
          String testSpecification = CloudTestingUtils.prepareTestSpecification(testRunConfiguration);

          TestMatrix testMatrix = CloudTestsLauncher
            .triggerTestApi(cloudProjectId, getBucketGcsPath(bucketName), getApkGcsPath(bucketName, appApkName),
                            getApkGcsPath(bucketName, testApkName), testSpecification, testRunConfiguration.INSTRUMENTATION_RUNNER_CLASS,
                            cloudTestingConfiguration, appPackage, testPackage);

          if (testMatrix != null) {
            String testRunId = TEST_RUN_ID_PREFIX + bucketName;
            CloudResultsAdapter cloudResultsAdapter =
              new CloudResultsAdapter(cloudProjectId, bucketName, cloudResultParser, expectedConfigurationInstances, testRunId, testMatrix);
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

  private String listAllApks(List<String> apkPaths) {
    List<String> allApks = new ArrayList<String>();
    for (String apkPath : apkPaths) {
      allApks.addAll(Arrays.asList(new File(apkPath).list(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.endsWith(".apk");
        }
      })));
    }
    String apkList = "";
    for (String apk : allApks) {
      apkList += apk + "\n";
    }
    return apkList;
  }

  private File findAppropriateApk(List<String> apkPaths, final boolean isTestApk) {
    List<File> allApkFiles = new ArrayList<File>();
    for (String apkPath : apkPaths) {
      allApkFiles.addAll(Arrays.asList(new File(apkPath).listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.endsWith(".apk") && (isTestApk ? name.contains("-test") : !name.contains("-test"));
        }
      })));
    }
    if (allApkFiles.size() == 0) {
      return null;
    }

    return Collections.max(allApkFiles, new Comparator<File>() {
      @Override
      public int compare(File file1, File file2) {
        return (int)(file1.lastModified() - file2.lastModified());
      }
    });
  }

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

  private List<String> getApkPaths(AndroidRunningState runningState) {
    String buildPath = runningState.getFacet().getModule().getModuleFile().getParent().getPath() + "/build";
    List<String> apkPaths = new LinkedList<String>();
    addPathIfExists(apkPaths, buildPath + "/apk/");
    addPathIfExists(apkPaths, buildPath + "/outputs/apk/");
    return apkPaths;
  }

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
