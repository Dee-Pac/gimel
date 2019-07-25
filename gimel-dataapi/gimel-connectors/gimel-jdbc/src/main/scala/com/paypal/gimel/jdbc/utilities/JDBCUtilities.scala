/*
 * Copyright 2017 PayPal Inc.
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

package com.paypal.gimel.jdbc.utilities

import java.sql.{BatchUpdateException, Connection, PreparedStatement, SQLException}

import scala.collection.immutable.Map
import scala.util.Try

import org.apache.commons.lang3.time.DurationFormatUtils
import org.apache.spark.TaskContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.sql.types.StructField

import com.paypal.gimel.common.catalog.DataSetProperties
import com.paypal.gimel.common.conf.{CatalogProviderConfigs, GimelConstants}
import com.paypal.gimel.jdbc.conf.{JdbcConfigs, JdbcConstants}
import com.paypal.gimel.jdbc.exception._
import com.paypal.gimel.jdbc.utilities.JdbcAuxiliaryUtilities.getJDBCSystem
import com.paypal.gimel.logger.Logger

/**
  * JDBC implementation internal to PCatalog
  * This implementation will be used to read from any JDBC data sources e.g. MYSQL, TERADATA
  */
object JDBCUtilities {

  val DEF_LOWER_BOUND : Long = 0
  val DEF_UPPER_BOUND : Long = 20

  def getOrCreateConnection(jdbcConnectionUtility: JDBCConnectionUtility,
                            conn: Option[Connection] = None): Connection = {
    if (conn.isEmpty || conn.get.isClosed) {
      jdbcConnectionUtility.getJdbcConnectionAndSetQueryBand()
    } else {
      conn.get
    }
  }

  def apply(sparkSession: SparkSession): JDBCUtilities = new JDBCUtilities(sparkSession)
}

class JDBCUtilities(sparkSession: SparkSession) extends Serializable {

  // val logger = Logger(this.getClass)
  val authUtilities: JDBCAuthUtilities = JDBCAuthUtilities(sparkSession)

