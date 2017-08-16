/**
 *   Copyright  (c) 2016-2017, Hortonworks Inc.  All rights reserved.
 *
 *   Except as expressly permitted in a written agreement between you or your
 *   company and Hortonworks, Inc. or an authorized affiliate or partner
 *   thereof, any use, reproduction, modification, redistribution, sharing,
 *   lending or other exploitation of all or any part of the contents of this
 *   software is strictly prohibited.
 */

package com.hortonworks.beacon.entity;

import com.hortonworks.beacon.client.entity.Cluster;
import com.hortonworks.beacon.client.entity.EntityType;
import com.hortonworks.beacon.config.BeaconConfig;
import com.hortonworks.beacon.entity.exceptions.ValidationException;
import com.hortonworks.beacon.entity.util.ClusterHelper;
import com.hortonworks.beacon.entity.util.HiveDRUtils;
import com.hortonworks.beacon.exceptions.BeaconException;
import com.hortonworks.beacon.log.BeaconLog;
import com.hortonworks.beacon.rb.MessageCode;
import com.hortonworks.beacon.util.FileSystemClientFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.NoSuchElementException;

/**
 * Validation helper function to validate Beacon Cluster definition.
 */
public class ClusterValidator extends EntityValidator<Cluster> {
    private static final BeaconLog LOG = BeaconLog.getLog(ClusterValidator.class);
    private static final String FS_DEFAULT_NAME_KEY = "fs.defaultFs";
    private static final String IPC_MAX_TRIES = "ipc.client.connect.max.retries";
    private static final String SHOW_DATABASES = "SHOW DATABASES";

    ClusterValidator() {
        super(EntityType.CLUSTER);
    }

    @Override
    public void validate(Cluster entity) throws BeaconException {
        if (entity.isLocal()) {
            validateClusterName(entity);
            validateClusterExists();
        }
        validateFSInterface(entity);
        validateHiveInterface(entity);
    }

    private void validateFSInterface(Cluster entity) throws ValidationException {
        String fsEndPoint = entity.getFsEndpoint();
        validateFileSystem(fsEndPoint);
    }

    private void validateFileSystem(String storageUrl) throws ValidationException {
        try {
            LOG.info(MessageCode.ENTI_000010.name(), storageUrl);
            Configuration conf = new Configuration();
            conf.set(FS_DEFAULT_NAME_KEY, storageUrl);
            conf.setInt(IPC_MAX_TRIES, 10);
            FileSystem fs = FileSystemClientFactory.get().createProxiedFileSystem(conf);
            fs.exists(new Path("/"));
        } catch (Exception e) {
            LOG.error(MessageCode.ENTI_000012.name(), storageUrl + ", " + e);
            throw new ValidationException(MessageCode.ENTI_000010.name(), storageUrl + ", " + e.getMessage());
        }
    }

    private void validateHiveInterface(Cluster entity) throws BeaconException {
        String hsEndPoint = entity.getHsEndpoint();
        LOG.info(MessageCode.ENIT_000011.name(), hsEndPoint);
        if (StringUtils.isBlank(hsEndPoint)) {
            return;
        }

        HiveDRUtils.initializeDriveClass();
        String connString = HiveDRUtils.getHS2ConnectionUrl(hsEndPoint, " ");
        Connection connection = null;
        Statement statement = null;
        try {
            connection = HiveDRUtils.getConnection(connString);
            statement = connection.createStatement();
            try (ResultSet rs = statement.executeQuery(SHOW_DATABASES)) {
                if (rs.next()) {
                    String db = rs.getString(1);
                    if (db == null) {
                        throw new SQLException(MessageCode.ENTI_000013.name(), hsEndPoint);
                    }
                }
            }
        } catch (Exception sqe) {
            LOG.error(MessageCode.ENTI_000014.name(), sqe.getMessage());
            throw new ValidationException(MessageCode.ENTI_000014.name(), sqe.getMessage());
        } finally {
            HiveDRUtils.cleanup(statement, connection);
        }
    }

    private void validateClusterExists() throws BeaconException {
        try {
            Cluster localCluster = ClusterHelper.getLocalCluster();
            if (localCluster != null) {
                throw new ValidationException(MessageCode.ENTI_000016.name(), localCluster.getName());
            }
        } catch (NoSuchElementException e) {
            //nothing to do.
        }
    }

    private void validateClusterName(Cluster cluster) throws ValidationException {
        boolean localCluster = ClusterHelper.isLocalCluster(cluster.getName());
        if (!localCluster) {
            throw new ValidationException(MessageCode.ENTI_000015.name(),
                    BeaconConfig.getInstance().getEngine().getLocalClusterName());
        }
    }
}
