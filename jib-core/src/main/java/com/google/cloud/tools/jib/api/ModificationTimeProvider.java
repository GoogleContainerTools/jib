package com.google.cloud.tools.jib.api;

import java.nio.file.Path;
import java.time.Instant;

/** Files modification time provider * */
public interface ModificationTimeProvider {

  /** ID of provider which keeps files original modification times */
  String KEEP_ORIGINAL = "keep_original";

  /** ID of provider which trims files modification time to (EPOCH + 1 second) */
  String EPOCH_PLUS_SECOND = "epoch_plus_second";

  /**
   * Returns modification time for a specific file
   *
   * @param file path to file
   * @param pathInContainer path to file in container
   * @return file modification time
   */
  Instant getModificationTime(Path file, AbsoluteUnixPath pathInContainer);
}
