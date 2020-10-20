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
# runs stateset.jar --initial-registration

set -e
if [ ! -f ${CONFIG_FOLDER}/node.conf ]; then
    echo "/etc/stateset/node.conf not found, creating using stateset-config-generator"
    stateset-config-generator
else
    echo "/etc/stateset/node.conf exists:"
    cat ${CONFIG_FOLDER}/node.conf
fi

# Persist a copy of node.conf
cp ${CONFIG_FOLDER}/node.conf ${CERTIFICATES_FOLDER}/registration-node.conf

echo "config generated and cached - finishing"
