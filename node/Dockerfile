# Copyright 2020, Stateset.

#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at

#    http://www.apache.org/licenses/LICENSE-2.0

#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# worth reading this https://developers.redhat.com/blog/2017/04/04/openjdk-and-containers/

FROM corda/corda-corretto-4.0

ARG BUILDTIME_CORDA_VERSION=4.0

ENV CORDA_VERSION=${BUILDTIME_CORDA_VERSION}

LABEL VENDOR="Stateset" \
      MAINTAINER="dom@stateset.io"

# Copy corda jar
ADD --chown=corda:corda https://dl.bintray.com/r3/corda/net/corda/corda/${CORDA_VERSION}/corda-${CORDA_VERSION}.jar                       /opt/corda/corda.jar
COPY --chown=corda:corda . .


USER root
RUN yum -y install tar && yum clean all
RUN mv corda.jar bin/corda.jar \
 && mv run-stateset.sh bin/run-stateset \
 && mv cache-nodeInfo.sh bin/cache-nodeInfo \
 && mv stateset-config-generator.sh bin/stateset-config-generator \
 && mv initial-registration.sh bin/initial-registration \
 && mv just-gen-config.sh bin/just-gen-config \
 && mv get-truststore.sh bin/get-truststore \
 && ln -s /opt/corda/persistence /opt/corda/artemis \
 && ln -s /opt/corda /opt/stateset
USER corda


ENV JVM_ARGS="-XX:+UnlockExperimentalVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+UseG1GC -XX:NativeMemoryTracking=summary -XX:+PrintNMTStatistics -javaagent:./libs/jolokia-jvm-1.6.0-agent.jar=port=7005,host=localhost"
ENV CORDA_ARGS="--log-to-console --no-local-shell"


CMD ["run-stateset"]
