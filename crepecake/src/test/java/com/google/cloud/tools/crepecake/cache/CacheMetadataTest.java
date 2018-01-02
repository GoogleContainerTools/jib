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
import com.google.cloud.tools.crepecake.image.ImageLayers;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link CacheMetadata}. */
public class CacheMetadataTest {

  private static CachedLayer mockCachedLayer() {
    CachedLayer mockCachedLayer = Mockito.mock(CachedLayer.class);
    BlobDescriptor mockBlobDescriptor = Mockito.mock(BlobDescriptor.class);
    DescriptorDigest mockDescriptorDigest = Mockito.mock(DescriptorDigest.class);
    Mockito.when(mockCachedLayer.getBlobDescriptor()).thenReturn(mockBlobDescriptor);
    Mockito.when(mockBlobDescriptor.getDigest()).thenReturn(mockDescriptorDigest);
    return mockCachedLayer;
  }

  @Test
  public void testAddLayer() throws LayerPropertyNotFoundException, DuplicateLayerException {
    CachedLayerWithMetadata testCachedLayerWithMetadata =
        new CachedLayerWithMetadata(mockCachedLayer(), Mockito.mock(LayerMetadata.class));

    CacheMetadata cacheMetadata = new CacheMetadata();
    cacheMetadata.addLayer(testCachedLayerWithMetadata);

    Assert.assertEquals(
        ImmutableList.of(testCachedLayerWithMetadata), cacheMetadata.getLayers().asList());
  }

  @Test
  public void testFilter_byType()
      throws LayerPropertyNotFoundException, DuplicateLayerException,
          CacheMetadataCorruptedException {
    List<CachedLayer> mockLayers =
        Stream.generate(CacheMetadataTest::mockCachedLayer).limit(4).collect(Collectors.toList());

    LayerMetadata fakeBaseLayerMetadata =
        new LayerMetadata(CachedLayerType.BASE, Collections.emptyList(), null, -1);
    LayerMetadata fakeClassesLayerMetadata =
        new LayerMetadata(CachedLayerType.CLASSES, Collections.emptyList(), null, -1);

    List<CachedLayerWithMetadata> cachedLayers =
        Arrays.asList(
            new CachedLayerWithMetadata(mockLayers.get(0), fakeBaseLayerMetadata),
            new CachedLayerWithMetadata(mockLayers.get(1), fakeClassesLayerMetadata),
            new CachedLayerWithMetadata(mockLayers.get(2), fakeBaseLayerMetadata),
            new CachedLayerWithMetadata(mockLayers.get(3), fakeClassesLayerMetadata));

    CacheMetadata cacheMetadata = new CacheMetadata();
    for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
      cacheMetadata.addLayer(cachedLayer);
    }

    ImageLayers<CachedLayerWithMetadata> filteredLayers =
        cacheMetadata.filterLayers().byType(CachedLayerType.CLASSES).filter();

    Assert.assertEquals(2, filteredLayers.size());
    for (CachedLayerWithMetadata cachedLayer : filteredLayers.asList()) {
      Assert.assertEquals(fakeClassesLayerMetadata, cachedLayer.getMetadata());
    }
  }

  @Test
  public void testFilter_bySourceDirectories()
      throws LayerPropertyNotFoundException, DuplicateLayerException,
          CacheMetadataCorruptedException {
    List<CachedLayer> mockLayers =
        Stream.generate(CacheMetadataTest::mockCachedLayer).limit(5).collect(Collectors.toList());

    LayerMetadata fakeExpectedSourceDirectoresClassesLayerMetadata =
        new LayerMetadata(
            CachedLayerType.CLASSES,
            Collections.emptyList(),
            Arrays.asList("some/source/directory", "some/other/source/directory"),
            0);
    LayerMetadata fakeExpectedSourceDirectoresResourcesLayerMetadata =
        new LayerMetadata(
            CachedLayerType.RESOURCES,
            Collections.emptyList(),
            Arrays.asList("some/source/directory", "some/other/source/directory"),
            0);
    LayerMetadata fakeOtherSourceDirectoriesLayerMetadata =
        new LayerMetadata(
            CachedLayerType.CLASSES,
            Collections.emptyList(),
            Collections.singletonList("not/the/same/source/directory"),
            0);

    List<CachedLayerWithMetadata> cachedLayers =
        Arrays.asList(
            new CachedLayerWithMetadata(mockLayers.get(0), fakeOtherSourceDirectoriesLayerMetadata),
            new CachedLayerWithMetadata(
                mockLayers.get(1), fakeExpectedSourceDirectoresResourcesLayerMetadata),
            new CachedLayerWithMetadata(mockLayers.get(2), fakeOtherSourceDirectoriesLayerMetadata),
            new CachedLayerWithMetadata(
                mockLayers.get(3), fakeExpectedSourceDirectoresClassesLayerMetadata),
            new CachedLayerWithMetadata(
                mockLayers.get(4), fakeExpectedSourceDirectoresResourcesLayerMetadata));

    CacheMetadata cacheMetadata = new CacheMetadata();
    for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
      cacheMetadata.addLayer(cachedLayer);
    }

    ImageLayers<CachedLayerWithMetadata> filteredLayers =
        cacheMetadata
            .filterLayers()
            .bySourceDirectories(
                new HashSet<>(
                    Arrays.asList(
                        new File("some/source/directory"),
                        new File("some/other/source/directory"))))
            .filter();

    Assert.assertEquals(3, filteredLayers.size());
    Assert.assertEquals(
        fakeExpectedSourceDirectoresResourcesLayerMetadata, filteredLayers.get(0).getMetadata());
    Assert.assertEquals(
        fakeExpectedSourceDirectoresClassesLayerMetadata, filteredLayers.get(1).getMetadata());
    Assert.assertEquals(
        fakeExpectedSourceDirectoresResourcesLayerMetadata, filteredLayers.get(2).getMetadata());
  }
}
