package com.sentinel.enforcement.persistence.report;

import com.sentinel.enforcement.application.report.ReportRepository;
import com.sentinel.enforcement.domain.report.Report;
import com.sentinel.enforcement.domain.report.ReportConflictException;
import com.sentinel.enforcement.domain.report.ReportStatus;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public final class ReportRepositoryMyBatisAdapter implements ReportRepository {
  private final SqlSessionFactory sqlSessionFactory;

  public ReportRepositoryMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  @Override
  public void save(Report report) {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      ReportMyBatisMapper mapper = session.getMapper(ReportMyBatisMapper.class);
      mapper.insert(toRecord(report));
      session.commit();
    }
  }

  @Override
  public void update(Report report) {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      ReportMyBatisMapper mapper = session.getMapper(ReportMyBatisMapper.class);
      int updated = mapper.update(toRecord(report), report.version() - 1);
      if (updated != 1) {
        throw new ReportConflictException(
            "CONCURRENT_MODIFICATION",
            "Report " + report.id() + " was modified concurrently before the update completed.");
      }
      session.commit();
    }
  }

  @Override
  public Optional<Report> findById(UUID reportId) {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      ReportMyBatisMapper mapper = session.getMapper(ReportMyBatisMapper.class);
      return Optional.ofNullable(mapper.findById(reportId)).map(this::toDomain);
    }
  }

  private ReportRecord toRecord(Report report) {
    return new ReportRecord(
        report.id(),
        report.title(),
        report.description(),
        report.jurisdictionCode(),
        report.reporterName(),
        report.status().name(),
        report.createdAt().atOffset(ZoneOffset.UTC),
        report.createdBy(),
        report.updatedAt().atOffset(ZoneOffset.UTC),
        report.updatedBy(),
        report.version());
  }

  private Report toDomain(ReportRecord reportRecord) {
    return new Report(
        reportRecord.id(),
        reportRecord.title(),
        reportRecord.description(),
        reportRecord.jurisdictionCode(),
        reportRecord.reporterName(),
        ReportStatus.valueOf(reportRecord.status()),
        reportRecord.createdAt().toInstant(),
        reportRecord.createdBy(),
        reportRecord.updatedAt().toInstant(),
        reportRecord.updatedBy(),
        reportRecord.version());
  }
}
