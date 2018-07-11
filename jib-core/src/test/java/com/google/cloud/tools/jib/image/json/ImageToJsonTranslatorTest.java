/*
 * Copyright 2017 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.image.json;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.configuration.Port.Protocol;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link ImageToJsonTranslator}. */
public class ImageToJsonTranslatorTest {

  private ImageToJsonTranslator imageToJsonTranslator;

  @Before
  public void setUp() throws DigestException, LayerPropertyNotFoundException {
    Image.Builder<CachedLayer> testImageBuilder = Image.builder();

    testImageBuilder.setEnvironmentVariable("VAR1", "VAL1");
    testImageBuilder.setEnvironmentVariable("VAR2", "VAL2");

    testImageBuilder.setEntrypoint(Arrays.asList("some", "entrypoint", "command"));

    testImageBuilder.setJavaArguments(Arrays.asList("arg1", "arg2"));

    testImageBuilder.setExposedPorts(
        ImmutableList.of(
            new Port(1000, Protocol.TCP),
            new Port(2000, Protocol.TCP),
            new Port(3000, Protocol.UDP)));

    DescriptorDigest fakeDigest =
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad");
    CachedLayer fakeLayer =
        new CachedLayer(Mockito.mock(Path.class), new BlobDescriptor(1000, fakeDigest), fakeDigest);
    testImageBuilder.addLayer(fakeLayer);

    imageToJsonTranslator = new ImageToJsonTranslator(testImageBuilder.build());
  }

  @Test
  public void testGetContainerConfiguration() throws IOException, URISyntaxException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource("json/containerconfig.json").toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    // Translates the image to the container configuration and writes the JSON string.
    Blob containerConfigurationBlob = imageToJsonTranslator.getContainerConfigurationBlob();

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    containerConfigurationBlob.writeTo(byteArrayOutputStream);

    Assert.assertEquals(
        expectedJson, new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
  }

  @Test
  public void testGetManifest_v22() throws URISyntaxException, IOException {
    testGetManifest(V22ManifestTemplate.class, "json/translated_v22manifest.json");
  }

  @Test
  public void testGetManifest_oci() throws URISyntaxException, IOException {
    testGetManifest(OCIManifestTemplate.class, "json/translated_ocimanifest.json");
  }

  /** Tests translation of image to {@link BuildableManifestTemplate}. */
  private <T extends BuildableManifestTemplate> void testGetManifest(
      Class<T> manifestTemplateClass, String translatedJsonFilename)
      throws URISyntaxException, IOException {
    // Loads the expected JSON string.
    Path jsonFile = Paths.get(Resources.getResource(translatedJsonFilename).toURI());
    String expectedJson = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);

    // Translates the image to the manifest and writes the JSON string.
    Blob containerConfigurationBlob = imageToJsonTranslator.getContainerConfigurationBlob();
    BlobDescriptor blobDescriptor =
        containerConfigurationBlob.writeTo(ByteStreams.nullOutputStream());
    T manifestTemplate =
        imageToJsonTranslator.getManifestTemplate(manifestTemplateClass, blobDescriptor);

    ByteArrayOutputStream jsonStream = new ByteArrayOutputStream();
    JsonTemplateMapper.toBlob(manifestTemplate).writeTo(jsonStream);

    Assert.assertEquals(expectedJson, new String(jsonStream.toByteArray(), StandardCharsets.UTF_8));
  }
}
