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
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A yaml block for specifying files layers.
 *
 * <p>Example use of this yaml snippet.
 *
 * <pre>{@code
 * name: "my classes layer"
 * files:
 *   - }{@link CopySpec}{@code
 *   - }{@link CopySpec}{@code
 * // optional properties
 * properties: see }{@link FilePropertiesSpec}{@code
 * }</pre>
 */
@JsonDeserialize(using = JsonDeserializer.None.class) // required since LayerSpec overrides this
public class FileLayerSpec implements LayerSpec {
  private final String name;
  private final List<CopySpec> files;
  @Nullable private final FilePropertiesSpec properties;

  /**
   * Constructor for use by jackson to populate this object.
   *
   * @param name a unique name for this layer
   * @param files a list of {@link CopySpec} describing files to add to the layer
   * @param properties a {@link FilePropertiesSpec} that applies to all files in this layer
   */
  @JsonCreator
  public FileLayerSpec(
      @JsonProperty(value = "name", required = true) String name,
      @JsonProperty(value = "files", required = true) List<CopySpec> files,
      @JsonProperty("properties") FilePropertiesSpec properties) {
    Validator.checkNotNullAndNotEmpty(name, "name");
    Validator.checkNotNullAndNotEmpty(files, "files");
    this.name = name;
    this.properties = properties;
    this.files = files;
  }

  public String getName() {
    return name;
  }

  public List<CopySpec> getFiles() {
    return files;
  }

  public Optional<FilePropertiesSpec> getProperties() {
    return Optional.ofNullable(properties);
  }
}
