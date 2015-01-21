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

import com.android.tools.idea.run.GoogleCloudTestingConfiguration;
import com.android.tools.idea.run.GoogleCloudTestingConfigurationFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Buckets;
import com.google.api.services.test.model.TestExecution;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gct.testing.config.GoogleCloudTestingConfigurable;
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

import javax.swing.*;
import java.io.File;
import java.util.*;

public class GoogleCloudTestingConfigurationFactoryImpl extends GoogleCloudTestingConfigurationFactory {

  private static final String TEST_RUN_ID_PREFIX = "GoogleCloudTest:";
  private static final Map<String, GoogleCloudTestingConfigurationImpl> testRunIdToCloudConfiguration = new HashMap<String, GoogleCloudTestingConfigurationImpl>();
  private static final Map<String, CloudResultsAdapter> testRunIdToCloudResultsAdapter = new HashMap<String, CloudResultsAdapter>();

  public static final Icon DEFAULT_ICON = AndroidIcons.AndroidFile;

  private GoogleCloudTestingConfiguration dialogSelectedConfiguration;
  private String dialogSelectedProjectId;


  public static final Function<GoogleCloudTestingConfiguration, GoogleCloudTestingConfigurationImpl> CLONE_CONFIGURATIONS =
    new Function<GoogleCloudTestingConfiguration, GoogleCloudTestingConfigurationImpl>() {
      @Override
      public GoogleCloudTestingConfigurationImpl apply(GoogleCloudTestingConfiguration configuration) {
        return ((GoogleCloudTestingConfigurationImpl) configuration).clone();
      }
    };

