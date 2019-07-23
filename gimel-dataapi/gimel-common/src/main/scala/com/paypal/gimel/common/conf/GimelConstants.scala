/*
 * Copyright 2018 PayPal Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.paypal.gimel.common.conf

object GimelConstants {

  // COMMON CONSTANTS USED ACROSS ENTIRE GIMEL
  val USER: String = "USER"
  val USER_NAME = "username"
  val HOST_NAME = "HOSTNAME"
  val EXIT_CONDITION = "exitCondition"
  val RESOLVED_HIVE_TABLE: String = "resolvedHiveTable"
  val SPARK_APP_NAME: String = "spark.app.name"
  val APP_NAME: String = "appName"
  val GIMEL_PROPERTIES_FILE_KEY: String = "gimel.property.file"
  val GIMEL_PROPERTIES_FILE_NAME = "/pcatalog.properties"
  val DATASET_PROPS: String = "dataSetProperties"
  val APP_TAG: String = "appTag"
  val DATASET: String = "dataSet"
  val KEY_TAB: String = "gimel.keytab"
  val KEY_TAB_PRINCIPAL: String = "gimel.principal"
  val SECURITY_AUTH: String = "hadoop.security.authentication"
  val KERBEROS: String = "kerberos"
  val DEFAULT_SECURITY_AUTH: String = KERBEROS
  val CLUSTER: String = "gimel.cluster"
  val DEPLOYMENT_CLUSTERS: String = "gimel.dataset.deployment.clusters"
  val SPARK_EXECUTOR_MEMORY: String = "spark.executor.memory"
  val SPARK_DRIVER_MEMORY: String = "spark.driver.memory"
  val SPARK_EXECUTOR_INSTANCES: String = "spark.executor.instances"
  val SPARK_DRIVER_CORES: String = "spark.driver.cores"
  val SPARK_EXECUTOR_CORES: String = "spark.executor.cores"
  val STORAGE_TYPE: String = "gimel.storage.type"
  val LOG_LEVEL: String = "gimel.logging.level"
  val DEFAULT_LOG_LEVEL: String = "INFO"
  val DATA_CACHE_IS_ENABLED: String = "gimel.dataset.caching.enabled"
  val DATA_CACHE_IS_ENABLED_FOR_ALL: String = "gimel.dataset.caching.enabled.for.all"
  val MAX_RESULTS_TO_SHOW: String = "gimel.query.results.show.rows.threshold"
  val SHOW_ROWS_ENABLED: String = "gimel.query.results.show.rows.only"
  val FS_DEFAULT_NAME: String = "fs.default.name"
  val NONE_STRING: String = "NONE"
  val DEFAULT_STRING: String = "default"
  val NULL_STRING: String = "null"
  val COMMA: String = ","
  val SEMI_COLON: String = ";"
  val PCATALOG_STRING: String = "pcatalog"
  val UDC_STRING: String = "udc"
  val STORAGE_HANDLER: String = "storage_handler"
  val HTTP_SUCCESS_STATUS_CODE: Int = 200
  val HTTP_SUCCESS_RESPONSE_CODE: Int = 300
  val GET_SYSTEM_PROPERTIES: String = "gimel.get.system.properties"
  val CREATE_STATEMENT_IS_PROVIDED: String = "gimel.sql.create.statement.is.provided"
  val TABLE_FILEDS: String = "gimel.table.fields"
  val TABLE_SQL: String = "gimel.table.sql"
  val TBL_PROPERTIES: String = "TBLPROPERTIES"
  val SQL_SELECT_STRING: String = "SELECT"
  val DDL_CREATE_STRING: String = "CREATE"
  val DDL_DROP_STRING: String = "DROP"
  val DDL_TRUNCATE_STRING: String = "TRUNCATE"
  val DDL_FROM_STRING: String = "FROM"
  val DDL_DELETE_STRING: String = "DELETE"
  val DML_INSERT_STRING: String = "INSERT"
  val DML_UPDATE_STRING: String = "UPDATE"
  val HIVE_DDL_PARTITIONS_STR: String = "PARTITIONS"
  val HIVE_DDL_PARTITIONED_BY_CLAUSE: String = "PARTITIONED"
  val HIVE_MAX_TABLE_NAME_SIZE: Int = 128
  val GSQL_TMP_TABLE_RANDOM_GENERATOR_MIN = 20
  val GSQL_TMP_TABLE_RANDOM_GENERATOR_MAX = 100

  // HIVE
  val HIVE_DATABASE_NAME: String = "gimel.hive.db.name"
  val HIVE_TABLE_NAME: String = "gimel.hive.table.name"

  // ELASTIC SEARCH CONSTANTS USED ACROSS ENTIRE GIMEL
  val ES_POLLING_STORAGES: String = "gimel.es.polling"
  val ES_NODE: String = "es.nodes"
  val ES_PORT: String = "es.port"
  val ES_URL_WITH_PORT: String = "gimel.es.elasticadpcluster.url"

  // HBASE CONSTANTS USED ACROSS ENTIRE GIMEL
  val HBASE_NAMESPACE: String = "gimel.hbase.namespace.name"
  val STORAGE_TYPE_HBASE: String = "hbase"

  // TERA DATA RELATED CONSTANTS
  val STORAGE_TYPE_JDBC = "JDBC"

  // Ranger
  val STORAGE_TYPE_HIVE: String = "hive"

  // spark configs
  val SPARK_SPECULATION = "spark.speculation"

  // KAFKA CONSTANTS USED ACROSS ENTIRE GIMEL
  val ZOOKEEPER_LIST: String = "gimel.zookeeper.host"
  val ZOOKEEPER_STATE: String = "gimel.zk.state.prefix"
  val CONFLUENT_SCHEMA_URL: String = "gimel.kafka.confluent.schema.url"
  val KAFKA_CDH_SCHEMA: String = "gimel.kafka.cdh.coreSchema"
  val KAFKA_CONSUMER_CHECKPOINT_PATH: String = "gimel.kafka.consumer.checkpoint.root"
  val KAFKA_BROKER_LIST: String = "gimel.kafka.broker"
  val CDH_BROKER_LIST: String = "gimel.kafka.cdh.broker"
  val STREAM_MODE: String = "gimel.kafka.stream.mode"
  val DS_STREAM: String = "direct.stream"
  val BATCH_INTERVAL: String = "15"
  val UNKNOWN_STRING = "unknown"

  // hdfs constants
  val HDFS_IMPL = "fs.hdfs.impl"
  val FILE_IMPL = "fs.file.impl"
  val DEFAULT_FILE_SYSTEM = "fs.defaultFS"
  val LOCAL_FS = "org.apache.hadoop.fs.LocalFileSystem"
  val DISTRIBUTED_FS = "org.apache.hadoop.hdfs.DistributedFileSystem"
  val HADDOP_FILE_SYSTEM = "hdfs"
  val LOCAL_FILE_SYSTEM = "local"

  val hdfsStorageNameKey = "gimel.hdfs.storage.name"
  val hdfsNameNodeKey = "gimel.hdfs.nn"

  // common string constants
  val SUCCESS = "success"
  val FAILURE = "failure"
  val EMPTY_STRING = ""
  val READ_OPERATION = "read"
  val WRITE_OPERATION = "write"

  // GTS
  val GTS_USER_CONFIG = "gimel.gts.user"
  val GTS_IMPERSONATION_FLAG = "spark.gimel.gts.impersonation.enabled"
  val GTS_DDL_TIMEOUT_MILLISEC = "gimel.gts.ddl.session.creation.timeout.sec"

  // connection timeout for requests in seconds
  val CONNECTION_TIMEOUT = 180

  // CONDITIONAL FLAGS
  val FALSE = "false"
}


