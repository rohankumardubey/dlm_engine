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

package com.hortonworks.beacon.replication.fs;

import com.hortonworks.beacon.exceptions.BeaconException;
import com.hortonworks.beacon.job.BeaconJob;
import com.hortonworks.beacon.job.JobContext;
import com.hortonworks.beacon.job.JobStatus;
import com.hortonworks.beacon.log.BeaconLog;
import com.hortonworks.beacon.log.BeaconLogUtils;
import com.hortonworks.beacon.metrics.ReplicationMetrics;
import com.hortonworks.beacon.metrics.util.ReplicationMetricsUtils;
import com.hortonworks.beacon.replication.InstanceReplication;
import com.hortonworks.beacon.replication.ReplicationJobDetails;
import com.hortonworks.beacon.replication.ReplicationUtils;
import com.hortonworks.beacon.util.FSUtils;
import com.hortonworks.beacon.util.ReplicationType;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.protocol.SnapshotDiffReport;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobID;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.tools.DistCp;
import org.apache.hadoop.tools.DistCpOptions;

import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * FileSystem Replication implementation.
 */
public class FSReplication extends InstanceReplication implements BeaconJob {

    private static final BeaconLog LOG = BeaconLog.getLog(FSReplication.class);

    private String sourceStagingUri;
    private String targetStagingUri;
    private FileSystem sourceFs;
    private FileSystem targetFs;
    private boolean isSnapshot;
    private boolean isHCFS;
    private static final int MAX_JOB_RETRIES = 10;

    public FSReplication(ReplicationJobDetails details) {
        super(details);
        isSnapshot = false;
    }

    @Override
    public void init(JobContext jobContext) throws BeaconException {
        BeaconLogUtils.setLogInfo(jobContext.getJobInstanceId());
        String sourceDataset = getProperties().getProperty(FSDRProperties.SOURCE_DATASET.getName());
        String targetDataset = getProperties().getProperty(FSDRProperties.TARGET_DATASET.getName());

        try {
            initializeFileSystem();
            sourceStagingUri = FSUtils.getStagingUri(getProperties().getProperty(FSDRProperties.SOURCE_NN.getName()),
                    sourceDataset);
            targetStagingUri = FSUtils.getStagingUri(getProperties().getProperty(FSDRProperties.TARGET_NN.getName()),
                    targetDataset);

            if (FSUtils.isHCFS(new Path(sourceStagingUri)) || FSUtils.isHCFS(new Path(targetStagingUri))) {
                isHCFS = true;
            }
        } catch (BeaconException e) {
            setInstanceExecutionDetails(jobContext, JobStatus.FAILED, e.getMessage(), null);
            cleanUp(jobContext);
            throw new BeaconException("Exception occurred in init : {}", e);
        }
    }

    @Override
    public void perform(JobContext jobContext) throws BeaconException {
        Job job = null;
        Properties fsDRProperties;
        String fsReplicationName;
        try {
            fsDRProperties = getProperties();
            fsReplicationName = getFSReplicationName(fsDRProperties);
            job = performCopy(jobContext, fsDRProperties, fsReplicationName, ReplicationMetrics.JobType.MAIN);
            if (job == null) {
                throw new BeaconException("FS Replication job is null");
            }
        } catch (InterruptedException e) {
            cleanUp(jobContext);
            throw new BeaconException(e);
        } catch (Exception e) {
            LOG.error("Exception occurred in FS Replication: {}", e.getMessage());
            setInstanceExecutionDetails(jobContext, JobStatus.FAILED, e.getMessage(), job);
            cleanUp(jobContext);
            throw new BeaconException(e);
        }
        performPostReplJobExecution(jobContext, job, fsDRProperties, fsReplicationName,
                ReplicationMetrics.JobType.MAIN);
    }

    Job performCopy(JobContext jobContext, Properties fsDRProperties,
                    String toSnapshot,
                    ReplicationMetrics.JobType jobType) throws BeaconException, InterruptedException {
        return performCopy(jobContext, fsDRProperties, toSnapshot,
                getLatestSnapshotOnTargetAvailableOnSource(), jobType);
    }