  /**
    * This method reads the data from JDBC data source into dataFrame
    *
    * @param dataset Name of the PCatalog Data Set
    * @param dataSetProps
    *                dataSetprops is the way to set various additional parameters for read and write operations in DataSet class
    *                Example UseCase :
    *                We can specify the additional parameters for databases using JDBC.
    *                e.g. For "Teradata", exporting data, specify "teradata.read.type" parameter as "FASTEXPORT" (optional)
    * @return DataFrame
    */
  def read(dataset: String, dataSetProps: Map[String, Any]): DataFrame = {
    import JDBCUtilities._
    import PartitionUtils._

    val logger = Logger(this.getClass.getName)
    // logger.setLogLevel("CONSOLE")
    // sparkSession.conf.set("gimel.logging.level", "CONSOLE")

    val jdbcConnectionUtility: JDBCConnectionUtility = JDBCConnectionUtility(sparkSession, dataSetProps)

    val jdbcOptions: Map[String, String] = JdbcAuxiliaryUtilities.getJDBCOptions(dataSetProps)
    logger.info(s"Received JDBC options: $jdbcOptions and dataset options: $dataSetProps")

    val jdbcURL = jdbcOptions(JdbcConfigs.jdbcUrl)
    val dbtable = jdbcOptions(JdbcConfigs.jdbcDbTable)

    // get connection
    val conn: Connection = getOrCreateConnection(jdbcConnectionUtility)

    // get partitionColumns
    val partitionOptions = if (dataSetProps.contains(JdbcConfigs.jdbcPartitionColumns)) {
      jdbcOptions + (JdbcConfigs.jdbcPartitionColumns -> dataSetProps(JdbcConfigs.jdbcPartitionColumns).toString)
    } else jdbcOptions
    val partitionColumns = PartitionUtils.getPartitionColumns(partitionOptions, conn)

    // set number of partitions
    val userSpecifiedPartitions = dataSetProps.get("numPartitions")
    val numPartitions: Int = JdbcAuxiliaryUtilities.getNumPartitions(jdbcURL, userSpecifiedPartitions,
      JdbcConstants.readOperation)

    val fetchSize = dataSetProps.getOrElse("fetchSize", JdbcConstants.defaultReadFetchSize).toString.toInt

    val jdbcSystem = getJDBCSystem(jdbcURL)
    try {
      jdbcSystem match {
        case JdbcConstants.TERADATA =>
          val selectStmt = s"SELECT * FROM $dbtable"

          val jdbcRDD: ExtendedJdbcRDD[Array[Object]] = new ExtendedJdbcRDD(sparkSession.sparkContext,
            new DbConnection(jdbcConnectionUtility), selectStmt, fetchSize,
            PartitionInfoWrapper(jdbcSystem, partitionColumns, DEF_LOWER_BOUND, DEF_UPPER_BOUND, numPartitions))

          // getting table schema to build final dataframe
          val tableSchema = JdbcReadUtility.resolveTable(jdbcURL, dbtable,
            getOrCreateConnection(jdbcConnectionUtility, Some(conn)))
          val rowRDD: RDD[Row] = jdbcRDD.map(v => Row(v: _*))
          sparkSession.createDataFrame(rowRDD, tableSchema)
        case _ =>
          val (lowerBoundValue: Double, upperBoundValue: Double) = if (partitionColumns.nonEmpty) {
            logger.info(s"Partition column is set to ${partitionColumns.head}")
            JdbcAuxiliaryUtilities.getMinMax(partitionColumns.head, dbtable, conn)
          }
          else {
            (JDBCUtilities.DEF_LOWER_BOUND, JDBCUtilities.DEF_UPPER_BOUND)
          }
          // get lowerBound & upperBound
          val lowerBound = dataSetProps.getOrElse("lowerBound", lowerBoundValue).toString.toLong
          val upperBound = dataSetProps.getOrElse("upperBound", upperBoundValue).toString.toLong

          // default spark JDBC read
          JdbcAuxiliaryUtilities.sparkJdbcRead(sparkSession, jdbcURL, dbtable,
            partitionColumns.head, lowerBound, upperBound, numPartitions, fetchSize,
            jdbcConnectionUtility.getConnectionProperties())
      }
    }
    catch {
      case exec: SQLException =>
        var ex: SQLException = exec
        while (ex != null) {
          ex.printStackTrace()
          ex = ex.getNextException
        }
        throw exec
      case e: Throwable =>
        throw e
    }
    finally {
      // re-setting all configs for read
      JDBCCommons.resetReadConfigs(sparkSession)
    }
  }


