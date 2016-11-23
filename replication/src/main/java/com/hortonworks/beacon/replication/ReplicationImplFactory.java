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


package com.hortonworks.beacon.replication;

import com.hortonworks.beacon.replication.hdfs.HDFSDRImpl;
import com.hortonworks.beacon.replication.hdfssnapshot.HDFSSnapshotDRImpl;
import com.hortonworks.beacon.replication.hive.HiveDRImpl;
import com.hortonworks.beacon.replication.test.TestDRImpl;

public class ReplicationImplFactory {

    private ReplicationImplFactory() {
    }

    public static DRReplication getReplicationImpl(ReplicationJobDetails details) {
        if ((details.getType()).equals(ReplicationType.HIVE.getName())) {
            return new HiveDRImpl(details);
        } else if ((details.getType()).equals(ReplicationType.HDFS.getName())) {
            return new HDFSDRImpl(details);
        } else if ((details.getType()).equals(ReplicationType.HDFSSNAPSHOT.getName())) {
            return new HDFSSnapshotDRImpl(details);
        } if (details.getType().equals(ReplicationType.TEST.getName())) {
            return new TestDRImpl(details);
        } else {
            throw new IllegalArgumentException("Invalid replication type: " + details.getName());
        }
    }
}
