package com.sentinel.enforcement.persistence.decision;

import com.sentinel.enforcement.application.decision.DecisionRepository;
import com.sentinel.enforcement.domain.decision.Decision;
import com.sentinel.enforcement.domain.decision.DecisionConflictException;
import com.sentinel.enforcement.domain.decision.DecisionStatus;
import com.sentinel.enforcement.domain.decision.DecisionVersion;
import com.sentinel.enforcement.domain.sanction.Sanction;
import com.sentinel.enforcement.domain.sanction.SanctionObligation;
import com.sentinel.enforcement.domain.sanction.SanctionObligationStatus;
import com.sentinel.enforcement.domain.sanction.SanctionStatus;
import com.sentinel.enforcement.persistence.MyBatisRepositorySupport;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;

public final class DecisionRepositoryMyBatisAdapter extends MyBatisRepositorySupport
    implements DecisionRepository {
  public DecisionRepositoryMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    super(sqlSessionFactory);
  }

  @Override
  public void save(Decision decision) {
    executeWrite(
        session -> {
          session.getMapper(DecisionMyBatisMapper.class).insertDecision(toRecord(decision));
          return null;
        });
  }

  @Override
  public Optional<Decision> findById(UUID decisionId) {
    return executeRead(
        session ->
            Optional.ofNullable(session.getMapper(DecisionMyBatisMapper.class).findById(decisionId))
                .map(this::toDomain));
  }

  @Override
  public Optional<Decision> findByCaseId(UUID caseId) {
    return executeRead(
        session ->
            Optional.ofNullable(session.getMapper(DecisionMyBatisMapper.class).findByCaseId(caseId))
                .map(this::toDomain));
  }

  @Override
  public void approve(Decision decision) {
    executeWrite(
        session -> {
          int updated =
              session
                  .getMapper(DecisionMyBatisMapper.class)
                  .approve(toRecord(decision), decision.version() - 1);
          if (updated != 1) {
            throw new DecisionConflictException(
                "CONCURRENT_MODIFICATION", "Decision was modified concurrently.");
          }
          return null;
        });
  }

  @Override
  public void publish(
      Decision decision,
      DecisionVersion decisionVersion,
      Sanction sanction,
      SanctionObligation sanctionObligation) {
    executeWrite(
        session -> {
          DecisionMyBatisMapper mapper = session.getMapper(DecisionMyBatisMapper.class);
          int updated = mapper.publish(toRecord(decision), decision.version() - 1);
          if (updated != 1) {
            throw new DecisionConflictException(
                "CONCURRENT_MODIFICATION", "Decision was modified concurrently.");
          }
          mapper.insertDecisionVersion(toRecord(decisionVersion));
          if (sanction != null && sanctionObligation != null) {
            mapper.insertSanction(toRecord(sanction));
            mapper.insertSanctionObligation(toRecord(sanctionObligation));
          }
          return null;
        });
  }

  @Override
  public boolean existsPublishedForCase(UUID caseId) {
    return executeRead(
        session -> session.getMapper(DecisionMyBatisMapper.class).existsPublishedForCase(caseId));
  }

  private DecisionRecord toRecord(Decision decision) {
    return new DecisionRecord(
        decision.id(),
        decision.caseId(),
        decision.recommendationId(),
        decision.title(),
        decision.summary(),
        decision.violationProven(),
        decision.sanctionSummary(),
        decision.obligationTitle(),
        decision.obligationDetails(),
        decision.obligationDueDate(),
        decision.appealDeadline(),
        decision.status().name(),
        decision.approvedAt() == null ? null : decision.approvedAt().atOffset(ZoneOffset.UTC),
        decision.approvedBy(),
        decision.publishedAt() == null ? null : decision.publishedAt().atOffset(ZoneOffset.UTC),
        decision.publishedBy(),
        decision.createdAt().atOffset(ZoneOffset.UTC),
        decision.createdBy(),
        decision.updatedAt().atOffset(ZoneOffset.UTC),
        decision.updatedBy(),
        decision.version());
  }

  private DecisionVersionRecord toRecord(DecisionVersion decisionVersion) {
    return new DecisionVersionRecord(
        decisionVersion.id(),
        decisionVersion.decisionId(),
        decisionVersion.versionNumber(),
        decisionVersion.title(),
        decisionVersion.summary(),
        decisionVersion.violationProven(),
        decisionVersion.sanctionSummary(),
        decisionVersion.obligationTitle(),
        decisionVersion.obligationDetails(),
        decisionVersion.obligationDueDate(),
        decisionVersion.appealDeadline(),
        decisionVersion.publishedAt().atOffset(ZoneOffset.UTC),
        decisionVersion.publishedBy(),
        decisionVersion.createdAt().atOffset(ZoneOffset.UTC),
        decisionVersion.createdBy());
  }

  private SanctionRecord toRecord(Sanction sanction) {
    return new SanctionRecord(
        sanction.id(),
        sanction.caseId(),
        sanction.decisionId(),
        sanction.summary(),
        sanction.status().name(),
        sanction.createdAt().atOffset(ZoneOffset.UTC),
        sanction.createdBy(),
        sanction.updatedAt().atOffset(ZoneOffset.UTC),
        sanction.updatedBy(),
        sanction.version());
  }

  private SanctionObligationRecord toRecord(SanctionObligation sanctionObligation) {
    return new SanctionObligationRecord(
        sanctionObligation.id(),
        sanctionObligation.sanctionId(),
        sanctionObligation.title(),
        sanctionObligation.details(),
        sanctionObligation.dueDate(),
        sanctionObligation.status().name(),
        sanctionObligation.createdAt().atOffset(ZoneOffset.UTC),
        sanctionObligation.createdBy(),
        sanctionObligation.updatedAt().atOffset(ZoneOffset.UTC),
        sanctionObligation.updatedBy(),
        sanctionObligation.version());
  }

  private Decision toDomain(DecisionRecord decisionRecord) {
    return new Decision(
        decisionRecord.id(),
        decisionRecord.caseId(),
        decisionRecord.recommendationId(),
        decisionRecord.title(),
        decisionRecord.summary(),
        decisionRecord.violationProven(),
        decisionRecord.sanctionSummary(),
        decisionRecord.obligationTitle(),
        decisionRecord.obligationDetails(),
        decisionRecord.obligationDueDate(),
        decisionRecord.appealDeadline(),
        DecisionStatus.valueOf(decisionRecord.status()),
        decisionRecord.approvedAt() == null ? null : decisionRecord.approvedAt().toInstant(),
        decisionRecord.approvedBy(),
        decisionRecord.publishedAt() == null ? null : decisionRecord.publishedAt().toInstant(),
        decisionRecord.publishedBy(),
        decisionRecord.createdAt().toInstant(),
        decisionRecord.createdBy(),
        decisionRecord.updatedAt().toInstant(),
        decisionRecord.updatedBy(),
        decisionRecord.version());
  }
}