  /**
    * This method writes the dataframe into given JDBC datasource
    *
    * @param dataset      Name of the PCatalog Data Set
    * @param dataFrame    The DataFrame to write into Target table
    * @param dataSetProps Any more options to provide
    * @return DataFrame
    */
  def write(dataset: String, dataFrame: DataFrame, dataSetProps: Map[String, Any]): DataFrame = {

    val logger = Logger(this.getClass.getName)
    val jdbcConnectionUtility: JDBCConnectionUtility = JDBCConnectionUtility(sparkSession, dataSetProps)
    val jdbcOptions: Map[String, String] = JdbcAuxiliaryUtilities.getJDBCOptions(dataSetProps)
    var jdbc_url = jdbcOptions(JdbcConfigs.jdbcUrl)

    val batchSize: Int = dataSetProps.getOrElse("batchSize", s"${JdbcConstants.defaultWriteBatchSize}").toString.toInt
    val dbtable = jdbcOptions(JdbcConfigs.jdbcDbTable)
    val teradataType: String = dataSetProps.getOrElse(JdbcConfigs.teradataWriteType, "").toString
    val insertStrategy: String = dataSetProps.getOrElse(JdbcConfigs.jdbcInsertStrategy, s"${JdbcConstants.defaultInsertStrategy}").toString

    // get number of user partitions
    val userSpecifiedPartitions = dataSetProps.get("numPartitions")

    // Get specific URL properties
    // get real user of JDBC
    val realUser: String = JDBCCommons.getJdbcUser(dataSetProps, sparkSession)

    // get password strategy for JDBC
    val jdbcPasswordStrategy = dataSetProps.getOrElse(JdbcConfigs.jdbcPasswordStrategy, JdbcConstants.jdbcDefaultPasswordStrategy).toString

    val partialArgHolder = if (insertStrategy.equalsIgnoreCase("update") || insertStrategy.equalsIgnoreCase("upsert")) {
      // get Set columns for update API
      val setColumns: List[String] = {
        val userSpecifiedSetColumns = dataSetProps.getOrElse(JdbcConfigs.jdbcUpdateSetColumns, null)
        if (userSpecifiedSetColumns == null) {
          dataFrame.columns.toList
        }
        else {
          userSpecifiedSetColumns.toString.split(",").toList
        }
      }

      // get WHERE columns for update API
      val whereColumns: List[String] = {
        val userSpecifiedWhereColumns = dataSetProps.getOrElse(JdbcConfigs.jdbcUpdateWhereColumns, null)
        if (userSpecifiedWhereColumns == null) {
          val primaryKeys = JdbcAuxiliaryUtilities.getPrimaryKeys(jdbc_url, dbtable, jdbcConnectionUtility.getJdbcConnectionAndSetQueryBand(), false)
          primaryKeys
        }
        else {
          userSpecifiedWhereColumns.toString.split(",").toList
        }
      }
      logger.info(s"Setting SET columns: $setColumns")
      logger.info(s"Setting WHERE columns: $whereColumns")
      JDBCArgsHolder(dataSetProps, dataFrame.columns, jdbc_url, realUser, jdbcPasswordStrategy, dbtable, _: Int,
        dataFrame.schema.length, setColumns, whereColumns)
    }
    else {
      JDBCArgsHolder(dataSetProps, dataFrame.columns, jdbc_url, realUser, jdbcPasswordStrategy,
        dbtable, _: Int, dataFrame.schema.length)
    }


    if (insertStrategy.equalsIgnoreCase("FullLoad")) {
      logger.info(s"Truncating the table :$dbtable as part of the FullLoad strategy")
      // truncate table
      JDBCConnectionUtility.withResources(jdbcConnectionUtility.getJdbcConnectionAndSetQueryBand()) {
        connection => JdbcAuxiliaryUtilities.truncateTable(jdbc_url, dbtable, connection, Some(logger))
      }
    }

    val (insetBatchSize, insertMethod) = (insertStrategy, teradataType) match {
      case ("update", _) =>
        (0, updateTable _)
      case ("upsert", _) =>
        (0, upsertTable _)
      case (_, _) =>
        (batchSize, insertParallelBatch _)
    }

    val sourceDataFrame = JdbcAuxiliaryUtilities.getPartitionedDataFrame(jdbc_url, dataFrame, userSpecifiedPartitions)

    val jdbcHolder = partialArgHolder(insetBatchSize)

    try {
      insertMethod(sourceDataFrame, jdbcConnectionUtility, jdbcHolder)
      dataFrame
    }
    catch {
      case exec: Throwable =>
        throw exec
    }
    finally {
      // re-setting all configs for write
      JDBCCommons.resetWriteConfigs(sparkSession)
    }
  }

  /**
    * This method returns the index of the column in array of columns
    *
    * @param dataFramecolumns Array of columns in dataframe
    * @param column           column for which index has to be found
    * @return Int
    */
  def getColumnIndex(dataFramecolumns: Array[String], column: String): Int = {
    dataFramecolumns.indexWhere(column.equalsIgnoreCase)
  }


  /**
    * It cooks the given preparedStatement with the given row.
    * It uses the columns whose index are given in `columnIndices` and sets parameters at indeices
    * given in `paramIndices`.
    *
    * @param st
    * @param row
    * @param columnIndices
    * @param paramIndices
    */
  private def cookStatementWithRow(st: PreparedStatement, row: Row,
                                   columnIndices: Seq[Int],
                                   paramIndices: Seq[Int]): PreparedStatement = {
    require(columnIndices.size == paramIndices.size,
      s"Different number of column and param indices were passed to $st.")
    columnIndices.zip(paramIndices).foreach { case (columnIndex, paramIndex) =>
      val columnValue = row.get(columnIndex)
      val targetSqlType = SparkToJavaConverter.getSQLType(row.schema(columnIndex).dataType)
      try {
        st.setObject(paramIndex, columnValue, targetSqlType)
      }
      catch {
        case e: Throwable =>
          e.printStackTrace()
          val msg =
            s"""
               |Error setting column value=$columnValue at column index=$columnIndex
               |with input dataFrame column dataType=${row.schema(columnIndex).dataType}
               |to target dataType=$targetSqlType
            """.stripMargin
          throw new SetColumnObjectException(msg, e)

      }
    }
    st
  }


