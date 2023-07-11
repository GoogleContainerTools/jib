/*
 * Copyright 2019 Google LLC.
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

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.ImageDetails;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.LocalBaseImageSteps.LocalImage;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.docker.CliDockerClient;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalBaseImageStepsTest {

  @TempDir static Path temporaryFolder;

  private final TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider();

  @Mock private BuildContext buildContext;
  @Mock private EventHandlers eventHandlers;
  @Mock private ProgressEventDispatcher.Factory progressEventDispatcherFactory;
  @Mock private ProgressEventDispatcher progressEventDispatcher;
  @Mock private ProgressEventDispatcher.Factory childFactory;
  @Mock private ProgressEventDispatcher childDispatcher;

  private static Path getResource(String resource) throws URISyntaxException {
    return Paths.get(Resources.getResource(resource).toURI());
  }

  @BeforeEach
  void setup() throws IOException, CacheDirectoryCreationException {
    Mockito.when(buildContext.getExecutorService())
        .thenReturn(MoreExecutors.newDirectExecutorService());
    Mockito.when(buildContext.getBaseImageLayersCache())
        .thenReturn(Cache.withDirectory(temporaryFolder));
    Mockito.when(buildContext.getEventHandlers()).thenReturn(eventHandlers);
    Mockito.when(progressEventDispatcherFactory.create(Mockito.anyString(), Mockito.anyLong()))
        .thenReturn(progressEventDispatcher);
    Mockito.when(progressEventDispatcher.newChildProducer()).thenReturn(childFactory);
    Mockito.when(childFactory.create(Mockito.anyString(), Mockito.anyLong()))
        .thenReturn(childDispatcher);
  }

  @AfterEach
  void tearDownAfterAll() {

    tempDirectoryProvider.close();
  }

  @Test
  void testCacheDockerImageTar_validDocker() throws Exception {
    Path dockerBuild = getResource("core/extraction/docker-save.tar");
    LocalImage result =
        LocalBaseImageSteps.cacheDockerImageTar(
            buildContext, dockerBuild, progressEventDispatcherFactory, new TempDirectoryProvider());

    Mockito.verify(progressEventDispatcher, Mockito.times(2)).newChildProducer();
    Assert.assertEquals(2, result.layers.size());
    Assert.assertEquals(
        "5e701122d3347fae0758cd5b7f0692c686fcd07b0e7fd9c4a125fbdbbedc04dd",
        result.layers.get(0).get().getDiffId().getHash());
    Assert.assertEquals(
        "0011328ac5dfe3dde40c7c5e0e00c98d1833a3aeae2bfb668cf9eb965c229c7f",
        result.layers.get(0).get().getBlobDescriptor().getDigest().getHash());
    Assert.assertEquals(
        "f1ac3015bcbf0ada4750d728626eb10f0f585199e2b667dcd79e49f0e926178e",
        result.layers.get(1).get().getDiffId().getHash());
    Assert.assertEquals(
        "c10ef24a5cef5092bbcb5a5666721cff7b86ce978c203a958d1fc86ee6c19f94",
        result.layers.get(1).get().getBlobDescriptor().getDigest().getHash());
    Assert.assertEquals(2, result.configurationTemplate.getLayerCount());
  }

  @Test
  @Ignore("sonar - currently disabled")
  void testCacheDockerImageTar_validTar() throws Exception {
    Path tarBuild = getResource("core/extraction/jib-image.tar");
    LocalImage result =
        LocalBaseImageSteps.cacheDockerImageTar(
            buildContext, tarBuild, progressEventDispatcherFactory, tempDirectoryProvider);

    Mockito.verify(progressEventDispatcher, Mockito.times(2)).newChildProducer();
    Assert.assertEquals(2, result.layers.size());
    Assert.assertEquals(
        "5e701122d3347fae0758cd5b7f0692c686fcd07b0e7fd9c4a125fbdbbedc04dd",
        result.layers.get(0).get().getDiffId().getHash());
    Assert.assertEquals(
        "0011328ac5dfe3dde40c7c5e0e00c98d1833a3aeae2bfb668cf9eb965c229c7f",
        result.layers.get(0).get().getBlobDescriptor().getDigest().getHash());
    Assert.assertEquals(
        "f1ac3015bcbf0ada4750d728626eb10f0f585199e2b667dcd79e49f0e926178e",
        result.layers.get(1).get().getDiffId().getHash());
    Assert.assertEquals(
        "c10ef24a5cef5092bbcb5a5666721cff7b86ce978c203a958d1fc86ee6c19f94",
        result.layers.get(1).get().getBlobDescriptor().getDigest().getHash());
    Assert.assertEquals(2, result.configurationTemplate.getLayerCount());
  }

  @Test
  @Ignore("sonar - currently disabled")
  void testGetCachedDockerImage()
      throws IOException, DigestException, CacheDirectoryCreationException, CacheCorruptedException,
          URISyntaxException {
    String dockerInspectJson =
        "{\"Size\": 0,"
            + "\"Id\": \"sha256:066872f17ae819f846a6d5abcfc3165abe13fb0a157640fa8cb7af81077670c0\","
            + "\"RootFS\": { \"Layers\": ["
            + "  \"sha256:5e701122d3347fae0758cd5b7f0692c686fcd07b0e7fd9c4a125fbdbbedc04dd\","
            + "  \"sha256:f1ac3015bcbf0ada4750d728626eb10f0f585199e2b667dcd79e49f0e926178e\" ] } }";
    ImageDetails dockerImageDetails =
        JsonTemplateMapper.readJson(dockerInspectJson, CliDockerClient.DockerImageDetails.class);
    Path cachePath = temporaryFolder;
    Files.createDirectories(cachePath.resolve("local/config"));
    Cache cache = Cache.withDirectory(cachePath);

    // Image not in cache
    Optional<LocalImage> localImage =
        LocalBaseImageSteps.getCachedDockerImage(cache, dockerImageDetails);
    Assert.assertFalse(localImage.isPresent());

    // Config in cache, but not layers
    String configHash = "066872f17ae819f846a6d5abcfc3165abe13fb0a157640fa8cb7af81077670c0";
    Files.copy(
        getResource("core/extraction/test-cache/local/config/" + configHash),
        cachePath.resolve("local/config/" + configHash));
    localImage = LocalBaseImageSteps.getCachedDockerImage(cache, dockerImageDetails);
    Assert.assertFalse(localImage.isPresent());

    // One layer missing
    String diffId = "5e701122d3347fae0758cd5b7f0692c686fcd07b0e7fd9c4a125fbdbbedc04dd";
    String digest = "0011328ac5dfe3dde40c7c5e0e00c98d1833a3aeae2bfb668cf9eb965c229c7f";
    Files.createDirectories(cachePath.resolve("local").resolve(diffId));
    Files.copy(
        getResource("core/extraction/test-cache/local/" + diffId + "/" + digest),
        cachePath.resolve("local").resolve(diffId).resolve(digest));
    localImage = LocalBaseImageSteps.getCachedDockerImage(cache, dockerImageDetails);
    Assert.assertFalse(localImage.isPresent());

    // Image fully in cache
    diffId = "f1ac3015bcbf0ada4750d728626eb10f0f585199e2b667dcd79e49f0e926178e";
    digest = "c10ef24a5cef5092bbcb5a5666721cff7b86ce978c203a958d1fc86ee6c19f94";
    Files.createDirectories(cachePath.resolve("local").resolve(diffId));
    Files.copy(
        getResource("core/extraction/test-cache/local/" + diffId + "/" + digest),
        cachePath.resolve("local").resolve(diffId).resolve(digest));
    localImage = LocalBaseImageSteps.getCachedDockerImage(cache, dockerImageDetails);
    Assert.assertTrue(localImage.isPresent());
    LocalImage image = localImage.get();
    Assert.assertEquals(2, image.configurationTemplate.getLayerCount());
    Assert.assertEquals(2, image.layers.size());
  }

  @Test
  @Ignore("sonar - currently disabled")
  void testIsGzipped() throws URISyntaxException, IOException {
    Assert.assertTrue(
        LocalBaseImageSteps.isGzipped(getResource("core/extraction/compressed.tar.gz")));
    Assert.assertFalse(
        LocalBaseImageSteps.isGzipped(getResource("core/extraction/not-compressed.tar")));
  }
}
