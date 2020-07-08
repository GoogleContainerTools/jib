/*
 * Copyright 2020 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.cli.buildfile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A yaml block for specifying file properties (used in conjunction with layers).
 *
 * <p>Example use of this yaml snippet.
 *
 * <pre>{@code
 * filePermissions: 644
 * directoryPermissions: 755
 * user: foo
 * group: bar
 * timestamp: 100
 * }</pre>
 */
public class FilePropertiesSpec {
  @Nullable private FilePermissions filePermissions;
  @Nullable private FilePermissions directoryPermissions;
  @Nullable private String user;
  @Nullable private String group;
  @Nullable private Instant timestamp;

  /** Constructor for use by jackson to populate this object. */
  @JsonCreator
  public FilePropertiesSpec(
      @JsonProperty("filePermissions") String filePermissions,
      @JsonProperty("directoryPermissions") String directoryPermissions,
      @JsonProperty("user") String user,
      @JsonProperty("group") String group,
      @JsonProperty("timestamp") String timestamp) {
    this.filePermissions =
        filePermissions == null ? null : FilePermissions.fromOctalString(filePermissions);
    this.directoryPermissions =
        directoryPermissions == null ? null : FilePermissions.fromOctalString(directoryPermissions);
    this.user = user;
    this.group = group;
    this.timestamp =
        (timestamp == null) ? null : Instants.fromMillisOrIso8601(timestamp, "timestamp");
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
