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

package com.hortonworks.beacon.api;

import com.hortonworks.beacon.client.entity.Entity;
import com.hortonworks.beacon.client.entity.Notification;
import com.hortonworks.beacon.client.entity.ReplicationPolicy;
import com.hortonworks.beacon.client.entity.Retry;
import com.hortonworks.beacon.client.resource.PolicyList;
import com.hortonworks.beacon.client.resource.PolicyList.PolicyElement;
import com.hortonworks.beacon.store.BeaconStoreException;
import com.hortonworks.beacon.store.JobStatus;
import com.hortonworks.beacon.store.bean.PolicyBean;
import com.hortonworks.beacon.store.bean.PolicyPropertiesBean;
import com.hortonworks.beacon.store.executors.PolicyExecutor;
import com.hortonworks.beacon.store.executors.PolicyInstanceExecutor;
import com.hortonworks.beacon.store.executors.PolicyListExecutor;
import com.hortonworks.beacon.util.DateUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

/**
 * Persistence helper for Beacon.
 */
public final class PersistenceHelper {

    private PersistenceHelper() {
    }

    static void persistPolicy(ReplicationPolicy policy) throws BeaconStoreException {
        PolicyBean bean = getPolicyBean(policy);
        PolicyExecutor executor = new PolicyExecutor(bean);
        executor.submitPolicy();
    }

    static ReplicationPolicy getPolicyForSchedule(String policyName) throws BeaconStoreException {
        PolicyExecutor executor = new PolicyExecutor(policyName);
        PolicyBean bean = executor.getSubmitted();
        return getReplicationPolicy(bean);
    }

    static void updatePolicyStatus(String name, String type, String status) {
        PolicyBean bean = new PolicyBean(name);
        bean.setType(type);
        bean.setStatus(status);
        bean.setLastModifiedTime(new Date());
        PolicyExecutor executor = new PolicyExecutor(bean);
        executor.executeUpdate(PolicyExecutor.PolicyQuery.UPDATE_STATUS);
    }

    static String getPolicyStatus(String name) throws BeaconStoreException {
        PolicyExecutor executor = new PolicyExecutor(name);
        PolicyBean bean = executor.getActivePolicy();
        return bean.getStatus();
    }

    static ReplicationPolicy getActivePolicy(String name) throws BeaconStoreException {
        PolicyExecutor executor = new PolicyExecutor(name);
        PolicyBean bean = executor.getActivePolicy();
        return getReplicationPolicy(bean);
    }

    static void markPolicyInstanceDeleted(String name, String type) {
        PolicyInstanceExecutor instanceExecutor = new PolicyInstanceExecutor();
        instanceExecutor.updatedDeletedInstances(name, type);
    }

    static int deletePolicy(String name) {
        PolicyBean bean = new PolicyBean(name);
        bean.setStatus(JobStatus.DELETED.name());
        bean.setDeletionTime(new Date());
        bean.setLastModifiedTime(new Date());
        PolicyExecutor executor = new PolicyExecutor(bean);
        return executor.executeUpdate(PolicyExecutor.PolicyQuery.DELETE_POLICY);
    }

    static PolicyList getFilteredPolicy(String fieldStr, String filterBy, String orderBy,
                                        String sortOrder, Integer offset, Integer resultsPerPage) {
        PolicyListExecutor executor = new PolicyListExecutor();
        List<PolicyBean> filteredPolicy = executor.getFilteredPolicy(filterBy, orderBy,
                sortOrder, offset, resultsPerPage);
        if (!filteredPolicy.isEmpty()) {
            HashSet<String> fields = new HashSet<>(Arrays.asList(fieldStr.toUpperCase().split(",")));
            PolicyElement[] policyElements = buildPolicyElements(fields, filteredPolicy);
            return new PolicyList(policyElements, policyElements.length);
        } else {
            return new PolicyList(new Entity[]{}, 0);
        }
    }

    private static PolicyList.PolicyElement[] buildPolicyElements(HashSet<String> fields, List<PolicyBean> entities) {
        PolicyElement[] elements = new PolicyElement[entities.size()];
        int elementIndex = 0;
        for (PolicyBean entity : entities) {
            elements[elementIndex++] = getPolicyElement(entity, fields);
        }
        return elements;
    }

