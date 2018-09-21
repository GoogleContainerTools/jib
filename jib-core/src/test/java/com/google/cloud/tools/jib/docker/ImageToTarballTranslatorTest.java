/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.docker;

import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.docker.json.DockerLoadManifestTemplate;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link ImageToTarballTranslator}. */
public class ImageToTarballTranslatorTest {

  @Test
  public void testToTarballBlob()
      throws InvalidImageReferenceException, IOException, URISyntaxException,
          LayerPropertyNotFoundException, DigestException {
    Path fileA = Paths.get(Resources.getResource("fileA").toURI());
    Path fileB = Paths.get(Resources.getResource("fileB").toURI());

    DescriptorDigest fakeDigestA =
        DescriptorDigest.fromHash(
            "5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc5");
    DescriptorDigest fakeDigestB =
        DescriptorDigest.fromHash(
            "5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc6");

    Image<CachedLayer> testImage =
        Image.<CachedLayer>builder()
            .addLayer(new CachedLayer(fileA, new BlobDescriptor(fakeDigestA), fakeDigestA))
            .addLayer(new CachedLayer(fileB, new BlobDescriptor(fakeDigestB), fakeDigestB))
            .build();

    Blob tarballBlob =
        new ImageToTarballTranslator(testImage).toTarballBlob(ImageReference.parse("my/image:tag"));

    try (ByteArrayInputStream tarballBytesStream =
            new ByteArrayInputStream(Blobs.writeToByteArray(tarballBlob));
        TarArchiveInputStream tarArchiveInputStream =
            new TarArchiveInputStream(tarballBytesStream)) {
      // Verifies layer with fileA was added.
      TarArchiveEntry headerFileALayer = tarArchiveInputStream.getNextTarEntry();
      Assert.assertEquals("fileA", headerFileALayer.getName());
      String fileAString =
          CharStreams.toString(
              new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
      Assert.assertEquals(Blobs.writeToString(Blobs.from(fileA)), fileAString);

      // Verifies layer with fileB was added.
      TarArchiveEntry headerFileBLayer = tarArchiveInputStream.getNextTarEntry();
      Assert.assertEquals("fileB", headerFileBLayer.getName());
      String fileBString =
          CharStreams.toString(
              new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
      Assert.assertEquals(Blobs.writeToString(Blobs.from(fileB)), fileBString);

      // Verifies container configuration was added.
      TarArchiveEntry headerContainerConfiguration = tarArchiveInputStream.getNextTarEntry();
      Assert.assertEquals("config.json", headerContainerConfiguration.getName());
      String containerConfigJson =
          CharStreams.toString(
              new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
      JsonTemplateMapper.readJson(containerConfigJson, ContainerConfigurationTemplate.class);

      // Verifies manifest was added.
      TarArchiveEntry headerManifest = tarArchiveInputStream.getNextTarEntry();
      Assert.assertEquals("manifest.json", headerManifest.getName());
      String manifestJson =
          CharStreams.toString(
              new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
      JsonTemplateMapper.readListOfJson(manifestJson, DockerLoadManifestTemplate.class);
    }
  }
}
