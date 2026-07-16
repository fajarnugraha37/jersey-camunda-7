package com.sentinel.enforcement.application.messaging;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

  void save(NotificationRecord notificationRecord);

  Optional<NotificationRecord> findById(UUID notificationId);

  void updateStatus(UUID notificationId, String status, String updatedBy);
}
