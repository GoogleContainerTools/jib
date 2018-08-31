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

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.cache.CachedLayer;
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
  @Mock private DigestOnlyLayer mockDigestOnlyLayer;
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
    Mockito.when(mockDigestOnlyLayer.getBlobDescriptor())
        .thenReturn(referenceNoDiffIdLayerBlobDescriptor);
    Mockito.when(mockUnwrittenLayer.getBlobDescriptor()).thenReturn(unwrittenLayerBlobDescriptor);
  }

  @Test
  public void testAddLayer_success() throws LayerPropertyNotFoundException {
    List<Layer> expectedLayers =
        Arrays.asList(mockCachedLayer, mockReferenceLayer, mockDigestOnlyLayer);

    ImageLayers<Layer> imageLayers =
        ImageLayers.builder()
            .add(mockCachedLayer)
            .add(mockReferenceLayer)
            .add(mockDigestOnlyLayer)
            .build();

    Assert.assertThat(imageLayers.getLayers(), CoreMatchers.is(expectedLayers));
  }

  @Test
  public void testAddLayer_sameAsLastLayer() throws LayerPropertyNotFoundException {
    List<Layer> expectedLayers =
        Arrays.asList(mockReferenceLayer, mockDigestOnlyLayer, mockUnwrittenLayer, mockCachedLayer);

    ImageLayers<Layer> imageLayers =
        ImageLayers.builder()
            .add(mockCachedLayer)
            .add(mockReferenceLayer)
            .add(mockDigestOnlyLayer)
            .add(mockUnwrittenLayer)
            .add(mockCachedLayer)
            .build();

    Assert.assertEquals(expectedLayers, imageLayers.getLayers());
  }
}
