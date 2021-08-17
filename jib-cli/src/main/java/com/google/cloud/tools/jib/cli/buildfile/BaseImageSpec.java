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
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * A yaml block for specifying a base image with support for multi platform selections.
 *
 * <p>Example use of this yaml snippet.
 *
 * <pre>{@code
 * image: gcr.io/example/baseimage
 * platforms:
 *   - see }{@link PlatformSpec}{@code
 *   - see }{@link PlatformSpec}{@code
 * }</pre>
 */
public class BaseImageSpec {
  private final String image;
  private final List<PlatformSpec> platforms;

  /**
   * Constructor for use by jackson to populate this object.
   *
   * @param image an image reference for a base image
   * @param platforms a list of platforms when using a manifest list or image index
   */
  @JsonCreator
  public BaseImageSpec(
      @JsonProperty(value = "image", required = true) String image,
      @JsonProperty("platforms") List<PlatformSpec> platforms) {
    Validator.checkNotNullAndNotEmpty(image, "image");
    Validator.checkNullOrNonNullEntries(platforms, "platforms");
    this.image = image;
    this.platforms = platforms == null ? ImmutableList.of() : platforms;
  }

  public String getImage() {
    return image;
  }

  public List<PlatformSpec> getPlatforms() {
    return platforms;
  }
}
