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


import com.android.tools.idea.run.AdditionalRunDebugOptionFactory;
import com.android.tools.idea.run.AdditionalRunDebugOptionPanel;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Buckets;
import com.google.gct.testing.config.GoogleCloudTestingConfigurable;
import com.google.gct.testing.config.GoogleCloudTestingSettings;
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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.run.AndroidRunningState;
import org.jetbrains.android.run.testing.AndroidTestConsoleProperties;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CloudTestOptionFactory extends AdditionalRunDebugOptionFactory {

  private static final String TEST_RUN_ID_PREFIX = "GoogleCloudTest:";

  private static final Map<String, GoogleCloudTestingConfiguration> testRunIdToCloudConfiguration = new HashMap<String, GoogleCloudTestingConfiguration>();
  private static final Map<String, CloudResultsAdapter> testRunIdToCloudResultsAdapter = new HashMap<String, CloudResultsAdapter>();


  @Override
  public AdditionalRunDebugOptionPanel createPanel(Project project, boolean isExtendedDeviceChooserDialog) {
    return new CloudTestOptionPanel(project, isExtendedDeviceChooserDialog);
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

    GoogleCloudTestingConfiguration googleCloudTestingConfiguration = GoogleCloudTestingUtils
      .getConfigurationById(selectedConfigurationId, runningState.getFacet());
    for (String configurationInstance : googleCloudTestingConfiguration.computeConfigurationInstances(
      ConfigurationInstance.DISPLAY_NAME_DELIMITER)) {
      cloudResultParser.getTestRunListener().testConfigurationScheduled(configurationInstance);
    }
    GoogleCloudTestingConfigurable.GoogleCloudTestingState googleCloudTestingState = GoogleCloudTestingSettings.getInstance(project).getState();
    if (!googleCloudTestingState.shouldUseFakeBucket) {
      performTestsInCloud(googleCloudTestingConfiguration, cloudProjectId, googleCloudTestingState, runningState, cloudResultParser);
    } else {
      String testRunId = TEST_RUN_ID_PREFIX + googleCloudTestingState.fakeBucketName + System.currentTimeMillis();
      CloudResultsAdapter cloudResultsAdapter =
        new CloudResultsAdapter(googleCloudTestingState.fakeBucketName, cloudResultParser, googleCloudTestingConfiguration.countCombinations(), testRunId);
      addGoogleCloudTestingConfiguration(testRunId, googleCloudTestingConfiguration);
      addCloudResultsAdapter(testRunId, cloudResultsAdapter);
      cloudResultsAdapter.startPolling();
    }
    return new DefaultExecutionResult(console, runningState.getProcessHandler());
  }

  @Override
  public boolean doesSupportDebugMode() {
    return false;
  }

  @Override
  public String performSanityCheck(Project project, String cloudProjectId) {
    // Check that all the required fields are populated.
    GoogleCloudTestingConfigurable.GoogleCloudTestingState googleCloudTestingState =
      GoogleCloudTestingSettings.getInstance(project).getState();
    if (googleCloudTestingState == null
        || googleCloudTestingState.shouldUseStagingJenkins
           && (googleCloudTestingState.stagingJenkinsUrl.isEmpty())
        || !googleCloudTestingState.shouldUseStagingJenkins
           && (googleCloudTestingState.prodJenkinsUrl.isEmpty())) {

      return "Cloud Test Configuration is incomplete!";
    }

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
        return "Failed to authorize to Google Cloud project, message= " + message;
      }
    }

    return null;
  }

  @Override
  public void displaySanityCheckError(String errorMessage, final Project project) {
    new Notification("Cloud Test Configuration Invalid", "", String.format("<b>%s</b> <a href=''>Fix it.</a>", errorMessage),
                     NotificationType.ERROR,
                     new NotificationListener.Adapter() {
                       @Override
                       protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
                         ShowSettingsUtil.getInstance().showSettingsDialog(project, "Google Cloud Testing");        }
                     })
      .notify(project.isDefault() ? null : project);
  }

  private void performTestsInCloud(final GoogleCloudTestingConfiguration googleCloudTestingConfiguration, final String cloudProjectId,
                                   final GoogleCloudTestingConfigurable.GoogleCloudTestingState googleCloudTestingState,
                                   final AndroidRunningState runningState, final GoogleCloudTestingResultParser cloudResultParser) {
    if (googleCloudTestingConfiguration != null && googleCloudTestingConfiguration.countCombinations() > 0) {
      final List<String> matrixInstances =
        googleCloudTestingConfiguration.computeConfigurationInstances(ConfigurationInstance.ENCODED_NAME_DELIMITER);
      new Thread(new Runnable() {
        @Override
        public void run() {
          AndroidTestRunConfiguration testRunConfiguration = (AndroidTestRunConfiguration) runningState.getConfiguration();
          String moduleName = runningState.getFacet().getModule().getName();
          String bucketName = "build-" + moduleName.toLowerCase() + "-" + System.currentTimeMillis();
          String jenkinsUrl = googleCloudTestingState.shouldUseStagingJenkins ? googleCloudTestingState.stagingJenkinsUrl : googleCloudTestingState.prodJenkinsUrl;
          String appPackage = runningState.getFacet().getAndroidModuleInfo().getPackage();
          String testPackage = appPackage + ".test";

          runningState.getProcessHandler().notifyTextAvailable(
            prepareProgressString("Creating Cloud Storage bucket " + bucketName + "...", ""), ProcessOutputTypes.STDOUT);
          CloudTestsLauncher.createBucket(cloudProjectId, bucketName);

          String apkPath = runningState.getFacet().getModule().getModuleFile().getParent().getPath() + "/build/apk/";
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

          CloudTestsLauncher
            .triggerTestApi(cloudProjectId, moduleName, getApkGcsPath(bucketName, appApkName), getApkGcsPath(bucketName, testApkName),
                            testSpecification, matrixInstances, appPackage, testPackage);

          //CloudTestsLauncher
          //  .triggerJenkinsJob(jenkinsUrl, cloudProjectId, moduleName, bucketName, testSpecification, matrixFilter, appPackage,
          //                     testPackage);

          String testRunId = TEST_RUN_ID_PREFIX + bucketName;
          CloudResultsAdapter cloudResultsAdapter =
            new CloudResultsAdapter(bucketName, cloudResultParser, googleCloudTestingConfiguration.countCombinations(), testRunId);
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

  private String getApkGcsPath(String bucketName, String apkName) {
    return "gs://" + bucketName + "/" + apkName;
  }

  private static void addCloudResultsAdapter(String testRunId, CloudResultsAdapter cloudResultsAdapter) {
    if (testRunIdToCloudResultsAdapter.get(testRunId) != null) {
      throw new IllegalStateException("Cannot add more than one cloud results adapter for test run id: " + testRunId);
    }
    testRunIdToCloudResultsAdapter.put(testRunId, cloudResultsAdapter);
  }

  private static void addGoogleCloudTestingConfiguration(String testRunId, GoogleCloudTestingConfiguration googleCloudTestingConfiguration) {
    if (testRunIdToCloudConfiguration.get(testRunId) != null) {
      throw new IllegalStateException("Cannot add more than one cloud configuration for test run id: " + testRunId);
    }
    testRunIdToCloudConfiguration.put(testRunId, googleCloudTestingConfiguration);
  }

  public static GoogleCloudTestingConfiguration getSelectedGoogleCloudTestingConfiguration(String testRunId) {
    return testRunIdToCloudConfiguration.get(testRunId);
  }

  public static CloudResultsAdapter getCloudResultsAdapter(String testRunId) {
    return testRunIdToCloudResultsAdapter.get(testRunId);
  }

}
