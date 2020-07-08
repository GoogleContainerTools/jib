package com.google.cloud.tools.jib.cli.buildfile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Optional;

public class FilePropertiesSpec {
  @Nullable private FilePermissions filePermissions;
  @Nullable private FilePermissions directoryPermissions;
  @Nullable private String user;
  @Nullable private String group;
  @Nullable private Instant timestamp;

  @JsonCreator
  public FilePropertiesSpec(@JsonProperty("filePermissions") String filePermissions,
                            @JsonProperty("directoryPermissions") String directoryPermissions,
                            @JsonProperty("user") String user,
                            @JsonProperty("group") String group,
                            @JsonProperty("timestamp") String timestamp) {
    this.filePermissions = filePermissions == null ? null : FilePermissions.fromOctalString(filePermissions);
    this.directoryPermissions = directoryPermissions == null ? null : FilePermissions.fromOctalString(directoryPermissions);
    this.user = user;
    this.group = group;
    this.timestamp = (timestamp == null) ? null : Instants.fromMillisOrIso8601(timestamp, "timestamp");
  }

  public Optional<FilePermissions> getFilePermissions() {
    return Optional.ofNullable(filePermissions);
  }

  public Optional<FilePermissions> getDirectoryPermissions() {
    return Optional.ofNullable(directoryPermissions);
  }

  public Optional<String> getUser() {
    return Optional.ofNullable(user);
  }

  public Optional<String> getGroup() {
    return Optional.ofNullable(group);
  }

  public Optional<Instant> getTimestamp() {
    return Optional.ofNullable(timestamp);
  }
}
