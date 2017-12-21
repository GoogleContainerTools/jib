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

package com.google.cloud.tools.crepecake.json;

import com.google.cloud.tools.crepecake.blob.Blob;
import com.google.cloud.tools.crepecake.blob.BlobDescriptor;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.Image;
import com.google.cloud.tools.crepecake.image.Layer;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.crepecake.image.ReferenceLayer;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link ImageToJsonTranslator}. */
public class ImageToJsonTranslatorTest {

  private ImageToJsonTranslator imageToJsonTranslator;

  @Before
  public void setUp()
      throws DigestException, LayerPropertyNotFoundException, DuplicateLayerException {
    Image testImage = new Image();

    testImage.setEnvironmentVariable("VAR1", "VAL1");
    testImage.setEnvironmentVariable("VAR2", "VAL2");

    testImage.setEntrypoint(Arrays.asList("some", "entrypoint", "command"));

    DescriptorDigest fakeDigest =
        DescriptorDigest.fromDigest(
            "sha256:8c662931926fa990b41da3c9f42663a537ccd498130030f9149173a0493832ad");
    Layer fakeLayer = new ReferenceLayer(new BlobDescriptor(1000, fakeDigest), fakeDigest);
    testImage.addLayer(fakeLayer);

    imageToJsonTranslator = new ImageToJsonTranslator(testImage);
  }

  @Test
  public void testGetContainerConfiguration()
      throws IOException, LayerPropertyNotFoundException, URISyntaxException {
    // Loads the expected JSON string.
    File jsonFile =
        new File(getClass().getClassLoader().getResource("json/containerconfig.json").toURI());
    final String expectedJson =
        CharStreams.toString(
            new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8));

    // Translates the image to the container configuration and writes the JSON string.
    Blob containerConfigurationBlob = imageToJsonTranslator.getContainerConfigurationBlob();

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    containerConfigurationBlob.writeTo(byteArrayOutputStream);

    Assert.assertEquals(expectedJson, byteArrayOutputStream.toString());
  }

  @Test
  public void testGetManifest()
      throws URISyntaxException, IOException, LayerPropertyNotFoundException {
    // Loads the expected JSON string.
    File jsonFile =
        new File(getClass().getClassLoader().getResource("json/translatedmanifest.json").toURI());
    final String expectedJson =
        CharStreams.toString(
            new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8));

    // Translates the image to the manifest and writes the JSON string.
    Blob containerConfigurationBlob = imageToJsonTranslator.getContainerConfigurationBlob();
    BlobDescriptor blobDescriptor =
        containerConfigurationBlob.writeTo(ByteStreams.nullOutputStream());
    Blob manifestBlob = imageToJsonTranslator.getManifestBlob(blobDescriptor);

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    manifestBlob.writeTo(byteArrayOutputStream);

    Assert.assertEquals(expectedJson, byteArrayOutputStream.toString());
  }
}
