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

import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link Containerizer}. */
@RunWith(MockitoJUnitRunner.class)
public class ContainerizerTest {

  @Mock private ExecutorService mockExecutorService;
  @Mock private EventHandlers mockEventHandlers;

  @Test
  public void testTo() throws CacheDirectoryCreationException {
    RegistryImage registryImage = RegistryImage.named(ImageReference.of(null, "repository", null));
    DockerDaemonImage dockerDaemonImage =
        DockerDaemonImage.named(ImageReference.of(null, "repository", null));
    TarImage tarImage =
        TarImage.named(ImageReference.of(null, "repository", null)).saveTo(Paths.get("ignored"));

    verifyTo(Containerizer.to(registryImage), registryImage);
    verifyTo(Containerizer.to(dockerDaemonImage), dockerDaemonImage);
    verifyTo(Containerizer.to(tarImage), tarImage);
  }

  private void verifyTo(Containerizer containerizer, TargetImage expectedTargetImage)
      throws CacheDirectoryCreationException {
    Assert.assertSame(expectedTargetImage, containerizer.getTargetImage());
    Assert.assertTrue(containerizer.getAdditionalTags().isEmpty());
    Assert.assertFalse(containerizer.getExecutorService().isPresent());
    Assert.assertFalse(containerizer.getEventHandlers().isPresent());
    Assert.assertEquals(
        Containerizer.DEFAULT_BASE_CACHE_DIRECTORY,
        containerizer.getBaseImageLayersCacheDirectory());
    Assert.assertNotEquals(
        Containerizer.DEFAULT_BASE_CACHE_DIRECTORY,
        containerizer.getApplicationLayersCacheDirectory());
    Assert.assertFalse(containerizer.getAllowInsecureRegistries());
    Assert.assertEquals("jib-core", containerizer.getToolName());

    containerizer
        .withAdditionalTag("tag1")
        .withAdditionalTag("tag2")
        .setExecutorService(mockExecutorService)
        .setEventHandlers(mockEventHandlers)
        .setBaseImageLayersCache(Paths.get("base/image/layers"))
        .setApplicationLayersCache(Paths.get("application/layers"))
        .setAllowInsecureRegistries(true)
        .setToolName("tool");

    Assert.assertSame(expectedTargetImage, containerizer.getTargetImage());
    Assert.assertEquals(ImmutableSet.of("tag1", "tag2"), containerizer.getAdditionalTags());
    Assert.assertTrue(containerizer.getExecutorService().isPresent());
    Assert.assertEquals(mockExecutorService, containerizer.getExecutorService().get());
    Assert.assertTrue(containerizer.getEventHandlers().isPresent());
    Assert.assertEquals(mockEventHandlers, containerizer.getEventHandlers().get());
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
}
