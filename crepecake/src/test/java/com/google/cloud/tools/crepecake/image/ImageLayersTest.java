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
import java.util.Arrays;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ImageLayers}. */
@RunWith(MockitoJUnitRunner.class)
public class ImageLayersTest {

  @Mock private CachedLayer mockCachedLayer;
  @Mock private ReferenceLayer mockReferenceLayer;
  @Mock private ReferenceNoDiffIdLayer mockReferenceNoDiffIdLayer;
  @Mock private UnwrittenLayer mockUnwrittenLayer;

  @Before
  public void setUpFakes() throws LayerPropertyNotFoundException {
    DescriptorDigest mockDescriptorDigest1 = Mockito.mock(DescriptorDigest.class);
    DescriptorDigest mockDescriptorDigest2 = Mockito.mock(DescriptorDigest.class);
    DescriptorDigest mockDescriptorDigest3 = Mockito.mock(DescriptorDigest.class);

    BlobDescriptor cachedLayerBlobDescriptor = new BlobDescriptor(0, mockDescriptorDigest1);
    BlobDescriptor referenceLayerBlobDescriptor = new BlobDescriptor(0, mockDescriptorDigest2);
    BlobDescriptor referenceNoDiffIdLayerBlobDescriptor =
        new BlobDescriptor(0, mockDescriptorDigest3);
    // Intentionally the same digest as the mockCachedLayer.
    BlobDescriptor unwrittenLayerBlobDescriptor = new BlobDescriptor(0, mockDescriptorDigest1);

    Mockito.when(mockCachedLayer.getBlobDescriptor()).thenReturn(cachedLayerBlobDescriptor);
    Mockito.when(mockReferenceLayer.getBlobDescriptor()).thenReturn(referenceLayerBlobDescriptor);
    Mockito.when(mockReferenceNoDiffIdLayer.getBlobDescriptor())
        .thenReturn(referenceNoDiffIdLayerBlobDescriptor);
    Mockito.when(mockUnwrittenLayer.getBlobDescriptor()).thenReturn(unwrittenLayerBlobDescriptor);
  }

  @Test
  public void testAddLayer_success()
      throws DuplicateLayerException, LayerPropertyNotFoundException {
    List<Layer> expectedLayers =
        Arrays.asList(mockCachedLayer, mockReferenceLayer, mockReferenceNoDiffIdLayer);

    ImageLayers<Layer> imageLayers = new ImageLayers<>();
    imageLayers.add(mockCachedLayer);
    imageLayers.add(mockReferenceLayer);
    imageLayers.add(mockReferenceNoDiffIdLayer);

    Assert.assertThat(imageLayers.asList(), CoreMatchers.is(expectedLayers));
  }

  @Test
  public void testAddLayer_duplicate()
      throws DuplicateLayerException, LayerPropertyNotFoundException {
    ImageLayers<Layer> imageLayers = new ImageLayers<>();
    imageLayers.add(mockCachedLayer);
    imageLayers.add(mockReferenceLayer);
    imageLayers.add(mockReferenceNoDiffIdLayer);

    try {
      imageLayers.add(mockUnwrittenLayer);
      Assert.fail("Adding duplicate layer should throw DuplicateLayerException");
    } catch (DuplicateLayerException ex) {
      Assert.assertEquals("Cannot add the same layer more than once", ex.getMessage());
    }
  }
}
