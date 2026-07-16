FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY sentinel-domain/pom.xml sentinel-domain/pom.xml
COPY sentinel-application/pom.xml sentinel-application/pom.xml
COPY sentinel-api/pom.xml sentinel-api/pom.xml
COPY sentinel-persistence/pom.xml sentinel-persistence/pom.xml
COPY sentinel-messaging/pom.xml sentinel-messaging/pom.xml
COPY sentinel-storage/pom.xml sentinel-storage/pom.xml
COPY sentinel-workflow/pom.xml sentinel-workflow/pom.xml
COPY sentinel-security/pom.xml sentinel-security/pom.xml
COPY sentinel-observability/pom.xml sentinel-observability/pom.xml
COPY sentinel-bootstrap/pom.xml sentinel-bootstrap/pom.xml
COPY sentinel-integration-tests/pom.xml sentinel-integration-tests/pom.xml
COPY docs/api/openapi.yaml docs/api/openapi.yaml
COPY sentinel-domain/src sentinel-domain/src
COPY sentinel-application/src sentinel-application/src
COPY sentinel-api/src sentinel-api/src
COPY sentinel-persistence/src sentinel-persistence/src
COPY sentinel-messaging/src sentinel-messaging/src
COPY sentinel-storage/src sentinel-storage/src
COPY sentinel-workflow/src sentinel-workflow/src
COPY sentinel-security/src sentinel-security/src
COPY sentinel-observability/src sentinel-observability/src
COPY sentinel-bootstrap/src sentinel-bootstrap/src
RUN mvn -q -pl sentinel-bootstrap -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 sentinel
COPY --from=build /workspace/sentinel-bootstrap/target/sentinel-bootstrap-0.1.0-SNAPSHOT-all.jar app.jar
USER sentinel
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
