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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.ImageLayers;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
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
  public void testAddLayer() throws LayerPropertyNotFoundException {
    CachedLayerWithMetadata testCachedLayerWithMetadata =
        new CachedLayerWithMetadata(mockCachedLayer(), Mockito.mock(LayerMetadata.class));

    CacheMetadata cacheMetadata = new CacheMetadata();
    cacheMetadata.addLayer(testCachedLayerWithMetadata);

    Assert.assertEquals(
        Collections.singletonList(testCachedLayerWithMetadata),
        cacheMetadata.getLayers().getLayers());
  }

  @Test
  public void testFilter_bySourceFiles()
      throws LayerPropertyNotFoundException, CacheMetadataCorruptedException {
    List<CachedLayer> mockLayers =
        Stream.generate(CacheMetadataTest::mockCachedLayer).limit(6).collect(Collectors.toList());

    LayerMetadata fakeExpectedSourceFilesClassesLayerMetadata =
        new LayerMetadata(
            Arrays.asList("some/source/file", "some/source/directory"), FileTime.fromMillis(0));
    LayerMetadata fakeExpectedSourceFilesResourcesLayerMetadata =
        new LayerMetadata(
            Arrays.asList("some/source/file", "some/source/directory"), FileTime.fromMillis(0));
    LayerMetadata fakeOtherSourceFilesLayerMetadata =
        new LayerMetadata(
            Collections.singletonList("not/the/same/source/file"), FileTime.fromMillis(0));
    LayerMetadata fakeEmptySourceFilesLayerMetadata =
        new LayerMetadata(Collections.emptyList(), FileTime.fromMillis(0));

    List<CachedLayerWithMetadata> cachedLayers =
        Arrays.asList(
            new CachedLayerWithMetadata(mockLayers.get(0), fakeOtherSourceFilesLayerMetadata),
            new CachedLayerWithMetadata(
                mockLayers.get(1), fakeExpectedSourceFilesResourcesLayerMetadata),
            new CachedLayerWithMetadata(mockLayers.get(2), fakeOtherSourceFilesLayerMetadata),
            new CachedLayerWithMetadata(mockLayers.get(3), fakeEmptySourceFilesLayerMetadata),
            new CachedLayerWithMetadata(
                mockLayers.get(4), fakeExpectedSourceFilesClassesLayerMetadata),
            new CachedLayerWithMetadata(
                mockLayers.get(5), fakeExpectedSourceFilesResourcesLayerMetadata));

    CacheMetadata cacheMetadata = new CacheMetadata();
    for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
      cacheMetadata.addLayer(cachedLayer);
    }

    ImageLayers<CachedLayerWithMetadata> filteredLayers =
        cacheMetadata
            .filterLayers()
            .bySourceFiles(
                Arrays.asList(
                    Paths.get("some", "source", "file"), Paths.get("some", "source", "directory")))
            .filter();

    Assert.assertEquals(3, filteredLayers.size());
    Assert.assertEquals(
        fakeExpectedSourceFilesResourcesLayerMetadata, filteredLayers.get(0).getMetadata());
    Assert.assertEquals(
        fakeExpectedSourceFilesClassesLayerMetadata, filteredLayers.get(1).getMetadata());
    Assert.assertEquals(
        fakeExpectedSourceFilesResourcesLayerMetadata, filteredLayers.get(2).getMetadata());
  }

  @Test
  public void testFilter_byEmptySourceFiles()
      throws LayerPropertyNotFoundException, CacheMetadataCorruptedException {
    List<CachedLayer> mockLayers =
        Stream.generate(CacheMetadataTest::mockCachedLayer).limit(2).collect(Collectors.toList());

    LayerMetadata fakeSourceFilesLayerMetadata =
        new LayerMetadata(
            Arrays.asList("some/source/file", "some/source/directory"), FileTime.fromMillis(0));
    LayerMetadata fakeEmptySourceFilesLayerMetadata =
        new LayerMetadata(Collections.emptyList(), FileTime.fromMillis(0));

    List<CachedLayerWithMetadata> cachedLayers =
        Arrays.asList(
            new CachedLayerWithMetadata(mockLayers.get(0), fakeSourceFilesLayerMetadata),
            new CachedLayerWithMetadata(mockLayers.get(1), fakeEmptySourceFilesLayerMetadata));

    CacheMetadata cacheMetadata = new CacheMetadata();
    for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
      cacheMetadata.addLayer(cachedLayer);
    }

    ImageLayers<CachedLayerWithMetadata> filteredLayers =
        cacheMetadata.filterLayers().bySourceFiles(Collections.emptyList()).filter();

    Assert.assertEquals(1, filteredLayers.size());
    Assert.assertEquals(fakeEmptySourceFilesLayerMetadata, filteredLayers.get(0).getMetadata());
  }
}
