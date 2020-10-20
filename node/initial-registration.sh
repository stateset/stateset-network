#!/usr/bin/env bash
#
#   Copyright 2020, Stateset.
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

set -e
if [ ! -f ${CONFIG_FOLDER}/node.conf ]; then
    echo "/etc/stateset/node.conf not found, creating using stateset-config-generator"
    stateset-config-generator
else
    echo "/etc/stateset/node.conf exists:"
    cat ${CONFIG_FOLDER}/node.conf
fi

TRUST_STORE_NAME=${TRUST_STORE_NAME:-network-root-truststore.jks}

# Stateset
if grep -xq '    "doormanURL" : "http://stateset.solutions:8080"' ${CONFIG_FOLDER}/node.conf; then
    TRUST_STORE_NAME="stateset-edge-truststore.jks"
    NETWORK_TRUST_PASSWORD="trustpass"
    curl http://stateset.solutions:8080/network-map/truststore --output ${CERTIFICATES_FOLDER}/${TRUST_STORE_NAME} --silent
fi

# Stateset
if grep -xq '    "doormanURL" : "http://stateset.solutions:8080"' ${CONFIG_FOLDER}/node.conf; then
    TRUST_STORE_NAME="stateset-test-truststore.jks"
    NETWORK_TRUST_PASSWORD="trustpass"
    curl http://stateset.solutions:8080/network-map/truststore --output ${CERTIFICATES_FOLDER}/${TRUST_STORE_NAME} --silent
fi

if [[ ! -f ${CERTIFICATES_FOLDER}/${TRUST_STORE_NAME} ]]; then
    echo "Network Trust Root file not found at ${CERTIFICATES_FOLDER}/${TRUST_STORE_NAME}"
    exit
fi

java -Djava.security.egd=file:/dev/./urandom -Dcapsule.jvm.args="${JVM_ARGS}" -jar /opt/stateset/bin/corda.jar \
        --initial-registration \
        --config-file ${CONFIG_FOLDER}/node.conf \
        --network-root-truststore-password=${NETWORK_TRUST_PASSWORD} \
        --network-root-truststore=${CERTIFICATES_FOLDER}/${TRUST_STORE_NAME} \
        --log-to-console --no-local-shell
echo "Succesfully registered with ${DOORMAN_URL}"
