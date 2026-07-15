package com.sentinel.enforcement.persistence.messaging;

import com.sentinel.enforcement.application.messaging.InboxEvent;
import com.sentinel.enforcement.application.messaging.InboxRepository;
import com.sentinel.enforcement.persistence.MyBatisRepositorySupport;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;

public final class InboxRepositoryMyBatisAdapter extends MyBatisRepositorySupport
    implements InboxRepository {

  public InboxRepositoryMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    super(sqlSessionFactory);
  }

  @Override
  public boolean beginProcessing(InboxEvent inboxEvent) {
    return executeWrite(
            session ->
                session
                    .getMapper(MessagingMyBatisMapper.class)
                    .insertInboxEventIfAbsent(
                        new InboxEventData(
                            inboxEvent.id(),
                            inboxEvent.consumerName(),
                            inboxEvent.eventId(),
                            inboxEvent.topic(),
                            inboxEvent.createdAt().atOffset(ZoneOffset.UTC),
                            inboxEvent.createdBy(),
                            inboxEvent.processedAt() == null
                                ? null
                                : inboxEvent.processedAt().atOffset(ZoneOffset.UTC),
                            inboxEvent.resultReference(),
                            inboxEvent.version())))
        == 1;
  }

  @Override
  public void completeProcessing(
      String consumerName,
      UUID eventId,
      Instant processedAt,
      String resultReference,
      String updatedBy) {
    executeWrite(
        session -> {
          session
              .getMapper(MessagingMyBatisMapper.class)
              .completeInboxEvent(
                  consumerName, eventId, processedAt.atOffset(ZoneOffset.UTC), resultReference);
          return null;
        });
  }
}