  //  /**
  //    * This method returns the hive schema for a given hive table as StructType
  //    * if the table is catalogued, the schema will be coming from the meta store (pcatalog/HIVE/USER)
  //    * if the table is NOT catalogued, we will query the respective storage to get the schema
  //    *
  //    * @param dataSetProps Dataset Properties
  //    * @return StructType
  //    *         The hive schema for the given hive table
  //    */
  //  def getHiveSchema(dataSetProps: Map[String, Any]): StructType = {
  //    val actualProps: DataSetProperties = dataSetProps(GimelConstants.DATASET_PROPS).asInstanceOf[DataSetProperties]
  //    val hiveTable: String = actualProps.props.get(GimelConstants.RESOLVED_HIVE_TABLE).toString
  //    val catalogProvider = dataSetProps.get(CatalogProviderConfigs.CATALOG_PROVIDER).get.toString
  //    val schema = catalogProvider match {
  //      case com.paypal.gimel.common.conf.CatalogProviderConstants.PCATALOG_PROVIDER => {
  //
  //        if (actualProps.props.get(CatalogProviderConstants.DYNAMIC_DATASET).get == "true") {
  //          val columns = getTableSchema(dataSetProps)
  //          val schema: Array[StructField] = columns.map(x => {
  //            org.apache.spark.sql.types.StructField(x.columName, SparkToJavaConverter.getSparkTypeFromTeradataDataType(x.columnType.toUpperCase()), true)
  //          }).toArray
  //          new StructType(schema)
  //        }
  //        else {
  //          val schema: Array[StructField] = actualProps.fields.map(x => {
  //            org.apache.spark.sql.types.StructField(x.fieldName, SparkToJavaConverter.getSparkTypeFromTeradataDataType(x.fieldType.toUpperCase()), true)
  //          })
  //          new StructType(schema)
  //        }
  //      }
  //      case com.paypal.gimel.common.conf.CatalogProviderConstants.HIVE_PROVIDER => {
  //        val table: DataFrame = sparkSession.sql(s"SELECT * FROM $hiveTable")
  //        table.schema
  //      }
  //    }
  //    schema
  //  }

