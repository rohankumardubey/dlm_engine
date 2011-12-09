package org.apache.airavat.mappers;

import org.apache.airavat.entity.v0.ProcessType;
import org.apache.airavat.entity.v0.ValidityType;
import org.apache.airavat.oozie.coordinator.COORDINATORAPP;
import org.testng.Assert;
import org.testng.annotations.Test;

/*
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

public class ProcessToCoordinatorTest {

	COORDINATORAPP coordinatorapp = new COORDINATORAPP();

	@Test
	public void testMap() {

		ProcessType processType = new ProcessType();
		processType.setFrequency("frequency");
		processType.setName("ProcessName");

		ValidityType validityType = new ValidityType();
		validityType.setValue("validityValue");

		processType.setValidity(validityType);
		
		coordinatorapp.setStart("StartTime");
		coordinatorapp.setEnd("EndTime");

		//Map
		new CoordinateMapper(processType, coordinatorapp).map();
		
		Assert.assertEquals(coordinatorapp.getName(),
				processType.getName());
		
		Assert.assertEquals(coordinatorapp.getFrequency(),
				processType.getFrequency());

		Assert.assertEquals(coordinatorapp.getControls().getTimeout(),
				processType.getValidity().getValue());
		
		Assert.assertEquals(coordinatorapp.getStart(), "StartTime");
		
		Assert.assertEquals(coordinatorapp.getEnd(), "EndTime");
	}

}
