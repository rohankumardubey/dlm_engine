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

package com.hortonworks.beacon.scheduler;

import com.hortonworks.beacon.config.BeaconConfig;
import com.hortonworks.beacon.log.BeaconLog;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for scheduling housekeeping jobs.
 */
public final class HousekeepingScheduler {

    private static final BeaconLog LOG = BeaconLog.getLog(HousekeepingScheduler.class);

    private static int housekeepingThreads = BeaconConfig.getInstance().getScheduler().getHousekeepingThreads();
    private static ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(housekeepingThreads);

    private HousekeepingScheduler() {
    }

    public static void schedule(final Callable<Void> callable, int frequency, int initialDelay,
                                TimeUnit initialDelayUnit) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    callable.call();
                } catch (Exception e) {
                    LOG.error("Exception while execution {}", callable.getClass().getName(), e);
                }
            }
        };
        scheduler.scheduleWithFixedDelay(runnable, initialDelay, frequency, initialDelayUnit);
    }
}