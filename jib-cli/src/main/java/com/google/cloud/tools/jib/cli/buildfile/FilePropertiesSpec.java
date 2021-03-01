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
import com.google.cloud.tools.jib.cli.Instants;
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
  @Nullable private final FilePermissions filePermissions;
  @Nullable private final FilePermissions directoryPermissions;
  @Nullable private final String user;
  @Nullable private final String group;
  @Nullable private final Instant timestamp;

  /**
   * Constructor for use by jackson to populate this object.
   *
   * @param filePermissions octal string for file permissions
   * @param directoryPermissions octal string for directory permissions
   * @param user name or number for ownership user
   * @param group name or number for ownership group
   * @param timestamp in milliseconds since epoch or ISO 8601 datetime
   */
  @JsonCreator
  public FilePropertiesSpec(
      @JsonProperty("filePermissions") String filePermissions,
      @JsonProperty("directoryPermissions") String directoryPermissions,
      @JsonProperty("user") String user,
      @JsonProperty("group") String group,
      @JsonProperty("timestamp") String timestamp) {
    Validator.checkNullOrNotEmpty(filePermissions, "filePermissions");
    Validator.checkNullOrNotEmpty(directoryPermissions, "directoryPermissions");
    Validator.checkNullOrNotEmpty(user, "user");
    Validator.checkNullOrNotEmpty(group, "group");
    Validator.checkNullOrNotEmpty(timestamp, "timestamp");
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
