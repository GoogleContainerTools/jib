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

package com.google.cloud.tools.jib.event.events;

import com.google.cloud.tools.jib.event.JibEvent;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;

/** Image has been created. */
public class ImageCreatedEvent implements JibEvent {
  private final Image<? extends Layer> image;
  private final DescriptorDigest imageDigest;
  private final DescriptorDigest imageId;

  public ImageCreatedEvent(
      Image<? extends Layer> image, DescriptorDigest imageDigest, DescriptorDigest imageId) {
    this.image = image;
    this.imageDigest = imageDigest;
    this.imageId = imageId;
  }

  /** @return the created image */
  public Image<? extends Layer> getImage() {
    return image;
  }

  /**
   * Return the <em>image digest</em>, the digest of the registry image manifest.
   *
   * @return the image digest
   */
  public DescriptorDigest getImageDigest() {
    return imageDigest;
  }

  /**
   * Return the <em>image ID</em>, the digest of the container configuration.
   *
   * @return the image ID
   */
  public DescriptorDigest getImageId() {
    return imageId;
  }
}
