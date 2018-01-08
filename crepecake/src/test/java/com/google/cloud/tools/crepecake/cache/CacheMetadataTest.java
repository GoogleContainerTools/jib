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
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
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
        new CachedLayerWithMetadata(
            mockCachedLayer(),
            Mockito.mock(CachedLayerType.class),
            Mockito.mock(LayerMetadata.class));

    CacheMetadata cacheMetadata = new CacheMetadata();
    cacheMetadata.addLayer(testCachedLayerWithMetadata);

    Assert.assertEquals(
        Collections.singletonList(testCachedLayerWithMetadata),
        cacheMetadata.getLayers().getLayers());
  }

  @Test
  public void testFilter_byType()
      throws LayerPropertyNotFoundException, DuplicateLayerException,
          CacheMetadataCorruptedException {
    List<CachedLayer> mockLayers =
        Stream.generate(CacheMetadataTest::mockCachedLayer).limit(4).collect(Collectors.toList());

    LayerMetadata mockClassesLayerMetadata = Mockito.mock(LayerMetadata.class);

    List<CachedLayerWithMetadata> cachedLayers =
        Arrays.asList(
            new CachedLayerWithMetadata(mockLayers.get(0), CachedLayerType.BASE, null),
            new CachedLayerWithMetadata(
                mockLayers.get(1), CachedLayerType.CLASSES, mockClassesLayerMetadata),
            new CachedLayerWithMetadata(mockLayers.get(2), CachedLayerType.BASE, null),
            new CachedLayerWithMetadata(
                mockLayers.get(3), CachedLayerType.CLASSES, mockClassesLayerMetadata));

    CacheMetadata cacheMetadata = new CacheMetadata();
    for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
      cacheMetadata.addLayer(cachedLayer);
    }

    ImageLayers<CachedLayerWithMetadata> filteredLayers =
        cacheMetadata.filterLayers().byType(CachedLayerType.CLASSES).filter();

    Assert.assertEquals(2, filteredLayers.size());
    for (CachedLayerWithMetadata cachedLayer : filteredLayers) {
      Assert.assertEquals(mockClassesLayerMetadata, cachedLayer.getMetadata());
    }
  }

  @Test
  public void testFilter_bySourceFiles()
      throws LayerPropertyNotFoundException, DuplicateLayerException,
          CacheMetadataCorruptedException {
    List<CachedLayer> mockLayers =
        Stream.generate(CacheMetadataTest::mockCachedLayer).limit(5).collect(Collectors.toList());

    LayerMetadata fakeExpectedSourceFilesClassesLayerMetadata =
        new LayerMetadata(
            Arrays.asList("some/source/file", "some/source/directory"), FileTime.fromMillis(0));
    LayerMetadata fakeExpectedSourceFilesResourcesLayerMetadata =
        new LayerMetadata(
            Arrays.asList("some/source/file", "some/source/directory"), FileTime.fromMillis(0));
    LayerMetadata fakeOtherSourceFilesLayerMetadata =
        new LayerMetadata(
            Collections.singletonList("not/the/same/source/file"), FileTime.fromMillis(0));

    List<CachedLayerWithMetadata> cachedLayers =
        Arrays.asList(
            new CachedLayerWithMetadata(
                mockLayers.get(0), CachedLayerType.CLASSES, fakeOtherSourceFilesLayerMetadata),
            new CachedLayerWithMetadata(
                mockLayers.get(1),
                CachedLayerType.RESOURCES,
                fakeExpectedSourceFilesResourcesLayerMetadata),
            new CachedLayerWithMetadata(
                mockLayers.get(2), CachedLayerType.CLASSES, fakeOtherSourceFilesLayerMetadata),
            new CachedLayerWithMetadata(
                mockLayers.get(3),
                CachedLayerType.CLASSES,
                fakeExpectedSourceFilesClassesLayerMetadata),
            new CachedLayerWithMetadata(
                mockLayers.get(4),
                CachedLayerType.RESOURCES,
                fakeExpectedSourceFilesResourcesLayerMetadata));

    CacheMetadata cacheMetadata = new CacheMetadata();
    for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
      cacheMetadata.addLayer(cachedLayer);
    }

    ImageLayers<CachedLayerWithMetadata> filteredLayers =
        cacheMetadata
            .filterLayers()
            .bySourceFiles(
                new HashSet<>(
                    Arrays.asList(
                        Paths.get("some/source/file"), Paths.get("some/source/directory"))))
            .filter();

    Assert.assertEquals(3, filteredLayers.size());
    Assert.assertEquals(
        fakeExpectedSourceFilesResourcesLayerMetadata, filteredLayers.get(0).getMetadata());
    Assert.assertEquals(
        fakeExpectedSourceFilesClassesLayerMetadata, filteredLayers.get(1).getMetadata());
    Assert.assertEquals(
        fakeExpectedSourceFilesResourcesLayerMetadata, filteredLayers.get(2).getMetadata());
  }
}
