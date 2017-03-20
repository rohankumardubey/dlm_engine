-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.


-- Auto drop and reset tables
-- Derby doesn't support if exists condition on table drop, so user must manually do this step if needed to.
-- noinspection SqlDialectInspection

-- DROP TABLE QUARTZ_BLOB_TRIGGERS;
-- DROP TABLE QUARTZ_CALENDARS;
-- DROP TABLE QUARTZ_CRON_TRIGGERS;
-- DROP TABLE QUARTZ_FIRED_TRIGGERS;
-- DROP TABLE QUARTZ_LOCKS;
-- DROP TABLE QUARTZ_SCHEDULER_STATE;
-- DROP TABLE QUARTZ_SIMPROP_TRIGGERS;
-- DROP TABLE QUARTZ_SIMPLE_TRIGGERS;
-- DROP TABLE QUARTZ_TRIGGERS;
-- DROP TABLE QUARTZ_PAUSED_TRIGGER_GRPS;
-- DROP TABLE QUARTZ_JOB_DETAILS;
-- DROP TABLE BEACON_POLICY;
-- DROP TABLE BEACON_POLICY_PROP;
-- DROP TABLE BEACON_POLICY_INSTANCE;
-- DROP TABLE BEACON_INSTANCE_JOB;
-- DROP TABLE BEACON_CHAINED_JOBS;

CREATE TABLE QUARTZ_JOB_DETAILS (
  SCHED_NAME        VARCHAR(120) NOT NULL,
  JOB_NAME          VARCHAR(200) NOT NULL,
  JOB_GROUP         VARCHAR(200) NOT NULL,
  DESCRIPTION       VARCHAR(250),
  JOB_CLASS_NAME    VARCHAR(250) NOT NULL,
  IS_DURABLE        VARCHAR(5)   NOT NULL,
  IS_NONCONCURRENT  VARCHAR(5)   NOT NULL,
  IS_UPDATE_DATA    VARCHAR(5)   NOT NULL,
  REQUESTS_RECOVERY VARCHAR(5)   NOT NULL,
  JOB_DATA          BLOB,
  PRIMARY KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
);

CREATE TABLE QUARTZ_TRIGGERS (
  SCHED_NAME     VARCHAR(120) NOT NULL,
  TRIGGER_NAME   VARCHAR(200) NOT NULL,
  TRIGGER_GROUP  VARCHAR(200) NOT NULL,
  JOB_NAME       VARCHAR(200) NOT NULL,
  JOB_GROUP      VARCHAR(200) NOT NULL,
  DESCRIPTION    VARCHAR(250),
  NEXT_FIRE_TIME BIGINT,
  PREV_FIRE_TIME BIGINT,
  PRIORITY       INTEGER,
  TRIGGER_STATE  VARCHAR(16)  NOT NULL,
  TRIGGER_TYPE   VARCHAR(8)   NOT NULL,
  START_TIME     BIGINT       NOT NULL,
  END_TIME       BIGINT,
  CALENDAR_NAME  VARCHAR(200),
  MISFIRE_INSTR  SMALLINT,
  JOB_DATA       BLOB,
  PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME, JOB_NAME, JOB_GROUP) REFERENCES QUARTZ_JOB_DETAILS (SCHED_NAME, JOB_NAME, JOB_GROUP)
);

