#!/usr/bin/env bash
#
#   Copyright 2020, Stateset
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

# Creates Stateset config
set -e

echo "Creating Stateset config"

# STATESET_COMPATIBILITY_ZONE_URL deprecated. Used to set NETWORKMAP_URL and DOORMAN_URL if set.
STATESET_COMPATIBILITY_ZONE_URL=${STATESET_COMPATIBILITY_ZONE_URL:-http://stateset.solutions:8080}

# Corda official environment variables. If set will be used instead of defaults
MY_LEGAL_NAME=${MY_LEGAL_NAME:-O=Stateset-$(od -x /dev/urandom | head -1 | awk '{print $7$8$9}'), OU=Stateset, L=San Francisco, C=US}
MY_PUBLIC_ADDRESS=${MY_PUBLIC_ADDRESS:-localhost}
# MY_P2P_PORT=10200 <- default set in stateset dockerfile
NETWORKMAP_URL=${NETWORKMAP_URL:-$STATESET_COMPATIBILITY_ZONE_URL}
DOORMAN_URL=${DOORMAN_URL:-$STATESET_COMPATIBILITY_ZONE_URL}
TRUST_STORE_NAME=${TRUST_STORE_NAME:-truststore.jks}
NETWORK_TRUST_PASSWORD=${NETWORK_TRUST_PASSWORD:-trustpass}
MY_EMAIL_ADDRESS=${MY_EMAIL_ADDRESS:-noreply@stateset.io}
# RPC_PASSWORD=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1) <- not used
# MY_RPC_PORT=10201 <- default set in stateset dockerfile.
# MY_RPC_ADMIN_PORT=10202 <- default set in corda dockerfile.
TLS_CERT_CRL_DIST_POINT=${TLS_CERT_CRL_DIST_POINT:-NULL}
TLS_CERT_CERL_ISSUER=${TLS_CERT_CERL_ISSUER:-NULL}


STATESET_LEGAL_NAME=${STATESET_LEGAL_NAME:-$MY_LEGAL_NAME}
STATESET_P2P_ADDRESS=${STATESET_P2P_ADDRESS:-$MY_PUBLIC_ADDRESS:$MY_P2P_PORT}
STATESET_KEY_STORE_PASSWORD=${STATESET_KEY_STORE_PASSWORD:-cordacadevpass}
STATESET_TRUST_STORE_PASSWORD=${STATESET_TRUST_STORE_PASSWORD:-$NETWORK_TRUST_PASSWORD}
STATESET_DB_USER=${STATESET_DB_USER:-sa}
STATESET_DB_PASS=${STATESET_DB_PASS:-dbpass}
STATESET_DB_DRIVER=${STATESET_DB_DRIVER:-org.postgresql.ds.PGSimpleDataSource}
STATESET_DB_DIR=${STATESET_DB_DIR:-$PERSISTENCE_FOLDER}
STATESET_DB_MAX_POOL_SIZE=${STATESET_DB_MAX_POOL_SIZE:-10}
STATESET_BRAID_PORT=${STATESET_BRAID_PORT:-8080}
STATESET_DEV_MODE=${STATESET_DEV_MODE:-true}
STATESET_DETECT_IP=${STATESET_DETECT_IP:-false}
STATESET_CACHE_NODEINFO=${STATESET_CACHE_NODEINFO:-false}
STATESET_LOG_MODE=${STATESET_LOG_MODE:-normal}
STATESET_JVM_MX=${STATESET_JVM_MX:-1536m}
STATESET_JVM_MS=${STATESET_JVM_MS:-512m}
STATESET_DB_PORT=${STATESET_DB_PORT:-9090}

#set STATESET_DB_URL
postgres_db_url="\"jdbc:postgres://${STATESET_DB_USER}:${STATESET_DB_PASSWORD}@${STATESET_DB_HOST}/postgres=${STATESET_DB_PORT}\""
STATESET_DB_URL=${STATESET_DB_URL:-$postgres_db_url}

# STATESET_LOG_CONFIG_FILE:
if [ "${STATESET_LOG_MODE}" == "json" ]; then
    STATESET_LOG_CONFIG_FILE=stateset-log4j2-json.xml
else
    STATESET_LOG_CONFIG_FILE=stateset-log4j2.xml
fi

# Create node.conf and default if variables not set
echo
echo
printenv
echo
echo
basedir=\"\${baseDirectory}\"
braidhost=${STATESET_LEGAL_NAME#*O=} && braidhost=${braidhost%%,*} && braidhost=$(echo $braidhost | sed 's/ //g')
cat > ${CONFIG_FOLDER}/node.conf <<EOL
myLegalName : "${STATESET_LEGAL_NAME}"
p2pAddress : "${STATESET_P2P_ADDRESS}"

networkServices : {
    "doormanURL" : "${DOORMAN_URL}"
    "networkMapURL" : "${NETWORKMAP_URL}"
}

tlsCertCrlDistPoint : "${TLS_CERT_CRL_DIST_POINT}"
tlsCertCrlIssuer : "${TLS_CERT_CERL_ISSUER}"

dataSourceProperties : {
    "dataSourceClassName" : "${STATESET_DB_DRIVER}"
    "dataSource.url" : "${STATESET_DB_URL}"
    "dataSource.user" : "${STATESET_DB_USER}"
    "dataSource.password" : "${STATESET_DB_PASS}"
    "maximumPoolSize" : "${STATESET_DB_MAX_POOL_SIZE}"
}

keyStorePassword : "${STATESET_KEY_STORE_PASSWORD}"
trustStorePassword : "${STATESET_TRUST_STORE_PASSWORD}"
detectPublicIp : ${STATESET_DETECT_IP}
devMode : ${STATESET_DEV_MODE}
jvmArgs : [ "-Dbraid.${braidhost}.port=${STATESET_BRAID_PORT}", "-Xms${STATESET_JVM_MS}", "-Xmx${STATESET_JVM_MX}", "-Dlog4j.configurationFile=${STATESET_LOG_CONFIG_FILE}" ]
jarDirs=[
    ${basedir}/libs
]
emailAddress : "${MY_EMAIL_ADDRESS}"
EOL



# Configure notaries
# for the moment we're dealing with two systems - later we can do this in a slightly different way
if [ "$STATESET_NOTARY" == "true" ] || [ "$STATESET_NOTARY" == "validating" ] || [ "$STATESET_NOTARY" == "non-validating" ] ; then
    NOTARY_VAL=false
    if [ "$STATESET_NOTARY" == "true" ] || [ "$STATESET_NOTARY" == "validating" ]; then
    NOTARY_VAL=true
    fi
    echo "STATESET_NOTARY set to ${STATESET_NOTARY}. Configuring node to be a notary with validating ${NOTARY_VAL}"
cat >> ${CONFIG_FOLDER}/node.conf <<EOL
notary {
    validating=${NOTARY_VAL}
}
EOL
fi

# do we want to turn on jolokia for monitoring?
if [ ! -z "$STATESET_EXPORT_JMX" ]; then
cat >> ${CONFIG_FOLDER}/node.conf <<EOL
exportJMXTo: "${STATESET_EXPORT_JMX}"
EOL
fi


echo "${CONFIG_FOLDER}/node.conf created:"
cat ${CONFIG_FOLDER}/node.conf