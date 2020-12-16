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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link Containerizer}. */
public class ContainerizerTest {

  @Test
  public void testTo() throws CacheDirectoryCreationException {
    RegistryImage registryImage = RegistryImage.named(ImageReference.of(null, "repository", null));
    DockerDaemonImage dockerDaemonImage =
        DockerDaemonImage.named(ImageReference.of(null, "repository", null));
    TarImage tarImage =
        TarImage.at(Paths.get("ignored")).named(ImageReference.of(null, "repository", null));

    verifyTo(Containerizer.to(registryImage));
    verifyTo(Containerizer.to(dockerDaemonImage));
    verifyTo(Containerizer.to(tarImage));
  }

  private void verifyTo(Containerizer containerizer) throws CacheDirectoryCreationException {
    Assert.assertTrue(containerizer.getAdditionalTags().isEmpty());
    Assert.assertFalse(containerizer.getExecutorService().isPresent());
    Assert.assertEquals(
        Containerizer.DEFAULT_BASE_CACHE_DIRECTORY,
        containerizer.getBaseImageLayersCacheDirectory());
    Assert.assertNotEquals(
        Containerizer.DEFAULT_BASE_CACHE_DIRECTORY,
        containerizer.getApplicationLayersCacheDirectory());
    Assert.assertFalse(containerizer.getAllowInsecureRegistries());
    Assert.assertEquals("jib-core", containerizer.getToolName());

    ExecutorService executorService = MoreExecutors.newDirectExecutorService();
    containerizer
        .withAdditionalTag("tag1")
        .withAdditionalTag("tag2")
        .setExecutorService(executorService)
        .setBaseImageLayersCache(Paths.get("base/image/layers"))
        .setApplicationLayersCache(Paths.get("application/layers"))
        .setAllowInsecureRegistries(true)
        .setToolName("tool");

    Assert.assertEquals(ImmutableSet.of("tag1", "tag2"), containerizer.getAdditionalTags());
    Assert.assertTrue(containerizer.getExecutorService().isPresent());
    Assert.assertSame(executorService, containerizer.getExecutorService().get());
    Assert.assertEquals(
        Paths.get("base/image/layers"), containerizer.getBaseImageLayersCacheDirectory());
    Assert.assertEquals(
        Paths.get("application/layers"), containerizer.getApplicationLayersCacheDirectory());
    Assert.assertTrue(containerizer.getAllowInsecureRegistries());
    Assert.assertEquals("tool", containerizer.getToolName());
  }

  @Test
  public void testWithAdditionalTag() {
    DockerDaemonImage dockerDaemonImage =
        DockerDaemonImage.named(ImageReference.of(null, "repository", null));
    Containerizer containerizer = Containerizer.to(dockerDaemonImage);

    containerizer.withAdditionalTag("tag");
    try {
      containerizer.withAdditionalTag("+invalid+");
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals("invalid tag '+invalid+'", ex.getMessage());
    }
  }

  @Test
  public void testGetImageConfiguration_registryImage() throws InvalidImageReferenceException {
    CredentialRetriever credentialRetriever = Mockito.mock(CredentialRetriever.class);
    Containerizer containerizer =
        Containerizer.to(
            RegistryImage.named("registry/image").addCredentialRetriever(credentialRetriever));

    ImageConfiguration imageConfiguration = containerizer.getImageConfiguration();
    Assert.assertEquals("registry/image", imageConfiguration.getImage().toString());
    Assert.assertEquals(
        Arrays.asList(credentialRetriever), imageConfiguration.getCredentialRetrievers());
  }

  @Test
  public void testGetImageConfiguration_dockerDaemonImage() throws InvalidImageReferenceException {
    Containerizer containerizer = Containerizer.to(DockerDaemonImage.named("docker/deamon/image"));

    ImageConfiguration imageConfiguration = containerizer.getImageConfiguration();
    Assert.assertEquals("docker/deamon/image", imageConfiguration.getImage().toString());
    Assert.assertEquals(0, imageConfiguration.getCredentialRetrievers().size());
  }

  @Test
  public void testGetImageConfiguration_tarImage() throws InvalidImageReferenceException {
    Containerizer containerizer =
        Containerizer.to(TarImage.at(Paths.get("output/file")).named("tar/image"));

    ImageConfiguration imageConfiguration = containerizer.getImageConfiguration();
    Assert.assertEquals("tar/image", imageConfiguration.getImage().toString());
    Assert.assertEquals(0, imageConfiguration.getCredentialRetrievers().size());
  }

  @Test
  public void testGetApplicationLayersCacheDirectory_defaults()
      throws InvalidImageReferenceException, CacheDirectoryCreationException, IOException {
    Containerizer containerizer = Containerizer.to(RegistryImage.named("registry/image"));
    Path applicationLayersCache = containerizer.getApplicationLayersCacheDirectory();
    Path expectedCacheDir =
        Paths.get(System.getProperty("java.io.tmpdir"))
            .resolve("jib-core-application-layers-cache");
    Assert.assertTrue(Files.isSameFile(expectedCacheDir, applicationLayersCache));
  }
}
