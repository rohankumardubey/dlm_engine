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

package com.hortonworks.beacon.store.executors;

import com.hortonworks.beacon.store.bean.InstanceJobBean;
import com.hortonworks.beacon.util.StringFormat;

import javax.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;

/**
 * Beacon store executor for instance jobs.
 */
public class InstanceJobExecutor extends BaseExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceJobExecutor.class);
    private InstanceJobBean bean;

    /**
     * Enums for InstanceJob named queries.
     */
    public enum InstanceJobQuery {
        GET_INSTANCE_JOB,
        UPDATE_STATUS_START,
        INSTANCE_JOB_UPDATE_STATUS,
        INSTANCE_JOB_REMAIN_RETIRE,
        UPDATE_JOB_COMPLETE,
        UPDATE_JOB_FAIL_RETIRE,
        UPDATE_JOB_RETRY_COUNT,
        DELETE_INSTANCE_JOB,
        DELETE_RETIRED_JOBS
    }

    public InstanceJobExecutor(InstanceJobBean bean) {
        this.bean = bean;
    }

    public void execute() {
        getEntityManager().persist(bean);
    }

    public void executeUpdate(InstanceJobQuery namedQuery) {
        Query query = getQuery(namedQuery);
        int update = query.executeUpdate();
        LOG.debug("Records updated for InstanceJobBean table namedQuery [{}], count [{}]", namedQuery, update);
    }

    private Query getQuery(InstanceJobQuery namedQuery) {
        Query query = getEntityManager().createNamedQuery(namedQuery.name());
        switch (namedQuery) {
            case UPDATE_STATUS_START:
                query.setParameter("status", bean.getStatus());
                query.setParameter("startTime", bean.getStartTime());
                query.setParameter("instanceId", bean.getInstanceId());
                query.setParameter("offset", bean.getOffset());
                break;
            case INSTANCE_JOB_UPDATE_STATUS:
                query.setParameter("status", bean.getStatus());
                query.setParameter("instanceId", bean.getInstanceId());
                break;
            case INSTANCE_JOB_REMAIN_RETIRE:
                query.setParameter("status", bean.getStatus());
                query.setParameter("instanceId", bean.getInstanceId());
                query.setParameter("retirementTime", bean.getRetirementTime());
                break;
            case UPDATE_JOB_COMPLETE:
                query.setParameter("status", bean.getStatus());
                query.setParameter("message", bean.getMessage());
                query.setParameter("endTime", bean.getEndTime());
                query.setParameter("contextData", bean.getContextData());
                query.setParameter("instanceId", bean.getInstanceId());
                query.setParameter("offset", bean.getOffset());
                break;
            case UPDATE_JOB_FAIL_RETIRE:
                query.setParameter("status", bean.getStatus());
                query.setParameter("message", bean.getMessage());
                query.setParameter("endTime", bean.getEndTime());
                query.setParameter("contextData", bean.getContextData());
                query.setParameter("retirementTime", bean.getRetirementTime());
                query.setParameter("instanceId", bean.getInstanceId());
                query.setParameter("offset", bean.getOffset());
                break;
            case UPDATE_JOB_RETRY_COUNT:
                query.setParameter("instanceId", bean.getInstanceId());
                query.setParameter("offset", bean.getOffset());
                query.setParameter("runCount", bean.getRunCount());
                break;
            case GET_INSTANCE_JOB:
                query.setParameter("instanceId", bean.getInstanceId());
                query.setParameter("offset", bean.getOffset());
                break;
            case DELETE_INSTANCE_JOB:
                query.setParameter("instanceId", bean.getInstanceId());
                query.setParameter("retirementTime", bean.getRetirementTime());
                break;
            case DELETE_RETIRED_JOBS:
                query.setParameter("retirementTime", new Timestamp(bean.getRetirementTime().getTime()));
                break;
            default:
                throw new IllegalArgumentException(
                    StringFormat.format("Invalid named query parameter passed: {}", namedQuery.name()));
        }
        return query;
    }

    public InstanceJobBean getInstanceJob(InstanceJobQuery namedQuery) {
        Query query = getQuery(namedQuery);
        return (InstanceJobBean) query.getSingleResult();
    }
}