    Job performCopy(JobContext jobContext, Properties fsDRProperties, String toSnapshot, String fromSnapshot,
                    ReplicationMetrics.JobType jobType) throws BeaconException, InterruptedException {
        Job job = null;
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
        try {
            boolean isInRecoveryMode = (jobType == ReplicationMetrics.JobType.RECOVERY) ? true : false;
            DistCpOptions options = getDistCpOptions(fsDRProperties, toSnapshot,
                    fromSnapshot, isInRecoveryMode);

            LOG.info("Started DistCp with source Path: {}  target path: {}", sourceStagingUri, targetStagingUri);
            Configuration conf = new Configuration();
            conf.set("mapred.job.queue.name", fsDRProperties.getProperty(FSDRProperties.QUEUE_NAME.getName()));
            DistCp distCp = new DistCp(conf, options);
            job = distCp.createAndSubmitJob();
            LOG.info("DistCp Hadoop job: {} for policy instance: [{}]", getJob(job), jobContext.getJobInstanceId());
            handlePostSubmit(timer, jobContext, job, jobType, distCp);

        } catch (InterruptedException e) {
            checkJobInterruption(jobContext, job);
            throw e;
        } catch (Exception e) {
            LOG.error("Exception occurred while performing copying of data : {}", e.getMessage());
            throw new BeaconException(e);
        } finally {
            timer.shutdown();
        }
        return job;
    }

    private void handlePostSubmit(ScheduledThreadPoolExecutor timer, JobContext jobContext,
                                  final Job job, ReplicationMetrics.JobType jobType, DistCp distCp) throws Exception {
        captureMetricsPeriodically(timer, jobContext, job, jobType, ReplicationUtils.getReplicationMetricsInterval());
        distCp.waitForJobCompletion(job);
    }

    private static ReplicationMetrics getCurrentJobDetails(JobContext jobContext) throws BeaconException {
        String instanceId = jobContext.getJobInstanceId();
        String trackingInfo = ReplicationUtils.getInstanceTrackingInfo(instanceId);

        List<ReplicationMetrics> metrics = ReplicationMetricsUtils.getListOfReplicationMetrics(trackingInfo);
        if (metrics == null || metrics.isEmpty()) {
            LOG.info("No job started, return");
            return null;
        }

        // List can have only 2 jobs: one main job and one recovery distcp job
        if (metrics.size() > 1) {
            // Recovery has kicked in, return recovery job id
            return metrics.get(1);
        } else {
            return metrics.get(0);
        }
    }

    private String getLatestSnapshotOnTargetAvailableOnSource() throws BeaconException {
        String fromSnapshot = null;

        try {
            LOG.info("Checking Snapshot directory on Source and Target");
            if (isSnapshot && targetFs.exists(new Path(targetStagingUri))) {
                fromSnapshot = FSSnapshotUtils.findLatestReplicatedSnapshot((DistributedFileSystem) sourceFs,
                        (DistributedFileSystem) targetFs, sourceStagingUri, targetStagingUri);
            }
        } catch (IOException e) {
            String msg = "Error occurred when checking target dir : {} exists " + targetStagingUri;
            LOG.error(msg);
            throw new BeaconException(msg);
        }
        return fromSnapshot;
    }


    private void initializeFileSystem() throws BeaconException {
        try {
            Configuration conf = new Configuration();
            conf.setBoolean("fs.hdfs.impl.disable.cache", true);
            sourceFs = FSUtils.getFileSystem(getProperties().getProperty(
                    FSDRProperties.SOURCE_NN.getName()), conf, isHCFS);
            targetFs = FSUtils.getFileSystem(getProperties().getProperty(
                    FSDRProperties.TARGET_NN.getName()), conf, isHCFS);
        } catch (BeaconException e) {
            LOG.error("Exception occurred while initializing DistributedFileSystem:" + e);
            throw new BeaconException(e.getMessage());
        }
    }

