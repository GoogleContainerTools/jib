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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** The container built by Jib. */
public class JibContainer {

  private final DescriptorDigest imageDigest;
  private final DescriptorDigest imageId;

  JibContainer(DescriptorDigest imageDigest, DescriptorDigest imageId) {
    this.imageDigest = imageDigest;
    this.imageId = imageId;
  }

  /**
   * Gets the image digest, the digest of the registry image manifest built by Jib. This digest can
   * be used to fetch a specific image from the registry in the form {@code
   * myregistry/myimage@digest}.
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
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof JibContainer)) {
      return false;
    }
    JibContainer otherContainer = (JibContainer) other;
    return imageDigest.equals(otherContainer.imageDigest) && imageId.equals(otherContainer.imageId);
  }

  /**
   * Write out the image digest to the given location.
   *
   * @param location the location to write the digest
   * @throws IOException a problem occurred when writing the digest
   */
  public void writeImageDigest(Path location) throws IOException {
    Files.write(location, imageDigest.toString().getBytes(StandardCharsets.UTF_8));
  }
}
