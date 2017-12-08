/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.image;

import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.blob.BlobStream;
import java.io.File;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link Layer}. */
public class LayerTest {

  @Mock private BlobStream mockCompressedBlobStream;

  @Mock private BlobStream mockUncompressedBlobStream;

  @Mock private File mockFile;

  @Mock private BlobDescriptor mockBlobDescriptor;

  @Mock private DescriptorDigest mockDiffId;

  @Before
  public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testNew_unwritten() {
    Layer layer = new UnwrittenLayer(mockCompressedBlobStream, mockUncompressedBlobStream);

    Assert.assertEquals(LayerType.UNWRITTEN, layer.getType());

    try {
      layer.getBlobDescriptor();
      Assert.fail("Blob descriptor should not be available for unwritten layer");
    } catch (LayerException ex) {
      Assert.assertEquals("Blob descriptor not available for unwritten layer", ex.getMessage());
    }

    try {
      layer.getDiffId();
      Assert.fail("Diff ID should not be available for unwritten layer");
    } catch (LayerException ex) {
      Assert.assertEquals("Diff ID not available for unwritten layer", ex.getMessage());
    }
  }

  @Test
  public void testNew_cached() throws LayerException {
    Layer layer = new CachedLayer(mockFile, mockBlobDescriptor, mockDiffId);

    Assert.assertEquals(LayerType.CACHED, layer.getType());
    Assert.assertEquals(mockBlobDescriptor, layer.getBlobDescriptor());
    Assert.assertEquals(mockDiffId, layer.getDiffId());
  }

  @Test
  public void testNew_reference() throws LayerException {
    Layer layer = new ReferenceLayer(mockBlobDescriptor, mockDiffId);

    Assert.assertEquals(LayerType.REFERENCE, layer.getType());
    Assert.assertEquals(mockBlobDescriptor, layer.getBlobDescriptor());
    Assert.assertEquals(mockDiffId, layer.getDiffId());
  }

  @Test
  public void testNew_referenceNoDiffId() throws LayerException {
    Layer layer = new ReferenceNoDiffIdLayer(mockBlobDescriptor);

    Assert.assertEquals(LayerType.REFERENCE_NO_DIFF_ID, layer.getType());
    Assert.assertEquals(mockBlobDescriptor, layer.getBlobDescriptor());

    try {
      layer.getDiffId();
      Assert.fail("Diff ID should not be available for reference layer without diff ID");
    } catch (LayerException ex) {
      Assert.assertEquals(
          "Diff ID not available for reference layer without diff ID", ex.getMessage());
    }
  }
}
