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

import com.android.tools.idea.run.CloudTestConfiguration;
import com.android.tools.idea.run.CloudTestConfigurationProvider;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Buckets;
import com.google.api.services.test.model.TestExecution;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gct.testing.config.GoogleCloudTestingConfigurable;
import com.google.gct.testing.config.GoogleCloudTestingDeveloperConfigurable;
import com.google.gct.testing.config.GoogleCloudTestingDeveloperSettings;
import com.google.gct.testing.config.GoogleCloudTestingSettings;
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
import java.util.*;

public class CloudTestConfigurationProviderImpl extends CloudTestConfigurationProvider {

  private static final String TEST_RUN_ID_PREFIX = "GoogleCloudTest:";
  private static final Map<String, CloudTestConfigurationImpl> testRunIdToCloudConfiguration = new HashMap<String, CloudTestConfigurationImpl>();
  private static final Map<String, CloudResultsAdapter> testRunIdToCloudResultsAdapter = new HashMap<String, CloudResultsAdapter>();

  public static final Icon DEFAULT_ICON = AndroidIcons.AndroidFile;

  public static final Function<CloudTestConfiguration, CloudTestConfigurationImpl> CLONE_CONFIGURATIONS =
    new Function<CloudTestConfiguration, CloudTestConfigurationImpl>() {
      @Override
      public CloudTestConfigurationImpl apply(CloudTestConfiguration configuration) {
        return ((CloudTestConfigurationImpl) configuration).clone();
      }
    };

  public static final Function<CloudTestConfiguration, CloudTestConfigurationImpl> CAST_CONFIGURATIONS =
    new Function<CloudTestConfiguration, CloudTestConfigurationImpl>() {
      @Override
      public CloudTestConfigurationImpl apply(CloudTestConfiguration configuration) {
        return (CloudTestConfigurationImpl) configuration;
      }
    };

  public static Map<String, List<? extends GoogleCloudTestingType>> getAllDimensionTypes() {
    Map<String, List<? extends GoogleCloudTestingType>> dimensionTypes = new HashMap<String, List<? extends GoogleCloudTestingType>>();
    dimensionTypes.put(DeviceDimension.DISPLAY_NAME, DeviceDimension.getFullDomain());
    dimensionTypes.put(ApiDimension.DISPLAY_NAME, ApiDimension.getFullDomain());
    dimensionTypes.put(LanguageDimension.DISPLAY_NAME, LanguageDimension.getFullDomain());
    dimensionTypes.put(OrientationDimension.DISPLAY_NAME, OrientationDimension.getFullDomain());
    return dimensionTypes;
  }

  @NotNull
  @Override
  public List<? extends CloudTestConfiguration> getTestingConfigurations(@NotNull AndroidFacet facet) {
    List<GoogleCloudTestingPersistentConfiguration> googleCloudTestingPersistentConfigurations =
      GoogleCloudTestingCustomPersistentConfigurations.getInstance(facet.getModule()).getState().myGoogleCloudTestingPersistentConfigurations;
    return Lists.newArrayList(Iterables.concat(deserializeConfigurations(googleCloudTestingPersistentConfigurations, true, facet),
                                               getDefaultConfigurations(facet)));
  }

  private List<? extends CloudTestConfiguration> getDefaultConfigurations(AndroidFacet facet) {
    CloudTestConfigurationImpl allConfiguration =
      new CloudTestConfigurationImpl(CloudTestConfigurationImpl.ALL_ID, "All Compatible", AndroidIcons.Display, facet);
    allConfiguration.deviceDimension.enableAll();
    allConfiguration.apiDimension.enableAll();
    allConfiguration.languageDimension.enableAll();
    allConfiguration.orientationDimension.enableAll();
    allConfiguration.setNonEditable();
    return ImmutableList.of(allConfiguration);
    //TODO: For now, there are no default configurations to store/read from the persistent storage (i.e., an xml file).
    //List<GoogleCloudTestingPersistentConfiguration> myGoogleCloudTestingPersistentConfigurations =
    //  GoogleCloudTestingDefaultPersistentConfigurations.getInstance(facet.getModule()).getState().myGoogleCloudTestingPersistentConfigurations;
    //return ImmutableList.copyOf(deserializeConfigurations(myGoogleCloudTestingPersistentConfigurations, false, facet));
  }

