/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.builder.steps.BuildResult;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import java.util.Set;

/** The container built by Jib. */
public class JibContainer {

  private final ImageReference targetImage;
  private final DescriptorDigest imageDigest;
  private final DescriptorDigest imageId;
  private final Set<String> tags;
  private final boolean imagePushed;

  @VisibleForTesting
  JibContainer(
      ImageReference targetImage,
      DescriptorDigest imageDigest,
      DescriptorDigest imageId,
      Set<String> tags,
      boolean imagePushed) {
    this.targetImage = targetImage;
    this.imageDigest = imageDigest;
    this.imageId = imageId;
    this.tags = tags;
    this.imagePushed = imagePushed;
  }

  static JibContainer from(BuildContext buildContext, BuildResult buildResult) {
    ImageReference targetImage = buildContext.getTargetImageConfiguration().getImage();
    DescriptorDigest imageDigest = buildResult.getImageDigest();
    DescriptorDigest imageId = buildResult.getImageId();
    Set<String> tags = buildContext.getAllTargetImageTags();
    return new JibContainer(targetImage, imageDigest, imageId, tags, buildResult.isImagePushed());
  }

  /**
   * Get the target image that was built.
   *
   * @return the target image reference.
   */
  public ImageReference getTargetImage() {
    return targetImage;
  }

  /**
   * Returns true if we pushed this image all the way to a registry.
   *
   * @return true if pushed.
   */
  public boolean isImagePushed() {
    return imagePushed;
  }

  /**
   * Gets the digest of the registry image manifest built by Jib. This digest can be used to fetch a
   * specific image from the registry in the form {@code myregistry/myimage@digest}.
   *
   * @return the image digest
   */
  public DescriptorDigest getDigest() {
    return imageDigest;
  }

  /**
   * Gets the digest of the container configuration built by Jib.
   *
   * @return the image ID
   */
  public DescriptorDigest getImageId() {
    return imageId;
  }

  /**
   * Get the tags applied to the container.
   *
   * @return the set of all tags
   */
  public Set<String> getTags() {
    return tags;
  }

  @Override
  public int hashCode() {
    return Objects.hash(targetImage, imageDigest, imageId, tags);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof JibContainer)) {
      return false;
    }
    JibContainer otherContainer = (JibContainer) other;
    return targetImage.equals(otherContainer.targetImage)
        && imageDigest.equals(otherContainer.imageDigest)
        && imageId.equals(otherContainer.imageId)
        && tags.equals(otherContainer.tags);
  }
}
