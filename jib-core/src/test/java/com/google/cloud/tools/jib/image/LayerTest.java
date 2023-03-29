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
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link Layer}. */
@RunWith(MockitoJUnitRunner.class)
public class LayerTest {

  @Mock private DescriptorDigest mockDescriptorDigest;
  @Mock private BlobDescriptor mockBlobDescriptor;
  @Mock private DescriptorDigest mockDiffId;

  @Test
  public void testNew_reference() throws LayerPropertyNotFoundException {
    Layer layer = new ReferenceLayer(mockBlobDescriptor, mockDiffId, CompressionAlgorithm.GZIP);

    try {
      layer.getBlob();
      Assert.fail("Blob content should not be available for reference layer");
    } catch (LayerPropertyNotFoundException ex) {
      Assert.assertEquals("Blob not available for reference layer", ex.getMessage());
    }

    Assert.assertEquals(mockBlobDescriptor, layer.getBlobDescriptor());
    Assert.assertEquals(mockDiffId, layer.getDiffId());
  }

  @Test
  public void testNew_digestOnly() throws LayerPropertyNotFoundException {
    Layer layer = new DigestOnlyLayer(mockDescriptorDigest);

    try {
      layer.getBlob();
      Assert.fail("Blob content should not be available for digest-only layer");
    } catch (LayerPropertyNotFoundException ex) {
      Assert.assertEquals("Blob not available for digest-only layer", ex.getMessage());
    }

    Assert.assertFalse(layer.getBlobDescriptor().hasSize());
    Assert.assertEquals(mockDescriptorDigest, layer.getBlobDescriptor().getDigest());

    try {
      layer.getDiffId();
      Assert.fail("Diff ID should not be available for digest-only layer");
    } catch (LayerPropertyNotFoundException ex) {
      Assert.assertEquals("Diff ID not available for digest-only layer", ex.getMessage());
    }
  }
}
