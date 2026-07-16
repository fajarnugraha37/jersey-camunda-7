package com.sentinel.enforcement.persistence.messaging;

import com.sentinel.enforcement.application.messaging.NotificationRecord;
import com.sentinel.enforcement.application.messaging.NotificationRepository;
import com.sentinel.enforcement.persistence.MyBatisRepositorySupport;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;

public final class NotificationRepositoryMyBatisAdapter extends MyBatisRepositorySupport
    implements NotificationRepository {

  public NotificationRepositoryMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    super(sqlSessionFactory);
  }

  @Override
  public void save(NotificationRecord notificationRecord) {
    executeWrite(
        session -> {
          session
              .getMapper(MessagingMyBatisMapper.class)
              .insertNotification(
                  new NotificationData(
                      notificationRecord.id(),
                      notificationRecord.consumerName(),
                      notificationRecord.eventId(),
                      notificationRecord.caseId(),
                      notificationRecord.notificationType(),
                      notificationRecord.title(),
                      notificationRecord.body(),
                      notificationRecord.status(),
                      notificationRecord.createdAt().atOffset(ZoneOffset.UTC),
                      notificationRecord.createdBy(),
                      notificationRecord.updatedAt().atOffset(ZoneOffset.UTC),
                      notificationRecord.updatedBy(),
                      notificationRecord.version()));
          return null;
        });
  }

  @Override
  public Optional<NotificationRecord> findById(UUID notificationId) {
    return executeRead(
        session ->
            Optional.ofNullable(
                    session
                        .getMapper(MessagingMyBatisMapper.class)
                        .findNotificationById(notificationId))
                .map(this::toDomain));
  }

  @Override
  public void updateStatus(UUID notificationId, String status, String updatedBy) {
    executeWrite(
        session -> {
          session
              .getMapper(MessagingMyBatisMapper.class)
              .updateNotificationStatus(notificationId, status, updatedBy);
          return null;
        });
  }

  private NotificationRecord toDomain(NotificationData notificationData) {
    return new NotificationRecord(
        notificationData.id(),
        notificationData.consumerName(),
        notificationData.eventId(),
        notificationData.caseId(),
        notificationData.notificationType(),
        notificationData.title(),
        notificationData.body(),
        notificationData.status(),
        notificationData.createdAt().toInstant(),
        notificationData.createdBy(),
        notificationData.updatedAt().toInstant(),
        notificationData.updatedBy(),
        notificationData.version());
  }
}
