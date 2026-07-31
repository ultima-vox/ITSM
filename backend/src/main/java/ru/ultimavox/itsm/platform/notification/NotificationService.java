package ru.ultimavox.itsm.platform.notification;

/** Platform port for multi-channel notification delivery. */
public interface NotificationService {
    void send(NotificationRequest request);
}
