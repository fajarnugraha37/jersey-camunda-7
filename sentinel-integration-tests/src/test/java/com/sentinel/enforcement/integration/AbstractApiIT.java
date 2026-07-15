package com.sentinel.enforcement.integration;

import com.sentinel.enforcement.api.generated.model.AppealResponse;
import com.sentinel.enforcement.api.generated.model.CreateAppealRequest;
import com.sentinel.enforcement.api.generated.model.CreateDecisionRequest;
import com.sentinel.enforcement.api.generated.model.CreateRecommendationRequest;
import com.sentinel.enforcement.api.generated.model.CreateReportRequest;
import com.sentinel.enforcement.api.generated.model.DecideAppealRequest;
import com.sentinel.enforcement.api.generated.model.DecisionResponse;
import com.sentinel.enforcement.api.generated.model.RecommendationResponse;
import com.sentinel.enforcement.api.generated.model.ReportResponse;
import com.sentinel.enforcement.api.generated.model.ReviewRecommendationRequest;
import com.sentinel.enforcement.api.generated.model.TriageReportRequest;
import com.sentinel.enforcement.api.json.ObjectMapperContextResolver;
import com.sentinel.enforcement.bootstrap.AppConfiguration;
import com.sentinel.enforcement.bootstrap.ApplicationRuntime;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

