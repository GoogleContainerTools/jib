/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.image;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.buildplan.CompressionAlgorithm;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;

/** A {@link Layer} reference that only has its {@link DescriptorDigest}. */
public class DigestOnlyLayer implements Layer {

  /** The {@link BlobDescriptor} of the compressed layer content. */
  private final BlobDescriptor blobDescriptor;

  /**
   * Instantiate with a {@link DescriptorDigest}.
   *
   * @param digest the digest to instantiate the {@link DigestOnlyLayer} from
   */
  public DigestOnlyLayer(DescriptorDigest digest) {
    blobDescriptor = new BlobDescriptor(digest);
  }

  @Override
  public Blob getBlob() throws LayerPropertyNotFoundException {
    throw new LayerPropertyNotFoundException("Blob not available for digest-only layer");
  }

  @Override
  public BlobDescriptor getBlobDescriptor() {
    return blobDescriptor;
  }

  @Override
  public DescriptorDigest getDiffId() throws LayerPropertyNotFoundException {
    throw new LayerPropertyNotFoundException("Diff ID not available for digest-only layer");
  }

  @Override
  public CompressionAlgorithm getCompressionAlgorithm() throws LayerPropertyNotFoundException {
    throw new LayerPropertyNotFoundException(
        "CompressionAlgorithm not available for digest-only layer");
  }
}
