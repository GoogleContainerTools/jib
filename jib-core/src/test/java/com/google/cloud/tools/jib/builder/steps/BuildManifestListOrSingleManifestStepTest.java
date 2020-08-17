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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link BuildManifestListOrSingleManifest}. */
@RunWith(MockitoJUnitRunner.class)
public class BuildManifestListOrSingleManifestStepTest {

  @Mock private ProgressEventDispatcher.Factory progressDispatcherFactory;
  @Mock private BuildContext buildContext;
  @Mock private Layer layer;

  private Image image1;
  private Image image2;

  @Before
  public void setUp() {
    Mockito.when(buildContext.getEventHandlers()).thenReturn(EventHandlers.NONE);
    Mockito.doReturn(V22ManifestTemplate.class).when(buildContext).getTargetFormat();
    Mockito.when(layer.getBlobDescriptor()).thenReturn(new BlobDescriptor(0, null));

    image1 =
        Image.builder(V22ManifestTemplate.class)
            .setArchitecture("amd64")
            .setOs("linux")
            .addLayer(new PreparedLayer.Builder(layer).setName("resources").build())
            .build();
    image2 =
        Image.builder(V22ManifestTemplate.class)
            .setArchitecture("arm64")
            .setOs("ubuntu")
            .addLayer(new PreparedLayer.Builder(layer).setName("classes").build())
            .build();
  }

  @Test
  public void testCall_singleManifest() throws IOException {
    String manifestResponse =
        "{\n"
            + "   \"schemaVersion\":2,\n"
            + "   \"mediaType\":\"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "   \"config\":{\n"
            + "      \"mediaType\":\"application/vnd.docker.container.image.v1+json\",\n"
            + "      \"digest\":\"sha256:1b2ff280940537177565443144a81319ad48528fd35d1cdc38cbde07f24f6912\",\n"
            + "      \"size\":158\n"
            + "   },\n"
            + "   \"layers\":[\n"
            + "      {\n"
            + "         \"mediaType\":\"application/vnd.docker.image.rootfs.diff.tar.gzip\",\n"
            + "         \"size\":0\n"
            + "      }\n"
            + "   ]\n"
            + "}";

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

    Assert.assertEquals(158, manifest.getContainerConfiguration().getSize());
  }

  @Test
  public void testCall_manifestList() throws IOException {
    String manifestListResponse =
        "{\n"
            + "   \"schemaVersion\":2,\n"
            + "   \"mediaType\":\"application/vnd.docker.distribution.manifest.list.v2+json\",\n"
            + "   \"manifests\":[\n"
            + "      {\n"
            + "         \"mediaType\":\"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "         \"digest\":\"sha256:9467fc431ac5dd84dafdc13f75111fc467cd57aff4732edda8c9e0bbcabe0183\",\n"
            + "         \"size\":338,\n"
            + "         \"platform\":{\n"
            + "            \"architecture\":\"amd64\",\n"
            + "            \"os\":\"linux\"\n"
            + "         }\n"
            + "      },\n"
            + "      {\n"
            + "         \"mediaType\":\"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "         \"digest\":\"sha256:6c2ff8d62273a93207b5e636d4ecf0ba597de01d21767b11437b48d6e5ff0b53\",\n"
            + "         \"size\":338,\n"
            + "         \"platform\":{\n"
            + "            \"architecture\":\"arm64\",\n"
            + "            \"os\":\"ubuntu\"\n"
            + "         }\n"
            + "      }\n"
            + "   ]\n"
            + "}";

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
        Arrays.asList("sha256:6c2ff8d62273a93207b5e636d4ecf0ba597de01d21767b11437b48d6e5ff0b53"),
        manifestList.getDigestsForPlatform("arm64", "ubuntu"));
  }
}
