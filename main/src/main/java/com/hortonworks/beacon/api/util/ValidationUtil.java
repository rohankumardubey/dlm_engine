package com.hortonworks.beacon.api.util;

import com.hortonworks.beacon.api.exception.BeaconWebException;
import com.hortonworks.beacon.client.entity.EntityType;
import com.hortonworks.beacon.client.entity.ReplicationPolicy;
import com.hortonworks.beacon.config.BeaconConfig;
import com.hortonworks.beacon.entity.store.ConfigurationStore;
import com.hortonworks.beacon.exceptions.BeaconException;

import java.util.NoSuchElementException;

public final class ValidationUtil {
    private static final String ERROR_MESSAGE_PART1 = "This operation is not allowed on source cluster: ";
    private static final String ERROR_MESSAGE_PART2 = ".Try it on target cluster: ";
    private static final BeaconConfig config = BeaconConfig.getInstance();
    private static final ConfigurationStore configStore = ConfigurationStore.getInstance();

    private ValidationUtil() {
    }

    public enum OperationType {
        READ,
        WRITE
    }

    public static void validateIfAPIRequestAllowed(String replicationPolicyName, OperationType operationType)
            throws BeaconException {
        if (OperationType.READ == operationType) {
            return;
        }

        ReplicationPolicy policy = configStore.getEntity(EntityType.REPLICATIONPOLICY, replicationPolicyName);
        if (policy == null) {
            throw new NoSuchElementException(replicationPolicyName + " (" + EntityType.REPLICATIONPOLICY.name() +
                    ") not " + "found");
        }

        isRequestAllowed(policy, operationType);
    }

    public static void validateIfAPIRequestAllowed(ReplicationPolicy policy, OperationType operationType)
            throws BeaconException {
        if (OperationType.READ == operationType) {
            return;
        }
        if (policy == null) {
            throw new BeaconException("Policy cannot be null");
        }

        isRequestAllowed(policy, operationType);
    }

    private static boolean isRequestAllowed(ReplicationPolicy policy, OperationType operationType) {
        String sourceClusterName = policy.getSourceCluster();
        String targetClusterName = policy.getTargetCluster();
        String localClusterName = config.getEngine().getLocalClusterName();

        if (localClusterName.equalsIgnoreCase(sourceClusterName) && OperationType.WRITE == operationType) {
            /* TODO : Add logic to check if target dataset is HCFS. Then write operations are allowed on source */
            String message = ERROR_MESSAGE_PART1 + sourceClusterName + ERROR_MESSAGE_PART2 + targetClusterName;
            throw BeaconWebException.newAPIException(message);
        }

        return true;
    }
}
