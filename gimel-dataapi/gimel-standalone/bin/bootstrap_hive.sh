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

this_script=`basename $0`
this_dir=`dirname $0`
full_file="${this_dir}/${this_script}"

source ${GIMEL_HOME}/build/gimel_functions

write_log "Started Script --> ${full_file}"

export FLIGHTS_LOAD_DATA_SCRIPT=$GIMEL_HOME/gimel-dataapi/gimel-standalone/sql/flights_load_data.sql

write_log "copying ${FLIGHTS_LOAD_DATA_SCRIPT} to HiveServer Docker Container..."
run_cmd "docker cp $FLIGHTS_LOAD_DATA_SCRIPT hive-server:/root"

write_log "Creating Hive external tables for CSV data..."
run_cmd "docker exec -it hive-server bash -c "hive -f /root/flights_load_data.sql""

write_log "Copying Bootstrap script to HiveServer Docker Container.."
run_cmd "docker cp $FLIGHTS_LOAD_DATA_SCRIPT hive-server:/root"

write_log "Creating the Bootstrap tables for user convenience ..."
run_cmd "docker exec -it hive-server bash -c "hive -f /root/bootstrap_hive.sql""

write_log "Completed Script --> ${full_file}"
