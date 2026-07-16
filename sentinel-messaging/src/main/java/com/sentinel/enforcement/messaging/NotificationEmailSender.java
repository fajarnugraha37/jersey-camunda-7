package com.sentinel.enforcement.messaging;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

final class NotificationEmailSender {
  private final String smtpHost;
  private final int smtpPort;

  NotificationEmailSender(String smtpHost, int smtpPort) {
    this.smtpHost = smtpHost;
    this.smtpPort = smtpPort;
  }

  void send(String fromEmail, String toEmail, String subject, String body) {
    try {
      Properties properties = new Properties();
      properties.put("mail.smtp.host", smtpHost);
      properties.put("mail.smtp.port", Integer.toString(smtpPort));
      Session session = Session.getInstance(properties);
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(fromEmail));
      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
      message.setSubject(subject);
      message.setText(body);
      Transport.send(message);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to send notification email.", exception);
    }
  }
}
