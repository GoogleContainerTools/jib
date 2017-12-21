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

package com.google.cloud.tools.crepecake.cache;

import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link CacheMetadata}. */
@RunWith(MockitoJUnitRunner.class)
public class CacheMetadataTest {

  @Mock private DescriptorDigest mockDescriptorDigest;
  @Mock private BlobDescriptor mockBlobDescriptor;
  @Mock private CachedLayerType mockCachedLayerType;
  @Mock private CachedLayerWithMetadata mockLayer;

  @Before
  public void setUpMocks() {
    Mockito.when(mockBlobDescriptor.getDigest()).thenReturn(mockDescriptorDigest);
    Mockito.when(mockLayer.getBlobDescriptor()).thenReturn(mockBlobDescriptor);
  }

  @Test
  public void testAddLayer() throws LayerPropertyNotFoundException, DuplicateLayerException {
    CacheMetadata cacheMetadata = new CacheMetadata();
    cacheMetadata.addLayer(mockLayer);

    Assert.assertThat(cacheMetadata.getLayers().asList(), CoreMatchers.hasItem(mockLayer));
  }
}
