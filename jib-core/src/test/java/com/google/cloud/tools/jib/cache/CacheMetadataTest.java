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

package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.ImageLayers;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link CacheMetadata}. */
@RunWith(MockitoJUnitRunner.class)
public class CacheMetadataTest {

  private static CachedLayer mockCachedLayer() {
    CachedLayer mockCachedLayer = Mockito.mock(CachedLayer.class);
    BlobDescriptor mockBlobDescriptor = Mockito.mock(BlobDescriptor.class);
    DescriptorDigest mockDescriptorDigest = Mockito.mock(DescriptorDigest.class);
    Mockito.when(mockCachedLayer.getBlobDescriptor()).thenReturn(mockBlobDescriptor);
    Mockito.when(mockBlobDescriptor.getDigest()).thenReturn(mockDescriptorDigest);
    return mockCachedLayer;
  }

  @Mock private LayerEntry mockLayerEntry;

  @Test
  public void testAddLayer() {
    CachedLayerWithMetadata testCachedLayerWithMetadata =
        new CachedLayerWithMetadata(mockCachedLayer(), Mockito.mock(LayerMetadata.class));

    CacheMetadata cacheMetadata =
        CacheMetadata.builder().addLayer(testCachedLayerWithMetadata).build();

    Assert.assertEquals(
        Collections.singletonList(testCachedLayerWithMetadata),
        cacheMetadata.getLayers().getLayers());
  }

  @Test
  public void testFilter_doLayerEntriesMatchMetadataEntries_mismatchSize() {
    Assert.assertFalse(
        CacheMetadata.LayerFilter.doLayerEntriesMatchMetadataEntries(
            ImmutableList.of(mockLayerEntry), ImmutableList.of()));
  }

  @Test
  public void testFilter_doLayerEntriesMatchMetadataEntries_extractionPath() {
    ImmutableList<LayerMetadata.LayerMetadataEntry> metadataEntries =
        LayerMetadata.from(
                ImmutableList.of(
                    new LayerEntry(
                        Paths.get("anotherSourceFile"), Paths.get("anotherExtractionPath"))),
                FileTime.fromMillis(0))
            .getEntries();

    Assert.assertFalse(
        CacheMetadata.LayerFilter.doLayerEntriesMatchMetadataEntries(
            ImmutableList.of(new LayerEntry(Paths.get("sourceFile"), Paths.get("extractionPath"))),
            metadataEntries));
    Assert.assertTrue(
        CacheMetadata.LayerFilter.doLayerEntriesMatchMetadataEntries(
            ImmutableList.of(
                new LayerEntry(Paths.get("anotherSourceFile"), Paths.get("anotherExtractionPath"))),
            metadataEntries));
  }

  @Test
  public void testFilter_doLayerEntriesMatchMetadataEntries_pass() {
    LayerEntry layerEntry1 = new LayerEntry(Paths.get("sourceFile1"), Paths.get("extractionPath"));
    LayerEntry layerEntry2 = new LayerEntry(Paths.get("sourceFile2"), Paths.get("extractionPath"));
    LayerEntry layerEntry3 =
        new LayerEntry(Paths.get("sourceFile3"), Paths.get("anotherExtractionPath"));
    LayerEntry layerEntry4 =
        new LayerEntry(Paths.get("sourceFile4"), Paths.get("anotherExtractionPath"));

    ImmutableList<LayerEntry> layerEntries =
        ImmutableList.of(layerEntry1, layerEntry2, layerEntry3, layerEntry4);
    ImmutableList<LayerMetadata.LayerMetadataEntry> metadataEntries =
        LayerMetadata.from(
                ImmutableList.of(layerEntry1, layerEntry2, layerEntry3, layerEntry4),
                FileTime.fromMillis(0))
            .getEntries();

    Assert.assertTrue(
        CacheMetadata.LayerFilter.doLayerEntriesMatchMetadataEntries(
            layerEntries, metadataEntries));
  }

