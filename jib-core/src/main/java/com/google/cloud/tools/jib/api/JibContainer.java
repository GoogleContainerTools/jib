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

import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.util.Objects;

/** The container built by Jib. */
public class JibContainer {

  /** Create a container. */
  public static JibContainer create(DescriptorDigest imageDigest, DescriptorDigest imageId) {
    return new JibContainer(imageDigest, imageId);
  }

  private final DescriptorDigest imageDigest;
  private final DescriptorDigest imageId;

  private JibContainer(DescriptorDigest imageDigest, DescriptorDigest imageId) {
    this.imageDigest = imageDigest;
    this.imageId = imageId;
  }

  /**
   * Gets the image digest, the digest of the registry image manifest built by Jib.
   *
   * @return the image digest
   */
  public DescriptorDigest getDigest() {
    return imageDigest;
  }

  /**
   * Gets the image ID, the digest of the container configuration built by Jib.
   *
   * @return the image ID
   */
  public DescriptorDigest getImageId() {
    return imageId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(imageDigest, imageId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof JibContainer)) {
      return false;
    }
    JibContainer other = (JibContainer) obj;
    return Objects.equals(imageDigest, other.imageDigest) && Objects.equals(imageId, other.imageId);
  }
}