  @Nullable
  @Override
  public CloudTestConfiguration openMatrixConfigurationDialog(@NotNull AndroidFacet currentFacet,
                                                              @NotNull CloudTestConfiguration selectedConfiguration) {
    Module currentModule = currentFacet.getModule();
    List<? extends CloudTestConfiguration> testingConfigurations = getTestingConfigurations(currentFacet);

    List<CloudTestConfigurationImpl> castDefaultConfigurations =
      Lists.newArrayList(Iterables.transform(Iterables.filter(testingConfigurations, new Predicate<CloudTestConfiguration>(){
        @Override
        public boolean apply(CloudTestConfiguration testingConfiguration) {
          return !testingConfiguration.isEditable();
        }
      }), CAST_CONFIGURATIONS));

    List<CloudTestConfigurationImpl> copyCustomConfigurations =
      Lists.newArrayList(Iterables.transform(Iterables.filter(testingConfigurations, new Predicate<CloudTestConfiguration>(){
        @Override
        public boolean apply(CloudTestConfiguration testingConfiguration) {
          return testingConfiguration.isEditable();
        }
      }), CLONE_CONFIGURATIONS));

    if (selectedConfiguration == null) {
      selectedConfiguration = new CloudTestConfigurationImpl(currentFacet);
    }

    CloudConfigurationChooserDialog dialog =
      new CloudConfigurationChooserDialog(currentModule, copyCustomConfigurations, castDefaultConfigurations,
                                          (CloudTestConfigurationImpl) selectedConfiguration);

    dialog.show();
    if (dialog.isOK()) {
      //Persist the edited configurations.
      GoogleCloudTestingPersistentState customState = new GoogleCloudTestingPersistentState();
      customState.myGoogleCloudTestingPersistentConfigurations = Lists.newArrayList(
        Iterables.transform(copyCustomConfigurations,
                            new Function<CloudTestConfigurationImpl, GoogleCloudTestingPersistentConfiguration>() {
                              @Override
                              public GoogleCloudTestingPersistentConfiguration apply(CloudTestConfigurationImpl configuration) {
                                return configuration.getPersistentConfiguration();
                              }
                            }));
      GoogleCloudTestingCustomPersistentConfigurations.getInstance(currentModule).loadState(customState);

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
        GoogleCloudTestingUtils
          .showErrorMessage(project, "Cloud test configuration is invalid",
                            "Failed to authorize to Google Cloud project! Please select a project you are authorized to use.\n"
                            + "Exception while performing a pre-trigger sanity check\n\n" + message);
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean supportsDebugging() {
    return false;
  }

  @Override
  public ExecutionResult execute(int selectedConfigurationId, String cloudProjectId, AndroidRunningState runningState, Executor executor) throws ExecutionException {

    Project project = runningState.getFacet().getModule().getProject();

    if (!validateCloudProject(project, cloudProjectId)) {
      // Cloud project is invalid, nothing to do.
      return null;
    }

    AndroidTestRunConfiguration testRunConfiguration = (AndroidTestRunConfiguration) runningState.getConfiguration();
    AndroidTestConsoleProperties properties =
      new AndroidTestConsoleProperties(testRunConfiguration, executor);
    ConsoleView console = GoogleCloudTestResultsConnectionUtil
      .createAndAttachConsole("Google Cloud Testing", runningState.getProcessHandler(), properties, runningState.getEnvironment());
    Disposer.register(project, console);

    GoogleCloudTestingResultParser
      cloudResultParser = new GoogleCloudTestingResultParser("Cloud Test Run", new GoogleCloudTestListener(runningState));

    CloudTestConfigurationImpl googleCloudTestingConfiguration = GoogleCloudTestingUtils
      .getConfigurationById(selectedConfigurationId, runningState.getFacet());
    List<String> expectedConfigurationInstances =
      googleCloudTestingConfiguration.computeConfigurationInstances(ConfigurationInstance.DISPLAY_NAME_DELIMITER);
    for (String configurationInstance : expectedConfigurationInstances) {
      cloudResultParser.getTestRunListener().testConfigurationScheduled(configurationInstance);
    }
    GoogleCloudTestingDeveloperConfigurable.GoogleCloudTestingDeveloperState googleCloudTestingDeveloperState =
      GoogleCloudTestingDeveloperSettings.getInstance(project).getState();
    if (!googleCloudTestingDeveloperState.shouldUseFakeBucket) {
      performTestsInCloud(googleCloudTestingConfiguration, cloudProjectId, runningState, cloudResultParser);
    } else {
      String testRunId = TEST_RUN_ID_PREFIX + googleCloudTestingDeveloperState.fakeBucketName + System.currentTimeMillis();
      CloudResultsAdapter cloudResultsAdapter =
        new CloudResultsAdapter(cloudProjectId, googleCloudTestingDeveloperState.fakeBucketName, cloudResultParser, expectedConfigurationInstances,
                                testRunId, null);
      addGoogleCloudTestingConfiguration(testRunId, googleCloudTestingConfiguration);
      addCloudResultsAdapter(testRunId, cloudResultsAdapter);
      cloudResultsAdapter.startPolling();
    }
    return new DefaultExecutionResult(console, runningState.getProcessHandler());
  }

  @Override
  public boolean isCloudTestingOptionEnabled(Project project) {
    GoogleCloudTestingConfigurable.GoogleCloudTestingState googleCloudTestingState =
      GoogleCloudTestingSettings.getInstance(project).getState();
    return googleCloudTestingState != null && googleCloudTestingState.enableCloudTesting;
  }

  private void performTestsInCloud(final CloudTestConfigurationImpl googleCloudTestingConfiguration, final String cloudProjectId,
                                   final AndroidRunningState runningState, final GoogleCloudTestingResultParser cloudResultParser) {
    if (googleCloudTestingConfiguration != null && googleCloudTestingConfiguration.getDeviceConfigurationCount() > 0) {
      final List<String> encodedMatrixInstances =
        googleCloudTestingConfiguration.computeConfigurationInstances(ConfigurationInstance.ENCODED_NAME_DELIMITER);
      final List<String> expectedConfigurationInstances =
        googleCloudTestingConfiguration.computeConfigurationInstances(ConfigurationInstance.DISPLAY_NAME_DELIMITER);
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
            GoogleCloudTestingUtils.showErrorMessage(runningState.getFacet().getModule().getProject(), "Error uploading app APK",
                                                     "Failed to find a supported app APK format!\n" +
                                                     "There is no supported app APK among the existing ones\n\n" + listAllApks(apkPaths));
            return;
          }
          String appApkName = CloudTestsLauncher.uploadFile(bucketName, appApk).getName();

          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Uploading test APK...", ""),
                                                               ProcessOutputTypes.STDOUT);
          File testApk = findAppropriateApk(apkPaths, true);
          if (testApk == null) {
            GoogleCloudTestingUtils.showErrorMessage(runningState.getFacet().getModule().getProject(), "Error uploading test APK",
                                                     "Failed to find a supported test APK format!\n" +
                                                     "There is no supported test APK among the existing ones\n\n" + listAllApks(apkPaths));
            return;
          }
          String testApkName = CloudTestsLauncher.uploadFile(bucketName, testApk).getName();

          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Invoking cloud test API...", "\n"),
                                                               ProcessOutputTypes.STDOUT);
          String testSpecification = GoogleCloudTestingUtils.prepareTestSpecification(testRunConfiguration);

          Map<String, TestExecution> testExecutions = CloudTestsLauncher
            .triggerTestApi(cloudProjectId, moduleName, getBucketGcsPath(bucketName), getApkGcsPath(bucketName, appApkName),
                            getApkGcsPath(bucketName, testApkName), testSpecification, testRunConfiguration.INSTRUMENTATION_RUNNER_CLASS,
                            encodedMatrixInstances, appPackage, testPackage);

          String testRunId = TEST_RUN_ID_PREFIX + bucketName;
          CloudResultsAdapter cloudResultsAdapter =
            new CloudResultsAdapter(cloudProjectId, bucketName, cloudResultParser, expectedConfigurationInstances, testRunId,
                                    testExecutions);
          addGoogleCloudTestingConfiguration(testRunId, googleCloudTestingConfiguration);
          addCloudResultsAdapter(testRunId, cloudResultsAdapter);
          cloudResultsAdapter.startPolling();
        }

