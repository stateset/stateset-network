FROM openjdk:8-jdk-alpine
VOLUME /tmp
ARG JAR_FILE
COPY ./build/libs/server-0.1.jar server.jar
ENTRYPOINT ["java","-jar","/server.jar"]

