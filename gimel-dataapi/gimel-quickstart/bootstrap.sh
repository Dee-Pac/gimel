#!/usr/bin/env bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

sh $GIMEL_HOME/quickstart/env
source $GIMEL_HOME/build/gimel_functions

export this_file=${0}
export this_script=$(basename $this_file)

#----------------------------------- Starting Containers -------------------------------------------#

write_log "Starting Docker Containers ...."

cd ${standalone_dir}/docker
export storages_list=$1
if [ "$storages_list" == "all" ] || [ -z "$storages_list" ]; then
  run_cmd "docker-compose up -d"
else
  export storages=$(printf '%s\n' "$storages_list" | tr ',' ' ')
  run_cmd "docker-compose up -d $storages namenode datanode spark-master spark-worker-1"
fi

#----------------------------------- Starting hive metastore ----------------------------------------#

#this sleep is required to allow time for DBs to start though the container is started.
sleep 5s
run_cmd "docker-compose up -d hive-metastore"

#check_error $? 999 "Bootstrap Dockers - failure"

sleep 5s

#-------------------------------------Unzip flights data ------------------------------------------------#

write_log "unzipping flights data ..."

cd $GIMEL_HOME/gimel-dataapi/gimel-quickstart/
if [ ! -d "flights" ]; then
  run_cmd "unzip flights.zip"
else 
  write_log "Looks like Flights Data already exists.. Skipping Unzip !"
fi


#-------------------------------------Copy the flights data to Docker images-------------------------------#

write_log "Attempting to Turn Off Safe Mode on Name Node..."
run_cmd "docker exec -it namenode hadoop dfsadmin -safemode leave"
run_cmd "docker cp flights namenode:/root"
run_cmd "docker exec -it namenode hadoop fs -rm -r -f /flights"
run_cmd "docker exec -it namenode hadoop fs -put /root/flights /"


#-------------------------------------Bootstraping the Physical Storages---------------------------------#

write_log "Bootstraping HBase tables"
run_cmd "sh ${standalone_dir}/bin/bootstrap_hbase.sh" ignore_errors

write_log "Bootstraping Kafka topic"
run_cmd "sh ${standalone_dir}/bin/bootstrap_kafka.sh" ignore_errors

sleep 5s

write_log "ALL STORAGE CONTAINERS - LAUNCHED"

#-----------------------------------------Spark Shell--------------------------------------------------#

write_log "Starting spark container..."

run_cmd "docker cp $final_jar spark-master:/root/"
run_cmd "docker cp hive-server:/opt/hive/conf/hive-site.xml $GIMEL_HOME/tmp/hive-site.xml"
run_cmd "docker cp $GIMEL_HOME/tmp/hive-site.xml spark-master:/spark/conf/"
run_cmd "docker cp hbase-master:/opt/hbase-1.2.6/conf/hbase-site.xml $GIMEL_HOME/tmp/hbase-site.xml"
run_cmd "docker cp $GIMEL_HOME/tmp/hbase-site.xml spark-master:/spark/conf/"
run_cmd "docker exec -it spark-master bash -c "export USER=$USER; export SPARK_HOME=/spark/; /spark/bin/spark-shell --jars /root/$gimel_jar_name""


# send_email "${this_script} : Gimel Docker Up - Success"

write_log "SUCCESS !!!"
