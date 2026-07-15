package com.sentinel.enforcement.persistence.decision;

import com.sentinel.enforcement.application.sanction.SanctionRepository;
import com.sentinel.enforcement.domain.decision.DecisionConflictException;
import com.sentinel.enforcement.domain.sanction.Sanction;
import com.sentinel.enforcement.domain.sanction.SanctionObligation;
import com.sentinel.enforcement.domain.sanction.SanctionObligationStatus;
import com.sentinel.enforcement.domain.sanction.SanctionStatus;
import com.sentinel.enforcement.persistence.MyBatisRepositorySupport;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;

public final class SanctionRepositoryMyBatisAdapter extends MyBatisRepositorySupport
    implements SanctionRepository {
  public SanctionRepositoryMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    super(sqlSessionFactory);
  }

  @Override
  public Optional<Sanction> findByDecisionId(UUID decisionId) {
    return executeRead(
        session ->
            Optional.ofNullable(
                    session
                        .getMapper(DecisionMyBatisMapper.class)
                        .findSanctionByDecisionId(decisionId))
                .map(this::toSanctionDomain));
  }

  @Override
  public Optional<Sanction> findByCaseId(UUID caseId) {
    return executeRead(
        session ->
            Optional.ofNullable(
                    session.getMapper(DecisionMyBatisMapper.class).findSanctionByCaseId(caseId))
                .map(this::toSanctionDomain));
  }

  @Override
  public long countActiveObligationsForCase(UUID caseId) {
    return executeRead(
        session ->
            session.getMapper(DecisionMyBatisMapper.class).countActiveObligationsForCase(caseId));
  }

  @Override
  public void cancelSanctionAndObligation(
      Sanction sanction, SanctionObligation sanctionObligation, String updatedBy) {
    executeWrite(
        session -> {
          DecisionMyBatisMapper mapper = session.getMapper(DecisionMyBatisMapper.class);
          int updatedSanction = mapper.cancelSanction(toRecord(sanction), sanction.version() - 1);
          int updatedObligation =
              mapper.cancelSanctionObligation(
                  toRecord(sanctionObligation), sanctionObligation.version() - 1);
          if (updatedSanction != 1 || updatedObligation != 1) {
            throw new DecisionConflictException(
                "CONCURRENT_MODIFICATION", "Sanction or obligation was modified concurrently.");
          }
          return null;
        });
  }

  @Override
  public Optional<SanctionObligation> findActiveObligationBySanctionId(UUID sanctionId) {
    return executeRead(
        session ->
            Optional.ofNullable(
                    session
                        .getMapper(DecisionMyBatisMapper.class)
                        .findActiveObligationBySanctionId(sanctionId))
                .map(this::toObligationDomain));
  }

  private Sanction toSanctionDomain(SanctionRecord sanctionRecord) {
    return new Sanction(
        sanctionRecord.id(),
        sanctionRecord.caseId(),
        sanctionRecord.decisionId(),
        sanctionRecord.summary(),
        SanctionStatus.valueOf(sanctionRecord.status()),
        sanctionRecord.createdAt().toInstant(),
        sanctionRecord.createdBy(),
        sanctionRecord.updatedAt().toInstant(),
        sanctionRecord.updatedBy(),
        sanctionRecord.version());
  }

  private SanctionObligation toObligationDomain(SanctionObligationRecord sanctionObligationRecord) {
    return new SanctionObligation(
        sanctionObligationRecord.id(),
        sanctionObligationRecord.sanctionId(),
        sanctionObligationRecord.title(),
        sanctionObligationRecord.details(),
        sanctionObligationRecord.dueDate(),
        SanctionObligationStatus.valueOf(sanctionObligationRecord.status()),
        sanctionObligationRecord.createdAt().toInstant(),
        sanctionObligationRecord.createdBy(),
        sanctionObligationRecord.updatedAt().toInstant(),
        sanctionObligationRecord.updatedBy(),
        sanctionObligationRecord.version());
  }

  private SanctionRecord toRecord(Sanction sanction) {
    return new SanctionRecord(
        sanction.id(),
        sanction.caseId(),
        sanction.decisionId(),
        sanction.summary(),
        sanction.status().name(),
        sanction.createdAt().atOffset(java.time.ZoneOffset.UTC),
        sanction.createdBy(),
        sanction.updatedAt().atOffset(java.time.ZoneOffset.UTC),
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
        sanctionObligation.createdAt().atOffset(java.time.ZoneOffset.UTC),
        sanctionObligation.createdBy(),
        sanctionObligation.updatedAt().atOffset(java.time.ZoneOffset.UTC),
        sanctionObligation.updatedBy(),
        sanctionObligation.version());
  }
}
