/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hortonworks.beacon.utils;

import com.hortonworks.beacon.config.BeaconConfig;
import com.hortonworks.beacon.config.Store;
import org.quartz.JobDataMap;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

public class SchedulerUtils {
    private static BeaconConfig config = BeaconConfig.getInstance();

    private static final String createQueries[] = {
            "create user \"quartz\" password \"quartz\" ADMIN",
            "create table beacon_job_details ("
                    + " sched_name varchar(120) not null,"
                    + " job_name varchar(200) not null,"
                    + " job_group varchar(200) not null,"
                    + " description varchar(250) ,"
                    + " job_class_name varchar(250) not null,"
                    + " is_durable varchar(5) not null,"
                    + " is_nonconcurrent varchar(5) not null,"
                    + " is_update_data varchar(5) not null,"
                    + " requests_recovery varchar(5) not null,"
                    + " job_data blob,"
                    + " primary key (sched_name,job_name,job_group)"
                    + " )",
            "create table beacon_triggers ("
                    + " sched_name varchar(120) not null,"
                    + " trigger_name varchar(200) not null,"
                    + " trigger_group varchar(200) not null,"
                    + " job_name varchar(200) not null,"
                    + " job_group varchar(200) not null,"
                    + " description varchar(250),"
                    + " next_fire_time bigint,"
                    + " prev_fire_time bigint,"
                    + " priority integer,"
                    + " trigger_state varchar(16) not null,"
                    + " trigger_type varchar(8) not null,"
                    + " start_time bigint not null,"
                    + " end_time bigint,"
                    + " calendar_name varchar(200),"
                    + " misfire_instr smallint,"
                    + " job_data blob,"
                    + " primary key (sched_name,trigger_name,trigger_group),"
                    + " foreign key (sched_name,job_name,job_group) references beacon_job_details(sched_name,job_name,job_group)"
                    + " )",

            "create table beacon_simple_triggers ("
                    + " sched_name varchar(120) not null,"
                    + " trigger_name varchar(200) not null,"
                    + " trigger_group varchar(200) not null,"
                    + " repeat_count bigint not null,"
                    + " repeat_interval bigint not null,"
                    + " times_triggered bigint not null,"
                    + " primary key (sched_name,trigger_name,trigger_group),"
                    + " foreign key (sched_name,trigger_name,trigger_group) references beacon_triggers(sched_name,trigger_name,trigger_group)"
                    + " )",

            "create table beacon_cron_triggers ("
                    + " sched_name varchar(120) not null,"
                    + " trigger_name varchar(200) not null,"
                    + " trigger_group varchar(200) not null,"
                    + " cron_expression varchar(120) not null,"
                    + " time_zone_id varchar(80),"
                    + " primary key (sched_name,trigger_name,trigger_group),"
                    + " foreign key (sched_name,trigger_name,trigger_group) references beacon_triggers(sched_name,trigger_name,trigger_group)"
                    + " )",

            "create table beacon_simprop_triggers ("
                    + " sched_name varchar(120) not null,"
                    + " trigger_name varchar(200) not null,"
                    + " trigger_group varchar(200) not null,"
                    + " str_prop_1 varchar(512),"
                    + " str_prop_2 varchar(512),"
                    + " str_prop_3 varchar(512),"
                    + " int_prop_1 int,"
                    + " int_prop_2 int,"
                    + " long_prop_1 bigint,"
                    + " long_prop_2 bigint,"
                    + " dec_prop_1 numeric(13,4),"
                    + " dec_prop_2 numeric(13,4),"
                    + " bool_prop_1 varchar(5),"
                    + " bool_prop_2 varchar(5),"
                    + " primary key (sched_name,trigger_name,trigger_group),"
                    + " foreign key (sched_name,trigger_name,trigger_group)"
                    + " references beacon_triggers(sched_name,trigger_name,trigger_group)"
                    + " )",

            "create table beacon_blob_triggers ("
                    + " sched_name varchar(120) not null,"
                    + " trigger_name varchar(200) not null,"
                    + " trigger_group varchar(200) not null,"
                    + " blob_data blob,"
                    + " primary key (sched_name,trigger_name,trigger_group),"
                    + " foreign key (sched_name,trigger_name,trigger_group) references beacon_triggers(sched_name,trigger_name,trigger_group)"
                    + " )",

            "create table beacon_calendars ("
                    + " sched_name varchar(120) not null,"
                    + " calendar_name varchar(200) not null,"
                    + " calendar blob not null,"
                    + " primary key (sched_name,calendar_name)"
                    + " )",

            "create table beacon_paused_trigger_grps ("
                    + " sched_name varchar(120) not null,"
                    + " trigger_group varchar(200) not null,"
                    + " primary key (sched_name,trigger_group)"
                    + " )",

            "create table beacon_fired_triggers ("
                    + " sched_name varchar(120) not null,"
                    + " entry_id varchar(95) not null,"
                    + " trigger_name varchar(200) not null,"
                    + " trigger_group varchar(200) not null,"
                    + " instance_name varchar(200) not null,"
                    + " fired_time bigint not null,"
                    + " sched_time bigint not null,"
                    + " priority integer not null,"
                    + " state varchar(16) not null,"
                    + " job_name varchar(200),"
                    + " job_group varchar(200),"
                    + " is_nonconcurrent varchar(5),"
                    + " requests_recovery varchar(5),"
                    + " primary key (sched_name,entry_id)"
                    + " )",

            "create table beacon_scheduler_state ("
                    + " sched_name varchar(120) not null,"
                    + " instance_name varchar(200) not null,"
                    + " last_checkin_time bigint not null,"
                    + " checkin_interval bigint not null,"
                    + " primary key (sched_name,instance_name)"
                    + " )",

            "create table beacon_locks ("
                    + " sched_name varchar(120) not null,"
                    + " lock_name varchar(40) not null,"
                    + " primary key (sched_name,lock_name)"
                    + " )",

            "create table chained_jobs ("
                    + " id bigint  generated by default as identity (start with 1, increment by 1),"
                    + " first_job_name varchar(40) not null,"
                    + " first_job_group varchar(40) not null,"
                    + " second_job_name varchar(40) not null,"
                    + " second_job_group varchar(40) not null,"
                    + "created_time bigint not null,"
                    + " primary key(id)"
                    + " )",

            "create table job_instance ("
                    + " id varchar(80) not null,"
                    + " job_name varchar(80) not null,"
                    + " job_group varchar(80) not null,"
                    + " class_name varchar(80) not null,"
                    + " name varchar(80) not null,"
                    + " job_type varchar(80) not null,"
                    + " start_time bigint,"
                    + " end_time bigint,"
                    + " frequency int,"
                    + " duration bigint,"
                    + " deleted int,"
                    + " status varchar(40),"
                    + " message varchar(255),"
                    + " primary key (id)"
                    + " )",
    };

    public static JobDataMap prepareJobData(Map<String, Object> map) {
        JobDataMap dataMap = new JobDataMap();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            dataMap.put(entry.getKey(), entry.getValue());
        }
        return dataMap;
    }

    public static String getUUID() {
        return UUID.randomUUID().toString();
    }

    public static void createDBSchema() throws Exception {
        Store store = config.getStore();
        Class.forName(store.getDriver());
        Connection con =
                DriverManager.getConnection(store.getUrl());
        String s = null;
        Statement stmt = null;
        try {
            stmt = con.createStatement();
            for (int i = 0; i < createQueries.length; ++i) {
                s = createQueries[i];
                stmt.execute(s);
            }
        } catch (SQLException sqle) {
            throw (IOException) new IOException("Exception executing " + s + ": " + sqle)
                    .initCause(sqle);
        } finally {
           if (stmt != null) {
               stmt.close();
           }
           if (con != null) {
               con.close();
           }
        }

    }
}
