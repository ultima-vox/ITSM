package ru.ultimavox.itsm.platform.notification;
import java.util.*;
/** Notification engine input. Template rendering applies recipient locale and channel preferences. */
public record NotificationRequest(UUID correlationId, String templateKey, String recipientSubject, String locale, Map<String,Object> variables, Channel channel) { public enum Channel { IN_APP, EMAIL, WEBHOOK } }
