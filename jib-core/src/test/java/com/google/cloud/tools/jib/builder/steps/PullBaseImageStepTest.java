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

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.ImagesAndRegistryClient;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.BadContainerConfigurationFormatException;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.ManifestAndConfig;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link PullBaseImageStep}. */
@RunWith(MockitoJUnitRunner.class)
public class PullBaseImageStepTest {

  private final ContainerConfigurationTemplate containerConfigJson =
      new ContainerConfigurationTemplate();
  private final ManifestAndConfig manifestAndConfig =
      new ManifestAndConfig(new V22ManifestTemplate(), containerConfigJson);

  @Mock private ProgressEventDispatcher.Factory progressDispatcherFactory;
  @Mock private BuildContext buildContext;
  @Mock private RegistryClient registryClient;
  @Mock private ImageConfiguration imageConfiguration;
  @Mock private ContainerConfiguration containerConfig;
  @Mock private Cache cache;
  @Mock private EventHandlers eventHandlers;

  private PullBaseImageStep pullBaseImageStep;

  @Before
  public void setUp() {
    containerConfigJson.setArchitecture("slim arch");
    containerConfigJson.setOs("fat system");
    Mockito.when(buildContext.getBaseImageConfiguration()).thenReturn(imageConfiguration);
    Mockito.when(buildContext.getEventHandlers()).thenReturn(eventHandlers);
    Mockito.when(buildContext.getBaseImageLayersCache()).thenReturn(cache);
    RegistryClient.Factory registryClientFactory = Mockito.mock(RegistryClient.Factory.class);
    Mockito.when(buildContext.newBaseImageRegistryClientFactory())
        .thenReturn(registryClientFactory);
    Mockito.when(registryClientFactory.newRegistryClient()).thenReturn(registryClient);
    Mockito.when(buildContext.getContainerConfiguration()).thenReturn(containerConfig);
    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("slim arch", "fat system")));

    pullBaseImageStep = new PullBaseImageStep(buildContext, progressDispatcherFactory);
  }

  @Test
  public void testCall_digestBaseImage()
      throws LayerPropertyNotFoundException, IOException, RegistryException,
          LayerCountMismatchException, BadContainerConfigurationFormatException,
          CacheCorruptedException, CredentialRetrievalException, InvalidImageReferenceException {
    ImageReference imageReference =
        ImageReference.parse(
            "awesome@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    Assert.assertTrue(imageReference.getDigest().isPresent());
    Mockito.when(imageConfiguration.getImage()).thenReturn(imageReference);
    Mockito.when(cache.retrieveMetadata(imageReference)).thenReturn(Optional.of(manifestAndConfig));

    ImagesAndRegistryClient result = pullBaseImageStep.call();
    Assert.assertEquals("fat system", result.images.get(0).getOs());
    Assert.assertEquals(registryClient, result.registryClient);
  }

  @Test
  public void testCall_offlineMode_notCached()
      throws LayerPropertyNotFoundException, RegistryException, LayerCountMismatchException,
          BadContainerConfigurationFormatException, CacheCorruptedException,
          CredentialRetrievalException, InvalidImageReferenceException {
    Mockito.when(imageConfiguration.getImage()).thenReturn(ImageReference.parse("cat"));
    Mockito.when(buildContext.isOffline()).thenReturn(true);

    try {
      pullBaseImageStep.call();
      Assert.fail();
    } catch (IOException ex) {
      Assert.assertEquals(
          "Cannot run Jib in offline mode; cat not found in local Jib cache", ex.getMessage());
    }
  }

  @Test
  public void testCall_offlineMode_cached()
      throws LayerPropertyNotFoundException, RegistryException, LayerCountMismatchException,
          BadContainerConfigurationFormatException, CacheCorruptedException,
          CredentialRetrievalException, InvalidImageReferenceException, IOException {
    ImageReference imageReference = ImageReference.parse("cat");
    Mockito.when(imageConfiguration.getImage()).thenReturn(imageReference);
    Mockito.when(buildContext.isOffline()).thenReturn(true);
    Mockito.when(cache.retrieveMetadata(imageReference)).thenReturn(Optional.of(manifestAndConfig));

    ImagesAndRegistryClient result = pullBaseImageStep.call();
    Assert.assertEquals("fat system", result.images.get(0).getOs());
    Assert.assertNull(result.registryClient);

    Mockito.verify(buildContext, Mockito.never()).newBaseImageRegistryClientFactory();
  }

  @Test
  public void testCall_mismatchedPlatformOfCachedManifest_offlineMode()
      throws LayerPropertyNotFoundException, IOException, RegistryException,
          LayerCountMismatchException, BadContainerConfigurationFormatException,
          CacheCorruptedException, CredentialRetrievalException, InvalidImageReferenceException {
    ImageReference imageReference = ImageReference.parse("cat");
    Mockito.when(imageConfiguration.getImage()).thenReturn(imageReference);
    Mockito.when(cache.retrieveMetadata(imageReference)).thenReturn(Optional.of(manifestAndConfig));
    Mockito.when(buildContext.isOffline()).thenReturn(true);
    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("testArch", "testOS")));

    try {
      pullBaseImageStep.call();
      Assert.fail();
    } catch (IllegalStateException ex) {
      Assert.assertEquals(
          "The cached base image manifest does not match the configured platform due to the "
              + "current implementation of limited platform support. As a workaround, re-run Jib "
              + "online once to re-cache the right image manifest.",
          ex.getMessage());
      Mockito.verify(eventHandlers)
          .dispatch(
              LogEvent.debug("platform of the cached manifest does not match the requested one"));
    }
  }

  @Test
  public void testCall_mismatchedPlatformOfCachedManifest_baseImageDigest()
      throws LayerPropertyNotFoundException, IOException, RegistryException,
          LayerCountMismatchException, BadContainerConfigurationFormatException,
          CacheCorruptedException, CredentialRetrievalException, InvalidImageReferenceException {
    ImageReference imageReference =
        ImageReference.parse(
            "awesome@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    Mockito.when(buildContext.getBaseImageConfiguration())
        .thenReturn(ImageConfiguration.builder(imageReference).build());
    Mockito.when(cache.retrieveMetadata(imageReference)).thenReturn(Optional.of(manifestAndConfig));
    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("testArch", "testOS")));

    Mockito.<ManifestAndDigest<?>>when(
            registryClient.pullManifest(
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
        .thenThrow(new RegistryException("fake error only to verify calling registryClient"));

    try {
      pullBaseImageStep.call();
      Assert.fail();
    } catch (RegistryException ex) {
      Assert.assertEquals("fake error only to verify calling registryClient", ex.getMessage());
      Mockito.verify(eventHandlers)
          .dispatch(
              LogEvent.debug("platform of the cached manifest does not match the requested one"));
    }
  }

  @Test
  public void testObtainPlatformSpecificImageManifest() throws IOException, RegistryException {
    String manifestListJson =
        " {\n"
            + "   \"schemaVersion\": 2,\n"
            + "   \"mediaType\": \"application/vnd.docker.distribution.manifest.list.v2+json\",\n"
            + "   \"manifests\": [\n"
            + "      {\n"
            + "         \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "         \"size\": 424,\n"
            + "         \"digest\": \"sha256:111111111111111111111111111111111111111111111111111111111111111\",\n"
            + "         \"platform\": {\n"
            + "            \"architecture\": \"arm64\",\n"
            + "            \"os\": \"linux\"\n"
            + "         }\n"
            + "      },\n"
            + "      {\n"
            + "         \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "         \"size\": 425,\n"
            + "         \"digest\": \"sha256:222222222222222222222222222222222222222222222222222222222222222222\",\n"
            + "         \"platform\": {\n"
            + "            \"architecture\": \"targetArchitecture\",\n"
            + "            \"os\": \"targetOS\"\n"
            + "         }\n"
            + "      }\n"
            + "   ]\n"
            + "}";

    V22ManifestListTemplate manifestList =
        JsonTemplateMapper.readJson(manifestListJson, V22ManifestListTemplate.class);
    ManifestAndDigest<?> manifest = Mockito.mock(ManifestAndDigest.class);
    Mockito.<ManifestAndDigest<?>>when(
            registryClient.pullManifest(
                "sha256:222222222222222222222222222222222222222222222222222222222222222222"))
        .thenReturn(manifest);

    ManifestAndDigest<?> returnManifest =
        pullBaseImageStep.obtainPlatformSpecificImageManifest(
            registryClient, manifestList, new Platform("targetArchitecture", "targetOS"));

    Assert.assertSame(manifest, returnManifest);
  }
}
