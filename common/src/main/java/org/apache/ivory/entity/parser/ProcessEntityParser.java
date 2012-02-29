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

package org.apache.ivory.entity.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Date;

import org.apache.ivory.IvoryException;
import org.apache.ivory.entity.store.ConfigurationStore;
import org.apache.ivory.entity.v0.EntityType;
import org.apache.ivory.entity.v0.feed.Cluster;
import org.apache.ivory.entity.v0.feed.Feed;
import org.apache.ivory.entity.v0.feed.LocationType;
import org.apache.ivory.entity.v0.process.Input;
import org.apache.ivory.entity.v0.process.Output;
import org.apache.ivory.entity.v0.process.Process;
import org.apache.ivory.entity.v0.process.Validity;
import org.apache.ivory.util.StartupProperties;
import org.apache.log4j.Logger;
import org.apache.oozie.client.OozieClient;
import org.apache.oozie.coord.CoordELFunctions;
import org.apache.oozie.coord.SyncCoordAction;
import org.apache.oozie.coord.SyncCoordDataset;
import org.apache.oozie.coord.TimeUnit;
import org.apache.oozie.service.ELService;
import org.apache.oozie.service.Services;
import org.apache.oozie.util.DateUtils;
import org.apache.oozie.util.ELEvaluator;
import org.apache.oozie.util.IOUtils;

/**
 * Concrete Parser which has XML parsing and validation logic for Process XML.
 * 
 */
public class ProcessEntityParser extends EntityParser<Process> {

	private static final Logger LOG = Logger
			.getLogger(ProcessEntityParser.class);

	public ProcessEntityParser() {
		super(EntityType.PROCESS);
	}

	public static void init() throws IvoryException {
		String uri = StartupProperties.get().getProperty(
				"config.oozie.conf.uri");
		System.setProperty(Services.OOZIE_HOME_DIR, uri);
		File confFile = new File(uri + "/conf");
		if (!confFile.exists() && !confFile.mkdirs())
			throw new IvoryException("Failed to create conf directory in path "
					+ uri);

		InputStream instream = ProcessEntityParser.class
				.getResourceAsStream("/oozie-site.xml");
		try {
			IOUtils.copyStream(instream, new FileOutputStream(uri
					+ "/conf/oozie-site.xml"));
			Services services = new Services();
			services.getConf().set("oozie.services",
					"org.apache.oozie.service.ELService");
			services.init();
		} catch (Exception e) {
			throw new IvoryException(e);
		}
	}

	@Override
	public void validate(Process process) throws IvoryException {
		// check if dependent entities exists
		if (process.getCluster() == null)
			throw new ValidationException("No clusters defined in process");

		String clusterName = process.getCluster().getName();
		validateEntityExists(EntityType.CLUSTER, clusterName);
		validateProcessValidity(process.getValidity().getStart(), process
				.getValidity().getEnd());

		if (process.getInputs() != null
				&& process.getInputs().getInput() != null)
			for (Input input : process.getInputs().getInput()) {
				validateEntityExists(EntityType.FEED, input.getFeed());
				validateFeedDefinedForCluster(input.getFeed(), clusterName);
				//validateFeedValidityForCluster(input.getStartInstance(),input.getEndInstance(),input.getFeed(),clusterName);
				validateInstanceRange(process, input);
			}

		if (process.getOutputs() != null
				&& process.getOutputs().getOutput() != null)
			for (Output output : process.getOutputs().getOutput()) {
				validateEntityExists(EntityType.FEED, output.getFeed());
				validateFeedDefinedForCluster(output.getFeed(), clusterName);
				validateInstance(process, output);
			}

		// validate partitions
		if (process.getInputs() != null
				&& process.getInputs().getInput() != null)
			for (Input input : process.getInputs().getInput()) {
				if (input.getPartition() != null) {
					String partition = input.getPartition();
					String[] parts = partition.split("/");
					Feed feed = ConfigurationStore.get().get(EntityType.FEED,
							input.getFeed());
					if (feed.getPartitions() == null
							|| feed.getPartitions().getPartition() == null
							|| feed.getPartitions().getPartition().size() == 0
							|| feed.getPartitions().getPartition().size() < parts.length)
						throw new ValidationException(
								"Partition specification in input "
										+ input.getName() + " is wrong");
				}
			}
	}

	private void validateProcessValidity(String start, String end)
			throws IvoryException {
		try {
			Date processStart = DateUtils.parseDateUTC(start);
			Date processEnd = DateUtils.parseDateUTC(end);
			if (processStart.after(processEnd)) {
				throw new ValidationException("Process start time: " + start
						+ " cannot be after process end time: " + end);
			}
		} catch (ValidationException e) {
			throw new ValidationException(e);
		} catch (Exception e) {
			throw new IvoryException(e);
		}
	}

	private void validateFeedDefinedForCluster(String feedName,
			String clusterName) throws IvoryException {
		Feed feed = (Feed) ConfigurationStore.get().get(EntityType.FEED,
				feedName);
		if (feed.getCluster(clusterName) == null)
			throw new ValidationException("Feed " + feed.getName()
					+ " is not defined for cluster " + clusterName);
	}