  /**
    * This method inserts into given table in given mode
    *
    * @param dataFrame
    * @param jdbcConnectionUtility
    * @param jdbcHolder
    */
  private def insertParallelBatch(dataFrame: DataFrame, jdbcConnectionUtility: JDBCConnectionUtility,
                                  jdbcHolder: JDBCArgsHolder) {
    import JDBCConnectionUtility.withResources
    val jdbcSystem = JdbcAuxiliaryUtilities.getJDBCSystem(jdbcHolder.jdbcURL)
    var ddl = ""
    jdbcSystem match {
      case JdbcConstants.TERADATA =>
        // create a JDBC connection to get DDL of target table
        withResources(jdbcConnectionUtility.getJdbcConnectionAndSetQueryBand()) {
          connection =>
            // get DDL of target table
            ddl = JdbcAuxiliaryUtilities.getDDL(jdbcHolder.jdbcURL, jdbcHolder.dbTable, connection)
        }
      case _ => // do nothing
    }


    // For each partition create a temp table to insert
    dataFrame.foreachPartition { batch =>

      // create logger inside the executor
      val logger = Logger(this.getClass.getName)

      // get the partition ID
      val partitionID = TaskContext.getPartitionId()

      // creating a new connection for every partition
      // NOTE: Here, singleton connection is replaced by java.sql.Connection creating a separate connection
      // for every partition.
      // Singleton connection connection needs to be correctly verified within multiple cores.
      val dbc = JdbcAuxiliaryUtilities.createConnectionWithPreConfigsSet(jdbcConnectionUtility, jdbcHolder)

      val partitionTableName = jdbcSystem match {
        case JdbcConstants.TERADATA =>
          val partitionTableName = JdbcAuxiliaryUtilities.getPartitionTableName(jdbcHolder.jdbcURL,
            jdbcHolder.dbTable, dbc, partitionID)

          // first drop the temp table, if exists
          JdbcAuxiliaryUtilities.dropTable(partitionTableName, dbc)

          try {
            // create JDBC temp table
            val tempTableDDL = ddl.replace(jdbcHolder.dbTable, partitionTableName)
            logger.info(s"Creating temp table: $partitionTableName with DDL = $tempTableDDL")
            // create a temp partition table
            JdbcAuxiliaryUtilities.executeQueryStatement(tempTableDDL, dbc)
          }
          catch {
            case ex: Throwable =>
              val msg = s"Failure creating temp partition table $partitionTableName"
              logger.error(msg + "\n" + s"${ex.toString}")
              ex.addSuppressed(new JDBCPartitionException(msg))
              throw ex
          }
          partitionTableName
        case _ =>
          jdbcHolder.dbTable
      }

      // close the connection, if batch is empty, so that we don't hold connection
      if (batch.isEmpty) {
        dbc.close()
      } else {
        logger.info(s"Inserting into $partitionTableName")
        val maxBatchSize = math.max(1, jdbcHolder.batchSize)
        withResources {
          JdbcAuxiliaryUtilities.createConnectionWithPreConfigsSet(jdbcConnectionUtility, jdbcHolder, Option(dbc))
        } { connection =>
          withResources {
            connection.prepareStatement(JdbcAuxiliaryUtilities.getInsertStatement(partitionTableName, jdbcHolder.cols))
          } {
            statement => {
              val start = System.currentTimeMillis
              var end = System.currentTimeMillis()
              connection.setAutoCommit(false)
              var batchCount = 0
              var rowCount = 0
              var startRowCount = rowCount
              batch.sliding(maxBatchSize, maxBatchSize).foreach {
                rows =>
                  startRowCount = rowCount
                  rows.foreach { row =>
                    cookStatementWithRow(statement, row, 0 until row.schema.length,
                      1 to row.schema.length)
                    statement.addBatch()
                    rowCount += 1
                  }
                  try {
                    val recordsUpserted: Array[Int] = statement.executeBatch()
                    end = System.currentTimeMillis
                    batchCount += 1
                    logger.info(s"Total time taken to insert [${Try(recordsUpserted.length).getOrElse(0)} records " +
                      s"with (start_row: $startRowCount & end_row: $rowCount and diff ${rowCount - startRowCount} " +
                      s"records)] for the batch[Batch no: $batchCount & Partition: $partitionTableName] : ${
                        DurationFormatUtils.formatDurationWords(
                          end - start, true, true
                        )
                      }")
                  }
                  catch {
                    case exec: Throwable =>
                      exec match {
                        case batchExc: BatchUpdateException =>
                          logger.error(s"Exception in inserting data into $partitionTableName " +
                            s"of Partition: $partitionID")
                          var ex: SQLException = batchExc
                          while (ex != null) {
                            logger.error(ex)
                            ex.printStackTrace()
                            ex = ex.getNextException
                          }
                          throw batchExc
                        case _ =>
                          logger.error(s"Exception in inserting data into $partitionTableName " +
                            s"of Partition: $partitionID")
                          throw exec
                      }
                  }
              }
              // commit per batch
              dbc.commit()
              logger.info(s"Successfully inserted into $partitionTableName of Partition: $partitionID " +
                s"with $rowCount rows and $batchCount batches," +
                s" overall time taken -> ${
                  DurationFormatUtils.formatDurationWords(
                    end - start, true, true
                  )
                } ")
              // Connection will be closed as part of JDBCConnectionUtility.withResources
            }
          }
        }
      }
    }

    // create logger inside the driver
    val driverLogger = Option(Logger(this.getClass.getName))

    jdbcSystem match {
      case JdbcConstants.TERADATA =>
        try {
          withResources(JdbcAuxiliaryUtilities.createConnectionWithPreConfigsSet(jdbcConnectionUtility, jdbcHolder)) {
            connection =>

              // Now union all the temp partition tables and insert into target table
              // and insert all partitions into target table
              JdbcAuxiliaryUtilities.insertPartitionsIntoTargetTable(jdbcHolder.dbTable,
                connection, dataFrame.toJavaRDD.getNumPartitions, driverLogger)

              // now drop all the temp tables created by executors
              JdbcAuxiliaryUtilities.dropAllPartitionTables(jdbcHolder.dbTable,
                connection, dataFrame.toJavaRDD.getNumPartitions, driverLogger)
          }
        } catch {
          case e: Throwable =>
            withResources {
              JdbcAuxiliaryUtilities.createConnectionWithPreConfigsSet(jdbcConnectionUtility, jdbcHolder)
            } { connection =>
              // now drop all the temp tables created by executors
              JdbcAuxiliaryUtilities.dropAllPartitionTables(jdbcHolder.dbTable,
                connection, dataFrame.toJavaRDD.getNumPartitions, driverLogger)
            }
            e.printStackTrace()
            throw e
        }
      case _ => // do nothing
    }
  }