  public static final Function<GoogleCloudTestingConfiguration, GoogleCloudTestingConfigurationImpl> CAST_CONFIGURATIONS =
    new Function<GoogleCloudTestingConfiguration, GoogleCloudTestingConfigurationImpl>() {
      @Override
      public GoogleCloudTestingConfigurationImpl apply(GoogleCloudTestingConfiguration configuration) {
        return (GoogleCloudTestingConfigurationImpl) configuration;
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

  @Override
  public ArrayList<? extends GoogleCloudTestingConfiguration> getTestingConfigurations(AndroidFacet facet) {
    List<GoogleCloudTestingPersistentConfiguration> googleCloudTestingPersistentConfigurations =
      GoogleCloudTestingCustomPersistentConfigurations.getInstance(facet.getModule()).getState().myGoogleCloudTestingPersistentConfigurations;
    return Lists.newArrayList(Iterables.concat(deserializeConfigurations(googleCloudTestingPersistentConfigurations, true, facet),
                                               getDefaultConfigurations(facet)));
  }

  private List<? extends GoogleCloudTestingConfiguration> getDefaultConfigurations(AndroidFacet facet) {
    GoogleCloudTestingConfigurationImpl allConfiguration =
      new GoogleCloudTestingConfigurationImpl(GoogleCloudTestingConfigurationImpl.ALL_ID, "All", AndroidIcons.Display, facet);
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

  @Override
  public boolean openMatrixConfigurationDialog(AndroidFacet currentFacet,
                                            List<? extends GoogleCloudTestingConfiguration> testingConfigurations,
                                            GoogleCloudTestingConfiguration selectedConfiguration) {

    Module currentModule = currentFacet.getModule();

    List<GoogleCloudTestingConfigurationImpl> castDefaultConfigurations =
      Lists.newArrayList(Iterables.transform(Iterables.filter(testingConfigurations, new Predicate<GoogleCloudTestingConfiguration>(){
        @Override
        public boolean apply(GoogleCloudTestingConfiguration testingConfiguration) {
          return !testingConfiguration.isEditable();
        }
      }), CAST_CONFIGURATIONS));

    List<GoogleCloudTestingConfigurationImpl> copyCustomConfigurations =
      Lists.newArrayList(Iterables.transform(Iterables.filter(testingConfigurations, new Predicate<GoogleCloudTestingConfiguration>(){
        @Override
        public boolean apply(GoogleCloudTestingConfiguration testingConfiguration) {
          return testingConfiguration.isEditable();
        }
      }), CLONE_CONFIGURATIONS));

    if (selectedConfiguration == null) {
      selectedConfiguration = new GoogleCloudTestingConfigurationImpl(currentFacet);
    }

    CloudConfigurationChooserDialog dialog =
      new CloudConfigurationChooserDialog(currentModule, copyCustomConfigurations, castDefaultConfigurations,
                                          (GoogleCloudTestingConfigurationImpl) selectedConfiguration);

    dialog.show();
    if (dialog.isOK()) {
      //Persist the edited configurations.
      GoogleCloudTestingPersistentState customState = new GoogleCloudTestingPersistentState();
      customState.myGoogleCloudTestingPersistentConfigurations = Lists.newArrayList(
        Iterables.transform(copyCustomConfigurations,
                            new Function<GoogleCloudTestingConfigurationImpl, GoogleCloudTestingPersistentConfiguration>() {
                              @Override
                              public GoogleCloudTestingPersistentConfiguration apply(GoogleCloudTestingConfigurationImpl configuration) {
                                return configuration.getPersistentConfiguration();
                              }
                            }));
      GoogleCloudTestingCustomPersistentConfigurations.getInstance(currentModule).loadState(customState);

      dialogSelectedConfiguration = dialog.getSelectedConfiguration();
      return true;
    }
    return false;
  }

  @Override
  public GoogleCloudTestingConfiguration getDialogSelectedConfiguration() {
    return dialogSelectedConfiguration;
  }

  @Override
  public boolean openCloudProjectConfigurationDialog(Project project, String projectId) {
    CloudProjectChooserDialog dialog = new CloudProjectChooserDialog(project, projectId);

    dialog.show();

    if (dialog.isOK()) {
      dialogSelectedProjectId = dialog.getSelectedProject();
      return true;
    }
    return false;
  }

  @Override
  public String getDialogSelectedProjectId() {
    return dialogSelectedProjectId;
  }

  @Override
  public String performSanityCheck(Project project, String cloudProjectId) {
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
        return "Failed to authorize to Google Cloud project! Please select a project you are authorized to use.\n"
          + "Exception while performing a pre-trigger sanity check\n\n" + message;
      }
    }
    return null;
  }

  @Override
  public void displaySanityCheckError(String errorMessage, final Project project) {
    GoogleCloudTestingUtils.showErrorMessage(project, "Cloud test configuration is invalid", errorMessage);
  }

  @Override
  public boolean doesSupportDebugMode() {
    return false;
  }

  @Override
  public ExecutionResult runWithConfigurationOnProject(
    int selectedConfigurationId, String cloudProjectId, AndroidRunningState runningState, Executor executor) throws ExecutionException {

    Project project = runningState.getFacet().getModule().getProject();

    AndroidTestRunConfiguration testRunConfiguration = (AndroidTestRunConfiguration) runningState.getConfiguration();
    AndroidTestConsoleProperties properties =
      new AndroidTestConsoleProperties(testRunConfiguration, executor);
    ConsoleView console = GoogleCloudTestResultsConnectionUtil
      .createAndAttachConsole("Google Cloud Testing", runningState.getProcessHandler(), properties, runningState.getEnvironment());
    Disposer.register(project, console);

    GoogleCloudTestingResultParser
      cloudResultParser = new GoogleCloudTestingResultParser("Cloud Test Run", new GoogleCloudTestListener(runningState));

    GoogleCloudTestingConfigurationImpl googleCloudTestingConfiguration = GoogleCloudTestingUtils
      .getConfigurationById(selectedConfigurationId, runningState.getFacet());
    List<String> expectedConfigurationInstances =
      googleCloudTestingConfiguration.computeConfigurationInstances(ConfigurationInstance.DISPLAY_NAME_DELIMITER);
    for (String configurationInstance : expectedConfigurationInstances) {
      cloudResultParser.getTestRunListener().testConfigurationScheduled(configurationInstance);
    }
    GoogleCloudTestingConfigurable.GoogleCloudTestingState googleCloudTestingState =
      GoogleCloudTestingSettings.getInstance(project).getState();
    if (!googleCloudTestingState.shouldUseFakeBucket) {
      performTestsInCloud(googleCloudTestingConfiguration, cloudProjectId, runningState, cloudResultParser);
    } else {
      String testRunId = TEST_RUN_ID_PREFIX + googleCloudTestingState.fakeBucketName + System.currentTimeMillis();
      CloudResultsAdapter cloudResultsAdapter =
        new CloudResultsAdapter(cloudProjectId, googleCloudTestingState.fakeBucketName, cloudResultParser, expectedConfigurationInstances,
                                testRunId, null);
      addGoogleCloudTestingConfiguration(testRunId, googleCloudTestingConfiguration);
      addCloudResultsAdapter(testRunId, cloudResultsAdapter);
      cloudResultsAdapter.startPolling();
    }
    return new DefaultExecutionResult(console, runningState.getProcessHandler());
  }

  private void performTestsInCloud(final GoogleCloudTestingConfigurationImpl googleCloudTestingConfiguration, final String cloudProjectId,
                                   final AndroidRunningState runningState, final GoogleCloudTestingResultParser cloudResultParser) {
    if (googleCloudTestingConfiguration != null && googleCloudTestingConfiguration.countCombinations() > 0) {
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

          String apkPath = getPathToApks(runningState);
          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Uploading debug APK...", ""),
                                                               ProcessOutputTypes.STDOUT);
          String appApkName = CloudTestsLauncher.uploadFile(bucketName, new File(apkPath + moduleName + "-debug-unaligned.apk")).getName();

          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Uploading test APK...", ""),
                                                               ProcessOutputTypes.STDOUT);
          String testApkName =
            CloudTestsLauncher.uploadFile(bucketName, new File(apkPath + moduleName + "-debug-test-unaligned.apk")).getName();

          runningState.getProcessHandler().notifyTextAvailable(prepareProgressString("Invoking test API...", "\n"),
                                                               ProcessOutputTypes.STDOUT);
          String testSpecification = GoogleCloudTestingUtils.prepareTestSpecification(testRunConfiguration);

          Map<String, TestExecution> testExecutions = CloudTestsLauncher
            .triggerTestApi(cloudProjectId, moduleName, getBucketGcsPath(bucketName), getApkGcsPath(bucketName, appApkName),
                            getApkGcsPath(bucketName, testApkName), testSpecification, encodedMatrixInstances, appPackage, testPackage);

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

  private String getPathToApks(AndroidRunningState runningState) {
    String buildPath = runningState.getFacet().getModule().getModuleFile().getParent().getPath() + "/build";
    File buildFolder = new File(buildPath);
    for (String subFolder : buildFolder.list()) {
      if (subFolder.equals("apk")) {
        return buildPath + "/apk/";
      }
    }
    return buildPath + "/outputs/apk/";
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
    String testRunId, GoogleCloudTestingConfigurationImpl googleCloudTestingConfiguration) {
    if (testRunIdToCloudConfiguration.get(testRunId) != null) {
      throw new IllegalStateException("Cannot add more than one cloud configuration for test run id: " + testRunId);
    }
    testRunIdToCloudConfiguration.put(testRunId, googleCloudTestingConfiguration);
  }

  public static GoogleCloudTestingConfigurationImpl getSelectedGoogleCloudTestingConfiguration(String testRunId) {
    return testRunIdToCloudConfiguration.get(testRunId);
  }

  public static CloudResultsAdapter getCloudResultsAdapter(String testRunId) {
    return testRunIdToCloudResultsAdapter.get(testRunId);
  }

  public static List<GoogleCloudTestingConfigurationImpl> deserializeConfigurations(
    final List<GoogleCloudTestingPersistentConfiguration> persistentConfigurations, boolean isEditable, AndroidFacet facet) {
    List<GoogleCloudTestingConfigurationImpl> googleCloudTestingConfigurations = new LinkedList<GoogleCloudTestingConfigurationImpl>();
    for (GoogleCloudTestingPersistentConfiguration persistentConfiguration : persistentConfigurations) {
      Icon icon = getIcon(persistentConfiguration.name, isEditable);
      GoogleCloudTestingConfigurationImpl configuration =
        new GoogleCloudTestingConfigurationImpl(persistentConfiguration.id, persistentConfiguration.name, icon, facet);
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
