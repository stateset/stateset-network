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

STATESET_COMPATIBILITY_ZONE_URL=${STATESET_COMPATIBILITY_ZONE_URL:-http://stateset.solutions}
NETWORK_MAP_URL=${NETWORK_MAP_URL:-$STATESET_COMPATIBILITY_ZONE_URL}
DOORMAN_URL=${DOORMAN_URL:-$NETWORK_MAP_URL}
STATESET_NMS=${STATESET_NMS:-false}

TRUST_STORE_NAME="truststore.jks"

# STATESET NMS
if [[ "${STATESET_NMS}" == "true" ]]; then
  echo "we are using a stateset NMS - downloading the truststore from ${NETWORK_MAP_URL}"
  DOORMAN_URL=${NETWORK_MAP_URL}
  curl ${NETWORK_MAP_URL}/network-map/truststore --output ${CERTIFICATES_FOLDER}/${TRUST_STORE_NAME} --silent
fi