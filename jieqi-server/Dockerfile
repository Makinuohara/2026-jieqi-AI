FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY jieqi-model/pom.xml jieqi-model/pom.xml
COPY jieqi-rules/pom.xml jieqi-rules/pom.xml
COPY jieqi-protocol/pom.xml jieqi-protocol/pom.xml
COPY jieqi-server/pom.xml jieqi-server/pom.xml
RUN ./mvnw -pl jieqi-server -am -DskipTests dependency:go-offline

COPY jieqi-model/src jieqi-model/src
COPY jieqi-rules/src jieqi-rules/src
COPY jieqi-protocol/src jieqi-protocol/src
COPY jieqi-server/src jieqi-server/src
RUN ./mvnw -pl jieqi-server -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/jieqi-server/target/jieqi-server-*-all.jar /app/server.jar

ENV JIEQI_SERVER_PORT=8887
EXPOSE 8887

ENTRYPOINT ["sh", "-c", "java -jar /app/server.jar ${JIEQI_SERVER_PORT}"]
