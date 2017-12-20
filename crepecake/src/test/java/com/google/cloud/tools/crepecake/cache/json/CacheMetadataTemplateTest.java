package com.google.cloud.tools.crepecake.cache.json;

import com.google.cloud.tools.crepecake.cache.CachedLayerType;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.json.JsonHelper;
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
import org.junit.Assert;
import org.junit.Test;

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
            .setSourceDirectories(Collections.singletonList(Paths.get("some/source/path")))
            .setLastModifiedTime(255073580723571L);

    cacheMetadataTemplate.addLayer(baseLayerTemplate).addLayer(classesLayerTemplate);

    // Serializes the JSON object.
    ByteArrayOutputStream jsonStream = new ByteArrayOutputStream();
    JsonHelper.writeJson(jsonStream, cacheMetadataTemplate);

    System.out.println(expectedJson);
    Assert.assertEquals(expectedJson, jsonStream.toString());
  }
}