CREATE TABLE QUARTZ_SIMPLE_TRIGGERS (
  SCHED_NAME      VARCHAR(120) NOT NULL,
  TRIGGER_NAME    VARCHAR(200) NOT NULL,
  TRIGGER_GROUP   VARCHAR(200) NOT NULL,
  REPEAT_COUNT    BIGINT       NOT NULL,
  REPEAT_INTERVAL BIGINT       NOT NULL,
  TIMES_TRIGGERED BIGINT       NOT NULL,
  PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QUARTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE QUARTZ_CRON_TRIGGERS (
  SCHED_NAME      VARCHAR(120) NOT NULL,
  TRIGGER_NAME    VARCHAR(200) NOT NULL,
  TRIGGER_GROUP   VARCHAR(200) NOT NULL,
  CRON_EXPRESSION VARCHAR(120) NOT NULL,
  TIME_ZONE_ID    VARCHAR(80),
  PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QUARTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE QUARTZ_SIMPROP_TRIGGERS
(
  SCHED_NAME    VARCHAR(120) NOT NULL,
  TRIGGER_NAME  VARCHAR(200) NOT NULL,
  TRIGGER_GROUP VARCHAR(200) NOT NULL,
  STR_PROP_1    VARCHAR(512),
  STR_PROP_2    VARCHAR(512),
  STR_PROP_3    VARCHAR(512),
  INT_PROP_1    INT,
  INT_PROP_2    INT,
  LONG_PROP_1   BIGINT,
  LONG_PROP_2   BIGINT,
  DEC_PROP_1    NUMERIC(13, 4),
  DEC_PROP_2    NUMERIC(13, 4),
  BOOL_PROP_1   VARCHAR(5),
  BOOL_PROP_2   VARCHAR(5),
  PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
  REFERENCES QUARTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE QUARTZ_BLOB_TRIGGERS (
  SCHED_NAME    VARCHAR(120) NOT NULL,
  TRIGGER_NAME  VARCHAR(200) NOT NULL,
  TRIGGER_GROUP VARCHAR(200) NOT NULL,
  BLOB_DATA     BLOB,
  PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
  FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QUARTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

CREATE TABLE QUARTZ_CALENDARS (
  SCHED_NAME    VARCHAR(120) NOT NULL,
  CALENDAR_NAME VARCHAR(200) NOT NULL,
  CALENDAR      BLOB         NOT NULL,
  PRIMARY KEY (SCHED_NAME, CALENDAR_NAME)
);

CREATE TABLE QUARTZ_PAUSED_TRIGGER_GRPS
(
  SCHED_NAME    VARCHAR(120) NOT NULL,
  TRIGGER_GROUP VARCHAR(200) NOT NULL,
  PRIMARY KEY (SCHED_NAME, TRIGGER_GROUP)
);

CREATE TABLE QUARTZ_FIRED_TRIGGERS (
  SCHED_NAME        VARCHAR(120) NOT NULL,
  ENTRY_ID          VARCHAR(95)  NOT NULL,
  TRIGGER_NAME      VARCHAR(200) NOT NULL,
  TRIGGER_GROUP     VARCHAR(200) NOT NULL,
  INSTANCE_NAME     VARCHAR(200) NOT NULL,
  FIRED_TIME        BIGINT       NOT NULL,
  SCHED_TIME        BIGINT       NOT NULL,
  PRIORITY          INTEGER      NOT NULL,
  STATE             VARCHAR(16)  NOT NULL,
  JOB_NAME          VARCHAR(200),
  JOB_GROUP         VARCHAR(200),
  IS_NONCONCURRENT  VARCHAR(5),
  REQUESTS_RECOVERY VARCHAR(5),
  PRIMARY KEY (SCHED_NAME, ENTRY_ID)
);

CREATE TABLE QUARTZ_SCHEDULER_STATE
(
  SCHED_NAME        VARCHAR(120) NOT NULL,
  INSTANCE_NAME     VARCHAR(200) NOT NULL,
  LAST_CHECKIN_TIME BIGINT       NOT NULL,
  CHECKIN_INTERVAL  BIGINT       NOT NULL,
  PRIMARY KEY (SCHED_NAME, INSTANCE_NAME)
);

CREATE TABLE QUARTZ_LOCKS
(
  SCHED_NAME VARCHAR(120) NOT NULL,
  LOCK_NAME  VARCHAR(40)  NOT NULL,
  PRIMARY KEY (SCHED_NAME, LOCK_NAME)
);

CREATE TABLE BEACON_POLICY
(
  ID                VARCHAR(200),
  NAME              VARCHAR(64),
  VERSION           INTEGER,
  CHANGE_ID         INTEGER,
  STATUS            VARCHAR(40),
  TYPE              VARCHAR(40),
  SOURCE_CLUSTER    VARCHAR(255),
  TARGET_CLUSTER    VARCHAR(255),
  SOURCE_DATASET    VARCHAR(4000),
  TARGET_DATASET    VARCHAR(4000),
  CREATED_TIME      TIMESTAMP,
  LAST_MODIFIED_TIME     TIMESTAMP,
  START_TIME        TIMESTAMP,
  END_TIME          TIMESTAMP,
  FREQUENCY         INTEGER,
  NOTIFICATION_TYPE VARCHAR(255),
  NOTIFICATION_TO   VARCHAR(255),
  RETRY_COUNT       INT,
  RETRY_DELAY       INT,
  TAGS              VARCHAR(1024),
  EXECUTION_TYPE    VARCHAR(40),
  RETIREMENT_TIME   TIMESTAMP,
  JOBS              VARCHAR(1024),
  PRIMARY KEY (ID)
);

CREATE TABLE BEACON_POLICY_PROP
(
  ID           BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
  POLICY_ID    VARCHAR(200),
  CREATED_TIME TIMESTAMP,
  NAME         VARCHAR(512),
  VALUE        VARCHAR(1024),
  TYPE         VARCHAR(20),
  PRIMARY KEY (ID),
  FOREIGN KEY (POLICY_ID) REFERENCES BEACON_POLICY (ID)
);

CREATE TABLE BEACON_POLICY_INSTANCE
(
  ID                 VARCHAR(255) NOT NULL,
  POLICY_ID          VARCHAR(200),
  JOB_EXECUTION_TYPE VARCHAR(80),
  START_TIME         TIMESTAMP,
  END_TIME           TIMESTAMP,
  RETIREMENT_TIME    TIMESTAMP,
  STATUS             VARCHAR(40),
  MESSAGE            VARCHAR(4000),
  RUN_COUNT          INTEGER,
  CURRENT_OFFSET     INTEGER,
  PRIMARY KEY (ID),
  FOREIGN KEY (POLICY_ID) REFERENCES BEACON_POLICY (ID)
);

CREATE TABLE BEACON_INSTANCE_JOB
(
  INSTANCE_ID     VARCHAR(255) NOT NULL,
  OFFSET          INTEGER      NOT NULL,
  STATUS          VARCHAR(40),
  START_TIME      TIMESTAMP,
  END_TIME        TIMESTAMP,
  MESSAGE         VARCHAR(4000),
  RETIREMENT_TIME TIMESTAMP,
  RUN_COUNT       INTEGER,
  CONTEXT_DATA    VARCHAR(4000),
  JOB_DETAIL      VARCHAR(4000),
  PRIMARY KEY (INSTANCE_ID, OFFSET),
  FOREIGN KEY (INSTANCE_ID) REFERENCES BEACON_POLICY_INSTANCE (ID)
);

CREATE TABLE BEACON_CHAINED_JOBS
(
  ID               BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
  CREATED_TIME     TIMESTAMP,
  FIRST_JOB_NAME   VARCHAR(200),
  FIRST_JOB_GROUP  VARCHAR(200),
  SECOND_JOB_NAME  VARCHAR(200),
  SECOND_JOB_GROUP VARCHAR(200),
  PRIMARY KEY (ID),
  FOREIGN KEY (FIRST_JOB_NAME) REFERENCES BEACON_POLICY (ID),
  FOREIGN KEY (SECOND_JOB_NAME) REFERENCES BEACON_POLICY (ID)
);
