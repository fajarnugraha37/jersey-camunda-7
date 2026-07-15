package com.sentinel.enforcement.persistence.appeal;

import com.sentinel.enforcement.application.appeal.AppealRepository;
import com.sentinel.enforcement.domain.appeal.Appeal;
import com.sentinel.enforcement.domain.appeal.AppealConflictException;
import com.sentinel.enforcement.domain.appeal.AppealDecision;
import com.sentinel.enforcement.domain.appeal.AppealDecisionOutcome;
import com.sentinel.enforcement.domain.appeal.AppealStatus;
import com.sentinel.enforcement.persistence.MyBatisRepositorySupport;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;

public final class AppealRepositoryMyBatisAdapter extends MyBatisRepositorySupport
    implements AppealRepository {
  public AppealRepositoryMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    super(sqlSessionFactory);
  }

  @Override
  public void save(Appeal appeal) {
    executeWrite(
        session -> {
          session.getMapper(AppealMyBatisMapper.class).insertAppeal(toRecord(appeal));
          return null;
        });
  }

  @Override
  public Optional<Appeal> findById(UUID appealId) {
    return executeRead(
        session ->
            Optional.ofNullable(session.getMapper(AppealMyBatisMapper.class).findById(appealId))
                .map(this::toDomain));
  }

  @Override
  public Optional<Appeal> findLatestByCaseId(UUID caseId) {
    return executeRead(
        session ->
            Optional.ofNullable(
                    session.getMapper(AppealMyBatisMapper.class).findLatestByCaseId(caseId))
                .map(this::toDomain));
  }

  @Override
  public Optional<Appeal> findActiveByDecisionId(UUID decisionId) {
    return executeRead(
        session ->
            Optional.ofNullable(
                    session.getMapper(AppealMyBatisMapper.class).findActiveByDecisionId(decisionId))
                .map(this::toDomain));
  }

  @Override
  public Optional<Appeal> findActiveByCaseId(UUID caseId) {
    return executeRead(
        session ->
            Optional.ofNullable(
                    session.getMapper(AppealMyBatisMapper.class).findActiveByCaseId(caseId))
                .map(this::toDomain));
  }

  @Override
  public Optional<AppealDecision> findDecisionByAppealId(UUID appealId) {
    return executeRead(
        session ->
            Optional.ofNullable(
                    session.getMapper(AppealMyBatisMapper.class).findDecisionByAppealId(appealId))
                .map(this::toDecisionDomain));
  }

  @Override
  public void decide(Appeal appeal, AppealDecision appealDecision) {
    executeWrite(
        session -> {
          AppealMyBatisMapper mapper = session.getMapper(AppealMyBatisMapper.class);
          int updated = mapper.decide(toRecord(appeal), appeal.version() - 1);
          if (updated != 1) {
            throw new AppealConflictException(
                "CONCURRENT_MODIFICATION", "Appeal was modified concurrently.");
          }
          mapper.insertAppealDecision(toRecord(appealDecision));
          return null;
        });
  }

  @Override
  public boolean existsActiveByCaseId(UUID caseId) {
    return executeRead(
        session -> session.getMapper(AppealMyBatisMapper.class).existsActiveByCaseId(caseId));
  }

  private AppealRecord toRecord(Appeal appeal) {
    return new AppealRecord(
        appeal.id(),
        appeal.caseId(),
        appeal.decisionId(),
        appeal.rationale(),
        appeal.supervisorOverride(),
        appeal.supervisorOverrideReason(),
        appeal.status().name(),
        appeal.submittedAt().atOffset(ZoneOffset.UTC),
        appeal.submittedBy(),
        appeal.decidedByAppealDecisionId(),
        appeal.createdAt().atOffset(ZoneOffset.UTC),
        appeal.createdBy(),
        appeal.updatedAt().atOffset(ZoneOffset.UTC),
        appeal.updatedBy(),
        appeal.version());
  }

  private AppealDecisionRecord toRecord(AppealDecision appealDecision) {
    return new AppealDecisionRecord(
        appealDecision.id(),
        appealDecision.appealId(),
        appealDecision.outcome().name(),
        appealDecision.summary(),
        appealDecision.decidedAt().atOffset(ZoneOffset.UTC),
        appealDecision.decidedBy(),
        appealDecision.createdAt().atOffset(ZoneOffset.UTC),
        appealDecision.createdBy(),
        appealDecision.version());
  }

  private Appeal toDomain(AppealRecord appealRecord) {
    return new Appeal(
        appealRecord.id(),
        appealRecord.caseId(),
        appealRecord.decisionId(),
        appealRecord.rationale(),
        appealRecord.supervisorOverride(),
        appealRecord.supervisorOverrideReason(),
        AppealStatus.valueOf(appealRecord.status()),
        appealRecord.submittedAt().toInstant(),
        appealRecord.submittedBy(),
        appealRecord.decidedByAppealDecisionId(),
        appealRecord.createdAt().toInstant(),
        appealRecord.createdBy(),
        appealRecord.updatedAt().toInstant(),
        appealRecord.updatedBy(),
        appealRecord.version());
  }

  private AppealDecision toDecisionDomain(AppealDecisionRecord appealDecisionRecord) {
    return new AppealDecision(
        appealDecisionRecord.id(),
        appealDecisionRecord.appealId(),
        AppealDecisionOutcome.valueOf(appealDecisionRecord.outcome()),
        appealDecisionRecord.summary(),
        appealDecisionRecord.decidedAt().toInstant(),
        appealDecisionRecord.decidedBy(),
        appealDecisionRecord.createdAt().toInstant(),
        appealDecisionRecord.createdBy(),
        appealDecisionRecord.version());
  }
}
