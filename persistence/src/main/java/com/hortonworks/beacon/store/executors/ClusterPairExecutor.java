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

package com.hortonworks.beacon.store.executors;

import com.hortonworks.beacon.service.Services;
import com.hortonworks.beacon.store.BeaconStoreException;
import com.hortonworks.beacon.store.BeaconStoreService;
import com.hortonworks.beacon.store.bean.ClusterPairBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;

/**
 * Beacon store executor for ClusterPair.
 */
public class ClusterPairExecutor {

    private BeaconStoreService store = Services.get().getService(BeaconStoreService.SERVICE_NAME);

    /**
     * Enum for ClusterPair named queries.
     */
    public enum ClusterPairQuery {
        GET_CLUSTER_PAIR,
        UPDATE_CLUSTER_PAIR_STATUS,
        EXIST_CLUSTER_PAIR
    }

    private static final Logger LOG = LoggerFactory.getLogger(ClusterExecutor.class);

    private ClusterPairBean bean;

    public ClusterPairExecutor(ClusterPairBean bean) {
        this.bean = bean;
    }

    private void execute(EntityManager entityManager) throws BeaconStoreException {
        try {
            entityManager.getTransaction().begin();
            entityManager.persist(bean);
            entityManager.getTransaction().commit();
        } catch (Exception e) {
            LOG.error("Error while persisting cluster pair data. Cluster name: [{}], version; [{}]",
                    bean.getClusterName(), bean.getClusterVersion(), e);
            throw new BeaconStoreException(e.getMessage(), e);
        } finally {
            entityManager.close();
        }
    }

    private void execute() throws BeaconStoreException {
        EntityManager entityManager = store.getEntityManager();
        execute(entityManager);
    }

    private void submitClusterPair() throws BeaconStoreException {
        LOG.info("Storing cluster pair data. Source Cluster [{}, {}], Remote Cluster [{}, {}]", bean.getClusterName(),
                bean.getClusterVersion(), bean.getPairedClusterName(), bean.getPairedClusterVersion());
        execute();
        LOG.info("Cluster pair data stored. Source Cluster [{}, {}], Remote Cluster [{}, {}]", bean.getClusterName(),
                bean.getClusterVersion(), bean.getPairedClusterName(), bean.getPairedClusterVersion());
    }

    private Query getQuery(EntityManager entityManager, ClusterPairQuery namedQuery) {
        Query query = entityManager.createNamedQuery(namedQuery.name());
        switch (namedQuery) {
            case GET_CLUSTER_PAIR:
                query.setParameter("clusterName", bean.getClusterName());
                query.setParameter("clusterVersion", bean.getClusterVersion());
                query.setParameter("pairedClusterName", bean.getClusterName());
                query.setParameter("pairedClusterVersion", bean.getClusterVersion());
                break;
            case UPDATE_CLUSTER_PAIR_STATUS:
                query.setParameter("clusterName", bean.getClusterName());
                query.setParameter("clusterVersion", bean.getClusterVersion());
                query.setParameter("pairedClusterName", bean.getPairedClusterName());
                query.setParameter("pairedClusterVersion", bean.getPairedClusterVersion());
                query.setParameter("status", bean.getStatus());
                query.setParameter("lastModifiedTime", bean.getLastModifiedTime());
                break;
            case EXIST_CLUSTER_PAIR:
                query.setParameter("clusterName", bean.getClusterName());
                query.setParameter("clusterVersion", bean.getClusterVersion());
                query.setParameter("pairedClusterName", bean.getPairedClusterName());
                query.setParameter("pairedClusterVersion", bean.getPairedClusterVersion());
                break;
            default:
                throw new IllegalArgumentException("Invalid named query parameter passed: " + namedQuery.name());
        }
        return query;
    }


    public List<ClusterPairBean> getPairedCluster() {
        EntityManager entityManager = store.getEntityManager();
        Query query = getQuery(entityManager, ClusterPairQuery.GET_CLUSTER_PAIR);
        List<ClusterPairBean> resultList = query.getResultList();
        if (resultList == null || resultList.isEmpty()) {
            LOG.info("No pairing data found. Cluster name: [{}], version: [{}]", bean.getClusterName(),
                     bean.getClusterVersion());
            resultList = new ArrayList<>();
        }
        entityManager.close();
        return resultList;
    }

    public void updateStatus() throws BeaconStoreException {
        EntityManager entityManager = store.getEntityManager();
        try {
            Query query = getQuery(entityManager, ClusterPairQuery.UPDATE_CLUSTER_PAIR_STATUS);
            entityManager.getTransaction().begin();
            int executeUpdate = query.executeUpdate();
            entityManager.getTransaction().commit();
            LOG.info("Cluster [local: {}, remote: {}] pair status: [{}] updated for [{}] records.",
                    bean.getClusterName(), bean.getPairedClusterName(), executeUpdate, bean.getStatus());
        } catch (Exception e) {
            LOG.error("Error while updating the status: [{}]", bean.getStatus(), e);
            throw new BeaconStoreException(e.getMessage(), e);
        } finally {
            entityManager.close();
        }
    }

    public void pairCluster() throws BeaconStoreException {
        try {
            EntityManager entityManager = store.getEntityManager();
            Query query = getQuery(entityManager, ClusterPairQuery.EXIST_CLUSTER_PAIR);
            List<ClusterPairBean> resultList =  query.getResultList();
            if (resultList == null || resultList.isEmpty()) {
                submitClusterPair();
            } else if (resultList.size() == 1) {
                updateStatus();
            } else {
                LOG.warn("ClusterPair table is in inconsistent state. Number of records found: [{}]",
                        resultList.size());
                throw new IllegalStateException("ClusterPair table is in inconsistent state. "
                        + "Number of records found: " + resultList.size());
            }
        } catch (Exception e) {
            throw new BeaconStoreException(e.getMessage(), e);
        }
    }
}
