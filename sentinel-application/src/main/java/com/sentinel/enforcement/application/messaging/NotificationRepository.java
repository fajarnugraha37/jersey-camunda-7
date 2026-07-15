package com.sentinel.enforcement.application.messaging;

public interface NotificationRepository {

  void save(NotificationRecord notificationRecord);
}
