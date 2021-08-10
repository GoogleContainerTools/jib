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
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A yaml block for specifying layers.
 *
 * <p>Example use of this yaml snippet.
 *
 * <pre>{@code
 * properties: see }{@link FilePropertiesSpec}{@code
 * entries:
 *   - see }{@link LayerSpec}{@code
 *   - see }{@link LayerSpec}{@code
 * }</pre>
 */
public class LayersSpec {
  private final List<LayerSpec> entries;
  @Nullable private final FilePropertiesSpec properties;

  /**
   * Constructor for use by jackson to populate this object.
   *
   * @param entries a list of {@link LayerSpec} defining the layers in this container
   * @param properties a {@link FilePropertiesSpec} that applies to all layers in this buildfile
   */
  @JsonCreator
  public LayersSpec(
      @JsonProperty(value = "entries", required = true) List<LayerSpec> entries,
      @JsonProperty("properties") FilePropertiesSpec properties) {
    Validator.checkNotNullAndNotEmpty(entries, "entries");
    this.entries = entries;
    this.properties = properties;
  }

  public Optional<FilePropertiesSpec> getProperties() {
    return Optional.ofNullable(properties);
  }

  public List<LayerSpec> getEntries() {
    return entries;
  }
}
