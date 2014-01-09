/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.applicationhistoryservice;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience.Private;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.service.AbstractService;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptReport;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerReport;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.applicationhistoryservice.records.ApplicationAttemptHistoryData;
import org.apache.hadoop.yarn.server.applicationhistoryservice.records.ApplicationHistoryData;
import org.apache.hadoop.yarn.server.applicationhistoryservice.records.ContainerHistoryData;

import com.google.common.annotations.VisibleForTesting;

public class ApplicationHistoryManagerImpl extends AbstractService implements
    ApplicationHistoryManager {
  private static final Log LOG = LogFactory
      .getLog(ApplicationHistoryManagerImpl.class);
  private static final String UNAVAILABLE = "N/A";

  private ApplicationHistoryStore historyStore;

  public ApplicationHistoryManagerImpl() {
    super(ApplicationHistoryManagerImpl.class.getName());
  }

  @Override
  protected void serviceInit(Configuration conf) throws Exception {
    LOG.info("ApplicationHistory Init");
    historyStore = ReflectionUtils.newInstance(conf.getClass(
        YarnConfiguration.AHS_STORE, FileSystemApplicationHistoryStore.class,
        ApplicationHistoryStore.class), conf);
    historyStore.init(conf);
    super.serviceInit(conf);
  }

  @Override
  protected void serviceStart() throws Exception {
    LOG.info("Starting ApplicationHistory");
    historyStore.start();
    super.serviceStart();
  }

  @Override
  protected void serviceStop() throws Exception {
    LOG.info("Stopping ApplicationHistory");
    historyStore.stop();
    super.serviceStop();
  }

  @Override
  public ContainerReport getAMContainer(ApplicationAttemptId appAttemptId)
      throws IOException {
    return convertToContainerReport(historyStore.getAMContainer(appAttemptId));
  }

  @Override
  public Map<ApplicationId, ApplicationReport> getAllApplications()
      throws IOException {
    Map<ApplicationId, ApplicationHistoryData> histData = historyStore
        .getAllApplications();
    HashMap<ApplicationId, ApplicationReport> applicationsReport = new HashMap<ApplicationId, ApplicationReport>();
    for (ApplicationId appId : histData.keySet()) {
      applicationsReport.put(appId, convertToApplicationReport(histData
          .get(appId)));
    }
    return applicationsReport;
  }

  @Override
  public ApplicationReport getApplication(ApplicationId appId)
      throws IOException {
    return convertToApplicationReport(historyStore.getApplication(appId));
  }

  private ApplicationReport convertToApplicationReport(
      ApplicationHistoryData appHistory) throws IOException {
    ApplicationAttemptId currentApplicationAttemptId = null;
    String trackingUrl = UNAVAILABLE;
    String host = UNAVAILABLE;
    int rpcPort = -1;

    ApplicationAttemptHistoryData lastAttempt = getLastAttempt(appHistory
        .getApplicationId());
    if (lastAttempt != null) {
      currentApplicationAttemptId = lastAttempt.getApplicationAttemptId();
      trackingUrl = lastAttempt.getTrackingURL();
      host = lastAttempt.getHost();
      rpcPort = lastAttempt.getRPCPort();
    }
    return ApplicationReport.newInstance(appHistory.getApplicationId(),
        currentApplicationAttemptId, appHistory.getUser(), appHistory
            .getQueue(), appHistory.getApplicationName(), host, rpcPort, null,
        appHistory.getYarnApplicationState(), appHistory.getDiagnosticsInfo(),
        trackingUrl, appHistory.getStartTime(), appHistory.getFinishTime(),
        appHistory.getFinalApplicationStatus(), null, "", 100, appHistory
            .getApplicationType(), null);
  }

  private ApplicationAttemptHistoryData getLastAttempt(ApplicationId appId)
      throws IOException {
    Map<ApplicationAttemptId, ApplicationAttemptHistoryData> attempts = historyStore
        .getApplicationAttempts(appId);
    ApplicationAttemptId prevMaxAttemptId = null;
    for (ApplicationAttemptId attemptId : attempts.keySet()) {
      if (prevMaxAttemptId == null) {
        prevMaxAttemptId = attemptId;
      } else {
        if (prevMaxAttemptId.getAttemptId() < attemptId.getAttemptId()) {
          prevMaxAttemptId = attemptId;
        }
      }
    }
    return attempts.get(prevMaxAttemptId);
  }

  private ApplicationAttemptReport convertToApplicationAttemptReport(
      ApplicationAttemptHistoryData appAttemptHistory) {
    return ApplicationAttemptReport.newInstance(appAttemptHistory
        .getApplicationAttemptId(), appAttemptHistory.getHost(),
        appAttemptHistory.getRPCPort(), appAttemptHistory.getTrackingURL(),
        appAttemptHistory.getDiagnosticsInfo(), null, appAttemptHistory
            .getMasterContainerId());
  }

  @Override
  public ApplicationAttemptReport getApplicationAttempt(
      ApplicationAttemptId appAttemptId) throws IOException {
    return convertToApplicationAttemptReport(historyStore
        .getApplicationAttempt(appAttemptId));
  }

  @Override
  public Map<ApplicationAttemptId, ApplicationAttemptReport> getApplicationAttempts(
      ApplicationId appId) throws IOException {
    Map<ApplicationAttemptId, ApplicationAttemptHistoryData> histData = historyStore
        .getApplicationAttempts(appId);
    HashMap<ApplicationAttemptId, ApplicationAttemptReport> applicationAttemptsReport = new HashMap<ApplicationAttemptId, ApplicationAttemptReport>();
    for (ApplicationAttemptId appAttemptId : histData.keySet()) {
      applicationAttemptsReport.put(appAttemptId,
          convertToApplicationAttemptReport(histData.get(appAttemptId)));
    }
    return applicationAttemptsReport;
  }

  @Override
  public ContainerReport getContainer(ContainerId containerId)
      throws IOException {
    return convertToContainerReport(historyStore.getContainer(containerId));
  }

  private ContainerReport convertToContainerReport(
      ContainerHistoryData containerHistory) {
    return ContainerReport.newInstance(containerHistory.getContainerId(),
        containerHistory.getAllocatedResource(), containerHistory
            .getAssignedNode(), containerHistory.getPriority(),
        containerHistory.getStartTime(), containerHistory.getFinishTime(),
        containerHistory.getDiagnosticsInfo(), containerHistory.getLogURL(),
        containerHistory.getContainerExitStatus(), containerHistory
            .getContainerState());
  }

  @Override
  public Map<ContainerId, ContainerReport> getContainers(
      ApplicationAttemptId appAttemptId) throws IOException {
    Map<ContainerId, ContainerHistoryData> histData = historyStore
        .getContainers(appAttemptId);
    HashMap<ContainerId, ContainerReport> containersReport = new HashMap<ContainerId, ContainerReport>();
    for (ContainerId container : histData.keySet()) {
      containersReport.put(container, convertToContainerReport(histData
          .get(container)));
    }
    return containersReport;
  }

  @Private
  @VisibleForTesting
  public ApplicationHistoryStore getHistoryStore() {
    return this.historyStore;
  }
}
