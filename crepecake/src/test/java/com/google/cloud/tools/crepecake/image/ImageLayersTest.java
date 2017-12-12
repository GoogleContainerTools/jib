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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests for {@link ImageLayers}. */
public class ImageLayersTest {

  @Mock private DescriptorDigest mockDescriptorDigest1;
  @Mock private DescriptorDigest mockDescriptorDigest2;
  @Mock private DescriptorDigest mockDescriptorDigest3;

  @Mock private CachedLayer mockCachedLayer;
  @Mock private BlobDescriptor mockCachedLayerBlobDescriptor;

  @Mock private ReferenceLayer mockReferenceLayer;
  @Mock private BlobDescriptor mockReferenceLayerBlobDescriptor;

  @Mock private ReferenceNoDiffIdLayer mockReferenceNoDiffIdLayer;
  @Mock private BlobDescriptor mockReferenceNoDiffIdLayerBlobDescriptor;

  @Mock private UnwrittenLayer mockUnwrittenLayer;
  @Mock private BlobDescriptor mockUnwrittenLayerBlobDescriptor;

  @Before
  public void setUpFakes() throws LayerPropertyNotFoundException {
    MockitoAnnotations.initMocks(this);

    Mockito.when(mockCachedLayerBlobDescriptor.getDigest()).thenReturn(mockDescriptorDigest1);
    Mockito.when(mockReferenceLayerBlobDescriptor.getDigest()).thenReturn(mockDescriptorDigest2);
    Mockito.when(mockReferenceNoDiffIdLayerBlobDescriptor.getDigest())
        .thenReturn(mockDescriptorDigest3);
    // Intentionally the same digest as the mockCachedLayer.
    Mockito.when(mockUnwrittenLayerBlobDescriptor.getDigest()).thenReturn(mockDescriptorDigest1);

    Mockito.when(mockCachedLayer.getBlobDescriptor()).thenReturn(mockCachedLayerBlobDescriptor);
    Mockito.when(mockReferenceLayer.getBlobDescriptor())
        .thenReturn(mockReferenceLayerBlobDescriptor);
    Mockito.when(mockReferenceNoDiffIdLayer.getBlobDescriptor())
        .thenReturn(mockReferenceNoDiffIdLayerBlobDescriptor);
    Mockito.when(mockUnwrittenLayer.getBlobDescriptor())
        .thenReturn(mockUnwrittenLayerBlobDescriptor);
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
