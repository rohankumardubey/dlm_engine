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
package org.apache.falcon.rerun.event;

public class LaterunEvent extends RerunEvent {

	public LaterunEvent(String clusterName, String wfId, long msgInsertTime,
			long delay, String entityType, String entityName, String instance,
			int runId) {
		super(clusterName, wfId, msgInsertTime, delay, entityType, entityName,
				instance, runId);
	}

	@Override
	public String toString() {
		return "clusterName=" + clusterName + SEP + "wfId=" + wfId + SEP
				+ "msgInsertTime=" + msgInsertTime + SEP + "delayInMilliSec="
				+ delayInMilliSec + SEP + "entityType=" + entityType + SEP
				+ "entityName=" + entityName + SEP + "instance=" + instance
				+ SEP + "runId=" + runId;
	}

}
