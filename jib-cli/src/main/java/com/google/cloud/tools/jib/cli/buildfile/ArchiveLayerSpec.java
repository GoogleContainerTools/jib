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
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A yaml block for specifying archive layers.
 *
 * <p>Example use of this yaml snippet.
 *
 * <pre>{@code
 * name: "my tar layer"
 * archive: output/mytar.tgz
 * // optional mediatype
 * mediaType: application/vnd.docker.image.rootfs.diff.tar.gzip
 * }</pre>
 */
@JsonDeserialize(using = JsonDeserializer.None.class) // required since LayerSpec overrides this
public class ArchiveLayerSpec implements LayerSpec {

  private final String name;
  // TODO: arhive should maybe be a uri to support file paths or urls
  private final Path archive;
  @Nullable private final String mediaType;

  /**
   * Constructor for use by jackson to populate this object.
   *
   * @param name a unique name for this layer
   * @param archive a path to an archive file
   * @param mediaType the media type of the file
   */
  @JsonCreator
  public ArchiveLayerSpec(
      @JsonProperty(value = "name", required = true) String name,
      @JsonProperty(value = "archive", required = true) String archive,
      @JsonProperty("mediaType") String mediaType) {
    Validator.checkNotNullAndNotEmpty(name, "name");
    Validator.checkNotNullAndNotEmpty(archive, "archive");
    Validator.checkNullOrNotEmpty(mediaType, "mediaType");
    this.name = name;
    this.archive = Paths.get(archive);
    this.mediaType = mediaType;
  }

  public String getName() {
    return name;
  }

  public Path getArchive() {
    return archive;
  }

  public Optional<String> getMediaType() {
    return Optional.ofNullable(mediaType);
  }
}
