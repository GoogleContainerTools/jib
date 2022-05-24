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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.hash.Digests;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ImageToJsonTranslator;
import java.io.IOException;
import java.util.Objects;

/** Used to record the results of a build. */
public class BuildResult {

  /**
   * Gets a {@link BuildResult} from an {@link Image}.
   *
   * @param image the image
   * @param targetFormat the target format of the image
   * @return a new {@link BuildResult} with the image's digest and id
   * @throws IOException if writing the digest or container configuration fails
   */
  public static BuildResult fromImage(
      Image image, Class<? extends BuildableManifestTemplate> targetFormat) throws IOException {
    ImageToJsonTranslator imageToJsonTranslator = new ImageToJsonTranslator(image);
    BlobDescriptor containerConfigurationBlobDescriptor =
        Digests.computeDigest(imageToJsonTranslator.getContainerConfiguration());
    BuildableManifestTemplate manifestTemplate =
        imageToJsonTranslator.getManifestTemplate(
            targetFormat, containerConfigurationBlobDescriptor);
    DescriptorDigest imageDigest = Digests.computeJsonDigest(manifestTemplate);
    DescriptorDigest imageId = containerConfigurationBlobDescriptor.getDigest();
    return new BuildResult(imageDigest, imageId, false);
  }

  private final DescriptorDigest imageDigest;
  private final DescriptorDigest imageId;
  private final Boolean imagePushed;

  BuildResult(DescriptorDigest imageDigest, DescriptorDigest imageId, boolean imagePushed) {
    this.imageDigest = imageDigest;
    this.imageId = imageId;
    this.imagePushed = imagePushed;
  }

  public DescriptorDigest getImageDigest() {
    return imageDigest;
  }

  public DescriptorDigest getImageId() {
    return imageId;
  }

  public boolean isImagePushed() {
    return imagePushed;
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
    if (!(other instanceof BuildResult)) {
      return false;
    }
    BuildResult otherBuildResult = (BuildResult) other;
    return imageDigest.equals(otherBuildResult.imageDigest)
        && imageId.equals(otherBuildResult.imageId)
        && imagePushed.equals(otherBuildResult.imagePushed);
  }
}