    private String getFSReplicationName(Properties fsDRProperties)
            throws BeaconException {
        boolean tdeEncryptionEnabled = Boolean.parseBoolean(fsDRProperties.getProperty(
                FSDRProperties.TDE_ENCRYPTION_ENABLED.getName()));
        LOG.info("TDE Encryption enabled : {}", tdeEncryptionEnabled);
        // check if source and target path's exist and are snapshot-able
        String fsReplicationName = fsDRProperties.getProperty(FSDRProperties.JOB_NAME.getName())
                + "-" + System.currentTimeMillis();
        if (!tdeEncryptionEnabled) {
            if (fsDRProperties.getProperty(FSDRProperties.SOURCE_SNAPSHOT_RETENTION_AGE_LIMIT.getName()) != null
                    && fsDRProperties.getProperty(FSDRProperties.SOURCE_SNAPSHOT_RETENTION_NUMBER.getName()) != null
                    && fsDRProperties.getProperty(FSDRProperties.TARGET_SNAPSHOT_RETENTION_AGE_LIMIT.getName()) != null
                    && fsDRProperties.getProperty(FSDRProperties.TARGET_SNAPSHOT_RETENTION_NUMBER.getName()) != null) {
                try {
                    isSnapshot = FSSnapshotUtils.isDirectorySnapshottable(sourceFs, targetFs,
                            sourceStagingUri, targetStagingUri);
                    if (isSnapshot) {
                        fsReplicationName = FSSnapshotUtils.SNAPSHOT_PREFIX
                                + fsDRProperties.getProperty(FSDRProperties.JOB_NAME.getName())
                                + "-" + System.currentTimeMillis();
                        FSSnapshotUtils.handleSnapshotCreation(sourceFs, sourceStagingUri, fsReplicationName);
                    }
                } catch (BeaconException e) {
                    throw new BeaconException(e);
                }
            }
        }
        return fsReplicationName;
    }

    private DistCpOptions getDistCpOptions(Properties fsDRProperties,
                                           String toSnapshot, String fromSnapshot,
                                           boolean isInRecoveryMode)
            throws BeaconException, IOException {
        // DistCpOptions expects the first argument to be a file OR a list of Paths

        List<Path> sourceUris = new ArrayList<>();
        sourceUris.add(new Path(sourceStagingUri));

        return DistCpOptionsUtil.getDistCpOptions(fsDRProperties, sourceUris, new Path(targetStagingUri),
                isSnapshot, fromSnapshot, toSnapshot, isInRecoveryMode);
    }

    private void performPostReplJobExecution(JobContext jobContext, Job job,
                                             Properties fsDRProperties,
                                             String fsReplicationName,
                                             ReplicationMetrics.JobType jobType) throws BeaconException {
        try {
            if (job.isComplete() && job.isSuccessful()) {
                if (isSnapshot) {
                    try {
                        FSSnapshotUtils.handleSnapshotCreation(targetFs, targetStagingUri, fsReplicationName);
                        FSSnapshotUtils.handleSnapshotEviction(sourceFs, fsDRProperties, sourceStagingUri);
                        FSSnapshotUtils.handleSnapshotEviction(targetFs, fsDRProperties, targetStagingUri);
                    } catch (BeaconException e) {
                        throw new BeaconException("Exception occurred while handling snapshot : {}", e);
                    }
                }
                LOG.info("Distcp Copy is successful");
                captureReplicationMetrics(job, jobType, jobContext, ReplicationType.FS, true);
                setInstanceExecutionDetails(jobContext, JobStatus.SUCCESS, JobStatus.SUCCESS.name(), job);
            } else {
                String message = "Job exception occurred:" + getJob(job);
                throw new BeaconException(message);
            }
        } catch (Exception e) {
            LOG.error("Exception occurred in FS Replication: {}", e.getMessage());
            setInstanceExecutionDetails(jobContext, JobStatus.FAILED, e.getMessage(), job);
            cleanUp(jobContext);
            throw new BeaconException(e);
        }
    }

    private void checkJobInterruption(JobContext jobContext, Job job) throws BeaconException {
        if (job != null) {
            try {
                LOG.error("replication job: {} interrupted, killing it.", getJob(job));
                job.killJob();
                setInstanceExecutionDetails(jobContext, JobStatus.KILLED, "job killed", job);
            } catch (IOException ioe) {
                LOG.error(ioe.getMessage(), ioe);
            }
        }
    }

    @Override
    public void cleanUp(JobContext jobContext) throws BeaconException {
        try {
            sourceFs.close();
            targetFs.close();
        } catch (IOException e) {
            throw new BeaconException("Exception occurred while closing FileSystem : {}", e);
        }
    }