  /**
    * This method is used to UPDATE into the table
    *
    * @param dataFrame  dataFrame to be loaded into table
    * @param jdbcHolder jdbc arguments required for writing into table
    */
  private def updateTable(dataFrame: DataFrame, jdbcConnectionUtility: JDBCConnectionUtility, jdbcHolder: JDBCArgsHolder) {

    dataFrame.foreachPartition { batch =>
      if (batch.nonEmpty) {

        // creating a new connection for every partition
        // NOTE: Here, singleton connection is replaced by java.sql.Connection creating a separate connection for every partition.
        // Singleton connection connection needs to be correctly verified within multiple cores.
        val dbc = jdbcConnectionUtility.getJdbcConnectionAndSetQueryBand()
        JdbcAuxiliaryUtilities.executePreConfigs(jdbcHolder.jdbcURL, jdbcHolder.dbTable, dbc)

        try {
          val updateStatement = JdbcAuxiliaryUtilities.getUpdateStatement(jdbcHolder.dbTable, jdbcHolder.setColumns, jdbcHolder.whereColumns)
          val st: PreparedStatement = dbc.prepareStatement(updateStatement)
          val maxBatchSize = math.max(1, jdbcHolder.batchSize)
          val setColumnIndices = jdbcHolder.setColumns.map(getColumnIndex(jdbcHolder.dataFrameColumns, _))
          val whereColumnIndices = jdbcHolder.whereColumns.map(getColumnIndex(jdbcHolder.dataFrameColumns, _))
          batch.sliding(maxBatchSize, maxBatchSize).foreach { rows =>
            rows.foreach { row =>
              cookStatementWithRow(st, row, setColumnIndices, 1 to setColumnIndices.length)
              // set the condition variables
              val whereColumnIndicesUpdated = (1 to whereColumnIndices.length).map(_ + setColumnIndices.length)
              cookStatementWithRow(st, row, whereColumnIndices, whereColumnIndicesUpdated)
              st.addBatch()
            }
            try {
              st.executeBatch()
            }
            catch {
              case exec: Throwable => {
                exec match {
                  case batchExc: BatchUpdateException =>
                    var ex: SQLException = batchExc
                    while (ex != null) {
                      ex.printStackTrace()
                      ex = ex.getNextException
                    }
                    throw batchExc
                  case _ =>
                    throw exec
                }
              }
            }
          }
        }
        catch {
          case exec: Throwable =>
            exec.printStackTrace()
            throw exec
        }
        finally {
          // check if any connection open inside executor and explicitly close it
          if (!dbc.isClosed && dbc != null) {
            dbc.close()
          }
        }

      }
    }
  }

