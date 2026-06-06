FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl jieqi-server -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/jieqi-server/target/jieqi-server-*-all.jar server.jar
EXPOSE 8887
ENTRYPOINT ["java", "-jar", "/app/server.jar"]

