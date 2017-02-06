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

package com.hortonworks.beacon.util;

import com.hortonworks.beacon.config.BeaconConfig;
import com.hortonworks.beacon.exceptions.BeaconException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test class for the Filesystem utilities.
 */
public class FSUtilsTest {

    @BeforeClass
    private void setup() throws Exception {
        Configuration conf = new Configuration();
        conf.set("fs.s3n.awsAccessKeyId", "testS3KeyId");
        conf.set("fs.s3n.awsSecretAccessKey", "testS3AccessKey");
        conf.set("fs.azure.account.key.mystorage.blob.core.windows.net", "dGVzdEF6dXJlQWNjZXNzS2V5");
        FSUtils.setDefaultConf(conf);
    }

    @Test(expectedExceptions = BeaconException.class, expectedExceptionsMessageRegExp = "filePath cannot be empty")
    public void testIsHCFSEmptyPath() throws Exception {
        FSUtils.isHCFS(null);
    }

    @Test
    public void testIsHCFSInTestMode() throws Exception {
        BeaconConfig.getInstance().getEngine().setInTestMode(true);
        boolean isHCFSPath = FSUtils.isHCFS(new Path("/apps/dr"));
        Assert.assertFalse(isHCFSPath);

        isHCFSPath = FSUtils.isHCFS(new Path("hdfs://localhost:54136/apps/dr"));
        Assert.assertFalse(isHCFSPath);

        isHCFSPath = FSUtils.isHCFS(new Path("s3n://testBucket/apps/dr"));
        Assert.assertTrue(isHCFSPath);

        isHCFSPath = FSUtils.isHCFS(new Path("wasb://replication-test@mystorage.blob.core.windows.net/apps/dr"));
        Assert.assertTrue(isHCFSPath);
    }

    @Test
    public void testIsHCFSInNonTestMode() throws Exception {
        boolean isHCFSPath = FSUtils.isHCFS(new Path("hdfs://localhost:54136/apps/dr"));
        Assert.assertFalse(isHCFSPath);

        isHCFSPath = FSUtils.isHCFS(new Path("s3n://testBucket/apps/dr"));
        Assert.assertTrue(isHCFSPath);

        isHCFSPath = FSUtils.isHCFS(new Path("wasb://replication-test@mystorage.blob.core.windows.net/apps/dr"));
        Assert.assertTrue(isHCFSPath);
    }

}
