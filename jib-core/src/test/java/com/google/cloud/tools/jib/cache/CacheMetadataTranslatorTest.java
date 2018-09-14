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
import com.google.cloud.tools.jib.cache.json.CacheMetadataTemplate;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.DigestException;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link CacheMetadataTranslator}. */
@RunWith(MockitoJUnitRunner.class)
public class CacheMetadataTranslatorTest {

  private static final String CLASSES_LAYER_SOURCE_FILE = "/some/source/path";
  private static final String CLASSES_LAYER_EXTRACTION_PATH = "/some/extraction/path";
  private static final FileTime CLASSES_LAYER_LAST_MODIFIED_TIME =
      FileTime.fromMillis(255073580723571L);

  @Mock private Path mockPath;

  private BlobDescriptor baseLayerBlobDescriptor;
  private DescriptorDigest baseLayerDiffId;
  private BlobDescriptor classesLayerBlobDescriptor;
  private DescriptorDigest classesLayerDiffId;

  @Before
  public void setUp() throws DigestException {
    baseLayerBlobDescriptor =
        new BlobDescriptor(
            631,
            DescriptorDigest.fromDigest(
                "sha256:5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef"));
    baseLayerDiffId =
        DescriptorDigest.fromDigest(
            "sha256:b56ae66c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a4647");
    classesLayerBlobDescriptor =
        new BlobDescriptor(
            223,
            DescriptorDigest.fromDigest(
                "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"));
    classesLayerDiffId =
        DescriptorDigest.fromDigest(
            "sha256:a3f3e99c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a8372");
  }

  @Test
  public void testFromTemplate()
      throws URISyntaxException, IOException, CacheMetadataCorruptedException {
    Path fakePath = Paths.get("fake/path");

    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("json/metadata-v3.json").toURI());

    // Deserializes into a metadata JSON object.
    CacheMetadataTemplate metadataTemplate =
        JsonTemplateMapper.readJsonFromFile(jsonFile, CacheMetadataTemplate.class);

    CacheMetadata cacheMetadata = CacheMetadataTranslator.fromTemplate(metadataTemplate, fakePath);

    List<CachedLayerWithMetadata> layers = cacheMetadata.getLayers().getLayers();

    // Checks that the base layer was translated correctly.
    CachedLayerWithMetadata baseLayer = layers.get(0);
    Assert.assertEquals(
        CacheFiles.getLayerFile(fakePath, baseLayerBlobDescriptor.getDigest()),
        baseLayer.getContentFile());
    Assert.assertEquals(baseLayerBlobDescriptor, baseLayer.getBlobDescriptor());
    Assert.assertEquals(baseLayerDiffId, baseLayer.getDiffId());

    // Checks that the classses layer was translated correctly.
    CachedLayerWithMetadata classesLayer = layers.get(1);
    Assert.assertEquals(
        CacheFiles.getLayerFile(fakePath, classesLayerBlobDescriptor.getDigest()),
        classesLayer.getContentFile());
    Assert.assertEquals(classesLayerBlobDescriptor, classesLayer.getBlobDescriptor());
    Assert.assertEquals(classesLayerDiffId, classesLayer.getDiffId());
    Assert.assertNotNull(classesLayer.getMetadata());
    Assert.assertEquals(
        CLASSES_LAYER_SOURCE_FILE,
        classesLayer.getMetadata().getEntries().get(0).getAbsoluteSourceFileString());
    Assert.assertEquals(
        CLASSES_LAYER_EXTRACTION_PATH,
        classesLayer.getMetadata().getEntries().get(0).getAbsoluteExtractionPathString());
    Assert.assertEquals(
        CLASSES_LAYER_LAST_MODIFIED_TIME, classesLayer.getMetadata().getLastModifiedTime());
  }

  @Test
  public void testToTemplate() throws URISyntaxException, IOException {
    Path jsonFile = Paths.get(Resources.getResource("json/metadata-v3.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    CachedLayer baseCachedLayer =
        new CachedLayer(mockPath, baseLayerBlobDescriptor, baseLayerDiffId);
    CachedLayerWithMetadata baseLayer = new CachedLayerWithMetadata(baseCachedLayer, null);

    CachedLayer classesCachedLayer =
        new CachedLayer(mockPath, classesLayerBlobDescriptor, classesLayerDiffId);
    LayerMetadata classesLayerMetadata =
        LayerMetadata.from(
            ImmutableList.of(
                new LayerEntry(
                    Paths.get(CLASSES_LAYER_SOURCE_FILE),
                    Paths.get(CLASSES_LAYER_EXTRACTION_PATH))),
            CLASSES_LAYER_LAST_MODIFIED_TIME);
    CachedLayerWithMetadata classesLayer =
        new CachedLayerWithMetadata(classesCachedLayer, classesLayerMetadata);

    CacheMetadata cacheMetadata =
        CacheMetadata.builder().addLayer(baseLayer).addLayer(classesLayer).build();

    CacheMetadataTemplate cacheMetadataTemplate = CacheMetadataTranslator.toTemplate(cacheMetadata);

    // Serializes the JSON object.
    ByteArrayOutputStream jsonStream = new ByteArrayOutputStream();
    JsonTemplateMapper.toBlob(cacheMetadataTemplate).writeTo(jsonStream);

    Assert.assertEquals(expectedJson, jsonStream.toString());
  }
}
