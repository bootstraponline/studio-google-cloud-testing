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

import com.google.api.services.testing.model.TestMatrix;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gct.testing.results.GoogleCloudTestingResultParser;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.google.gct.testing.CloudTestingUtils.ConfigurationStopReason;

public class CloudResultsAdapter {

  private final String cloudProjectId;
  private final CloudResultsLoader loader;
  private final GoogleCloudTestingResultParser resultParser;
  private final List<String> expectedConfigurationInstances;
  // Indexed by encoded configuration instance name.
  private final Map<String, ConfigurationResult> results = new HashMap<String, ConfigurationResult>();
  // The set of configurations for which we've gotten a result and published it to the parser.
  private final Set<ConfigurationResult> publishedConfigurations = new HashSet<ConfigurationResult>();
  // The set of configurations that were marked as pending in the test results tree.
  private final Set<ConfigurationResult> markedAsPendingConfigurations = new HashSet<ConfigurationResult>();
  // The set of configurations that were marked as finished in the test results tree.
  private final Set<ConfigurationResult> markedAsFinishedConfigurations = new HashSet<ConfigurationResult>();
  private final PollingTicker pollingTicker;


  public CloudResultsAdapter(String cloudProjectId, String bucketName, GoogleCloudTestingResultParser resultParser,
                             List<String> expectedConfigurationInstances, String testRunId, @Nullable TestMatrix testMatrix,
                             @Nullable CloudMatrixExecutionCancellator matrixExecutionCancellator) {
    this.cloudProjectId = cloudProjectId;
    loader = new CloudResultsLoader(cloudProjectId, resultParser.getTestRunListener(), bucketName, testMatrix);
    this.resultParser = resultParser;
    this.expectedConfigurationInstances = expectedConfigurationInstances;
    pollingTicker = new PollingTicker(matrixExecutionCancellator);
    // Update the tree's root node with the index of the adapter that communicates with the tree through this parser.
    resultParser.getTestRunListener().setTestRunId(testRunId);
  }

  public void startPolling() {
    new Thread(pollingTicker).start();
  }

  public Map<String, ConfigurationResult> getResults() {
    return results;
  }

  /**
   *
   * @return true if all results arrived (i.e., we should stop polling the cloud bucket).
   */
  private boolean poll() {
    // Ask the loader to update the results map (possibly adding more).
    boolean newDataReceived = loader.updateResults(results);
    if (newDataReceived) {
      pollingTicker.resetTimeout();
    }

    for (ConfigurationResult result : results.values()) {
      if (!markedAsPendingConfigurations.contains(result)) {
        markedAsPendingConfigurations.add(result);
        resultParser.getTestRunListener().testConfigurationStarted(result.getConfigurationInstance().getResultsViewerDisplayString());
      }
    }

    // Count number of completed results we have gotten back.
    int completedConfigurationInstances = Lists.newArrayList(Iterables.filter(results.values(), new Predicate<ConfigurationResult>(){
      @Override
      public boolean apply(ConfigurationResult result) {
        return result.isNoProgressExpected();
      }
    })).size();

    // Publish any results that haven't been published yet.
    for (ConfigurationResult result : results.values()) {
      if (result.hasResult() && !publishedConfigurations.contains(result)) {
        byte[] inputBytes = getParserInput(result);
        resultParser.addOutput(inputBytes, 0, inputBytes.length);
        publishedConfigurations.add(result);
      }
    }

    //TODO: Decide whether we need to distinguish finished vs. stopped configurations.
    // Also, currently test suites in a configuration stop only when the whole configuration is stopped.
    for (ConfigurationResult result : results.values()) {
      if ((result.isComplete() || result.isInfrastructureFailure() || result.isTriggeringError())
          && !markedAsFinishedConfigurations.contains(result)) {

        markedAsFinishedConfigurations.add(result);
        ConfigurationStopReason stopReason = result.isComplete()
                                             ? ConfigurationStopReason.FINISHED
                                             : result.isInfrastructureFailure()
                                               ? ConfigurationStopReason.INFRASTRUCTURE_FAILURE
                                               : ConfigurationStopReason.TRIGGERING_ERROR;
        resultParser.getTestRunListener().stopTestConfiguration(result.getConfigurationInstance().getResultsViewerDisplayString(), stopReason);
      }
    }

    // == should be enough, but to be conservative, we use >=
    return completedConfigurationInstances >= expectedConfigurationInstances.size();
  }

  private void timeoutResultProcessing(boolean allResultsArrived) {
    if (!allResultsArrived) {
      for (String configurationInstance : expectedConfigurationInstances) {
        resultParser.getTestRunListener().stopTestConfiguration(configurationInstance, ConfigurationStopReason.TIMED_OUT);
      }

      // Mark all yet unfinished configurations as timed out.
      //for (ConfigurationResult result : results.values()) {
      //  if (!markedAsFinishedConfigurations.contains(result)) {
      //    resultParser.getTestRunListener()
      //      .stopTestConfiguration(result.getConfigurationInstance().getResultsViewerDisplayString(), ConfigurationStopReason.TIMED_OUT);
      //  }
      //}
    }
    // Flushing stops the parser => stops (terminates) all scheduled and in-progress nodes in the results viewer,
    // so need to time out them properly before flushing the parser.
    resultParser.flush();
  }

  private byte[] getParserInput(ConfigurationResult result) {
    String resultString = result.getResult();
    if (resultString.indexOf("\r\n") == -1 && resultString.indexOf("\n") != -1) {
      //Make sure the result uses \r\n (Windows-style EOL) as line delimiter as this is what parser expects/produces.
      resultString = resultString.replaceAll("\\n", "\r\n");
    }
    String configurationName = "\nINSTRUMENTATION_STATUS: configuration="
                               + result.getConfigurationInstance().getResultsViewerDisplayString() + "\r\n";
    String classNamePrefix = "INSTRUMENTATION_STATUS: class=";
    return resultString.replaceAll("\\n" + classNamePrefix, configurationName + classNamePrefix).getBytes();
  }

  class PollingTicker implements Runnable {
    private static final long INITIAL_TIMEOUT = 10 * 60 * 1000; // 10 minutes
    //TODO: Reduce back to 5 minutes after we get incremental results for test execution.
    private static final long DYNAMIC_TIMEOUT = 30 * 60 * 1000; // 30 minutes
    private static final int POLLING_INTERVAL = 3 * 1000; // 3 seconds

    private long stopTime;
    private final CloudMatrixExecutionCancellator matrixExecutionCancellator;

    public PollingTicker(@Nullable CloudMatrixExecutionCancellator matrixExecutionCancellator) {
      this.matrixExecutionCancellator = matrixExecutionCancellator;
    }

    public void resetTimeout() {
      long newStopTime = System.currentTimeMillis() + DYNAMIC_TIMEOUT;
      if (newStopTime > stopTime) {
        stopTime = newStopTime;
      }
    }

    @Override
    public void run() {
      boolean allResultsArrived = false;
      stopTime = System.currentTimeMillis() + INITIAL_TIMEOUT;
      while (System.currentTimeMillis() < stopTime) {
        if (matrixExecutionCancellator != null && matrixExecutionCancellator.isCancelled()) {
          break;
        }
        allResultsArrived = poll();
        if (allResultsArrived) {
          break;
        }
        try {
          Thread.sleep(POLLING_INTERVAL);
        } catch (InterruptedException e) {
          //ignore
        }
      }
      //Stop either because all results arrived or because the polling timed out.
      timeoutResultProcessing(allResultsArrived);
    }
  }
}
