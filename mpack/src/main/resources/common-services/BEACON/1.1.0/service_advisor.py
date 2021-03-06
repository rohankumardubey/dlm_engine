#!/usr/bin/env ambari-python-wrap

"""
  HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES

  (c) 2016-2018 Hortonworks, Inc. All rights reserved.

  This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
  Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
  to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
  properly licensed third party, you do not have any rights to this code.

  If this code is provided to you under the terms of the AGPLv3:
  (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
  (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
    LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
  (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
    FROM OR RELATED TO THE CODE; AND
  (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
    DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
    DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
    OR LOSS OR CORRUPTION OF DATA.
"""
import os
import fnmatch
import imp
import socket
import sys
import traceback
import re

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
STACKS_DIR = os.path.join(SCRIPT_DIR, '../../../../../stacks/')
PARENT_FILE = os.path.join(STACKS_DIR, 'service_advisor.py')

try:
  with open(PARENT_FILE, 'rb') as fp:
    service_advisor = imp.load_module('service_advisor', fp, PARENT_FILE, ('.py', 'rb', imp.PY_SOURCE))
except Exception as e:
  traceback.print_exc()
  print "Failed to load parent"

class BEACON110ServiceAdvisor(service_advisor.ServiceAdvisor):

  def __init__(self, *args, **kwargs):
    self.as_super = super(BEACON110ServiceAdvisor, self)
    self.as_super.__init__(*args, **kwargs)


  def getServiceComponentLayoutValidations(self, services, hosts):

    componentsListList = [service["components"] for service in services["services"]]
    componentsList = [item["StackServiceComponents"] for sublist in componentsListList for item in sublist]

    items = []

    # Metron Must Co-locate with KAFKA_BROKER and STORM_SUPERVISOR
    return items

  def getServiceConfigurationRecommendations(self, configurations, clusterData, services, hosts):
    servicesList = [service['StackServices']['service_name'] for service in services['services']]
    putBeaconSecurityProperty = self.putProperty(configurations, 'beacon-security-site', services)
    putbeaconEnvProperty = self.putProperty(configurations, 'beacon-env', services)

    hive_site = self.getServicesSiteProperties(services, "hive-site")
    putHiveSiteProperty = self.putProperty(configurations, "hive-site", services)

    beacon_server_hosts = self.getComponentHostNames(services, 'BEACON', 'BEACON_SERVER')
    beacon_server_host = None
    if len(beacon_server_hosts) > 0:
      beacon_server_host = beacon_server_hosts[0]

    if 'forced-configurations' not in services:
      services["forced-configurations"] = []

    if 'beacon-env' in services['configurations'] and ('beacon_database' in services['configurations']['beacon-env']['properties']) \
      and ('beacon_store_driver' in services['configurations']['beacon-env']['properties']):
      beacon_database_type = services['configurations']['beacon-env']['properties']['beacon_database']
      putbeaconEnvProperty('beacon_store_driver', self.getBeaconDBDriver(beacon_database_type))

      if ('beacon_store_db_name' in services['configurations']['beacon-env']['properties']) \
        and ('beacon_store_url' in services['configurations']['beacon-env']['properties']):
        beacon_db_connection_url = services['configurations']['beacon-env']['properties']['beacon_store_url']
        beacon_store_db_name = services['configurations']['beacon-env']['properties']['beacon_store_db_name']
        protocol = self.getDBProtocol(beacon_database_type)
        old_schema_name = self.getOldPropertyValue(services, 'beacon-env', 'beacon_store_db_name')
        old_db_type = self.getOldPropertyValue(services, 'beacon-env', 'beacon_database')
        # under these if constructions we are checking if beacon server hostname available,
        # if it's default db connection url with "localhost" or if schema name was changed or if db type was changed (only for db type change from default mysql to existing mysql)
        # or if protocol according to current db type differs with protocol in db connection url(other db types changes)
        if beacon_server_host is not None:
          if (beacon_db_connection_url and "//localhost" in beacon_db_connection_url) \
            or old_schema_name or old_db_type or (protocol and beacon_db_connection_url \
            and not beacon_db_connection_url.startswith(protocol)):
            db_connection = self.getBeaconDBConnectionString(beacon_database_type).format(beacon_server_host, beacon_store_db_name)
            putbeaconEnvProperty('beacon_store_url', db_connection)

    knox_host = 'localhost'
    knox_port = '8443'
    if 'KNOX' in servicesList:
      knox_hosts = self.getComponentHostNames(services, "KNOX", "KNOX_GATEWAY")
      if len(knox_hosts) > 0:
        knox_hosts.sort()
        knox_host = knox_hosts[0]
      if 'gateway-site' in services['configurations'] and 'gateway.port' in services['configurations']['gateway-site']['properties']:
        knox_port = services['configurations']['gateway-site']['properties']['gateway.port']
      putBeaconSecurityProperty('beacon.sso.knox.providerurl', 'https://{0}:{1}/gateway/knoxsso/api/v1/websso'.format(knox_host, knox_port))

    beacon_user = 'beacon'
    if 'beacon-env' in services['configurations'] and 'beacon_user' in services['configurations']['beacon-env']['properties']:
      beacon_user = services['configurations']['beacon-env']['properties']['beacon_user']

    if 'HDFS' in servicesList and 'core-site' in services['configurations']:
      putHdfsCoreSiteProperty = self.putProperty(configurations, 'core-site', services)
      putHdfsCoreSitePropertyAttribute = self.putPropertyAttribute(configurations, 'core-site')
      beacon_old_user = self.getOldPropertyValue(services, 'beacon-env', 'beacon_user')

      putHdfsCoreSiteProperty('hadoop.proxyuser.{0}.hosts'.format(beacon_user), '*')
      putHdfsCoreSiteProperty('hadoop.proxyuser.{0}.groups'.format(beacon_user), '*')
      putHdfsCoreSiteProperty('hadoop.proxyuser.{0}.users'.format(beacon_user), '*')

      if beacon_old_user is not None and beacon_user != beacon_old_user:
        putHdfsCoreSitePropertyAttribute('hadoop.proxyuser.{0}.hosts'.format(beacon_old_user), 'delete', 'true')
        services['forced-configurations'].append({'type' : 'core-site', 'name' : 'hadoop.proxyuser.{0}.hosts'.format(beacon_old_user)})
        services['forced-configurations'].append({'type' : 'core-site', 'name' : 'hadoop.proxyuser.{0}.hosts'.format(beacon_user)})

        putHdfsCoreSitePropertyAttribute('hadoop.proxyuser.{0}.groups'.format(beacon_old_user), 'delete', 'true')
        services['forced-configurations'].append({'type' : 'core-site', 'name' : 'hadoop.proxyuser.{0}.groups'.format(beacon_old_user)})
        services['forced-configurations'].append({'type' : 'core-site', 'name' : 'hadoop.proxyuser.{0}.groups'.format(beacon_user)})

        putHdfsCoreSitePropertyAttribute('hadoop.proxyuser.{0}.users'.format(beacon_old_user), 'delete', 'true')
        services['forced-configurations'].append({'type' : 'core-site', 'name' : 'hadoop.proxyuser.{0}.users'.format(beacon_old_user)})
        services['forced-configurations'].append({'type' : 'core-site', 'name' : 'hadoop.proxyuser.{0}.users'.format(beacon_user)})

    if 'HIVE' in servicesList and 'beacon-env' in services['configurations'] \
            and 'set_hive_configs' in services['configurations']['beacon-env']['properties'] \
            and services['configurations']['beacon-env']['properties']['set_hive_configs'] == 'true' \
            and hive_site and not self.is_cloud_warehouse(hive_site['hive.metastore.warehouse.dir']):
      putHiveSiteProperty('hive.metastore.dml.events', 'true')
      putHiveSiteProperty('hive.repl.cm.enabled', 'true')
      services['forced-configurations'].append({'type' : 'hive-site', 'name' : 'hive.metastore.dml.events'})
      services['forced-configurations'].append({'type' : 'hive-site', 'name' : 'hive.repl.cm.enabled'})
      # split existing values, append new one and merge back
      listeners_delimiter = ","
      listeners_values = set(['org.apache.hive.hcatalog.listener.DbNotificationListener'])
      if hive_site and 'hive.metastore.transactional.event.listeners' in hive_site and hive_site['hive.metastore.transactional.event.listeners'] is not None:
        listeners_values.update(
          [item.strip() for item in hive_site['hive.metastore.transactional.event.listeners'].split(listeners_delimiter)
           if item.strip() != ""]
        )
      listeners_property_value = listeners_delimiter.join(listeners_values)
      putHiveSiteProperty('hive.metastore.transactional.event.listeners', listeners_property_value)
      services['forced-configurations'].append({'type' : 'hive-site', 'name' : 'hive.metastore.transactional.event.listeners'})

      if hive_site:
        hive_home_folder = os.path.dirname(hive_site['hive.metastore.warehouse.dir'])
        putHiveSiteProperty('hive.repl.cmrootdir', os.path.join(hive_home_folder, 'cmroot'))
        putHiveSiteProperty('hive.repl.rootdir', os.path.join(hive_home_folder, 'repl'))

  def getOldPropertyValue(self, services, configType, propertyName):
    if services:
      if 'changed-configurations' in services.keys():
        changedConfigs = services["changed-configurations"]
        for changedConfig in changedConfigs:
          if changedConfig["type"] == configType and changedConfig["name"]== propertyName and "old_value" in changedConfig:
            return changedConfig["old_value"]
    return None

  def getBeaconDBDriver(self, databaseType):
    driverDict = {
      'NEW MYSQL DATABASE': 'com.mysql.jdbc.Driver',
      'NEW DERBY DATABASE': 'org.apache.derby.jdbc.EmbeddedDriver',
      'EXISTING MYSQL DATABASE': 'com.mysql.jdbc.Driver',
      'EXISTING MYSQL / MARIADB DATABASE': 'com.mysql.jdbc.Driver',
      'EXISTING POSTGRESQL DATABASE': 'org.postgresql.Driver',
      'EXISTING ORACLE DATABASE': 'oracle.jdbc.driver.OracleDriver',
      'EXISTING SQL ANYWHERE DATABASE': 'sap.jdbc4.sqlanywhere.IDriver'
    }
    return driverDict.get(databaseType.upper())

  def getBeaconDBConnectionString(self, databaseType):
    driverDict = {
      'NEW DERBY DATABASE': 'jdbc:derby:${{beacon.data.dir}}/${{beacon.store.db.name}}-db;create=true',
      'EXISTING MYSQL DATABASE': 'jdbc:mysql://{0}/{1}',
      'EXISTING MYSQL / MARIADB DATABASE': 'jdbc:mysql://{0}/{1}',
      'EXISTING POSTGRESQL DATABASE': 'jdbc:postgresql://{0}/{1}',
      'EXISTING ORACLE DATABASE': 'jdbc:oracle:thin:@//{0}:1521/{1}'
    }
    return driverDict.get(databaseType.upper())

  def getDBProtocol(self, databaseType):
    first_parts_of_connection_string = {
      'NEW MYSQL DATABASE': 'jdbc:mysql',
      'NEW DERBY DATABASE': 'jdbc:derby',
      'EXISTING MYSQL DATABASE': 'jdbc:mysql',
      'EXISTING MYSQL / MARIADB DATABASE': 'jdbc:mysql',
      'EXISTING POSTGRESQL DATABASE': 'jdbc:postgresql',
      'EXISTING ORACLE DATABASE': 'jdbc:oracle',
      'EXISTING SQL ANYWHERE DATABASE': 'jdbc:sqlanywhere'
    }
    return first_parts_of_connection_string.get(databaseType.upper())

  def is_cloud_warehouse(self, hive_warehouse_dir):
    pat = re.compile(r's3.?://')
    m = pat.match(hive_warehouse_dir)
    return m is not None

