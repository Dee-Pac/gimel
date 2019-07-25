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

import java.sql.{Connection, DriverManager}
import java.util.Properties

import scala.util.control.NonFatal

import org.apache.spark.sql.SparkSession

import com.paypal.gimel.common.conf.GimelConstants
import com.paypal.gimel.jdbc.conf.{JdbcConfigs, JdbcConstants}
import com.paypal.gimel.jdbc.exception.JDBCConnectionError
import com.paypal.gimel.jdbc.utilities.JdbcAuxiliaryUtilities.getJDBCSystem

/**
  * This object defines the connection object required any time during Sparksession
  */
object JDBCConnectionUtility {
  def apply(sparkSession: SparkSession, dataSetProps: Map[String, Any]): JDBCConnectionUtility =
    new JDBCConnectionUtility(sparkSession: SparkSession, dataSetProps: Map[String, Any])

  /**
    * Using try-with-resources, as described in https://medium.com/@dkomanov/scala-try-with-resources-735baad0fd7d
    *
    * @param r
    * @param f
    * @tparam T
    * @tparam V
    * @return
    */
  def withResources[T <: AutoCloseable, V](r: => T)(f: T => V): V = {
    val resource: T = r
    require(resource != null, "resource is null")
    var exception: Throwable = null
    try {
      f(resource)
    } catch {
      case NonFatal(e) =>
        exception = e
        throw e
    } finally {
      closeAndAddSuppressed(exception, resource)
    }
  }

  private def closeAndAddSuppressed(e: Throwable,
                                    resource: AutoCloseable): Unit = {
    if (e != null) {
      try {
        resource.close()
      } catch {
        case NonFatal(suppressed) =>
          e.addSuppressed(suppressed)
      }
    } else {
      resource.close()
    }
  }

}

class JDBCConnectionUtility(sparkSession: SparkSession, dataSetProps: Map[String, Any]) extends Serializable {
  private val authUtilities: JDBCAuthUtilities = JDBCAuthUtilities(sparkSession)

  // get url
  val url = JdbcAuxiliaryUtilities.getJdbcUrl(dataSetProps)

  // get JDBC system type
  val jdbcSystem = getJDBCSystem(url)

  // get actual JDBC user
  var jdbcUser: String = JDBCCommons.getJdbcUser(dataSetProps, sparkSession)

  // Get username and password
  private val (userName, password) = authUtilities.getJDBCCredentials(url, dataSetProps)

  val jdbcPasswordStrategy = dataSetProps.getOrElse(JdbcConfigs.jdbcPasswordStrategy, JdbcConstants.jdbcDefaultPasswordStrategy).toString

  // check valid access
  val validUserAccess: Boolean = verifyUserForQueryBand()

  /**
    * This method returns the connection object
    *
    * @return
    */
  def getJdbcConnection(): Connection = {
    try {
      jdbcSystem match {
        case JdbcConstants.TERADATA =>
          // For making sure unit and integration tests are able to access
          Class.forName("com.teradata.jdbc.TeraDriver")
        case _ => // Do nothing
      }
      DriverManager.getConnection(url, userName, password)
    }
    catch {
      case e: Throwable =>
        throw new JDBCConnectionError(s"Error getting JDBC connection: ${e.getMessage}", e)
    }
  }

  /**
    * This method sets the queryband and returns the connection object
    *
    * @return connection
    */
  def getJdbcConnectionAndSetQueryBand(): Connection = {
    val con = getJdbcConnection()
    jdbcSystem match {
      case JdbcConstants.TERADATA =>
        JDBCCommons.setQueryBand(con, url, jdbcUser, jdbcPasswordStrategy)
      case _ =>
      // do nothing
    }
    con
  }


  /**
    * This is PayPal specific verification in order to restrict anyone to override user via proxyuser
    *
    */
  def verifyUserForQueryBand(): Boolean = {

    val sparkUser = sparkSession.sparkContext.sparkUser
    jdbcSystem match {
      case JdbcConstants.TERADATA => {
        if (jdbcPasswordStrategy.equalsIgnoreCase(JdbcConstants.jdbcDefaultPasswordStrategy)) {
          // check For GTS queries
          // If GTS user != jdbcUser, then throw exception.

          val gtsUser: String = sparkSession.sparkContext.getLocalProperty(GimelConstants.GTS_USER_CONFIG)
          if (!jdbcUser.equalsIgnoreCase(gtsUser)) {
            throw new Exception(
              s"""SECURITY VIOLATION | [${gtsUser}] attempting Teradata Query Band Via JDBC User [${jdbcUser}]
                  |This Operation is NOT permitted."""
                .stripMargin
            )
          }
          // check for non-GTS queries
          // If jdbcUser != sparkUser, then throw exception.
          if (!jdbcUser.equalsIgnoreCase(sparkUser)) {
            throw new Exception(
              s"""
                 |SECURITY VIOLATION | [${sparkUser}] attempting Teradata Query Band Via JDBC User [${jdbcUser}]
                 |This Operation is NOT permitted."""
                .stripMargin
            )
          }
        }
      }
      case _ =>
      // do nothing
    }
    true
  }

  /**
    *
    * @return
    */
  def getConnectionProperties(): Properties = {
    val connectionProperties = new Properties()
    connectionProperties.put("user", s"${userName}")
    connectionProperties.put("password", s"${password}")

    // set driver class
    val driverClass = JdbcAuxiliaryUtilities.getJdbcStorageOptions(dataSetProps)(JdbcConfigs.jdbcDriverClassKey)
    connectionProperties.setProperty("Driver", driverClass)

    connectionProperties
  }

}
