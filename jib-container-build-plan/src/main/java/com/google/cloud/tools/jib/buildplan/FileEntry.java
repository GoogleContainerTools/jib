package com.google.cloud.tools.jib.buildplan;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * Represents an entry in the layer. A layer consists of many entries that can be converted into tar
 * archive entries.
 *
 * <p>This class is immutable and thread-safe.
 */
@Immutable
public class FileEntry {

  private final Path sourceFile;
  private final AbsoluteUnixPath extractionPath;
  private final FilePermissions permissions;
  private final Instant modificationTime;

  /**
   * Instantiates with a source file and the path to place the source file in the container file
   * system.
   *
   * <p>For example, {@code new FileEntry(Paths.get("HelloWorld.class"),
   * AbsoluteUnixPath.get("/app/classes/HelloWorld.class"))} adds a file {@code HelloWorld.class} to
   * the container file system at {@code /app/classes/HelloWorld.class}.
   *
   * <p>For example, {@code new FileEntry(Paths.get("com"),
   * AbsoluteUnixPath.get("/app/classes/com"))} adds a directory to the container file system at
   * {@code /app/classes/com}. This does <b>not</b> add the contents of {@code com/}.
   *
   * <p>Note that:
   *
   * <ul>
   *   <li>Entry source files can be either files or directories.
   *   <li>Adding a directory does not include the contents of the directory. Each file under a
   *       directory must be added as a separate {@link FileEntry}.
   * </ul>
   *
   * @param sourceFile the source file to add to the layer
   * @param extractionPath the path in the container file system corresponding to the {@code
   *     sourceFile}
   * @param permissions the file permissions on the container
   * @param modificationTime the file modification time
   */
  public FileEntry(
      Path sourceFile,
      AbsoluteUnixPath extractionPath,
      FilePermissions permissions,
      Instant modificationTime) {
    this.sourceFile = sourceFile;
    this.extractionPath = extractionPath;
    this.permissions = permissions;
    this.modificationTime = modificationTime;
  }

  /**
   * Returns the modification time of the file in the entry.
   *
   * @return the modification time
   */
  public Instant getModificationTime() {
    return modificationTime;
  }

  /**
   * Gets the source file. The source file may be relative or absolute, so the caller should use
   * {@code getSourceFile().toAbsolutePath().toString()} for the serialized form since the
   * serialization could change independently of the path representation.
   *
   * @return the source file
   */
  public Path getSourceFile() {
    return sourceFile;
  }

  /**
   * Gets the extraction path.
   *
   * @return the extraction path
   */
  public AbsoluteUnixPath getExtractionPath() {
    return extractionPath;
  }

  /**
   * Gets the file permissions on the container.
   *
   * @return the file permissions on the container
   */
  public FilePermissions getPermissions() {
    return permissions;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof FileEntry)) {
      return false;
    }
    FileEntry otherFileEntry = (FileEntry) other;
    return sourceFile.equals(otherFileEntry.sourceFile)
        && extractionPath.equals(otherFileEntry.extractionPath)
        && Objects.equals(permissions, otherFileEntry.permissions)
        && Objects.equals(modificationTime, otherFileEntry.modificationTime);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceFile, extractionPath, permissions, modificationTime);
  }
}