  @Test
  public void testFilter_bySourceFiles() throws CacheMetadataCorruptedException {
    List<CachedLayer> mockLayers =
        Stream.generate(CacheMetadataTest::mockCachedLayer).limit(6).collect(Collectors.toList());

    LayerEntry fakeLayerEntry1 =
        new LayerEntry(Paths.get("some/source/file"), Paths.get("extractionPath"));
    LayerEntry fakeLayerEntry2 =
        new LayerEntry(Paths.get("some/source/directory"), Paths.get("extractionPath"));

    LayerMetadata fakeExpectedSourceFilesClassesLayerMetadata =
        LayerMetadata.from(
            ImmutableList.of(fakeLayerEntry1, fakeLayerEntry2), FileTime.fromMillis(0));
    LayerMetadata fakeExpectedSourceFilesResourcesLayerMetadata =
        LayerMetadata.from(
            ImmutableList.of(fakeLayerEntry1, fakeLayerEntry2), FileTime.fromMillis(0));
    LayerMetadata fakeOtherSourceFilesLayerMetadata =
        LayerMetadata.from(
            ImmutableList.of(
                new LayerEntry(Paths.get("not/the/same/source/file"), Paths.get("extractionPath"))),
            FileTime.fromMillis(0));
    LayerMetadata fakeEmptySourceFilesLayerMetadata =
        LayerMetadata.from(ImmutableList.of(), FileTime.fromMillis(0));

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

    CacheMetadata.Builder cacheMetadataBuilder = CacheMetadata.builder();
    for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
      cacheMetadataBuilder.addLayer(cachedLayer);
    }
    CacheMetadata cacheMetadata = cacheMetadataBuilder.build();

    ImageLayers<CachedLayerWithMetadata> filteredLayers =
        cacheMetadata
            .filterLayers()
            .byLayerEntries(ImmutableList.of(fakeLayerEntry1, fakeLayerEntry2))
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
  public void testFilter_byNoEntries() throws CacheMetadataCorruptedException {
    List<CachedLayer> mockLayers =
        Stream.generate(CacheMetadataTest::mockCachedLayer).limit(2).collect(Collectors.toList());

    LayerEntry fakeLayerEntry =
        new LayerEntry(
            Paths.get("some/source/file", "some/source/directory"), Paths.get("extractionPath"));

    LayerMetadata fakeSourceFilesLayerMetadata =
        LayerMetadata.from(ImmutableList.of(fakeLayerEntry), FileTime.fromMillis(0));
    LayerMetadata fakeNoEntriesLayerMetadata =
        new LayerMetadata(ImmutableList.of(), FileTime.fromMillis(0));

    List<CachedLayerWithMetadata> cachedLayers =
        Arrays.asList(
            new CachedLayerWithMetadata(mockLayers.get(0), fakeSourceFilesLayerMetadata),
            new CachedLayerWithMetadata(mockLayers.get(1), fakeNoEntriesLayerMetadata));

    CacheMetadata.Builder cacheMetadataBuilder = CacheMetadata.builder();
    for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
      cacheMetadataBuilder.addLayer(cachedLayer);
    }
    CacheMetadata cacheMetadata = cacheMetadataBuilder.build();

    ImageLayers<CachedLayerWithMetadata> filteredLayers =
        cacheMetadata.filterLayers().byLayerEntries(ImmutableList.of()).filter();

    Assert.assertEquals(1, filteredLayers.size());
    Assert.assertEquals(fakeNoEntriesLayerMetadata, filteredLayers.get(0).getMetadata());
  }

  @Test
  public void testFilter_byEmptySourceFiles() throws CacheMetadataCorruptedException {
    List<CachedLayer> mockLayers =
        Stream.generate(CacheMetadataTest::mockCachedLayer).limit(2).collect(Collectors.toList());

    LayerEntry fakeLayerEntry =
        new LayerEntry(
            Paths.get("some/source/file", "some/source/directory"), Paths.get("extractionPath"));

    LayerMetadata fakeSourceFilesLayerMetadata =
        LayerMetadata.from(ImmutableList.of(fakeLayerEntry), FileTime.fromMillis(0));
    LayerMetadata fakeEmptySourceFilesLayerMetadata =
        LayerMetadata.from(ImmutableList.of(), FileTime.fromMillis(0));

    List<CachedLayerWithMetadata> cachedLayers =
        Arrays.asList(
            new CachedLayerWithMetadata(mockLayers.get(0), fakeSourceFilesLayerMetadata),
            new CachedLayerWithMetadata(mockLayers.get(1), fakeEmptySourceFilesLayerMetadata));

    CacheMetadata.Builder cacheMetadataBuilder = CacheMetadata.builder();
    for (CachedLayerWithMetadata cachedLayer : cachedLayers) {
      cacheMetadataBuilder.addLayer(cachedLayer);
    }
    CacheMetadata cacheMetadata = cacheMetadataBuilder.build();

    ImageLayers<CachedLayerWithMetadata> filteredLayers =
        cacheMetadata.filterLayers().byLayerEntries(ImmutableList.of()).filter();

    Assert.assertEquals(1, filteredLayers.size());
    Assert.assertEquals(fakeEmptySourceFilesLayerMetadata, filteredLayers.get(0).getMetadata());
  }
}