    private static PolicyElement getPolicyElement(PolicyBean bean, HashSet<String> fields) {
        PolicyElement elem = new PolicyElement();
        elem.name = bean.getName();
        elem.type = bean.getType();
        if (fields.contains(PolicyList.PolicyFieldList.STATUS.name())) {
            elem.status = bean.getStatus();
        }
        if (fields.contains(PolicyList.PolicyFieldList.FREQUENCY.name())) {
            elem.frequency = bean.getFrequencyInSec();
        }
        if (fields.contains(PolicyList.PolicyFieldList.STARTTIME.name())) {
            elem.startTime = DateUtil.formatDate(bean.getStartTime());
        }
        if (fields.contains(PolicyList.PolicyFieldList.ENDTIME.name())) {
            elem.endTime = DateUtil.formatDate(bean.getEndTime());
        }
        if (fields.contains(PolicyList.PolicyFieldList.TAGS.name())) {
            String rawTags = bean.getTags();
            List<String> tags = new ArrayList<>();
            if (!StringUtils.isBlank(rawTags)) {
                for (String tag : rawTags.split(",")) {
                    tags.add(tag.trim());
                }
            }
            elem.tag = tags;
        }
        if (fields.contains(PolicyList.PolicyFieldList.CLUSTERS.name())) {
            elem.sourceCluster = new ArrayList<>(Arrays.asList(bean.getSourceCluster()));
            elem.targetCluster = new ArrayList<>(Arrays.asList(bean.getTargetCluster()));
        }
        return elem;
    }

    private static PolicyBean getPolicyBean(ReplicationPolicy policy) {
        PolicyBean bean = new PolicyBean();
        bean.setName(policy.getName());
        bean.setType(policy.getType());
        bean.setSourceCluster(policy.getSourceCluster());
        bean.setTargetCluster(policy.getTargetCluster());
        bean.setSourceDataset(policy.getSourceDataset());
        bean.setTargetDataset(policy.getTargetDataset());
        bean.setStartTime(policy.getStartTime());
        bean.setEndTime(policy.getEndTime());
        bean.setFrequencyInSec(policy.getFrequencyInSec());
        bean.setNotificationType(policy.getNotification().getType());
        bean.setNotificationTo(policy.getNotification().getTo());
        bean.setRetryCount(policy.getRetry().getAttempts());
        bean.setRetryDelay(policy.getRetry().getDelay());
        List<PolicyPropertiesBean> propertiesBeans = new ArrayList<>();
        Properties customProp = policy.getCustomProperties();
        for (String key : customProp.stringPropertyNames()) {
            PolicyPropertiesBean propertiesBean = new PolicyPropertiesBean();
            propertiesBean.setName(key);
            propertiesBean.setValue(customProp.getProperty(key));
            propertiesBeans.add(propertiesBean);
        }
        bean.setCustomProperties(propertiesBeans);
        bean.setTags(policy.getTags());
        return bean;
    }

    private static ReplicationPolicy getReplicationPolicy(PolicyBean bean) {
        ReplicationPolicy policy = new ReplicationPolicy();
        policy.setName(bean.getName());
        policy.setType(bean.getType());
        policy.setSourceCluster(bean.getSourceCluster());
        policy.setTargetCluster(bean.getTargetCluster());
        policy.setSourceDataset(bean.getSourceDataset());
        policy.setTargetDataset(bean.getTargetDataset());
        policy.setStartTime(bean.getStartTime());
        policy.setEndTime(bean.getEndTime());
        policy.setFrequencyInSec(bean.getFrequencyInSec());
        policy.setNotification(new Notification(bean.getNotificationType(), bean.getNotificationTo()));
        policy.setRetry(new Retry(bean.getRetryCount(), bean.getRetryDelay()));
        policy.setTags(bean.getTags());
        List<PolicyPropertiesBean> customProp = bean.getCustomProperties();
        Properties prop = new Properties();
        for (PolicyPropertiesBean propertiesBean : customProp) {
            prop.setProperty(propertiesBean.getName(), propertiesBean.getValue());
        }
        policy.setCustomProperties(prop);
        return policy;
    }
}