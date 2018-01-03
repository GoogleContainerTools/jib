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

import com.google.cloud.tools.crepecake.cache.CachedLayerType;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.json.JsonTemplateMapper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    Path jsonFile =
        Paths.get(getClass().getClassLoader().getResource("json/metadata.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

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
    CacheMetadataLayerPropertiesObjectTemplate propertiesTemplate =
        new CacheMetadataLayerPropertiesObjectTemplate()
            .setSourceFiles(Collections.singletonList(Paths.get("some/source/path").toString()))
            .setLastModifiedTime(255073580723571L);
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
            .setProperties(propertiesTemplate);

    cacheMetadataTemplate.addLayer(baseLayerTemplate).addLayer(classesLayerTemplate);

    // Serializes the JSON object.
    ByteArrayOutputStream jsonStream = new ByteArrayOutputStream();
    JsonTemplateMapper.writeJson(jsonStream, cacheMetadataTemplate);

    Assert.assertEquals(expectedJson, jsonStream.toString());
  }

  @Test
  public void testFromJson() throws URISyntaxException, IOException, DigestException {
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
    Assert.assertNull(baseLayerTemplate.getProperties());

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
    Assert.assertNotNull(classesLayerTemplate.getProperties());
    Assert.assertEquals(
        Collections.singletonList(Paths.get("some/source/path").toString()),
        classesLayerTemplate.getProperties().getSourceFiles());
    Assert.assertEquals(
        255073580723571L, classesLayerTemplate.getProperties().getLastModifiedTime());
  }
}
