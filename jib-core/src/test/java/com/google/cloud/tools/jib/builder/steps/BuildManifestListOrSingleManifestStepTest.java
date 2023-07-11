/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link BuildManifestListOrSingleManifest}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuildManifestListOrSingleManifestStepTest {

  @Mock private ProgressEventDispatcher.Factory progressDispatcherFactory;
  @Mock private BuildContext buildContext;
  @Mock private Layer layer;

  private Image image1;
  private Image image2;

  @BeforeEach
  void setUp() {
    Mockito.when(buildContext.getEventHandlers()).thenReturn(EventHandlers.NONE);
    Mockito.doReturn(V22ManifestTemplate.class).when(buildContext).getTargetFormat();
    Mockito.when(layer.getBlobDescriptor()).thenReturn(new BlobDescriptor(0, null));

    image1 =
        Image.builder(V22ManifestTemplate.class)
            .setArchitecture("amd64")
            .setOs("linux")
            .addLayer(layer)
            .build();
    image2 =
        Image.builder(V22ManifestTemplate.class)
            .setArchitecture("arm64")
            .setOs("windows")
            .addLayer(layer)
            .build();
  }

  @Test
  void testCall_singleManifest() throws IOException {

    // Expected manifest JSON
    //  {
    //  "schemaVersion":2,
    //  "mediaType":"application/vnd.docker.distribution.manifest.v2+json",
    //  "config":{
    //    "mediaType":"application/vnd.docker.container.image.v1+json",
    //    "digest":"sha256:1b2ff280940537177565443144a81319ad48528fd35d1cdc38cbde07f24f6912",
    //    "size":158
    //  },
    //  "layers":[
    //    {
    //      "mediaType":"application/vnd.docker.image.rootfs.diff.tar.gzip",
    //      "size":0
    //    }
    //  ]
    // }

    ManifestTemplate manifestTemplate =
        new BuildManifestListOrSingleManifestStep(
                buildContext, progressDispatcherFactory, Arrays.asList(image1))
            .call();

    Assert.assertTrue(manifestTemplate instanceof V22ManifestTemplate);
    V22ManifestTemplate manifest = (V22ManifestTemplate) manifestTemplate;
    Assert.assertEquals(2, manifest.getSchemaVersion());
    Assert.assertEquals(
        "application/vnd.docker.distribution.manifest.v2+json", manifest.getManifestMediaType());
    Assert.assertEquals(
        "sha256:1b2ff280940537177565443144a81319ad48528fd35d1cdc38cbde07f24f6912",
        manifest.getContainerConfiguration().getDigest().toString());
    Assert.assertEquals(0, manifest.getLayers().get(0).getSize());
    Assert.assertEquals(158, manifest.getContainerConfiguration().getSize());
  }

  @Test
  void testCall_manifestList() throws IOException {

    // Expected Manifest List JSON
    //  {
    //  "schemaVersion":2,
    //  "mediaType":"application/vnd.docker.distribution.manifest.list.v2+json",
    //  "manifests":[
    //    {
    //      "mediaType":"application/vnd.docker.distribution.manifest.v2+json",
    //      "digest":"sha256:9467fc431ac5dd84dafdc13f75111fc467cd57aff4732edda8c9e0bbcabe0183",
    //      "size":338,
    //      "platform":{
    //        "architecture":"amd64",
    //        "os":"linux"
    //      }
    //    },
    //    {
    //      "mediaType":"application/vnd.docker.distribution.manifest.v2+json",
    //      "digest":"sha256:439351c848845c46a3952f28416992b66003361d00943b6cdb04b6d5533f02bf",
    //      "size":338,
    //      "platform":{
    //        "architecture":"arm64",
    //        "os":"windows"
    //      }
    //    }
    //  ]
    // }

    ManifestTemplate manifestTemplate =
        new BuildManifestListOrSingleManifestStep(
                buildContext, progressDispatcherFactory, Arrays.asList(image1, image2))
            .call();

    Assert.assertTrue(manifestTemplate instanceof V22ManifestListTemplate);
    V22ManifestListTemplate manifestList = (V22ManifestListTemplate) manifestTemplate;
    Assert.assertEquals(2, manifestList.getSchemaVersion());
    Assert.assertEquals(
        Arrays.asList("sha256:9467fc431ac5dd84dafdc13f75111fc467cd57aff4732edda8c9e0bbcabe0183"),
        manifestList.getDigestsForPlatform("amd64", "linux"));
    Assert.assertEquals(
        Arrays.asList("sha256:439351c848845c46a3952f28416992b66003361d00943b6cdb04b6d5533f02bf"),
        manifestList.getDigestsForPlatform("arm64", "windows"));
  }

  @Test
  void testCall_emptyImagesList() throws IOException {
    try {
      new BuildManifestListOrSingleManifestStep(
              buildContext, progressDispatcherFactory, Collections.emptyList())
          .call();
      Assert.fail();
    } catch (IllegalStateException ex) {
      Assert.assertEquals("no images given", ex.getMessage());
    }
  }
}