abstract class AbstractApiIT {
  protected static final String REALM_NAME = "sentinel";
  protected static final String CLIENT_ID = "sentinel-api";
  protected static final String DEFAULT_PASSWORD = "sentinel";

  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:18.3-alpine");
  protected static final GenericContainer<?> KEYCLOAK =
      new GenericContainer<>("quay.io/keycloak/keycloak:26.6")
          .withExposedPorts(8080, 9000)
          .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
          .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
          .withEnv("KC_HEALTH_ENABLED", "true")
          .withCommand("start-dev", "--http-port=8080", "--import-realm")
          .withClasspathResourceMapping(
              "keycloak/sentinel-realm.json",
              "/opt/keycloak/data/import/sentinel-realm.json",
              BindMode.READ_ONLY)
          .waitingFor(
              Wait.forHttp("/health/ready")
                  .forPort(9000)
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(3)));
  protected static final GenericContainer<?> MINIO =
      new GenericContainer<>("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z")
          .withExposedPorts(9000, 9001)
          .withEnv("MINIO_ROOT_USER", "sentinel")
          .withEnv("MINIO_ROOT_PASSWORD", "sentinel-secret")
          .withCommand("server", "/data", "--console-address", ":9001")
          .waitingFor(
              Wait.forHttp("/minio/health/ready")
                  .forPort(9000)
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(2)));
  protected static final FixedPortKafkaContainer KAFKA = new FixedPortKafkaContainer();

  protected static ApplicationRuntime applicationRuntime;
  protected static Client client;
  protected static AppConfiguration testConfiguration;

  @BeforeAll
  static void setUp() {
    if (!POSTGRES.isRunning()) {
      POSTGRES.start();
    }
    if (!KEYCLOAK.isRunning()) {
      KEYCLOAK.start();
    }
    if (!MINIO.isRunning()) {
      MINIO.start();
    }
    if (!KAFKA.isRunning()) {
      KAFKA.start();
    }
    if (testConfiguration == null) {
      testConfiguration =
          AppConfiguration.fromEnvironment(
              Map.ofEntries(
                  Map.entry("HTTP_PORT", "0"),
                  Map.entry("DB_URL", POSTGRES.getJdbcUrl()),
                  Map.entry("DB_USERNAME", POSTGRES.getUsername()),
                  Map.entry("DB_PASSWORD", POSTGRES.getPassword()),
                  Map.entry("KAFKA_BOOTSTRAP_SERVERS", kafkaBootstrapServers()),
                  Map.entry("APP_INSTANCE_ID", "integration-tests"),
                  Map.entry("OUTBOX_POLL_INTERVAL", "PT1S"),
                  Map.entry("OUTBOX_LEASE_DURATION", "PT10S"),
                  Map.entry("OUTBOX_BATCH_SIZE", "10"),
                  Map.entry("NOTIFICATION_CONSUMER_GROUP_ID", "sentinel-it"),
                  Map.entry("NOTIFICATION_MAX_RETRIES", "2"),
                  Map.entry("MINIO_ENDPOINT", minioEndpoint()),
                  Map.entry("MINIO_ACCESS_KEY", "sentinel"),
                  Map.entry("MINIO_SECRET_KEY", "sentinel-secret"),
                  Map.entry("MINIO_EVIDENCE_BUCKET", "sentinel-evidence"),
                  Map.entry("EVIDENCE_UPLOAD_URL_TTL", "PT15M"),
                  Map.entry("EVIDENCE_DOWNLOAD_URL_TTL", "PT10M"),
                  Map.entry("KEYCLOAK_ISSUER", keycloakIssuer()),
                  Map.entry("KEYCLOAK_AUDIENCE", CLIENT_ID),
                  Map.entry("KEYCLOAK_JWKS_URL", keycloakJwksUrl()),
                  Map.entry("WORKFLOW_INVESTIGATION_ESCALATION_DURATION", "PT2S")));
    }
    if (applicationRuntime == null) {
      ApplicationRuntime.migrate(testConfiguration);
      applicationRuntime = ApplicationRuntime.start(testConfiguration);
    }
    if (client == null) {
      client =
          ClientBuilder.newBuilder()
              .register(JacksonFeature.class)
              .register(ObjectMapperContextResolver.class)
              .build();
    }
  }

  @AfterAll
  static void tearDown() {
    if (client != null) {
      client.close();
      client = null;
    }
    if (applicationRuntime != null) {
      applicationRuntime.close();
      applicationRuntime = null;
    }
    testConfiguration = null;
    KAFKA.stop();
    MINIO.stop();
    KEYCLOAK.stop();
    POSTGRES.stop();
  }

  protected static ReportResponse createReport(String accessToken, String jurisdictionCode) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/reports")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(
            Entity.entity(
                new CreateReportRequest()
                    .title("Improper gift disclosure")
                    .description("Potential violation involving unreported gifts.")
                    .jurisdictionCode(jurisdictionCode)
                    .reporterName("Analyst A"),
                MediaType.APPLICATION_JSON_TYPE),
            ReportResponse.class);
  }

  protected static ReportResponse createTriagedReport(
      String intakeAccessToken, String triageAccessToken, String jurisdictionCode) {
    ReportResponse report = createReport(intakeAccessToken, jurisdictionCode);
    return triageReport(
        triageAccessToken,
        report.getId(),
        report.getVersion(),
        "Report accepted for case creation.");
  }

  protected static ReportResponse triageReport(
      String accessToken, UUID reportId, long expectedVersion, String reason) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/reports/" + reportId + "/triage")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(
            Entity.entity(
                new TriageReportRequest().expectedVersion(expectedVersion).reason(reason),
                MediaType.APPLICATION_JSON_TYPE),
            ReportResponse.class);
  }

  protected static RecommendationResponse createRecommendation(
      String accessToken,
      UUID caseId,
      String title,
      String summary,
      String proposedDecision,
      String proposedSanction) {
    try (Response response =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/cases/" + caseId + "/recommendations")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .post(
                Entity.entity(
                    new CreateRecommendationRequest()
                        .title(title)
                        .summary(summary)
                        .proposedDecision(proposedDecision)
                        .proposedSanction(proposedSanction),
                    MediaType.APPLICATION_JSON_TYPE))) {
      if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
        String body = response.hasEntity() ? response.readEntity(String.class) : "";
        throw new IllegalStateException(
            "Create recommendation failed with status "
                + response.getStatus()
                + " and body: "
                + body);
      }
      return response.readEntity(RecommendationResponse.class);
    }
  }

  protected static RecommendationResponse submitRecommendation(
      String accessToken, UUID recommendationId) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/recommendations/" + recommendationId + "/submit")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE), RecommendationResponse.class);
  }

  protected static RecommendationResponse approveRecommendation(
      String accessToken, UUID recommendationId, String reviewSummary) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/recommendations/" + recommendationId + "/reviews")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(
            Entity.entity(
                new ReviewRecommendationRequest().reviewSummary(reviewSummary),
                MediaType.APPLICATION_JSON_TYPE),
            RecommendationResponse.class);
  }

  protected static DecisionResponse createDecision(
      String accessToken,
      UUID caseId,
      String title,
      String summary,
      boolean violationProven,
      String sanctionSummary,
      String obligationTitle,
      String obligationDetails,
      LocalDate obligationDueDate,
      LocalDate appealDeadline) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/cases/" + caseId + "/decisions")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(
            Entity.entity(
                new CreateDecisionRequest()
                    .title(title)
                    .summary(summary)
                    .violationProven(violationProven)
                    .sanctionSummary(sanctionSummary)
                    .obligationTitle(obligationTitle)
                    .obligationDetails(obligationDetails)
                    .obligationDueDate(obligationDueDate)
                    .appealDeadline(appealDeadline),
                MediaType.APPLICATION_JSON_TYPE),
            DecisionResponse.class);
  }

  protected static DecisionResponse approveDecision(String accessToken, UUID decisionId) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/decisions/" + decisionId + "/approve")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE), DecisionResponse.class);
  }

  protected static DecisionResponse publishDecision(String accessToken, UUID decisionId) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/decisions/" + decisionId + "/publish")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE), DecisionResponse.class);
  }

  protected static AppealResponse createAppeal(
      String accessToken,
      UUID decisionId,
      String rationale,
      OffsetDateTime submittedAt,
      Boolean supervisorOverride,
      String supervisorOverrideReason) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/decisions/" + decisionId + "/appeals")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(
            Entity.entity(
                new CreateAppealRequest()
                    .rationale(rationale)
                    .submittedAt(submittedAt)
                    .supervisorOverride(supervisorOverride)
                    .supervisorOverrideReason(supervisorOverrideReason),
                MediaType.APPLICATION_JSON_TYPE),
            AppealResponse.class);
  }

  protected static AppealResponse decideAppeal(
      String accessToken,
      UUID appealId,
      com.sentinel.enforcement.api.generated.model.AppealDecisionOutcomeValue outcome,
      String summary) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/appeals/" + appealId + "/decisions")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(
            Entity.entity(
                new DecideAppealRequest().outcome(outcome).summary(summary),
                MediaType.APPLICATION_JSON_TYPE),
            AppealResponse.class);
  }

  protected static String accessToken(String username) {
    Response response =
        client
            .target(keycloakBaseUrl())
            .path("/realms/" + REALM_NAME + "/protocol/openid-connect/token")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .post(
                Entity.entity(
                    new Form()
                        .param("client_id", CLIENT_ID)
                        .param("grant_type", "password")
                        .param("username", username)
                        .param("password", DEFAULT_PASSWORD),
                    MediaType.APPLICATION_FORM_URLENCODED_TYPE));

    Map<String, Object> payload = response.readEntity(new GenericType<>() {});
    Object accessToken = payload.get("access_token");
    if (response.getStatus() != 200 || accessToken == null) {
      throw new IllegalStateException(
          "Failed to obtain access token for user "
              + username
              + ": status="
              + response.getStatus()
              + ", payload="
              + payload);
    }
    return accessToken.toString();
  }

  protected static long countByCaseId(String tableName, UUID caseId) {
    String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE case_id = ?";
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, caseId);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to count rows in " + tableName, exception);
    }
  }

  protected static long countAuditEventsByType(UUID caseId, String eventType) {
    String sql = "SELECT COUNT(*) FROM audit_event WHERE case_id = ? AND event_type = ?";
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, caseId);
      statement.setString(2, eventType);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to count audit events for case " + caseId, exception);
    }
  }

  protected static String workflowStatus(UUID caseId) {
    String sql = "SELECT status FROM workflow_instance WHERE case_id = ?";
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, caseId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return null;
        }
        return resultSet.getString(1);
      }
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Failed to load workflow status for case " + caseId, exception);
    }
  }

  protected static int executeUpdate(String sql, Object... parameters) {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bindParameters(statement, parameters);
      return statement.executeUpdate();
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to execute SQL update.", exception);
    }
  }

  protected static String queryForString(String sql, Object... parameters) {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bindParameters(statement, parameters);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return null;
        }
        return resultSet.getString(1);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to execute SQL query.", exception);
    }
  }

  private static void bindParameters(PreparedStatement statement, Object... parameters)
      throws Exception {
    for (int index = 0; index < parameters.length; index++) {
      statement.setObject(index + 1, parameters[index]);
    }
  }

  protected static String keycloakBaseUrl() {
    return "http://127.0.0.1:" + KEYCLOAK.getMappedPort(8080);
  }

  private static String keycloakIssuer() {
    return keycloakBaseUrl() + "/realms/" + REALM_NAME;
  }

  private static String keycloakJwksUrl() {
    return keycloakIssuer() + "/protocol/openid-connect/certs";
  }

  private static String minioEndpoint() {
    return "http://127.0.0.1:" + MINIO.getMappedPort(9000);
  }

  protected static long queryForLong(String sql, Object... parameters) {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bindParameters(statement, parameters);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return 0L;
        }
        return resultSet.getLong(1);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to execute SQL query.", exception);
    }
  }

  protected static void produceRawEvent(String topic, String key, String payload) {
    try (KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProducerProperties())) {
      producer.send(new ProducerRecord<>(topic, key, payload)).get();
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to produce Kafka test event.", exception);
    }
  }

  protected static String kafkaBootstrapServers() {
    return "127.0.0.1:29092";
  }

  private static Map<String, Object> kafkaProducerProperties() {
    return Map.of(
        "bootstrap.servers", kafkaBootstrapServers(),
        "key.serializer", StringSerializer.class.getName(),
        "value.serializer", StringSerializer.class.getName(),
        "acks", "all");
  }

  protected static final class FixedPortKafkaContainer
      extends GenericContainer<FixedPortKafkaContainer> {
    private static final int HOST_PORT = 29092;

    FixedPortKafkaContainer() {
      super("confluentinc/cp-kafka:7.8.1");
      addFixedExposedPort(HOST_PORT, HOST_PORT);
      withEnv("CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk");
      withEnv("KAFKA_NODE_ID", "1");
      withEnv("KAFKA_PROCESS_ROLES", "broker,controller");
      withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093");
      withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://127.0.0.1:29092");
      withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT");
      withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT");
      withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER");
      withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@127.0.0.1:9093");
      withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1");
      withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
      withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
      withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0");
      withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");
      waitingFor(
          Wait.forLogMessage(".*Transitioning from RECOVERY to RUNNING.*", 1)
              .withStartupTimeout(Duration.ofMinutes(3)));
    }
  }
}
