/**
 * HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES
 *
 * (c) 2016-2018 Hortonworks, Inc. All rights reserved.
 *
 * This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
 * Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
 * to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
 * properly licensed third party, you do not have any rights to this code.
 *
 * If this code is provided to you under the terms of the AGPLv3:
 * (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
 * (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
 *    LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
 * (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
 *    FROM OR RELATED TO THE CODE; AND
 * (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
 *    DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
 *    DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
 *    OR LOSS OR CORRUPTION OF DATA.
 */

package com.hortonworks.beacon.nodes;

import com.hortonworks.beacon.replication.ReplicationJobDetails;
import com.hortonworks.beacon.util.ReplicationType;

import java.util.ArrayList;
import java.util.List;

/**
 * Node generator for jobs.
 */
public final class NodeGenerator {

    public static final String START_NODE = "START-NODE";
    public static final String END_NODE = "END-NODE";

    private NodeGenerator() {
    }

    private static ReplicationJobDetails generateNode(String policyName, String identifier, String type) {
        return new ReplicationJobDetails(identifier, policyName, type, null);
    }

    public static List<ReplicationJobDetails> appendNodes(List<ReplicationJobDetails> details) {
        String policyName = details.get(0).getName();
        List<ReplicationJobDetails> jobDetails = new ArrayList<>(details);
        ReplicationJobDetails startNode = generateNode(policyName, START_NODE, ReplicationType.START.getName());
        jobDetails.add(0, startNode);
        ReplicationJobDetails endNode = generateNode(policyName, END_NODE, ReplicationType.END.getName());
        jobDetails.add(endNode);
        return jobDetails;
    }
}
