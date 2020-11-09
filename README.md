```python
!scala -version
```

________________________________
<span style="font-family:PayPal; font-size:3em; color:rgb(100, 25, 138)">Initiated Spark Session with Gimel Libraries</span>
________________________________


```python
from pyspark.sql import SparkSession
spark = SparkSession.builder \
  .appName('Gimel BigQuery Storage')\
  .config('spark.jars', 'gs://demc-test/lib/gimel-core-2.4.7-SNAPSHOT-uber.jar,gs://spark-lib/bigquery/spark-bigquery-latest_2.12.jar') \
  .config('spark.driver.extraClassPath','gimel-core-2.4.7-SNAPSHOT-uber.jar:spark-bigquery-latest_2.12.jar') \
  .config('spark.executor.extraClassPath','gimel-core-2.4.7-SNAPSHOT-uber.jar:spark-bigquery-latest_2.12.jar') \
  .getOrCreate()
```


```python
spark.version
```

________________________________
<span style="font-family:PayPal; font-size:3em; color:rgb(100, 25, 138)">Initiated Gimel Data / SQL API</span>
________________________________


```python
# import DataFrame and SparkSession
from pyspark.sql import DataFrame, SparkSession, SQLContext

# fetch reference to the class in JVM
ScalaDataSet = sc._jvm.com.paypal.gimel.DataSet

# fetch reference to java SparkSession
jspark = spark._jsparkSession

# initiate dataset
dataset = ScalaDataSet.apply(jspark)
```

________________________________
<span style="font-family:PayPal; font-size:3em; color:rgb(100, 25, 138)">Read, Transform, Write</span>
________________________________


```python
## ---- Catalog Provider can be HIVE, USER or external Catalog
spark.sql("set gimel.catalog.provider=UDC")
spark.sql("set udc.host=host_fqdn_or_ip")
spark.sql("set udc.port=port")
spark.sql("set udc.protocol=https")


## --- READ API

dataFrame = dataset.read("shakespere",options ={})


## ---- Write to GCS (defining a custom config/catalog)

dataSetProperties = s"""
  { "datasetName":"shakespere_sink",
      "datasetType": "gcs",
      "fields": [],
      "partitionFields": [],
      "props": {
            "gimel.storage.type":"gcs",
            "datasetName":"my_cloud_table",
            "bucket":"gs://demc-test/pp-devcos-dataproc-gcs.BQ_benchmark.date_dim"
            "format":"parquet",
            "options":[{"key":"val"}]
         }
  }"""

## ---- Catalog Provider can be HIVE, USER or external Catalog
spark.sql("set gimel.catalog.provider=USER")
options = Map("shakespere_sink.dataSetProperties" -> dataSetProperties)

## --- READ API

val dataFrame = dataFrame.write("shakespere_sink",options)

## --- Gimel SQL API

gsql("""
insert into shakespere_sink
select * from shakespere
where ...
""")

```

________________________________
<span style="font-family:PayPal; font-size:3em; color:rgb(100, 25, 138)">Set External Catalog Provider</span>
________________________________


```python


## ---- Catalog Provider can be HIVE, USER or external Catalog
spark.sql("set gimel.catalog.provider=UDC")
spark.sql("set udc.host=host_fqdn_or_ip")
spark.sql("set udc.port=port")
spark.sql("set udc.protocol=https")
```

________________________________
<span style="font-family:PayPal; font-size:3em; color:rgb(100, 25, 138)">Set Runtime Catalog</span>
________________________________


```python
## ---- Catalog Provider can be HIVE, USER or external Catalog
spark.sql("set gimel.catalog.provider=USER")
val dataFrame = dataset.read("shakespere",options)

```


