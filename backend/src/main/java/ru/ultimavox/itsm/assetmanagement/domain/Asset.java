package ru.ultimavox.itsm.assetmanagement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Asset lifecycle is independent from a CMDB CI, but an asset may reference the CI it realizes. */
public record Asset(
    UUID id,
    String assetTag,
    String name,
    Kind kind,
    Status status,
    String ownerSubject,
    UUID configurationItemId,
    LocalDate acquiredOn,
    LocalDate warrantyUntil,
    String location,
    String supplier,
    BigDecimal cost,
    long version
) {
  public Asset assignTo(String subject) {
    if (status != Status.IN_STOCK && status != Status.IN_USE) {
      throw new IllegalStateException("Only stock or active assets may be assigned");
    }
    return copy(Status.IN_USE, requireSubject(subject), configurationItemId, name, location, supplier, cost);
  }

  public Asset linkToCi(UUID ciId) {
    if (ciId == null) {
      throw new IllegalArgumentException("configurationItemId is required");
    }
    if (status == Status.RETIRED || status == Status.LOST) {
      throw new IllegalStateException("Cannot link retired or lost assets to a CI");
    }
    return copy(status, ownerSubject, ciId, name, location, supplier, cost);
  }

  public Asset transitionTo(Status target) {
    if (!allowed(status, target)) {
      throw new IllegalStateException("Asset transition %s -> %s is not allowed".formatted(status, target));
    }
    return copy(target, ownerSubject, configurationItemId, name, location, supplier, cost);
  }

  public Asset updateFields(String newName, String newLocation, String newSupplier, BigDecimal newCost) {
    return copy(
        status,
        ownerSubject,
        configurationItemId,
        newName != null ? newName : name,
        newLocation != null ? newLocation : location,
        newSupplier != null ? blankToNull(newSupplier) : supplier,
        newCost != null ? newCost : cost
    );
  }

  private Asset copy(
      Status nextStatus,
      String nextOwner,
      UUID nextCi,
      String nextName,
      String nextLocation,
      String nextSupplier,
      BigDecimal nextCost
  ) {
    return new Asset(
        id, assetTag, nextName, kind, nextStatus, nextOwner, nextCi,
        acquiredOn, warrantyUntil, nextLocation, nextSupplier, nextCost, version + 1
    );
  }

  private static String requireSubject(String subject) {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("Owner is required");
    }
    return subject;
  }

  private static String blankToNull(String value) {
    return value.isBlank() ? null : value;
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
