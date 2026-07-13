FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY sentinel-domain/pom.xml sentinel-domain/pom.xml
COPY sentinel-application/pom.xml sentinel-application/pom.xml
COPY sentinel-api/pom.xml sentinel-api/pom.xml
COPY sentinel-persistence/pom.xml sentinel-persistence/pom.xml
COPY sentinel-bootstrap/pom.xml sentinel-bootstrap/pom.xml
COPY sentinel-integration-tests/pom.xml sentinel-integration-tests/pom.xml
COPY docs/api/openapi.yaml docs/api/openapi.yaml
COPY sentinel-domain/src sentinel-domain/src
COPY sentinel-application/src sentinel-application/src
COPY sentinel-api/src sentinel-api/src
COPY sentinel-persistence/src sentinel-persistence/src
COPY sentinel-bootstrap/src sentinel-bootstrap/src
RUN mvn -q -pl sentinel-bootstrap -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 sentinel
COPY --from=build /workspace/sentinel-bootstrap/target/sentinel-bootstrap-0.1.0-SNAPSHOT.jar app.jar
USER sentinel
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