  /**
    * This method is used to UPSERT into the table
    *
    * @param dataFrame
    * @param jDBCConnectionUtility
    * @param jdbcHolder
    */
  private def upsertTable(dataFrame: DataFrame, jDBCConnectionUtility: JDBCConnectionUtility, jdbcHolder: JDBCArgsHolder) {
    dataFrame.foreachPartition { batch =>
      if (batch.nonEmpty) {
        val dbc = jDBCConnectionUtility.getJdbcConnectionAndSetQueryBand()
        JdbcAuxiliaryUtilities.executePreConfigs(jdbcHolder.jdbcURL, jdbcHolder.dbTable, dbc)
        try {
          val numCols: Int = jdbcHolder.cols
          val updateStatement = JdbcAuxiliaryUtilities.getUpdateStatement(jdbcHolder.dbTable, jdbcHolder.setColumns, jdbcHolder.whereColumns)
          val st: PreparedStatement = dbc.prepareStatement(updateStatement)
          val setColumnIndices = jdbcHolder.setColumns.map(getColumnIndex(jdbcHolder.dataFrameColumns, _))
          val whereColumnIndices = jdbcHolder.whereColumns.map(getColumnIndex(jdbcHolder.dataFrameColumns, _))
          batch.foreach { row =>
            cookStatementWithRow(st, row, setColumnIndices, 1 to setColumnIndices.length)
            // set the condition variables
            val whereColumnIndicesUpdated = (1 to whereColumnIndices.length).map(_ + setColumnIndices.length)
            cookStatementWithRow(st, row, whereColumnIndices, whereColumnIndicesUpdated)
            // update table
            val updateResult: Int = st.executeUpdate()
            // if no update, then insert into table
            if (updateResult == 0) {
              val st = dbc.prepareStatement(JdbcAuxiliaryUtilities.getInsertStatement(jdbcHolder.dbTable, numCols))
              cookStatementWithRow(st, row, 0 until row.schema.length, 1 to row.schema.length)
              // insert into table
              try {
                st.executeUpdate()
              }
              catch {
                case exec: Throwable => {
                  exec match {
                    case batchExc: BatchUpdateException =>
                      var ex: SQLException = batchExc
                      while (ex != null) {
                        ex.printStackTrace()
                        ex = ex.getNextException
                      }
                      throw batchExc
                    case _ =>
                      throw exec
                  }
                }
              }
            }
          }
        }
        catch {
          case exec: Throwable =>
            exec.printStackTrace()
            throw exec
        }
        finally {
          // check if any connection open inside executor and explicitly close it
          if (!dbc.isClosed && dbc != null) {
            dbc.close()
          }
        }
      }
    }
  }


  /** This method creates the table in teradata database
    *
    * @param dataset      dataset name
    * @param dataSetProps dataset properties
    * @return Boolean
    */
  def create(dataset: String, dataSetProps: Map[String, Any]): Boolean = {
    val logger = Logger(this.getClass.getName)
    val jdbcConnectionUtility: JDBCConnectionUtility = JDBCConnectionUtility(sparkSession, dataSetProps)
    val jdbcOptions: Map[String, String] = JdbcAuxiliaryUtilities.getJDBCOptions(dataSetProps)
    val actualProps: DataSetProperties = dataSetProps(GimelConstants.DATASET_PROPS).asInstanceOf[DataSetProperties]
    val catalogProvider = dataSetProps.get(CatalogProviderConfigs.CATALOG_PROVIDER).get.toString
    val sql = dataSetProps.get(GimelConstants.TABLE_SQL).get.toString
    logger.info("sql statement " + sql)
    val createTableStatement = catalogProvider match {
      case com.paypal.gimel.common.conf.CatalogProviderConstants.PCATALOG_PROVIDER | com.paypal.gimel.common.conf.CatalogProviderConstants.UDC_PROVIDER => {
        dataSetProps.get(GimelConstants.CREATE_STATEMENT_IS_PROVIDED).get match {
          case "true" =>
            // Since create statement is provided, we do not need to prepare the statement instead we need
            // to pass the sql as is to the teradata engine.
            sql
          case _ =>
            // As the create statement is not provided we need to infer schema from the dataframe.
            // In GimelQueryProcesser we already infer schema and pass the columns with their data types
            // we need construct the create statement
            JDBCUtilityFunctions.prepareCreateStatement(sql, jdbcOptions(JdbcConfigs.jdbcDbTable), dataSetProps)
        }
      }
      case GimelConstants.USER => {
        val colList: Array[String] = actualProps.fields.map(x => (x.fieldName + " " + (x.fieldType) + ","))
        val conCatColumns = colList.mkString("")
        s"""CREATE TABLE ${jdbcOptions(JdbcConfigs.jdbcDbTable)} (${conCatColumns.dropRight(1)} ) """
      }
    }
    val con = jdbcConnectionUtility.getJdbcConnectionAndSetQueryBand()
    val columnStatement: PreparedStatement = con.prepareStatement(createTableStatement)
    columnStatement.execute()
  }

