package com.google.cloud.tools.jib.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Modification time provider which returns original file modification time */
public class KeepOriginalModificationTimeProvider implements ModificationTimeProvider {

  /**
   * Returns the original file modification time
   *
   * @param file path to file
   * @param pathInContainer path to file in container
   * @return the original file modification time
   */
  @Override
  public Instant getModificationTime(Path file, AbsoluteUnixPath pathInContainer) {
    try {
      return Files.getLastModifiedTime(file).toInstant();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to define the modification time of " + file);
    }
  }
}
