package ru.ultimavox.itsm.assetmanagement.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Asset lifecycle is independent from a CMDB CI, but an asset may reference the CI it realizes. */
public record Asset(
    UUID id,
    String assetTag,
    Kind kind,
    Status status,
    String ownerSubject,
    UUID configurationItemId,
    LocalDate acquiredOn,
    LocalDate warrantyUntil,
    long version
) {
  public Asset assignTo(String subject) {
    if (status != Status.IN_STOCK && status != Status.IN_USE) {
      throw new IllegalStateException("Only stock or active assets may be assigned");
    }
    return new Asset(id, assetTag, kind, Status.IN_USE, requireSubject(subject), configurationItemId, acquiredOn, warrantyUntil, version + 1);
  }

  public Asset linkToCi(UUID ciId) {
    if (ciId == null) {
      throw new IllegalArgumentException("configurationItemId is required");
    }
    if (status == Status.RETIRED || status == Status.LOST) {
      throw new IllegalStateException("Cannot link retired or lost assets to a CI");
    }
    return new Asset(id, assetTag, kind, status, ownerSubject, ciId, acquiredOn, warrantyUntil, version + 1);
  }

  public Asset transitionTo(Status target) {
    if (!allowed(status, target)) {
      throw new IllegalStateException("Asset transition %s -> %s is not allowed".formatted(status, target));
    }
    return new Asset(id, assetTag, kind, target, ownerSubject, configurationItemId, acquiredOn, warrantyUntil, version + 1);
  }

  private static String requireSubject(String subject) {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("Owner is required");
    }
    return subject;
  }

  private static boolean allowed(Status from, Status to) {
    return switch (from) {
      case ORDERED -> to == Status.IN_STOCK || to == Status.RETIRED;
      case IN_STOCK -> to == Status.IN_USE || to == Status.REPAIRED || to == Status.RETIRED;
      case IN_USE -> to == Status.IN_STOCK || to == Status.REPAIRED || to == Status.LOST || to == Status.RETIRED;
      case REPAIRED -> to == Status.IN_STOCK || to == Status.IN_USE || to == Status.RETIRED;
      case LOST, RETIRED -> false;
    };
  }

  public enum Kind {
    LAPTOP, DESKTOP, MONITOR, MOBILE_DEVICE, SERVER, NETWORK_DEVICE, SOFTWARE_LICENSE, OTHER
  }

  public enum Status { ORDERED, IN_STOCK, IN_USE, REPAIRED, LOST, RETIRED }
}