```python

## ---- Big Query DataSet ----

dataSetProperties = s"""
  { "datasetName":"shakespere",
      "datasetType": "bigquery",
      "fields": [],
      "partitionFields": [],
      "props": {
            "gimel.storage.type":"bigquery",
            "datasetName":"my_cloud_table",
            "project":"bigquery-public-data",
            "dataset":"samples",
            "object":"shakespeare",
            "object_type":"table",
            "options":[{"key":"val"}]
         }
  }"""

## ---- Kafka DataSet ----

dataSetProperties = 
  """{"datasetName":"shakespere",
      "datasetType": "kafka",
      "fields": [],
      "partitionFields": [],
      "props": {
            "gimel.storage.type":"kafka",
            "datasetName":"query_logs",
            "auto.offset.reset":"earliest",
            "gimel.kafka.checkpoint.zookeeper.host":"zk1:2181,zk2:2181",
            "gimel.kafka.avro.schema.source.url":"Defaults",
            "gimel.kafka.avro.schema.source.wrapper.key":"Defaults",
            "gimel.kafka.avro.schema.source":"INLINE",
            "zookeeper.connection.timeout.ms":"3000",
            "bootstrap.servers":"broker1:9092,broker2:9092,broker4:9092",
            "gimel.kafka.checkpoint.zookeeper.path":"/kafka_consumer/checkpoint",
            "key.deserializer":"org.apache.kafka.common.serialization.StringDeserializer",
            "value.deserializer":"org.apache.kafka.common.serialization.StringDeserializer",
            "key.serializer":"org.apache.kafka.common.serialization.StringSerializer",
            "value.serializer":"org.apache.kafka.common.serialization.StringSerializer",
            "gimel.kafka.whitelist.topics":"kafka_topic_name",
            "gimel.kafka.message.value.type":"json",
            "gimel.kafka.api.version":"1"
         }
  }"""

## ---- JDBC DataSet ----

dataSetProperties_mysql = s"""
  {   "datasetName":"shakespere",
      "datasetType": "JDBC",
      "fields": [],
      "partitionFields": [],
      "props": {
		"gimel.jdbc.p.strategy":"file",
		"gimel.jdbc.p.file":"gs://user/dmohanakumarchan/udc.prod.pass",
		"gimel.jdbc.username":"user",
        "gimel.jdbc.input.table.name":"public_dataset.shakersphere",
		"gimel.storage.type":"JDBC",
		"gimel.jdbc.url":"jdbc:mysql://host:3115/pcatalog?useSSL=true&requireSSL=true&verifyServerCertificate=false",
		"gimel.jdbc.driver.class":"com.mysql.jdbc.Driver"
         }
  }"""

## ---- Elastic Search DataSet ----

dataSetProperties = s"""
  {   "datasetName":"shakespere",
      "datasetType": "ELASTIC_SEARCH",
      "fields": [],
      "partitionFields": [],
      "props": {
            "datasetName":"search_logs",
            "es.port":"9200",
            "es.nodes":"http://elastic_host",
            "gimel.storage.type":"ELASTIC_SEARCH",
            "gimel.storage.version":"6",
            "gimel.es.index.partition.list":"202009,202010",
            "es.mapping.date.rich":"false",
            "es.resource":"search/log",
            "gimel.es.index.partition.isEnabled":"true",
            "gimel.es.index.partition.delimiter":"_",
            "es.read.field.exclude":"appEndTime,jobStartTime,jobEndTime",
            "es.read.field.as.array.include":"columns,logTime"
         }
  }"""

options = Map("shakespere.dataSetProperties" -> dataSetProperties)
```


```python

```


```python

```


```python
df = spark.sparkContext.textFile("gs://spark-lib/bigquery/spark-bigquery-latest_2.12.jar")
df.saveAsTextFile("/user/dmohanakumarchan/lib/spark-bigquery-latest_2.12.jar")
```


```python

```


```python

```


```python

```


```python
table = "pp-devcos-dataproc-gcs.BQ_benchmark.date_dim"
df = spark.read \
  .format("bigquery") \
  .option("table", table) \
  .load()
df.printSchema()
```


```python
val df = spark.read.format("bigquery").option("table", "pp-devcos-dataproc-gcs.BQ_benchmark.date_dim").load()
df.printSchema()
df.show(5)
df.write.parquet("gs://demc-test/pp-devcos-dataproc-gcs.BQ_benchmark.date_dim")
```


```python
class DataSet:
    def __init__(self,sparkSession):
        self.spark = sparkSession
        self.sparkSession = sparkSession
        
    def read(self,dataset,options={}):
        df = None
        dataset_type = options.get("datasetType","hive").lower()
        if (dataset_type == ("bigquery")):
            df = spark.read.format("bigquery").option("table", dataset).load()
        elif (dataset_type == ("gcs")):
            data_format = options.get("format","parquet").lower()
            if (data_format == "parquet"):
                spark.read.parquet(dataset)
            elif (data_format = "orc"):
                spark.read.orc(dataset)
            else:
                spark.sparkContext.textFile(path)
        else:
            df = spark.read.table(dataset)
        return df

```


```python
dataset = DataSet(spark)
```


```python
dataset.read("a")
```

### 
