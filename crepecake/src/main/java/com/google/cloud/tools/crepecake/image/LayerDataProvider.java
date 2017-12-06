package com.google.cloud.tools.crepecake.image;

import com.google.cloud.tools.crepecake.blob.BlobDescriptor;

/** Implementations represent the various layer types. */
abstract class LayerDataProvider {

  /**
   * @return the layer's content {@link BlobDescriptor}
   * @throws LayerException if not available
   */
  abstract BlobDescriptor getBlobDescriptor() throws LayerException;

  /**
   * @return the layer's diff ID
   * @throws LayerException if not available
   */
  abstract DescriptorDigest getDiffId() throws LayerException;
}