    @Override
    public void recover(JobContext jobContext) throws BeaconException {
        if (!isSnapshot) {
            LOG.info("policy instance: [{}] not snapshottable, return", jobContext.getJobInstanceId());
            return;
        }
        LOG.info("recover policy instance: [{}]", jobContext.getJobInstanceId());

        ReplicationMetrics currentJobMetric = getCurrentJobDetails(jobContext);
        if (currentJobMetric == null) {
            LOG.info("Nothing to recover as no DistCp job was launched, return");
            return;
        }

        LOG.info("recover job [{}] and job type [{}]", currentJobMetric.getJobId(), currentJobMetric.getJobType());

        RunningJob job = getJobWithRetries(currentJobMetric.getJobId());
        Job currentJob;
        org.apache.hadoop.mapred.JobStatus jobStatus;
        try {
            jobStatus = job.getJobStatus();
            currentJob = getJobClient().getClusterHandle().getJob(job.getID());
        } catch (IOException | InterruptedException e) {
            throw new BeaconException(e);
        }

        Properties fsDRProperties = getProperties();
        if (org.apache.hadoop.mapred.JobStatus.State.RUNNING.getValue() == jobStatus.getRunState()
                || org.apache.hadoop.mapred.JobStatus.State.PREP.getValue() == jobStatus.getRunState()) {
            ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
            DistCp distCp;
            try {
                distCp = new DistCp(new Configuration(), getDistCpOptions(fsDRProperties, null,
                        null, false));
                handlePostSubmit(timer, jobContext, currentJob, ReplicationMetrics.JobType.MAIN, distCp);
                performPostReplJobExecution(jobContext, currentJob, fsDRProperties,
                        getFSReplicationName(fsDRProperties), ReplicationMetrics.JobType.MAIN);
            } catch (Exception e) {
                throw new BeaconException(e);
            } finally {
                timer.shutdown();
            }
        } else if (org.apache.hadoop.mapred.JobStatus.State.SUCCEEDED.getValue() == jobStatus.getRunState()) {
            performPostReplJobExecution(jobContext, currentJob, fsDRProperties, getFSReplicationName(fsDRProperties),
                    ReplicationMetrics.JobType.MAIN);
        } else {
            // Job failed
            handleRecovery(jobContext);
        }
    }

    private void handleRecovery(JobContext jobContext) throws BeaconException {

        // Current state on the target cluster
        String toSnapshot = ".";
        String fromSnapshot = getLatestSnapshotOnTargetAvailableOnSource();
        if (StringUtils.isBlank(fromSnapshot)) {
            LOG.info("replicatedSnapshotName is null. No recovery needed for policy instance: [{}], return",
                    jobContext.getJobInstanceId());
            return;
        }

        Job job = null;
        try {
            SnapshotDiffReport diffReport = ((DistributedFileSystem) targetFs).getSnapshotDiffReport(
                    new Path(targetStagingUri), fromSnapshot, toSnapshot);
            List diffList = diffReport.getDiffList();
            if (diffList == null || diffList.isEmpty()) {
                LOG.info("No recovery needed for policy instance: [{}], return", jobContext.getJobInstanceId());
                return;
            }
            LOG.info("Recovery needed for policy instance: [{}]. Start recovery!", jobContext.getJobInstanceId());
            Properties fsDRProperties = getProperties();
            try {
                job = performCopy(jobContext, fsDRProperties, toSnapshot,
                        fromSnapshot, ReplicationMetrics.JobType.RECOVERY);
            } catch (InterruptedException e) {
                cleanUp(jobContext);
                throw new BeaconException(e);
            }
            if (job == null) {
                throw new BeaconException("FS Replication recovery job is null");
            }
        } catch (Exception e) {
            String msg = "Error occurred when getting diff report for target dir: " + targetStagingUri + ", "
                    + "fromSnapshot: " + fromSnapshot + " & toSnapshot: " + toSnapshot;
            LOG.error(msg);
            setInstanceExecutionDetails(jobContext, JobStatus.FAILED, e.getMessage(), job);
            throw new BeaconException(msg, e);
        }
    }

    private RunningJob getJobWithRetries(String jobId) throws BeaconException {
        RunningJob runningJob = null;
        if (jobId != null) {
            int retries = 0;
            while (retries++ < MAX_JOB_RETRIES) {
                LOG.info("Trying to get job [{0}], attempt [{1}]", jobId, retries);
                try {
                    runningJob = getJobClient().getJob(JobID.forName(jobId));
                } catch (IOException ioe) {
                    throw new BeaconException(ioe);
                }

                if (runningJob != null) {
                    break;
                }

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    // Do nothing
                }
            }
        }
        return runningJob;
    }

    private JobClient getJobClient() throws BeaconException {
        try {
            UserGroupInformation loginUser = UserGroupInformation.getLoginUser();
            JobClient jobClient = loginUser.doAs(new PrivilegedExceptionAction<JobClient>() {
                public JobClient run() throws Exception {
                    return new JobClient(new JobConf());
                }
            });
            return jobClient;
        } catch (InterruptedException | IOException e) {
            throw new BeaconException("Exception creating job client:" + e.getMessage(), e);
        }
    }

}
