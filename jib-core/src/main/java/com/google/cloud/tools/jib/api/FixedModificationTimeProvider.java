package com.google.cloud.tools.jib.api;

import java.nio.file.Path;
import java.time.Instant;

/** Modification time provider which returns fixed instant */
public class FixedModificationTimeProvider implements ModificationTimeProvider {

  /** Default file modification time (EPOCH + 1 second). */
  public static final Instant EPOCH_PLUS_ONE_SECOND = Instant.ofEpochSecond(1);

  /** Fixed modification time * */
  private final Instant modificationTime;

  /**
   * Initializes with a fixed modification time
   *
   * @param modificationTime fixed modification time
   */
  public FixedModificationTimeProvider(Instant modificationTime) {
    this.modificationTime = modificationTime;
  }

  /**
   * Returns preconfigured fixed modification time
   *
   * @param file path to file
   * @param pathInContainer path to file in container
   * @return preconfigured fixed modification time
   */
  @Override
  public Instant getModificationTime(Path file, AbsoluteUnixPath pathInContainer) {
    return modificationTime;
  }
}
