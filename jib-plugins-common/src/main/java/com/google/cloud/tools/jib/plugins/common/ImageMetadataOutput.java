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

package com.google.cloud.tools.jib.plugins.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;

/**
 * Builds a JSON string containing metadata about a {@link JibContainer} from a build.
 *
 * <p>Example:
 *
 * <pre>{@code
 * {
 *   "image": "gcr.io/project/image:tag",
 *   "imageId": "sha256:61bb3ec31a47cb730eb58a38bbfa813761a51dca69d10e39c24c3d00a7b2c7a9",
 *   "imageDigest": "sha256:3f1be7e19129edb202c071a659a4db35280ab2bb1a16f223bfd5d1948657b6f",
 *   "tags": ["latest", "tag"]
 * }
 * }</pre>
 */
public class ImageMetadataOutput implements JsonTemplate {

  private final String image;
  private final String imageId;
  private final String imageDigest;
  private final List<String> tags;
  private final Boolean imagePushed;

  @JsonCreator
  ImageMetadataOutput(
      @JsonProperty(value = "image", required = true) String image,
      @JsonProperty(value = "imageId", required = true) String imageId,
      @JsonProperty(value = "imageDigest", required = true) String imageDigest,
      @JsonProperty(value = "tags", required = true) List<String> tags,
      @JsonProperty(value = "imagePushed", required = true) Boolean imagePushed) {
    this.image = image;
    this.imageId = imageId;
    this.imageDigest = imageDigest;
    this.tags = tags;
    this.imagePushed = imagePushed;
  }

  @VisibleForTesting
  static ImageMetadataOutput fromJson(String json) throws IOException {
    return JsonTemplateMapper.readJson(json, ImageMetadataOutput.class);
  }

  /**
   * Create reproducible image build metadata from {@link JibContainer} information.
   *
   * @param jibContainer the metadata source
   * @return a json template populated with image metadata
   */
  public static ImageMetadataOutput fromJibContainer(JibContainer jibContainer) {
    String image = jibContainer.getTargetImage().toString();
    String imageId = jibContainer.getImageId().toString();
    String imageDigest = jibContainer.getDigest().toString();
    Boolean imagePushed = jibContainer.isImagePushed();

    // Make sure tags always appear in a predictable way, by sorting them into a list
    List<String> tags = ImmutableList.sortedCopyOf(jibContainer.getTags());

    return new ImageMetadataOutput(image, imageId, imageDigest, tags, imagePushed);
  }

  public String getImage() {
    return image;
  }

  public String getImageId() {
    return imageId;
  }

  public String getImageDigest() {
    return imageDigest;
  }

  public List<String> getTags() {
    return tags;
  }

  public Boolean isImagePushed() {
    return imagePushed;
  }

  public String toJson() throws IOException {
    return JsonTemplateMapper.toUtf8String(this);
  }
}
