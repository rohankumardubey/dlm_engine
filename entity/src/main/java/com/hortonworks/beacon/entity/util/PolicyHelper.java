/**
 *   Copyright  (c) 2016-2017, Hortonworks Inc.  All rights reserved.
 *
 *   Except as expressly permitted in a written agreement between you or your
 *   company and Hortonworks, Inc. or an authorized affiliate or partner
 *   thereof, any use, reproduction, modification, redistribution, sharing,
 *   lending or other exploitation of all or any part of the contents of this
 *   software is strictly prohibited.
 */

package com.hortonworks.beacon.entity.util;

import com.hortonworks.beacon.EncryptionAlgorithmType;
import com.hortonworks.beacon.client.entity.Cluster;
import com.hortonworks.beacon.client.entity.ReplicationPolicy;
import com.hortonworks.beacon.exceptions.BeaconException;
import com.hortonworks.beacon.util.FSUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper util class for Beacon ReplicationPolicy resource.
 */
public final class PolicyHelper {
    private static final Logger LOG = LoggerFactory.getLogger(PolicyHelper.class);

    private PolicyHelper() {
    }

    public static String getRemoteBeaconEndpoint(final ReplicationPolicy policy) throws BeaconException {

        if (PolicyHelper.isPolicyHCFS(policy.getSourceDataset(), policy.getTargetDataset())) {
            throw new BeaconException("No remote beacon endpoint for HCFS policy: {}", policy.getName());
        }
        String remoteClusterName = getRemoteClusterName(policy);
        Cluster remoteCluster = ClusterHelper.getActiveCluster(remoteClusterName);
        return remoteCluster.getBeaconEndpoint();
    }

    public static String getRemoteClusterName(final ReplicationPolicy policy) throws BeaconException {
        String localClusterName = ClusterHelper.getLocalCluster().getName();
        return localClusterName.equalsIgnoreCase(policy.getSourceCluster())
                ? policy.getTargetCluster() : policy.getSourceCluster();
    }

    public static boolean isPolicyHCFS(final String sourceDataset, final String targetDataset) throws BeaconException {
        return isDatasetHCFS(sourceDataset) || isDatasetHCFS(targetDataset);
    }

    public static boolean isDatasetHCFS(final String dataset) throws BeaconException {
        if (StringUtils.isNotBlank(dataset)) {
            Path datasetPath = new Path(dataset);
            if (FSUtils.isHCFS(datasetPath)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCloudEncryptionEnabled(final ReplicationPolicy policy) {
        return StringUtils.isNotEmpty(policy.getCloudEncryptionAlgorithm());
    }

    public static EncryptionAlgorithmType getCloudEncryptionAlgorithm(final ReplicationPolicy policy)
            throws BeaconException {
        if (!isCloudEncryptionEnabled(policy)) {
            return EncryptionAlgorithmType.NONE;
        }
        String cloudEncAlgo = policy.getCloudEncryptionAlgorithm();
        try {
            return EncryptionAlgorithmType.valueOf(cloudEncAlgo);
        } catch (IllegalArgumentException iEx) {
            throw new BeaconException("Invalid cloud algorithm type is specified " + cloudEncAlgo, iEx);
        } catch (NullPointerException npe) {
            throw new BeaconException("Cloud Encryption Algorithm cannot be null", npe);
        }
    }

    public static String getRMTokenConf() {
        StringBuilder rmTokenConf = new StringBuilder();
        rmTokenConf.append("dfs.nameservices|")
                .append("^dfs.namenode.rpc-address.*$|")
                .append("^dfs.ha.namenodes.*$|")
                .append("^dfs.client.failover.proxy.provider.*$|")
                .append("dfs.namenode.kerberos.principal|")
                .append("dfs.namenode.kerberos.principal.pattern|")
                .append("mapreduce.jobhistory.principal|")
                .append("^ssl.client.*$|")
                .append("^hadoop.ssl.*$|")
                .append("hadoop.rpc.protection|")
                .append("^yarn.timeline-service.*$|")
                .append("fs.defaultFS|")
                .append("yarn.http.policy");
        return rmTokenConf.toString();
    }
}
