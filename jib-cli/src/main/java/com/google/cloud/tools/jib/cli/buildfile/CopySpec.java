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
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A yaml block for specifying a copy directive.
 *
 * <p>Example use of this yaml snippet.
 *
 * <pre>{@code
 * src: path/to/somewhere
 * dest: /absolute/path/on/container
 * includes:
 *   - **\/*.txt
 * excludes:
 *   - **\/goose.txt
 *   - **\/moose.txt
 * properties: see }{@link FilePropertiesSpec}{@code
 * }</pre>
 */
public class CopySpec {
  private final Path src;
  private final AbsoluteUnixPath dest;
  // extra metadata to determine if the target can be considered a file
  private final boolean destEndsWithSlash;

  @Nullable private final FilePropertiesSpec properties;
  private final List<String> excludes;
  private final List<String> includes;

  /**
   * Constructor for use by jackson to populate this object.
   *
   * @param src a file/directory on the local filesystem to copy *from*
   * @param dest an absolute unix style path to copy *to* on the container
   * @param includes glob to filter files to include from "src"
   * @param excludes glob to filter out files included by "src" and "includes" (applied last)
   * @param properties a {@link FilePropertiesSpec} that applies to all files in this copy
   */
  @JsonCreator
  public CopySpec(
      @JsonProperty(value = "src", required = true) String src,
      @JsonProperty(value = "dest", required = true) String dest,
      @JsonProperty("includes") List<String> includes,
      @JsonProperty("excludes") List<String> excludes,
      @JsonProperty("properties") FilePropertiesSpec properties) {
    Validator.checkNotNullAndNotEmpty(src, "src");
    Validator.checkNotNullAndNotEmpty(dest, "dest");
    Validator.checkNullOrNonNullNonEmptyEntries(includes, "includes");
    Validator.checkNullOrNonNullNonEmptyEntries(excludes, "excludes");
    this.src = Paths.get(src);
    this.dest = AbsoluteUnixPath.get(dest);
    this.destEndsWithSlash = dest.endsWith("/");
    this.excludes = (excludes == null) ? ImmutableList.of() : excludes;
    this.includes = (includes == null) ? ImmutableList.of() : includes;
    this.properties = properties;
  }

  public Path getSrc() {
    return src;
  }

  public AbsoluteUnixPath getDest() {
    return dest;
  }

  public boolean isDestEndsWithSlash() {
    return destEndsWithSlash;
  }

  public List<String> getExcludes() {
    return excludes;
  }

  public List<String> getIncludes() {
    return includes;
  }

  public Optional<FilePropertiesSpec> getProperties() {
    return Optional.ofNullable(properties);
  }
}