	private void configEvaluator(SyncCoordDataset ds, SyncCoordAction appInst,
			ELEvaluator eval, Feed feed, String clusterName,
			Validity procValidity) throws IvoryException {
		try {
			Cluster cluster = feed.getCluster(clusterName);
			ds.setInitInstance(DateUtils.parseDateUTC(cluster.getValidity()
					.getStart()));
			ds.setFrequency(Integer.valueOf(feed.getPeriodicity()));
			ds.setTimeUnit(Frequency.valueOf(feed.getFrequency()).timeUnit);
			ds.setEndOfDuration(Frequency.valueOf(feed.getFrequency()).endOfDuration);
			ds.setTimeZone(DateUtils.getTimeZone(cluster.getValidity()
					.getTimezone()));
			ds.setName(feed.getName());
			ds.setUriTemplate(feed.getLocations().get(LocationType.DATA)
					.getPath());
			ds.setType("SYNC");
			ds.setDoneFlag("");

			appInst.setActualTime(DateUtils.parseDateUTC(procValidity
					.getStart()));
			appInst.setNominalTime(DateUtils.parseDateUTC(procValidity
					.getStart()));
			appInst.setTimeZone(DateUtils.getTimeZone(procValidity
					.getTimezone()));
			appInst.setActionId("porcess@1");
			appInst.setName("process");

			eval.setVariable(OozieClient.USER_NAME, "test_user");
			eval.setVariable(OozieClient.GROUP_NAME, "test_group");
			CoordELFunctions.configureEvaluator(eval, ds, appInst);
		} catch (Exception e) {
			throw new IvoryException(e);
		}
	}

	// Mapping to oozie coord's dataset fields
	private enum Frequency {
		minutes(TimeUnit.MINUTE, TimeUnit.NONE), hours(TimeUnit.HOUR,
				TimeUnit.NONE), days(TimeUnit.DAY, TimeUnit.NONE), months(
				TimeUnit.MONTH, TimeUnit.NONE), endOfDays(TimeUnit.DAY,
				TimeUnit.END_OF_DAY), endOfMonths(TimeUnit.MONTH,
				TimeUnit.END_OF_MONTH);

		private TimeUnit timeUnit;
		private TimeUnit endOfDuration;

		private Frequency(TimeUnit timeUnit, TimeUnit endOfDuration) {
			this.timeUnit = timeUnit;
			this.endOfDuration = endOfDuration;
		}
	}

	private void validateInstance(Process process, Output output)
			throws IvoryException {
		ELEvaluator eval = Services.get().get(ELService.class)
				.createEvaluator("coord-action-create");
		SyncCoordDataset ds = new SyncCoordDataset();
		SyncCoordAction appInst = new SyncCoordAction();
		Feed feed = ConfigurationStore.get().get(EntityType.FEED,
				output.getFeed());
		String clusterName = process.getCluster().getName();
		configEvaluator(ds, appInst, eval, feed, clusterName,
				process.getValidity());

		try {
			org.apache.ivory.entity.v0.feed.Validity feedValidity = feed
					.getCluster(clusterName).getValidity();
			Date feedEnd = DateUtils.parseDateUTC(feedValidity.getEnd());

			String instEL = output.getInstance();
			String instStr = CoordELFunctions.evalAndWrap(eval, "${elext:"
					+ instEL + "}");
			if (instStr.equals(""))
				throw new ValidationException("Instance  " + instEL
						+ " of feed " + feed.getName()
						+ " is before the start of feed "
						+ feedValidity.getStart());

			Date inst = DateUtils.parseDateUTC(instStr);
			if (inst.after(feedEnd))
				throw new ValidationException("End instance " + instEL
						+ " for feed " + feed.getName()
						+ " is after the end of feed " + feedValidity.getEnd());
		} catch (ValidationException e) {
			throw e;
		} catch (Exception e) {
			throw new IvoryException(e);
		}
	}

	private void validateInstanceRange(Process process, Input input)
			throws IvoryException {
		ELEvaluator eval = Services.get().get(ELService.class)
				.createEvaluator("coord-action-create");
		SyncCoordDataset ds = new SyncCoordDataset();
		SyncCoordAction appInst = new SyncCoordAction();
		Feed feed = ConfigurationStore.get().get(EntityType.FEED,
				input.getFeed());
		String clusterName = process.getCluster().getName();
		configEvaluator(ds, appInst, eval, feed, clusterName,
				process.getValidity());

		try {

			org.apache.ivory.entity.v0.feed.Validity feedValidity = feed
					.getCluster(clusterName).getValidity();
			Date feedEnd = DateUtils.parseDateUTC(feedValidity.getEnd());

			String instStartEL = input.getStartInstance();
			String instEndEL = input.getEndInstance();

			String instStartStr = CoordELFunctions.evalAndWrap(eval, "${elext:"
					+ instStartEL + "}");
			if (instStartStr.equals(""))
				throw new ValidationException("Start instance  " + instStartEL
						+ " of feed " + feed.getName()
						+ " is before the start of feed "
						+ feedValidity.getStart());

			String instEndStr = CoordELFunctions.evalAndWrap(eval, "${elext:"
					+ instEndEL + "}");
			if (instEndStr.equals(""))
				throw new ValidationException("End instance  " + instEndEL
						+ " of feed " + feed.getName()
						+ " is before the start of feed "
						+ feedValidity.getStart());

			Date instStart = DateUtils.parseDateUTC(instStartStr);
			Date instEnd = DateUtils.parseDateUTC(instEndStr);
			if (instEnd.before(instStart))
				throw new ValidationException("End instance " + instEndEL
						+ " for feed " + feed.getName()
						+ " is before the start instance " + instStartEL);

			if (instEnd.after(feedEnd))
				throw new ValidationException("End instance " + instEndEL
						+ " for feed " + feed.getName()
						+ " is after the end of feed " + feedValidity.getEnd());
		} catch (ValidationException e) {
			throw e;
		} catch (Exception e) {
			throw new IvoryException(e);
		}
	}
}