        private String prepareProgressString(String progressMessage, String suffix) {
          return GoogleCloudTestingUtils.shouldShowProgressTimestamps()
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

  private static void addGoogleCloudTestingConfiguration(
    String testRunId, CloudTestConfigurationImpl googleCloudTestingConfiguration) {
    if (testRunIdToCloudConfiguration.get(testRunId) != null) {
      throw new IllegalStateException("Cannot add more than one cloud configuration for test run id: " + testRunId);
    }
    testRunIdToCloudConfiguration.put(testRunId, googleCloudTestingConfiguration);
  }

  public static CloudTestConfigurationImpl getSelectedGoogleCloudTestingConfiguration(String testRunId) {
    return testRunIdToCloudConfiguration.get(testRunId);
  }

  public static CloudResultsAdapter getCloudResultsAdapter(String testRunId) {
    return testRunIdToCloudResultsAdapter.get(testRunId);
  }

  public static List<CloudTestConfigurationImpl> deserializeConfigurations(
    final List<GoogleCloudTestingPersistentConfiguration> persistentConfigurations, boolean isEditable, AndroidFacet facet) {
    List<CloudTestConfigurationImpl> googleCloudTestingConfigurations = new LinkedList<CloudTestConfigurationImpl>();
    for (GoogleCloudTestingPersistentConfiguration persistentConfiguration : persistentConfigurations) {
      Icon icon = getIcon(persistentConfiguration.name, isEditable);
      CloudTestConfigurationImpl configuration =
        new CloudTestConfigurationImpl(persistentConfiguration.id, persistentConfiguration.name, icon, facet);
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