  /**
    * prepareCreateStatement - From the column details passed in datasetproperties from GimelQueryProcesser,
    * create statement is constructed. If user passes primary index , set or multi set table, those will be added in the create statement
    *
    * @param dbtable      - Table Name
    * @param dataSetProps - Data set properties
    * @return - the created prepared statement for creating the table
    */
  def prepareCreateStatement(sql: String, dbtable: String, dataSetProps: Map[String, Any]): String = {
    // Here we remove the SELECT portion and have only the CREATE portion of the DDL supplied so that we can use that to create the table
    val sqlParts = sql.split(" ")
    val lenPartKeys = sqlParts.length
    val index = sqlParts.indexWhere(_.toUpperCase() == "SELECT")
    val createOnly: String = sqlParts.slice(0, index - 1).mkString(" ")

    // Here we remove the PCATALOG prefix => we replace pcatalog.storagetype.storagesystem.DB.Table with DB.Table
    val createParts = createOnly.split(" ")
    val pcatSQL = createParts.map(element => {
      if (element.toLowerCase().contains(GimelConstants.PCATALOG_STRING) || element.toLowerCase().contains(GimelConstants.UDC_STRING) ) {
        // we replace pcatalog.storagetype.storagesystem.DB.Table with DB.Table
        element.split('.').tail.mkString(".").split('.').tail.mkString(".").split('.').tail.mkString(".")
      }
      else {
        element
      }
    }
    ).mkString(" ")

    val sparkSchema = dataSetProps(GimelConstants.TABLE_FILEDS).asInstanceOf[Array[StructField]]
    // From the dataframe schema, translate them into Teradata data types
    val gimelSchema: Array[com.paypal.gimel.common.catalog.Field] = sparkSchema.map(x => {
      com.paypal.gimel.common.catalog.Field(x.name, SparkToJavaConverter.getTeradataDataType(x.dataType), x.nullable)
    })
    val colList: Array[String] = gimelSchema.map(x => (x.fieldName + " " + (x.fieldType) + ","))
    val conCatColumns = colList.mkString("").dropRight(1)
    val colQulifier = s"""(${conCatColumns})"""

    // Here we inject back the columns with data types back in the SQL statemnt
    val newSqlParts = pcatSQL.split(" ")
    val PCATindex = newSqlParts.indexWhere(_.toUpperCase().contains("TABLE"))
    val catPrefix = newSqlParts.slice(0, PCATindex + 2).mkString(" ")
    val catSuffix = newSqlParts.slice(PCATindex + 2, newSqlParts.length).mkString(" ")
    val fullStatement = s"""${catPrefix} ${colQulifier} ${catSuffix}"""
    fullStatement
  }

  /** This method drops the table from teradata database
    *
    * @param dataset      dataset name
    * @param dataSetProps dataset properties
    * @return Boolean
    */
  def drop(dataset: String, dataSetProps: Map[String, Any]): Boolean = {
    val jdbcConnectionUtility: JDBCConnectionUtility = JDBCConnectionUtility(sparkSession, dataSetProps)
    val jdbcOptions: Map[String, String] = JdbcAuxiliaryUtilities.getJDBCOptions(dataSetProps)
    val actualProps: DataSetProperties = dataSetProps(GimelConstants.DATASET_PROPS).asInstanceOf[DataSetProperties]
    val dropTableStatement = s"""DROP TABLE ${jdbcOptions(JdbcConfigs.jdbcDbTable)}"""
    val con = jdbcConnectionUtility.getJdbcConnectionAndSetQueryBand()
    val columnStatement: PreparedStatement = con.prepareStatement(dropTableStatement)
    columnStatement.execute()
  }

  /** This method purges data in the table
    *
    * @param dataset      dataset name
    * @param dataSetProps dataset properties
    * @return Boolean
    */
  def truncate(dataset: String, dataSetProps: Map[String, Any]): Boolean = {
    val jdbcConnectionUtility: JDBCConnectionUtility = JDBCConnectionUtility(sparkSession, dataSetProps)
    val jdbcOptions: Map[String, String] = JdbcAuxiliaryUtilities.getJDBCOptions(dataSetProps)
    val actualProps: DataSetProperties = dataSetProps(GimelConstants.DATASET_PROPS).asInstanceOf[DataSetProperties]
    val dropTableStatement = s"""DELETE FROM ${jdbcOptions(JdbcConfigs.jdbcDbTable)}"""
    val con = jdbcConnectionUtility.getJdbcConnectionAndSetQueryBand()
    val columnStatement: PreparedStatement = con.prepareStatement(dropTableStatement)
    columnStatement.execute()
  }

}

/**
  * case class to define column
  *
  * @param columName
  * @param columnType
  */
case class Column(columName: String, columnType: String)
