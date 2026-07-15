package com.sentinel.enforcement.persistence.messaging;

import com.sentinel.enforcement.application.messaging.NotificationRecord;
import com.sentinel.enforcement.application.messaging.NotificationRepository;
import com.sentinel.enforcement.persistence.MyBatisRepositorySupport;
import java.time.ZoneOffset;
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
}
