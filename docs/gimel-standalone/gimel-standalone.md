* [Gimel Standalone](#gimel-standalone)
   * [Note](#note)
   * [Prerequisite](#prerequisite)
   * [Download the Gimel Jar](#download-the-gimel-jar)
   * [Run Gimel Quickstart Script](#run-gimel-quickstart-script)
   * [Common Imports and Initializations](#common-imports-and-initializations)
   * [Create the Datasets](#create-the-datasets)
      * [Catalog Provider as USER](#catalog-provider-as-user)
        * [Create HDFS Datasets for loading Flights Data](#create-hdfs-datasets-for-loading-flights-data)
        * [Create Kafka Dataset](#create-kafka-dataset)
        * [Create HBase Datasets](#create-hbase-datasets)
        * [Create Elastic Search Dataset](#create-elastic-search-dataset)
      * [Catalog Provider as HIVE](#catalog-provider-as-hive)
   * [Cache Airports Data from HDFS](#cache-airports-data-from-hdfs)
   * [Load Flights Data into Kafka Dataset](#load-flights-data-into-kafka-dataset)
   * [Load Flights Lookup Data into HBase Datasets](#load-flights-lookup-data-into-hbase-datasets)
   * [Enrich Flights Data, Denormalize, Add Geo Coordinate](#enrich-flights-data-denormalize-add-geo-coordinate)
   * [Save Enriched Data in Elastic Search](#save-enriched-data-in-elastic-search)
   * [Explore, Visualize and Discover Data on Kibana](#explore-visualize-and-discover-data-on-kibana)

--------------------------------------------------------------------------------------------------------------------

# Gimel Standalone

## Note

* The Gimel Standalone feature will provide capability for developers / users alike to

  * Try Gimel in local/laptop without requiring all the ecosystems on a hadoop cluster.
  * Standalone would comprise of docker containers spawned for each storage type that the user would like to explore. Storage type examples : kafka , elasticsearch.
  * Standalone would bootstrap these containers (storage types) with sample flights data.
  * Once containers are spawned & data is bootstrapped, the use can then refer the connector docs & try the Gimel Data API / Gimel SQL on the local laptop.
  * Also in the future : the standalone feature would be useful to automate regression tests & run standalone spark JVMs for container based solutions. 

___________________________________________________________________________________________________________________

## Prerequisite

* Install docker on your machine 
  * MAC - https://docs.docker.com/docker-for-mac/install/

___________________________________________________________________________________________________________________

## Download the Gimel Jar

* Download the gimel jar from {PLACEHOLDER}
* Move it to gimel/gimel-dataapi/gimel-standalone/lib

___________________________________________________________________________________________________________________
## Run Gimel Quickstart Script

```
$ quickstart/gimel
```

* This script will do the following:
  * Start docker containers for each storage
  * Bootstrap the physical storages (Create Kafka Topic and HBase tables)
  * Start a spark-shell with gimel jar added
  
Note: If you want to start a spark shell by youself, Run the following command

```
docker exec -it spark-master bash -c \
"export USER=an;export SPARK_HOME=/spark/;export PATH=$PATH:$SPARK_HOME/bin:$SPARK_HOME/sbin; \
/spark/bin/spark-shell --jars /root/gimel-tools-1.2.0-SNAPSHOT-uber.jar"
```
  
___________________________________________________________________________________________________________________


## Common Imports and Initializations

```
import org.apache.spark.sql.{DataFrame, SQLContext};
import org.apache.spark.sql.hive.HiveContext;

//Initiate HiveContext
val hiveContext = new HiveContext(sc);

//Initiate SQLContext
val sqlContext = hiveContext.asInstanceOf[SQLContext]
```

___________________________________________________________________________________________________________________


## Create the Datasets

### Catalog Provider as USER

```
hiveContext.sql("set gimel.catalog.provider=USER");
```

#### Create HDFS Datasets for loading Flights Data

```
sqlContext.sql("""set pcatalog.flights_hdfs.dataSetProperties={ 
    "datasetType": "HDFS",
    "fields": [],
    "partitionFields": [],
    "props": {
         "gimel.hdfs.data.format":"csv",
         "location":"hdfs://namenode:8020/flights/data",
         "datasetName":"pcatalog.flights_hdfs"
    }
}""")
```

```
sqlContext.sql("""set pcatalog.flights_lookup_carrier_code_hdfs.dataSetProperties={ 
    "datasetType": "HDFS",
    "fields": [],
    "partitionFields": [],
    "props": {
         "gimel.hdfs.data.format":"csv",
         "location":"hdfs://namenode:8020/flights/lkp/carrier_code",
         "datasetName":"pcatalog.flights_lookup_carrier_code_hdfs"
    }
}""")
```

```
sqlContext.sql("""set pcatalog.flights_lookup_airline_id_hdfs.dataSetProperties={ 
    "datasetType": "HDFS",
    "fields": [],
    "partitionFields": [],
    "props": {
         "gimel.hdfs.data.format":"csv",
         "location":"hdfs://namenode:8020/flights/lkp/airline_id",
         "datasetName":"pcatalog.flights_lookup_airline_id_hdfs"
    }
}""")
```

```

sqlContext.sql("""set pcatalog.flights_lookup_cancellation_code_hdfs.dataSetProperties={ 
    "datasetType": "HDFS",
    "fields": [],
    "partitionFields": [],
    "props": {
         "gimel.hdfs.data.format":"csv",
         "location":"hdfs://namenode:8020/flights/lkp/cancellation_code",
         "datasetName":"pcatalog.flights_lookup_cancellation_code_hdfs"
    }
}""")
```

```

sqlContext.sql("""set pcatalog.flights_lookup_airports_hdfs.dataSetProperties={ 
    "datasetType": "HDFS",
    "fields": [],
    "partitionFields": [],
    "props": {
         "gimel.hdfs.data.format":"csv",
         "location":"hdfs://namenode:8020/flights/lkp/airports",
         "datasetName":"pcatalog.flights_lookup_airports_hdfs"
    }
}""")
```

#### Create Kafka Dataset

```
sqlContext.sql("""set pcatalog.flights_kafka_json.dataSetProperties={ 
    "datasetType": "KAFKA",
    "fields": [],
    "partitionFields": [],
    "props": {
        "gimel.storage.type":"kafka",
  		"gimel.kafka.message.value.type":"json",
  		"gimel.kafka.whitelist.topics":"gimel.demo.flights.json",
  		"bootstrap.servers":"kafka:9092",
  		"gimel.kafka.checkpoint.zookeeper.host":"zookeeper:2181",
  		"gimel.kafka.checkpoint.zookeeper.path":"/pcatalog/kafka_consumer/checkpoint/flights",
  		"gimel.kafka.zookeeper.connection.timeout.ms":"10000",
  		"gimel.kafka.throttle.batch.maxRecordsPerPartition":"10000000",
  		"gimel.kafka.throttle.batch.fetchRowsOnFirstRun":"10000000",    
  		"auto.offset.reset":"earliest",
  		"key.serializer":"org.apache.kafka.common.serialization.StringSerializer",
  		"value.serializer":"org.apache.kafka.common.serialization.StringSerializer",
  		"key.deserializer":"org.apache.kafka.common.serialization.StringDeserializer",
  		"value.deserializer":"org.apache.kafka.common.serialization.StringDeserializer",
  		"datasetName":"pcatalog.flights_kafka_json"
    }
}""")

```

#### Create HBase Datasets

```
sqlContext.sql("""set pcatalog.flights_lookup_cancellation_code_hbase.dataSetProperties=
{
    "datasetType": "HBASE",
    "fields": [
        {
            "fieldName": "Code",
            "fieldType": "string",
            "isFieldNullable": false
        },
        {
            "fieldName": "Description",
            "fieldType": "string",
            "isFieldNullable": false
        }
    ],
    "partitionFields": [],
    "props": {
        "gimel.hbase.rowkey":"Code",
        "gimel.hbase.table.name":"flights:flights_lookup_cancellation_code",
        "gimel.hbase.namespace.name":"flights",
        "gimel.hbase.columns.mapping":":key,flights:Description",
         "datasetName":"pcatalog.flights_lookup_cancellation_code_hbase"
    }
}
""")
```

```
sqlContext.sql("""set pcatalog.flights_lookup_carrier_code_hbase.dataSetProperties=
{
    "datasetType": "HBASE",
    "fields": [
        {
            "fieldName": "Code",
            "fieldType": "string",
            "isFieldNullable": false
        },
        {
            "fieldName": "Description",
            "fieldType": "string",
            "isFieldNullable": false
        }
    ],
    "partitionFields": [],
    "props": {
        "gimel.hbase.rowkey":"Code",
        "gimel.hbase.table.name":"flights:flights_lookup_carrier_code",
        "gimel.hbase.namespace.name":"flights",
        "gimel.hbase.columns.mapping":":key,flights:Description",
         "datasetName":"pcatalog.flights_lookup_carrier_code_hbase"
    }
}
""")
```

```
sqlContext.sql("""set pcatalog.flights_lookup_airline_id_hbase.dataSetProperties=
{
    "datasetType": "HBASE",
    "fields": [
        {
            "fieldName": "Code",
            "fieldType": "string",
            "isFieldNullable": false
        },
        {
            "fieldName": "Description",
            "fieldType": "string",
            "isFieldNullable": false
        }
    ],
    "partitionFields": [],
    "props": {
        "gimel.hbase.rowkey":"Code",
        "gimel.hbase.table.name":"flights:flights_lookup_airline_id",
        "gimel.hbase.namespace.name":"flights",
        "gimel.hbase.columns.mapping":":key,flights:Description",
         "datasetName":"pcatalog.flights_lookup_airline_id_hbase"
    }
}
""")


```

#### Create Elastic Search Dataset

```
sqlContext.sql("""set pcatalog.gimel_flights_elastic.dataSetProperties=
{
    "datasetType": "ELASTIC_SEARCH",
    "fields": [],
    "partitionFields": [],
    "props": {
  		"es.mapping.date.rich":"true",
  		"es.nodes":"http://elasticsearch",
  		"es.port":"9200",
  		"es.resource":"flights/data",
  		"es.index.auto.create":"true",
  		"gimel.es.schema.mapping":"{\"location\": { \"type\": \"geo_point\" } }",
		"gimel.es.index.partition.delimiter":"-",
		"gimel.es.index.partition.isEnabled":"true",
		"gimel.es.index.read.all.partitions.isEnabled":"true",
		"gimel.es.index.partition.suffix":"20180205",
		"gimel.es.schema.mapping":"{\"executionStartTime\": {\"format\": \"strict_date_optional_time||epoch_millis\", \"type\": \"date\" }, \"createdTime\": {\"format\": \"strict_date_optional_time||epoch_millis\", \"type\": \"date\"},\"endTime\": {\"format\": \"strict_date_optional_time||epoch_millis\", \"type\": \"date\"}}",
		"gimel.storage.type":"ELASTIC_SEARCH",
		"datasetName":"pcatalog.gimel_flights_elastic"
    }
}
""")
```

### Catalog Provider as HIVE

```
hiveContext.sql("set gimel.catalog.provider=HIVE");
```

___________________________________________________________________________________________________________________


## Cache Airports Data from HDFS

```
val sql="""cache table lkp_airport
select 
struct(lat,lon) as location
,concat(lat,",",lon) as location1
, * 
from 
(
select iata, lat, lon, country, city, name
, row_number() over (partition by iata order by 1 desc ) as rnk
from pcatalog.flights_lookup_airports_hdfs
) tbl
where rnk  = 1
"""

com.paypal.gimel.sql.GimelQueryProcessor.executeBatch(sql, spark)
```

___________________________________________________________________________________________________________________


## Load Flights Data into Kafka Dataset

```
com.paypal.gimel.sql.GimelQueryProcessor.executeBatch(
"insert into pcatalog.flights_kafka_json select * from pcatalog.flights_hdfs",
spark)
```

### Cache Kafka Data

```
com.paypal.gimel.sql.GimelQueryProcessor.executeBatch(
"cache table flights select * from  pcatalog.flights_kafka_json",
spark)
```

___________________________________________________________________________________________________________________

## Load Flights Lookup Data into HBase Datasets

```
com.paypal.gimel.sql.GimelQueryProcessor.executeBatch(
"insert into pcatalog.flights_lookup_cancellation_code_hbase select * from pcatalog.flights_lookup_cancellation_code_hdfs", 
spark)

com.paypal.gimel.sql.GimelQueryProcessor.executeBatch(
"insert into pcatalog.flights_lookup_airline_id_hbase select * from pcatalog.flights_lookup_airline_id_hdfs",
spark)

com.paypal.gimel.sql.GimelQueryProcessor.executeBatch(
"insert into pcatalog.flights_lookup_carrier_code_hbase select * from pcatalog.flights_lookup_carrier_code_hdfs",
spark)
```

### Cache lookup Tables from HBase

```
com.paypal.gimel.sql.GimelQueryProcessor.executeBatch(
"cache table lkp_carrier select * from pcatalog.flights_lookup_carrier_code_hbase", 
spark)

com.paypal.gimel.sql.GimelQueryProcessor.executeBatch(
"cache table lkp_airline select * from pcatalog.flights_lookup_airline_id_hbase",
spark)

com.paypal.gimel.sql.GimelQueryProcessor.executeBatch(
"cache table lkp_cancellation select * from pcatalog.flights_lookup_cancellation_code_hbase", 
spark)

```

___________________________________________________________________________________________________________________

## Enrich Flights Data, Denormalize, Add Geo Coordinate

```
val sql = """cache table flights_log_enriched

SELECT 
 to_date(substr(fl_date,1,10)) as flight_date
,flights_kafka.*
,lkp_airport_origin.location as origin_location
,lkp_airport_origin.name as origin_airport_name
,lkp_airport_origin.city as origin_airport_city
,lkp_airport_origin.country as origin_airport_country
,lkp_airport_dest.location as dest_location
,lkp_airport_dest.name as dest_airport_name
,lkp_airport_dest.city as dest_airport_city
,lkp_airport_dest.country as dest_airport_country
,lkp_carrier.description as carrier_desc
,lkp_airline.description as airline_desc
,lkp_cancellation.description as cancellation_reason

from flights flights_kafka                                  

left join lkp_carrier lkp_carrier                          
on flights_kafka.unique_carrier = lkp_carrier.code 

left join lkp_airline lkp_airline                          
on flights_kafka.airline_id = lkp_airline.code

left join lkp_cancellation lkp_cancellation                 
on flights_kafka.CANCELLATION_CODE = lkp_cancellation.code

left join lkp_airport lkp_airport_origin                   
on flights_kafka.origin = lkp_airport_origin.iata

left join lkp_airport lkp_airport_dest                    
on flights_kafka.dest = lkp_airport_dest.iata
"""

com.paypal.gimel.sql.GimelQueryProcessor.executeBatch(sql,spark)


```

___________________________________________________________________________________________________________________

## Save Enriched Data in Elastic Search

```
val sql = """insert into pcatalog.gimel_flights_elastic
select * from flights_log_enriched
where cancelled = 1"""

com.paypal.gimel.sql.GimelQueryProcessor.executeBatch(sql,spark)
```

___________________________________________________________________________________________________________________

## Explore, Visualize and Discover Data on Kibana

* Go to Kibana at http://localhost:5601
* Create the index pattern for flights index
* Explore and Visualize your data on Kibana Dashboard

___________________________________________________________________________________________________________________
