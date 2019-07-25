/*
 * Copyright 2019 PayPal Inc.
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

import java.sql.{Connection, PreparedStatement, ResultSet}
import java.util.Properties

import scala.collection.immutable.Map

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.types.{StructField, StructType}

import com.paypal.gimel.common.catalog.{DataSetProperties, Field}
import com.paypal.gimel.common.conf.GimelConstants
import com.paypal.gimel.jdbc.conf.{JdbcConfigs, JdbcConstants}
import com.paypal.gimel.logger.Logger

object JdbcAuxiliaryUtilities {

  /**
    *
    * @param resultSet
    * @param columnIndex
    * @return
    */
  def getStringColumn(resultSet: ResultSet, columnIndex: Int): Option[String] = {
    if (resultSet.next()) {
      Some(resultSet.getString(columnIndex))
    } else {
      None
    }
  }

  /**
    * This method gets the Primary key column of Teradata which is any numeric field
    *
    * @param databaseName
    * @param tableName
    * @param dbConn
    * @param NUMERIC_ONLY_FLAG
    * @return
    */
  def getPrimaryKeysForTeradata(databaseName: String, tableName: String, dbConn: Connection, NUMERIC_ONLY_FLAG: Boolean): List[String] = {
    val primaryKeyStatement: String =
      s"""
         |SELECT ColumnName FROM
         |dbc.columnsv
         |WHERE
         |DatabaseName = '${databaseName}'
         |AND TableName = '${tableName}'
         |AND ColumnName IN
         |(SELECT ColumnName FROM dbc.indicesv
         |WHERE DatabaseName = '${databaseName}'
         |AND TableName = '${tableName}'
         |AND IndexType = 'P')
         |
       """.stripMargin

    val updatedPrimaryKeyStatement = NUMERIC_ONLY_FLAG match {

      case true =>
        s"""
           | ${primaryKeyStatement}
           | AND ColumnType in ('I','I1','I2','I8', 'F', 'D', 'BF', 'BV')
           |
           """.stripMargin

      case _ =>
        primaryKeyStatement
    }

    val st: PreparedStatement = dbConn.prepareStatement(updatedPrimaryKeyStatement)
    val resultSet: ResultSet = st.executeQuery()

    Iterator
      .continually(getStringColumn(resultSet, columnIndex = 1))
      .takeWhile(_.nonEmpty)
      .flatten
      .toList
  }

  /**
    *
    * @param databaseName
    * @param tableName
    * @param dbConn
    * @param NUMERIC_ONLY_FLAG
    * @return
    */
  def getPrimaryKeysForMysql(databaseName: String, tableName: String, dbConn: Connection, NUMERIC_ONLY_FLAG: Boolean): List[String] = {
    val primaryKeyStatement: String =
      s"""
         |SELECT COLUMN_NAME as ColumnName
         |FROM information_schema.COLUMNS
         |WHERE (TABLE_SCHEMA = '${databaseName}')
         |AND (TABLE_NAME = '${tableName}')
         |AND (COLUMN_KEY = 'PRI')
         |
       """.stripMargin

    val updatedPrimaryKeyStatement = NUMERIC_ONLY_FLAG match {

      case true =>
        s"""
           | ${primaryKeyStatement}
           | AND DATA_TYPE in ('INT', 'SMALLINT', 'TINYINT', 'MEDIUMINT', 'BIGINT', 'FLOAT', 'DOUBLE')
           |
           """.stripMargin

      case _ =>
        primaryKeyStatement
    }

    val st: PreparedStatement = dbConn.prepareStatement(updatedPrimaryKeyStatement)
    val resultSet: ResultSet = st.executeQuery()

    Iterator
      .continually(getStringColumn(resultSet, columnIndex = 1))
      .takeWhile(_.nonEmpty)
      .flatten
      .toList
  }

  /**
    *
    * @param databaseName
    * @param tableName
    * @param dbConn
    * @param NUMERIC_ONLY_FLAG
    * @return
    */
  def getPrimaryKeysForOracle(databaseName: String, tableName: String, dbConn: Connection, NUMERIC_ONLY_FLAG: Boolean): List[String] = {
    val primaryKeyStatement: String =
      s"""
         |SELECT cols.table_name as ColumnName
         |FROM all_constraints cons, all_cons_columns cols
         |WHERE cols.table_name = '${tableName.toUpperCase}'
         |AND cons.constraint_type = 'P'
         |AND cons.constraint_name = cols.constraint_name
         |AND cons.owner = cols.owner
         |ORDER BY cols.table_name, cols.position;
         |
       """.stripMargin

    val updatedPrimaryKeyStatement = NUMERIC_ONLY_FLAG match {

      case true =>
        // to be implemented
        primaryKeyStatement
      case _ =>
        primaryKeyStatement
    }

    val st: PreparedStatement = dbConn.prepareStatement(updatedPrimaryKeyStatement)
    val resultSet: ResultSet = st.executeQuery()

    Iterator
      .continually(getStringColumn(resultSet, columnIndex = 1))
      .takeWhile(_.nonEmpty)
      .flatten
      .toList
  }


  /**
    * This method returns the primary keys of the table
    *
    * @param url
    * @param dbTable
    * @param dbConn
    * @param NUMERIC_ONLY_FLAG this specifies if only numeric primary keys are need to be retrived
    * @return
    */
  def getPrimaryKeys(url: String, dbTable: String, dbConn: Connection, NUMERIC_ONLY_FLAG: Boolean = false): List[String] = {
    val Array(databaseName, tableName) = dbTable.split("""\.""")

    val jdbcSystem = getJDBCSystem(url)
    jdbcSystem match {
      case JdbcConstants.TERADATA =>
        getPrimaryKeysForTeradata(databaseName, tableName, dbConn, NUMERIC_ONLY_FLAG)

      case JdbcConstants.MYSQL =>
        getPrimaryKeysForMysql(databaseName, tableName, dbConn, NUMERIC_ONLY_FLAG)

      case JdbcConstants.POSTGRESQL =>
        getPrimaryKeysForMysql(databaseName, tableName, dbConn, NUMERIC_ONLY_FLAG)

      case JdbcConstants.ORCALE =>
        getPrimaryKeysForOracle(databaseName, tableName, dbConn, NUMERIC_ONLY_FLAG)

      case _ =>
        List()
    }
  }


  //  /** this method gets the list of columns
  //    *
  //    * @param dataSetProps dataset properties
  //    * @return Boolean
  //    */
  //  def getTableSchema(dataSetProps: Map[String, Any]): List[Column] = {
  //
  //    val jdbcOptions: Map[String, String] = getJDBCOptions(dataSetProps)
  //    val con = JDBCCommons.getJdbcConnection(jdbcOptions("url"), jdbcOptions("user"), jdbcOptions("password"))
  //    val dbTable = jdbcOptions("dbtable")
  //    val Array(databaseName, tableName) = dbTable.split("""\.""")
  //    val getColumnTypesQuery = s"${getColumnsForDBQuery(databaseName)} AND tablename = '${tableName}'"
  //    val columnStatement: PreparedStatement = con.prepareStatement(getColumnTypesQuery)
  //    val columnsResultSet: ResultSet = columnStatement.executeQuery()
  //    var columns: List[Column] = List()
  //    while (columnsResultSet.asInstanceOf[ResultSet].next()) {
  //      val columnName: String = columnsResultSet.asInstanceOf[ResultSet].getString("ColumnName").trim
  //      var columnType: String = columnsResultSet.asInstanceOf[ResultSet].getString("ColumnDataType")
  //      // if columnType s is NULL
  //      if (columnType == null) {
  //        columnType = ""
  //      }
  //      columns = columns :+ Column(columnName, columnType)
  //    }
  //    con.close()
  //    columns
  //  }


  /** this method prepares the query to get the columns
    *
    * @param db database properties
    * @return Boolean
    */
  def getColumnsForDBQuery(db: String): String = {
    // get the columns for  particular table
    val getColumnTypesQuery: String =
    s"""
       | SELECT DatabaseName, TableName, ColumnName, CASE ColumnType
       |    WHEN 'BF' THEN 'BYTE('            || TRIM(CAST(ColumnLength AS INTEGER)) || ')'
       |    WHEN 'BV' THEN 'VARBYTE('         || TRIM(CAST(ColumnLength AS INTEGER)) || ')'
       |    WHEN 'CF' THEN 'CHAR('            || TRIM(CAST(ColumnLength AS INTEGER)) || ')'
       |    WHEN 'CV' THEN 'VARCHAR('         || TRIM(CAST(ColumnLength AS INTEGER)) || ')'
       |    WHEN 'D' THEN 'DECIMAL('         || TRIM(DecimalTotalDigits) || ','
       |                                      || TRIM(DecimalFractionalDigits) || ')'
       |    WHEN 'DA' THEN 'DATE'
       |    WHEN 'F ' THEN 'FLOAT'
       |    WHEN 'I1' THEN 'BYTEINT'
       |    WHEN 'I2' THEN 'SMALLINT'
       |    WHEN 'I8' THEN 'BIGINT'
       |    WHEN 'I ' THEN 'INTEGER'
       |    WHEN 'AT' THEN 'TIME('            || TRIM(DecimalFractionalDigits) || ')'
       |    WHEN 'TS' THEN 'TIMESTAMP('       || TRIM(DecimalFractionalDigits) || ')'
       |    WHEN 'TZ' THEN 'TIME('            || TRIM(DecimalFractionalDigits) || ')' || ' WITH TIME ZONE'
       |    WHEN 'SZ' THEN 'TIMESTAMP('       || TRIM(DecimalFractionalDigits) || ')' || ' WITH TIME ZONE'
       |    WHEN 'YR' THEN 'INTERVAL YEAR('   || TRIM(DecimalTotalDigits) || ')'
       |    WHEN 'YM' THEN 'INTERVAL YEAR('   || TRIM(DecimalTotalDigits) || ')'      || ' TO MONTH'
       |    WHEN 'MO' THEN 'INTERVAL MONTH('  || TRIM(DecimalTotalDigits) || ')'
       |    WHEN 'DY' THEN 'INTERVAL DAY('    || TRIM(DecimalTotalDigits) || ')'
       |    WHEN 'DH' THEN 'INTERVAL DAY('    || TRIM(DecimalTotalDigits) || ')'      || ' TO HOUR'
       |    WHEN 'DM' THEN 'INTERVAL DAY('    || TRIM(DecimalTotalDigits) || ')'      || ' TO MINUTE'
       |    WHEN 'DS' THEN 'INTERVAL DAY('    || TRIM(DecimalTotalDigits) || ')'      || ' TO SECOND('
       |                                      || TRIM(DecimalFractionalDigits) || ')'
       |    WHEN 'HR' THEN 'INTERVAL HOUR('   || TRIM(DecimalTotalDigits) || ')'
       |    WHEN 'HM' THEN 'INTERVAL HOUR('   || TRIM(DecimalTotalDigits) || ')'      || ' TO MINUTE'
       |    WHEN 'HS' THEN 'INTERVAL HOUR('   || TRIM(DecimalTotalDigits) || ')'      || ' TO SECOND('
       |                                      || TRIM(DecimalFractionalDigits) || ')'
       |    WHEN 'MI' THEN 'INTERVAL MINUTE(' || TRIM(DecimalTotalDigits) || ')'
       |    WHEN 'MS' THEN 'INTERVAL MINUTE(' || TRIM(DecimalTotalDigits) || ')'      || ' TO SECOND('
       |                                      || TRIM(DecimalFractionalDigits) || ')'
       |    WHEN 'SC' THEN 'INTERVAL SECOND(' || TRIM(DecimalTotalDigits) || ','
       |                                      || TRIM(DecimalFractionalDigits) || ')'
       |    WHEN 'BO' THEN 'BLOB('            || TRIM(CAST(ColumnLength AS INTEGER)) || ')'
       |    WHEN 'CO' THEN 'CLOB('            || TRIM(CAST(ColumnLength AS INTEGER)) || ')'
       |
                 |    WHEN 'PD' THEN 'PERIOD(DATE)'
       |    WHEN 'PM' THEN 'PERIOD(TIMESTAMP('|| TRIM(DecimalFractionalDigits) || ')' || ' WITH TIME ZONE'
       |    WHEN 'PS' THEN 'PERIOD(TIMESTAMP('|| TRIM(DecimalFractionalDigits) || '))'
       |    WHEN 'PT' THEN 'PERIOD(TIME('     || TRIM(DecimalFractionalDigits) || '))'
       |    WHEN 'PZ' THEN 'PERIOD(TIME('     || TRIM(DecimalFractionalDigits) || '))' || ' WITH TIME ZONE'
       |    WHEN 'UT' THEN COALESCE(ColumnUDTName,  '<Unknown> ' || ColumnType)
       |
         |    WHEN '++' THEN 'TD_ANYTYPE'
       |    WHEN 'N'  THEN 'NUMBER('          || CASE WHEN DecimalTotalDigits = -128 THEN '*' ELSE TRIM(DecimalTotalDigits) END
       |                                      || CASE WHEN DecimalFractionalDigits IN (0, -128) THEN '' ELSE ',' || TRIM(DecimalFractionalDigits) END
       |                                      || ')'
       |    WHEN 'A1' THEN COALESCE('SYSUDTLIB.' || ColumnUDTName,  '<Unknown> ' || ColumnType)
       |    WHEN 'AN' THEN COALESCE('SYSUDTLIB.' || ColumnUDTName,  '<Unknown> ' || ColumnType)
       |
         |    ELSE '<Unknown> ' || ColumnType
       |  END
       |  || CASE
       |        WHEN ColumnType IN ('CV', 'CF', 'CO')
       |        THEN CASE CharType
       |                WHEN 1 THEN ' CHARACTER SET LATIN'
       |                WHEN 2 THEN ' CHARACTER SET UNICODE'
       |                WHEN 3 THEN ' CHARACTER SET KANJISJIS'
       |                WHEN 4 THEN ' CHARACTER SET GRAPHIC'
       |                WHEN 5 THEN ' CHARACTER SET KANJI1'
       |                ELSE ''
       |             END
       |         ELSE ''
       |      END AS ColumnDataType
       |
                 |      from dbc.COLUMNSV where databasename='${db}'
               """.stripMargin

    getColumnTypesQuery
  }

  /**
    * This method truncates the given table give the connection to JDBC data source
    *
    * @param url     URL of JDBC system
    * @param dbTable table name
    * @param dbconn  JDBC data source connection
    * @return Boolean
    */
  def truncateTable(url: String, dbTable: String, dbconn: Connection,
                    logger: Option[Logger] = None): Boolean = {
    val jdbcSystem = getJDBCSystem(url)
    jdbcSystem match {
      case JdbcConstants.MYSQL =>
        val truncateTableStatement = s"DELETE FROM $dbTable"
        if (logger.isDefined) {
          logger.get.info(s"In TRUNCATE: $truncateTableStatement")
        }
        executeQueryStatement(truncateTableStatement, dbconn)
      case JdbcConstants.TERADATA =>
        val truncateTableStatement = s"DELETE $dbTable ALL"
        if (logger.isDefined) {
          logger.get.info(s"In TRUNCATE: $truncateTableStatement")
        }
        executeQueryStatement(truncateTableStatement, dbconn)
      case _ =>
        throw new Exception(s"This JDBC System is not supported. Please check gimel docs.")
    }
  }

  /**
    *
    * @param dataFrame
    * @return
    */
  def getFieldsFromDataFrame(dataFrame: DataFrame): Array[Field] = {

    // construct a schema for Teradata table
    val schema: StructType = dataFrame.schema
    val fields: Array[Field] = schema.map { x =>
      Field(x.name,
        SparkToJavaConverter.getTeradataDataType(x.dataType),
        x.nullable)
    }.toArray
    fields
  }


  /**
    * This method generates the UPADTE statement dynamically
    *
    * @param dbtable      tablename of the JDBC data source
    * @param setColumns   Array of dataframe columns
    * @param whereColumns Array of dataframe columns
    * @return updateStatement
    *         Update statement as a String
    */
  def getUpdateStatement(dbtable: String, setColumns: List[String], whereColumns: List[String]): String = {
    require(setColumns.nonEmpty, s"Column names cannot be an empty array for table $dbtable.")
    require(setColumns.nonEmpty, s"The set of primary keys cannot be empty for table $dbtable.")
    val setColumnsStatement = setColumns.map(columnName => s"${columnName} = ?")
    val separator = ", "
    val statementStart = s"UPDATE $dbtable SET "
    val whereConditions = whereColumns.map { key => s"${key} = ?" }
    val whereClause = whereConditions.mkString(" WHERE ", " AND ", "")
    setColumnsStatement.mkString(statementStart, separator, whereClause)
  }

  /**
    * prepareCreateStatement - From the column details passed in datasetproperties from GimelQueryProcesser,
    * create statement is constructed. If user passes primary index , set or multi set table, those will be added in the create statement
    *
    * @param dbtable      - Table Name
    * @param dataSetProps - Data set properties
    * @return - the created prepared statement for creating the table
    */
  def prepareCreateStatement(dbtable: String, dataSetProps: Map[String, Any]): String = {
    val sparkSchema = dataSetProps(GimelConstants.TABLE_FILEDS).asInstanceOf[Array[StructField]]
    // From the dataframe schema, translate them into Teradata data types
    val gimelSchema: Array[com.paypal.gimel.common.catalog.Field] = sparkSchema.map(x => {
      com.paypal.gimel.common.catalog.Field(x.name, SparkToJavaConverter.getTeradataDataType(x.dataType), x.nullable)
    })
    val colList: Array[String] = gimelSchema.map(x => (x.fieldName + " " + (x.fieldType) + ","))
    val conCatColumns = colList.mkString("")
    val paramsMapBuilder = dataSetProps.get(GimelConstants.TBL_PROPERTIES).get.asInstanceOf[Map[String, String]]
    val (tableType: String, indexString: String) = paramsMapBuilder.size match {
      case 0 => ("", "")
      case _ =>
        // If table type, primary index are passed, use them in the create statement
        val tableType = paramsMapBuilder.getOrElse(GimelConstants.TERA_DATA_TABLE_TYPE_COLUMN, "")
        val indexName = paramsMapBuilder.getOrElse(GimelConstants.TERA_DATA_INDEX_NAME_COLUMN, "")
        val indexColumn = paramsMapBuilder.getOrElse(GimelConstants.TERA_DATA_INDEX_COLUMN, "")
        paramsMapBuilder.contains("index_name") match {
          case true => (tableType, s"""${indexName}(${indexColumn})""")
          case _ => (tableType, "")
        }
    }
    s"""CREATE ${tableType} TABLE ${dbtable} (${conCatColumns.dropRight(1)} ) ${indexString}  """
  }

  /**
    * This method generates the INSERT statement dynamically
    *
    * @param dbtable tablename of the JDBC data source
    * @param cols    Number of columns in the table
    * @return insertStatement
    *         Insert statement as a String
    */
  def getInsertStatement(dbtable: String, cols: Int): String = {
    val statementStart = s"INSERT INTO $dbtable  VALUES( "
    val statementEnd = ")"
    val separator = ", "

    Seq.fill(cols)("?").mkString(statementStart, separator, statementEnd)
  }


  /**
    *
    * @param column column to get min-max for
    * @param conn   connection parameter
    * @return Tuple of min and max value of that column
    */
  def getMinMax(column: String, dbTable: String, conn: Connection): (Double, Double) = {
    val getMinMaxStatement = s"""SELECT MIN($column) as lowerBound, MAX($column) as upperBound FROM $dbTable"""
    val columnStatement: PreparedStatement = conn.prepareStatement(getMinMaxStatement)
    try {
      val resultSet: ResultSet = columnStatement.executeQuery()
      if (resultSet.next()) {
        val lowerBound = resultSet.getObject("lowerbound")
        val upperBound = resultSet.getObject("upperbound")
        if (lowerBound != null && upperBound != null) {
          (lowerBound.toString.toDouble, upperBound.toString.toDouble)
        }
        else {
          (0.0, 20.0)
        }
      }
      else {
        (0.0, 20.0)
      }
    }
    catch {
      case e: Exception =>
        throw new IllegalStateException(s"Error while getting min & max value from $dbTable: ${e.getMessage}", e)
    }
  }

  /**
    *
    * @param dbtable
    * @param con
    */
  def dropTable(dbtable: String, con: Connection, digestException: Boolean = true,
                logger: Option[Logger] = None): Unit = {
    val dropStatement =
      s"""
         | DROP TABLE $dbtable
       """.stripMargin
    if (logger.isDefined) {
      logger.get.info(s"About to execute DROP -> $dropStatement")
    }
    try {
      executeQueryStatement(dropStatement, con)
    }
    catch {
      case e: Exception =>
        // if the table doesn't exist then ignoring the exception
        new Logger(this.getClass.getName).error("Digesting drop table exception! ", e)
        if (!digestException) {
          throw e
        }
    }
  }


  /**
    *
    * @param dbTable       table
    * @param con           connection
    * @param numPartitions num of partitions
    * @return
    */
  def insertPartitionsIntoTargetTable(dbTable: String, con: Connection, numPartitions: Int,
                                      logger: Option[Logger] = None): Boolean = {
    val insertStatement =
      s"""
         | INSERT INTO $dbTable
         | ${getUnionAllStatetementFromPartitions(dbTable, numPartitions)}
       """.stripMargin
    if (logger.isDefined) {
      logger.get.info(s"About to execute INSERT -> $insertStatement")
    }
    executeQueryStatement(insertStatement, con)
  }

  /**
    *
    * @param dbTable       table
    * @param numPartitions num of partitions
    * @return
    */
  def getUnionAllStatetementFromPartitions(dbTable: String, numPartitions: Int): String = {
    val selStmt =
      """
        | SELECT * FROM
      """.stripMargin
    (0 until numPartitions).map(partitionId =>
      s"$selStmt ${dbTable}_${JdbcConstants.GIMEL_TEMP_PARTITION}_$partitionId"
    ).mkString(" UNION ALL ")
  }

  /**
    *
    * @param dbtable
    * @param con
    * @param numPartitions
    */
  def dropAllPartitionTables(dbtable: String, con: Connection, numPartitions: Int,
                             logger: Option[Logger] = None): Unit = {
    for (partitionId <- 0 until numPartitions) {
      JdbcAuxiliaryUtilities.dropTable(s"${dbtable}_${JdbcConstants.GIMEL_TEMP_PARTITION}_$partitionId",
        con, digestException = false)
    }
  }

  /**
    *
    * @param jdbcConnectionUtility
    * @param jdbcHolder
    * @return
    */
  def createConnectionWithPreConfigsSet(jdbcConnectionUtility: JDBCConnectionUtility,
                                        jdbcHolder: JDBCArgsHolder,
                                        connection: Option[Connection] = None): Connection = {
    if (connection.isDefined && !connection.get.isClosed) {
      connection.get
    } else {
      val dbc = jdbcConnectionUtility.getJdbcConnectionAndSetQueryBand()
      JdbcAuxiliaryUtilities.executePreConfigs(jdbcHolder.jdbcURL, jdbcHolder.dbTable, dbc)
      dbc
    }
  }

  /**
    *
    * @param query string
    * @param con   jdbc connection
    * @return
    */
  def executeQueryStatement(query: String, con: Connection, validateStatementExecution: Boolean = false): Boolean = {
    val isStatementExecutionSuccess = JDBCConnectionUtility.withResources(con.prepareStatement(query)) { statement =>
      val executeStatus: Boolean = statement.execute()
      con.commit()
      executeStatus
    }
    if (validateStatementExecution && !isStatementExecutionSuccess) {
      throw new IllegalStateException(s"Failed to execute query : $query")
    }
    isStatementExecutionSuccess
  }


  /**
    *
    * @param dbTable jdbc tablename
    * @param con     jdbc connection
    * @return ddl fo the table
    */
  def getDDL(url: String, dbTable: String, con: Connection): String = {

    val jdbcSystem = getJDBCSystem(url)

    // get the DDL for table for jdbc system
    import JDBCConnectionUtility.withResources
    val ddl = jdbcSystem match {
      case JdbcConstants.TERADATA =>
        val showTableQuery = s"SHOW TABLE $dbTable"
        withResources(con.prepareStatement(showTableQuery)) {
          preparedStatement =>
            withResources(preparedStatement.executeQuery()) {
              resultSet =>
                val stringBuilder = new StringBuilder
                while (resultSet.next()) {
                  stringBuilder ++= resultSet.getString(1).replaceAll("[\u0000-\u001f]", " ").replaceAll(" +", " ")
                }
                stringBuilder.toString
            }
        }
      case JdbcConstants.MYSQL =>
        val showTableQuery = s"SHOW CREATE TABLE $dbTable"
        withResources(con.prepareStatement(showTableQuery)) {
          preparedStatement =>
            withResources(preparedStatement.executeQuery()) {
              resultSet =>
                val stringBuilder = new StringBuilder
                while (resultSet.next()) {
                  stringBuilder ++= resultSet.getString("Create Table")
                    .replaceAll("[\u0000-\u001f]", " ").replaceAll(" +", " ")
                }
                val tempDdl = stringBuilder.toString
                // if ddl does not contain the database name, replace table name with db.tablename
                val ddlString = if (!tempDdl.contains(dbTable)) {
                  val tableName = dbTable.split("\\.")(1)
                  tempDdl.replace(tableName, dbTable)
                }
                else {
                  tempDdl
                }
                ddlString
            }
        }
      case _ =>
        throw new Exception(s"The JDBC System for $url is not supported")
    }
    ddl
  }

  /**
    * This returns the dataset properties based on the attribute set in the properties map
    *
    * @param dataSetProps
    * @return
    */
  def getDataSetProperties(dataSetProps: Map[String, Any]): Map[String, Any] = {
    val dsetProperties: Map[String, Any] = dataSetProps.contains(GimelConstants.DATASET_PROPS) match {
      case true =>
        // adding the properties map received from metadata to the outer map as well
        val datasetPropsOption: DataSetProperties = dataSetProps(GimelConstants.DATASET_PROPS).asInstanceOf[DataSetProperties]
        datasetPropsOption.props ++ dataSetProps
      case _ =>
        dataSetProps
    }
    dsetProperties
  }

  /**
    *
    * @param dsetProperties
    * @return
    */
  def getMysqlOptions(dsetProperties: Map[String, Any]): Map[String, String] = {

    // url
    val url = getJdbcUrl(dsetProperties)
    // driver
    val driver = dsetProperties(JdbcConfigs.jdbcDriverClassKey).toString
    val jdbcOptions: Map[String, String] = Map(JdbcConfigs.jdbcUrl -> url, JdbcConfigs.jdbcDriverClassKey -> driver)
    jdbcOptions
  }

  /**
    *
    * @param dsetProperties
    * @return
    */
  def getTeradataOptions(dsetProperties: Map[String, Any]): Map[String, String] = {

    // url
    val url = getJdbcUrl(dsetProperties)
    // driver
    val driver = dsetProperties(JdbcConfigs.jdbcDriverClassKey).toString

    val jdbcOptions: Map[String, String] = Map(JdbcConfigs.jdbcUrl -> url, JdbcConfigs.jdbcDriverClassKey -> driver)
    jdbcOptions
  }

  /**
    * This method will retrieve JDBC system specific options
    *
    * @param dsetProperties
    * @return
    */
  def getJdbcStorageOptions(dsetProperties: Map[String, Any]): Map[String, String] = {

    // url
    val url = dsetProperties(JdbcConfigs.jdbcUrl).toString

    val jdbcSystem = getJDBCSystem(url)
    val jdbcOptions: Map[String, String] = jdbcSystem match {
      case JdbcConstants.TERADATA =>
        getTeradataOptions(dsetProperties)
      case _ =>
        getMysqlOptions(dsetProperties)
    }
    jdbcOptions
  }

  /**
    * This function returns the JDBC properties map from HIVE table properties
    *
    * @param dataSetProps This will set all thew options required for READ/WRITE from/to JDBC data source
    * @return jdbcOptions
    */
  def getJDBCOptions(dataSetProps: Map[String, Any]): Map[String, String] = {
    val dsetProperties: Map[String, Any] = getDataSetProperties(dataSetProps)

    val jdbcOptions = getJdbcStorageOptions(dsetProperties)

    // table
    if (dsetProperties.contains(JdbcConfigs.jdbcInputTableNameKey)) {
      val jdbcTableName = dsetProperties(JdbcConfigs.jdbcInputTableNameKey).toString
      jdbcOptions + (JdbcConfigs.jdbcDbTable -> jdbcTableName)
    } else {
      jdbcOptions
    }
  }

  /**
    *
    * @param dataSetProps
    * @return string url
    */
  def getJdbcUrl(dataSetProps: Map[String, Any]): String = {

    val dsetProperties: Map[String, Any] = getDataSetProperties(dataSetProps)

    // get basic url
    val url = dsetProperties(JdbcConfigs.jdbcUrl).toString

    val jdbcSystem = getJDBCSystem(url)
    val jdbcUrl = jdbcSystem match {
      case JdbcConstants.TERADATA => {
        val charset = dataSetProps.getOrElse("charset", JdbcConstants.deafaultCharset).toString
        val teradataReadType: String = dataSetProps.getOrElse(JdbcConfigs.teradataReadType, "").toString
        val teradataWriteType: String = dataSetProps.getOrElse(JdbcConfigs.teradataWriteType, "").toString

        // get sessions
        val teradataSessions: String = dataSetProps.getOrElse("SESSIONS", JdbcConstants.defaultSessions).toString

        var newURL = url + "/" + "charset=" + charset
        // Teradata READ type
        newURL = if (teradataReadType.toUpperCase.equals("FASTEXPORT")) {
          newURL + "," + "TYPE=FASTEXPORT" + "," + s"SESSIONS=$teradataSessions"
        }
        else if (teradataWriteType.toUpperCase.equals("FASTLOAD")) {
          newURL + "," + "TYPE=FASTLOAD" + "," + s"SESSIONS=$teradataSessions"
        }
        else {
          newURL
        }
        newURL
      }
      case _ =>
        url
    }
    jdbcUrl
  }

  /**
    * These are some pre configs to set as soon as the connection is created
    *
    * @param url
    * @param dbTable
    * @param con
    * @return
    */
  def executePreConfigs(url: String, dbTable: String, con: Connection): Unit = {

    val jdbcSystem = getJDBCSystem(url)
    jdbcSystem match {
      case JdbcConstants.MYSQL =>
        // set the database to use for MYSQL
        val db = dbTable.split("\\.")(0)
        executeQueryStatement(s"USE $db", con)
      case _ =>
      // do nothing
    }
  }

  /**
    *
    * @param jdbcUrl
    * @return
    */
  def getJDBCSystem(jdbcUrl: String): String = {
    if (jdbcUrl.toLowerCase.contains("jdbc:teradata")) {
      JdbcConstants.TERADATA
    }
    else if (jdbcUrl.toLowerCase.contains("jdbc:mysql")) {
      JdbcConstants.MYSQL
    }
    else if (jdbcUrl.toLowerCase.contains("jdbc:oracle")) {
      JdbcConstants.ORCALE
    }
    else if (jdbcUrl.toLowerCase.contains("jdbc:postgresql")) {
      JdbcConstants.POSTGRESQL
    }
    else {
      throw new Exception(s"This JDBC System with ${jdbcUrl} is not supported. Please check gimel docs.")
    }
  }

  /** This method creates the table in teradata database
    *
    * @param dbTable JDBC table name in format db.table
    * @param fields  schama fields in dataframe
    * @return Boolean
    */
  def createJDBCTable(dbTable: String, fields: Array[Field], con: Connection): Boolean = {
    var colList = ""
    fields.foreach(x => colList = colList + x.fieldName + " " + (x.fieldType) + ",")
    val createTableStatement = s"""CREATE TABLE ${dbTable} (${colList.dropRight(1)} ) """
    val columnStatement: PreparedStatement = con.prepareStatement(createTableStatement)
    columnStatement.execute()
  }

  /**
    * This method returns the partition table name based on jdbc system
    *
    * @param url
    * @param dbTable
    * @param con
    * @return
    */
  def getPartitionTableName(url: String, dbTable: String, con: Connection, partitionId: Int): String = {

    val jdbcSystem = getJDBCSystem(url)
    val partitionTable = jdbcSystem match {
      case JdbcConstants.MYSQL =>
        s"${dbTable.split("\\.")(1)}_${JdbcConstants.GIMEL_TEMP_PARTITION}_$partitionId"

      case JdbcConstants.TERADATA =>
        s"${dbTable}_${JdbcConstants.GIMEL_TEMP_PARTITION}_$partitionId"

      case _ =>
        throw new Exception(s"This JDBC System is not supported. Please check gimel docs.")
    }
    partitionTable
  }

  /**
    * This methos retuns the partitioned dataframe based on the partition method and the current number of partitions in df
    *
    * @param url
    * @param df
    * @param userSpecifiedPartitions
    * @return
    */
  def getPartitionedDataFrame(url: String, df: DataFrame, userSpecifiedPartitions: Option[Any]): DataFrame = {
    val logger = Logger()
    val currentPartitions = df.toJavaRDD.getNumPartitions
    val maxAllowedPartitions = getMaxPartitions(url, JdbcConstants.writeOperation)
    val (partitions, partitionMethod) = userSpecifiedPartitions match {
      case None =>
        (Math.min(maxAllowedPartitions, currentPartitions), JdbcConstants.coalesceMethod)
      case _ =>
        val userPartitions = userSpecifiedPartitions.get.toString.toInt
        if (userPartitions > currentPartitions) {
          (Math.min(userPartitions, maxAllowedPartitions), JdbcConstants.repartitionMethod)
        }
        else {
          (Math.min(userPartitions, maxAllowedPartitions), JdbcConstants.coalesceMethod)
        }
    }

    // this case is added specifically for the dataframe having no data i.e. partitions = 0
    val partitionsToSet = Math.max(partitions, 1)

    logger.info(s"Setting number of partitions of the dataFrame to ${partitionsToSet} with ${partitionMethod}")
    val partitionedDataFrame = partitionMethod match {
      case JdbcConstants.repartitionMethod =>
        df.repartition(partitionsToSet)
      case JdbcConstants.coalesceMethod =>
        df.coalesce(partitionsToSet)
      case _ =>
        df
    }
    partitionedDataFrame
  }

  /**
    * This method returns the max number of partitions for the JDBC system based on the operation
    *
    * @param url
    * @param operationMethod
    */
  def getMaxPartitions(url: String, operationMethod: String): Int = {
    val jdbcSystem = getJDBCSystem(url)
    val defaultPartitions = jdbcSystem match {
      case JdbcConstants.TERADATA => {
        operationMethod match {
          case JdbcConstants.readOperation =>
            JdbcConstants.NUM_READ_PARTITIONS
          case JdbcConstants.writeOperation =>
            JdbcConstants.NUM_WRITE_PARTITIONS
        }
      }
      case _ => {
        operationMethod match {
          case JdbcConstants.readOperation =>
            JdbcConstants.defaultJDBCReadPartitions
          case JdbcConstants.writeOperation =>
            JdbcConstants.defaultJDBCWritePartitions
        }
      }
    }
    defaultPartitions
  }

  /**
    * This method returns the number of partitions based on user parameter and JDBC system
    *
    * @param url
    * @param userSpecifiedPartitions
    * @param operationMethod
    * @return
    */
  def getNumPartitions(url: String, userSpecifiedPartitions: Option[Any], operationMethod: String): Int = {
    val logger = Logger()
    val jdbcSystem = getJDBCSystem(url)
    val maxAllowedPartitions = getMaxPartitions(url, operationMethod)
    val userPartitions = userSpecifiedPartitions.getOrElse(maxAllowedPartitions).toString.toInt
    if (userPartitions > maxAllowedPartitions) {
      logger.info(s"Warning: Maximum number of partitions are SET to ${maxAllowedPartitions} for ${jdbcSystem} due to connections limitations")
    }
    val numPartitions = Math.min(userPartitions, maxAllowedPartitions)
    logger.info(s"Maximum number of partitions are SET to ${numPartitions} for ${jdbcSystem}")
    numPartitions
  }

  /**
    *
    * @param sparkSession
    * @param url
    * @param table
    * @param partitionColumn
    * @param lowerBound
    * @param upperBound
    * @param numPartitions
    * @param fetchSize
    * @param connectionProperties
    * @return
    */
  def sparkJdbcRead(sparkSession: SparkSession, url: String, table: String, partitionColumn: String, lowerBound: Long, upperBound: Long, numPartitions: Int, fetchSize: Int, connectionProperties: Properties): DataFrame = {

    val logger = Logger(this.getClass.getName)
    logger.info(s"Using the default spark JDBC read for ${url}")
    sparkSession.read.jdbc(url = url,
      table = table,
      columnName = partitionColumn,
      lowerBound = lowerBound,
      upperBound = upperBound,
      numPartitions = numPartitions,
      connectionProperties = connectionProperties)
  }
}
