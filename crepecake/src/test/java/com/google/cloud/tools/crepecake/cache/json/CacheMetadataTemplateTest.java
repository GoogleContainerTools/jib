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

package com.google.cloud.tools.crepecake.cache.json;

import com.google.cloud.tools.crepecake.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.crepecake.cache.CachedLayerType;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.json.JsonTemplateMapper;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link CacheMetadataTemplate}. */
public class CacheMetadataTemplateTest {

  @Test
  public void testToJson() throws URISyntaxException, IOException, DigestException {
    // Loads the expected JSON string.
    File jsonFile = new File(getClass().getClassLoader().getResource("json/metadata.json").toURI());
    final String expectedJson =
        CharStreams.toString(new InputStreamReader(new FileInputStream(jsonFile), Charsets.UTF_8));

    CacheMetadataTemplate cacheMetadataTemplate = new CacheMetadataTemplate();

    // Adds a base layer.
    CacheMetadataLayerObjectTemplate baseLayerTemplate =
        new CacheMetadataLayerObjectTemplate()
            .setType(CachedLayerType.BASE)
            .setSize(631)
            .setDigest(
                DescriptorDigest.fromDigest(
                    "sha256:5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef"))
            .setDiffId(
                DescriptorDigest.fromDigest(
                    "sha256:b56ae66c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a4647"));

    // Adds an application layer.
    CacheMetadataLayerObjectTemplate classesLayerTemplate =
        new CacheMetadataLayerObjectTemplate()
            .setType(CachedLayerType.CLASSES)
            .setSize(223)
            .setDigest(
                DescriptorDigest.fromDigest(
                    "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"))
            .setDiffId(
                DescriptorDigest.fromDigest(
                    "sha256:a3f3e99c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a8372"))
            .setExistsOn(Collections.singletonList("some/image/tag"))
            .setSourceDirectories(
                Collections.singletonList(Paths.get("some/source/path").toString()))
            .setLastModifiedTime(255073580723571L);

    cacheMetadataTemplate.addLayer(baseLayerTemplate).addLayer(classesLayerTemplate);

    // Serializes the JSON object.
    ByteArrayOutputStream jsonStream = new ByteArrayOutputStream();
    JsonTemplateMapper.writeJson(jsonStream, cacheMetadataTemplate);

    Assert.assertEquals(expectedJson, jsonStream.toString());
  }

  @Test
  public void testFromJson()
      throws URISyntaxException, IOException, DigestException, CacheMetadataCorruptedException {
    // Loads the expected JSON string.
    File jsonFile = new File(getClass().getClassLoader().getResource("json/metadata.json").toURI());

    // Deserializes into a metadata JSON object.
    CacheMetadataTemplate metadataTemplate =
        JsonTemplateMapper.readJsonFromFile(jsonFile, CacheMetadataTemplate.class);

    List<CacheMetadataLayerObjectTemplate> layers = metadataTemplate.getLayers();

    Assert.assertEquals(2, layers.size());

    // Checks the first layer is correct.
    CacheMetadataLayerObjectTemplate baseLayerTemplate = layers.get(0);
    Assert.assertEquals(CachedLayerType.BASE, baseLayerTemplate.getType());
    Assert.assertEquals(631, baseLayerTemplate.getSize());
    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef"),
        baseLayerTemplate.getDigest());
    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:b56ae66c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a4647"),
        baseLayerTemplate.getDiffId());
    Assert.assertEquals(0, baseLayerTemplate.getExistsOn().size());

    // Checks the second layer is correct.
    CacheMetadataLayerObjectTemplate classesLayerTemplate = layers.get(1);
    Assert.assertEquals(CachedLayerType.CLASSES, classesLayerTemplate.getType());
    Assert.assertEquals(223, classesLayerTemplate.getSize());
    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad"),
        classesLayerTemplate.getDigest());
    Assert.assertEquals(
        DescriptorDigest.fromDigest(
            "sha256:a3f3e99c29370df48e7377c8f9baa744a3958058a766793f821dadcb144a8372"),
        classesLayerTemplate.getDiffId());
    Assert.assertEquals(
        Collections.singletonList("some/image/tag"), classesLayerTemplate.getExistsOn());
    Assert.assertEquals(
        Collections.singletonList(Paths.get("some/source/path").toString()),
        classesLayerTemplate.getSourceDirectories());
    Assert.assertEquals(255073580723571L, classesLayerTemplate.getLastModifiedTime());

    // Checks that the base layer does not have properties.
    try {
      baseLayerTemplate.getSourceDirectories();
      Assert.fail("Should not be able to get source directories for a base layer");
    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "Properties is not a valid field for non-application layer type", ex.getMessage());
    }
  }

  @Test
  public void testFromJson_noPropertiesForApplicationLayer()
      throws URISyntaxException, IOException {
    // Loads the expected JSON string.
    File jsonFile =
        new File(getClass().getClassLoader().getResource("json/metadata_corrupted.json").toURI());

    // Deserializes into a metadata JSON object.
    CacheMetadataTemplate metadataTemplate =
        JsonTemplateMapper.readJsonFromFile(jsonFile, CacheMetadataTemplate.class);

    try {
      metadataTemplate.getLayers().get(0).getSourceDirectories();
      Assert.fail("Corrupted metadata should not have source directories");
    } catch (CacheMetadataCorruptedException ex) {
      Assert.assertEquals("Properties not found for application layer type", ex.getMessage());
    }
  }
}
