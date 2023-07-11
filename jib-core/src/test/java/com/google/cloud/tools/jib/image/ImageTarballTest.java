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

package com.google.cloud.tools.jib.image;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.docker.json.DockerManifestEntryTemplate;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.OciIndexTemplate;
import com.google.cloud.tools.jib.image.json.OciManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link ImageTarball}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageTarballTest {

  private Path fileA;
  private Path fileB;
  private DescriptorDigest fakeDigestA;
  private DescriptorDigest fakeDigestB;

  @Mock private Layer mockLayer1;
  @Mock private Layer mockLayer2;

  @BeforeEach
  void setup() throws URISyntaxException, IOException, DigestException {
    fileA = Paths.get(Resources.getResource("core/fileA").toURI());
    fileB = Paths.get(Resources.getResource("core/fileB").toURI());
    long fileASize = Files.size(fileA);
    long fileBSize = Files.size(fileB);

    fakeDigestA =
        DescriptorDigest.fromHash(
            "5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc5");
    fakeDigestB =
        DescriptorDigest.fromHash(
            "5994471abb01112afcc18159f6cc74b4f511b99806da59b3caf5a9c173cacfc6");

    Mockito.when(mockLayer1.getBlob()).thenReturn(Blobs.from(fileA));
    Mockito.when(mockLayer1.getBlobDescriptor())
        .thenReturn(new BlobDescriptor(fileASize, fakeDigestA));
    Mockito.when(mockLayer1.getDiffId()).thenReturn(fakeDigestA);
    Mockito.when(mockLayer2.getBlob()).thenReturn(Blobs.from(fileB));
    Mockito.when(mockLayer2.getBlobDescriptor())
        .thenReturn(new BlobDescriptor(fileBSize, fakeDigestB));
    Mockito.when(mockLayer2.getDiffId()).thenReturn(fakeDigestB);
  }

  @Test
  void testWriteTo_docker()
      throws InvalidImageReferenceException, IOException, LayerPropertyNotFoundException {
    Image testImage =
        Image.builder(V22ManifestTemplate.class).addLayer(mockLayer1).addLayer(mockLayer2).build();
    ImageTarball imageTarball =
        new ImageTarball(
            testImage,
            ImageReference.parse("my/image:tag"),
            ImmutableSet.of("tag", "another-tag", "tag3"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    imageTarball.writeTo(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(in)) {

      // Verifies layer with fileA was added.
      TarArchiveEntry headerFileALayer = tarArchiveInputStream.getNextTarEntry();
      Assert.assertEquals(fakeDigestA.getHash() + ".tar.gz", headerFileALayer.getName());
      String fileAString =
          CharStreams.toString(
              new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
      Assert.assertEquals(Blobs.writeToString(Blobs.from(fileA)), fileAString);

      // Verifies layer with fileB was added.
      TarArchiveEntry headerFileBLayer = tarArchiveInputStream.getNextTarEntry();
      Assert.assertEquals(fakeDigestB.getHash() + ".tar.gz", headerFileBLayer.getName());
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
      DockerManifestEntryTemplate manifest =
          JsonTemplateMapper.readListOfJson(manifestJson, DockerManifestEntryTemplate.class).get(0);
      Assert.assertEquals(
          ImmutableList.of("my/image:tag", "my/image:another-tag", "my/image:tag3"),
          manifest.getRepoTags());
    }
  }

  @Test
  void testWriteTo_oci()
      throws InvalidImageReferenceException, IOException, LayerPropertyNotFoundException {
    Image testImage =
        Image.builder(OciManifestTemplate.class).addLayer(mockLayer1).addLayer(mockLayer2).build();
    ImageTarball imageTarball =
        new ImageTarball(
            testImage,
            ImageReference.parse("my/image:tag"),
            ImmutableSet.of("tag", "another-tag", "tag3"));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    imageTarball.writeTo(out);
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(in)) {

      // Verifies layer with fileA was added.
      TarArchiveEntry headerFileALayer = tarArchiveInputStream.getNextTarEntry();
      Assert.assertEquals("blobs/sha256/" + fakeDigestA.getHash(), headerFileALayer.getName());
      String fileAString =
          CharStreams.toString(
              new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
      Assert.assertEquals(Blobs.writeToString(Blobs.from(fileA)), fileAString);

      // Verifies layer with fileB was added.
      TarArchiveEntry headerFileBLayer = tarArchiveInputStream.getNextTarEntry();
      Assert.assertEquals("blobs/sha256/" + fakeDigestB.getHash(), headerFileBLayer.getName());
      String fileBString =
          CharStreams.toString(
              new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
      Assert.assertEquals(Blobs.writeToString(Blobs.from(fileB)), fileBString);

      // Verifies container configuration was added.
      TarArchiveEntry headerContainerConfiguration = tarArchiveInputStream.getNextTarEntry();
      Assert.assertEquals(
          "blobs/sha256/011212cff4d5d6b18c7d3a00a7a2701514a1fdd3ec0d250a03756f84f3d955d4",
          headerContainerConfiguration.getName());
      JsonTemplateMapper.readJson(tarArchiveInputStream, ContainerConfigurationTemplate.class);

      // Verifies manifest was added.
      TarArchiveEntry headerManifest = tarArchiveInputStream.getNextTarEntry();
      Assert.assertEquals(
          "blobs/sha256/1543d061159a8d6877087938bfd62681cdeff873e1fa3e1fcf12dec358c112a4",
          headerManifest.getName());
      JsonTemplateMapper.readJson(tarArchiveInputStream, OciManifestTemplate.class);

      // Verifies oci-layout was added.
      TarArchiveEntry headerOciLayout = tarArchiveInputStream.getNextTarEntry();
      Assert.assertEquals("oci-layout", headerOciLayout.getName());
      String ociLayoutJson =
          CharStreams.toString(
              new InputStreamReader(tarArchiveInputStream, StandardCharsets.UTF_8));
      Assert.assertEquals("{\"imageLayoutVersion\": \"1.0.0\"}", ociLayoutJson);

      // Verifies index.json was added.
      TarArchiveEntry headerIndex = tarArchiveInputStream.getNextTarEntry();
      Assert.assertEquals("index.json", headerIndex.getName());
      OciIndexTemplate index =
          JsonTemplateMapper.readJson(tarArchiveInputStream, OciIndexTemplate.class);
      BuildableManifestTemplate.ContentDescriptorTemplate indexManifest =
          index.getManifests().get(0);
      Assert.assertEquals(
          "1543d061159a8d6877087938bfd62681cdeff873e1fa3e1fcf12dec358c112a4",
          indexManifest.getDigest().getHash());
    }
  }
}
