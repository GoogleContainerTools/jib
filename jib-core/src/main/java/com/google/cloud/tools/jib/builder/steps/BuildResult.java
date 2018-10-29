/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.image.DescriptorDigest;
import java.util.Objects;

/** Used to record the results of a build. */
public class BuildResult {

  private final DescriptorDigest imageDigest;
  private final DescriptorDigest imageId;

  BuildResult(DescriptorDigest imageDigest, DescriptorDigest imageId) {
    this.imageDigest = imageDigest;
    this.imageId = imageId;
  }

  public DescriptorDigest getImageDigest() {
    return imageDigest;
  }

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
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    BuildResult otherBuildResult = (BuildResult) other;
    return imageDigest.equals(otherBuildResult.imageDigest)
        && imageId.equals(otherBuildResult.imageId);
  }
}
