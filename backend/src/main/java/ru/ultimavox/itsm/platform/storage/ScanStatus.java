package ru.ultimavox.itsm.platform.storage;

/** Content / malware scan outcome for an attachment. */
public enum ScanStatus {
  /** Scan not finished yet (async engines). */
  PENDING,
  /** Safe to download / distribute. */
  CLEAN,
  /** Known-bad signature or policy block. */
  INFECTED,
  /** Scanner disabled or not applicable. */
  SKIPPED,
  /** Scanner failure — treat as not clean for download. */
  ERROR
}
