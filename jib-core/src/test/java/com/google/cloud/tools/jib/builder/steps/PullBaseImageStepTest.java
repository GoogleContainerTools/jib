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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
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
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.BadContainerConfigurationFormatException;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.ImageMetadataTemplate;
import com.google.cloud.tools.jib.image.json.ManifestAndConfigTemplate;
import com.google.cloud.tools.jib.image.json.UnlistedPlatformInManifestListException;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.security.DigestException;
import java.util.Arrays;
import java.util.List;
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
  public void testCall_scratch_singlePlatform()
      throws LayerPropertyNotFoundException, IOException, RegistryException,
          LayerCountMismatchException, BadContainerConfigurationFormatException,
          CacheCorruptedException, CredentialRetrievalException {
    Mockito.when(imageConfiguration.getImage()).thenReturn(ImageReference.scratch());
    ImagesAndRegistryClient result = pullBaseImageStep.call();

    Assert.assertEquals(1, result.images.size());
    Assert.assertEquals("slim arch", result.images.get(0).getArchitecture());
    Assert.assertEquals("fat system", result.images.get(0).getOs());
    Assert.assertNull(result.registryClient);
  }

  @Test
  public void testCall_scratch_multiplePlatforms()
      throws LayerPropertyNotFoundException, IOException, RegistryException,
          LayerCountMismatchException, BadContainerConfigurationFormatException,
          CacheCorruptedException, CredentialRetrievalException {
    Mockito.when(imageConfiguration.getImage()).thenReturn(ImageReference.scratch());
    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(
            ImmutableSet.of(
                new Platform("architecture1", "os1"), new Platform("architecture2", "os2")));
    ImagesAndRegistryClient result = pullBaseImageStep.call();

    Assert.assertEquals(2, result.images.size());
    Assert.assertEquals("architecture1", result.images.get(0).getArchitecture());
    Assert.assertEquals("os1", result.images.get(0).getOs());
    Assert.assertEquals("architecture2", result.images.get(1).getArchitecture());
    Assert.assertEquals("os2", result.images.get(1).getOs());
    Assert.assertNull(result.registryClient);
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

    ContainerConfigurationTemplate containerConfigJson = new ContainerConfigurationTemplate();
    containerConfigJson.setArchitecture("slim arch");
    containerConfigJson.setOs("fat system");
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            new V22ManifestTemplate(), containerConfigJson, "sha256:digest");
    ImageMetadataTemplate imageMetadata =
        new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig));
    Mockito.when(cache.retrieveMetadata(imageReference)).thenReturn(Optional.of(imageMetadata));

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

    ContainerConfigurationTemplate containerConfigJson = new ContainerConfigurationTemplate();
    containerConfigJson.setArchitecture("slim arch");
    containerConfigJson.setOs("fat system");
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            new V22ManifestTemplate(), containerConfigJson, "sha256:digest");
    ImageMetadataTemplate imageMetadata =
        new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig));
    Mockito.when(cache.retrieveMetadata(imageReference)).thenReturn(Optional.of(imageMetadata));

    ImagesAndRegistryClient result = pullBaseImageStep.call();
    Assert.assertEquals("fat system", result.images.get(0).getOs());
    Assert.assertNull(result.registryClient);

    Mockito.verify(buildContext, Mockito.never()).newBaseImageRegistryClientFactory();
  }

  @Test
  public void testLookUpPlatformSpecificImageManifest()
      throws IOException, UnlistedPlatformInManifestListException {
    String manifestListJson =
        " {\n"
            + "   \"schemaVersion\": 2,\n"
            + "   \"mediaType\": \"application/vnd.docker.distribution.manifest.list.v2+json\",\n"
            + "   \"manifests\": [\n"
            + "      {\n"
            + "         \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "         \"size\": 424,\n"
            + "         \"digest\": \"sha256:1111111111111111111111111111111111111111111111111111111111111111\",\n"
            + "         \"platform\": {\n"
            + "            \"architecture\": \"arm64\",\n"
            + "            \"os\": \"linux\"\n"
            + "         }\n"
            + "      },\n"
            + "      {\n"
            + "         \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "         \"size\": 425,\n"
            + "         \"digest\": \"sha256:2222222222222222222222222222222222222222222222222222222222222222\",\n"
            + "         \"platform\": {\n"
            + "            \"architecture\": \"targetArchitecture\",\n"
            + "            \"os\": \"targetOS\"\n"
            + "         }\n"
            + "      }\n"
            + "   ]\n"
            + "}";

    V22ManifestListTemplate manifestList =
        JsonTemplateMapper.readJson(manifestListJson, V22ManifestListTemplate.class);

    String manifestDigest =
        pullBaseImageStep.lookUpPlatformSpecificImageManifest(
            manifestList, new Platform("targetArchitecture", "targetOS"));

    Assert.assertEquals(
        "sha256:2222222222222222222222222222222222222222222222222222222222222222", manifestDigest);
  }

  @Test
  public void testGetCachedBaseImages_emptyCache()
      throws InvalidImageReferenceException, IOException, CacheCorruptedException,
          UnlistedPlatformInManifestListException, BadContainerConfigurationFormatException,
          LayerCountMismatchException {
    ImageReference imageReference = ImageReference.parse("cat");
    Mockito.when(buildContext.getBaseImageConfiguration())
        .thenReturn(ImageConfiguration.builder(imageReference).build());
    Mockito.when(cache.retrieveMetadata(imageReference)).thenReturn(Optional.empty());

    Assert.assertEquals(Arrays.asList(), pullBaseImageStep.getCachedBaseImages());
  }

  @Test
  public void testGetCachedBaseImages_v21ManifestCached()
      throws InvalidImageReferenceException, IOException, CacheCorruptedException,
          UnlistedPlatformInManifestListException, BadContainerConfigurationFormatException,
          LayerCountMismatchException, DigestException {
    ImageReference imageReference = ImageReference.parse("cat");
    Mockito.when(buildContext.getBaseImageConfiguration())
        .thenReturn(ImageConfiguration.builder(imageReference).build());

    DescriptorDigest layerDigest =
        DescriptorDigest.fromHash(
            "1111111111111111111111111111111111111111111111111111111111111111");
    V21ManifestTemplate v21Manifest = Mockito.mock(V21ManifestTemplate.class);
    Mockito.when(v21Manifest.getLayerDigests()).thenReturn(Arrays.asList(layerDigest));
    ImageMetadataTemplate imageMetadata =
        new ImageMetadataTemplate(
            null, Arrays.asList(new ManifestAndConfigTemplate(v21Manifest, null)));

    Mockito.when(cache.retrieveMetadata(imageReference)).thenReturn(Optional.of(imageMetadata));

    List<Image> images = pullBaseImageStep.getCachedBaseImages();

    Assert.assertEquals(1, images.size());
    Assert.assertEquals(1, images.get(0).getLayers().size());
    Assert.assertEquals(
        "1111111111111111111111111111111111111111111111111111111111111111",
        images.get(0).getLayers().get(0).getBlobDescriptor().getDigest().getHash());
  }

  @Test
  public void testGetCachedBaseImages_v22ManifestCached()
      throws InvalidImageReferenceException, IOException, CacheCorruptedException,
          UnlistedPlatformInManifestListException, BadContainerConfigurationFormatException,
          LayerCountMismatchException {
    ImageReference imageReference = ImageReference.parse("cat");
    Mockito.when(buildContext.getBaseImageConfiguration())
        .thenReturn(ImageConfiguration.builder(imageReference).build());

    ContainerConfigurationTemplate containerConfigJson = new ContainerConfigurationTemplate();
    containerConfigJson.setArchitecture("slim arch");
    containerConfigJson.setOs("fat system");
    ManifestAndConfigTemplate manifestAndConfig =
        new ManifestAndConfigTemplate(
            new V22ManifestTemplate(), containerConfigJson, "sha256:digest");
    ImageMetadataTemplate imageMetadata =
        new ImageMetadataTemplate(null, Arrays.asList(manifestAndConfig));
    Mockito.when(cache.retrieveMetadata(imageReference)).thenReturn(Optional.of(imageMetadata));

    List<Image> images = pullBaseImageStep.getCachedBaseImages();

    Assert.assertEquals(1, images.size());
    Assert.assertEquals("slim arch", images.get(0).getArchitecture());
    Assert.assertEquals("fat system", images.get(0).getOs());
  }

  @Test
  public void testGetCachedBaseImages_v22ManifestListCached()
      throws InvalidImageReferenceException, IOException, CacheCorruptedException,
          UnlistedPlatformInManifestListException, BadContainerConfigurationFormatException,
          LayerCountMismatchException {
    ImageReference imageReference = ImageReference.parse("cat");
    Mockito.when(buildContext.getBaseImageConfiguration())
        .thenReturn(ImageConfiguration.builder(imageReference).build());

    ContainerConfigurationTemplate containerConfigJson1 = new ContainerConfigurationTemplate();
    ContainerConfigurationTemplate containerConfigJson2 = new ContainerConfigurationTemplate();
    containerConfigJson1.setContainerUser("user1");
    containerConfigJson2.setContainerUser("user2");

    V22ManifestListTemplate manifestList = Mockito.mock(V22ManifestListTemplate.class);
    Mockito.when(manifestList.getDigestsForPlatform("arch1", "os1"))
        .thenReturn(Arrays.asList("sha256:digest1"));
    Mockito.when(manifestList.getDigestsForPlatform("arch2", "os2"))
        .thenReturn(Arrays.asList("sha256:digest2"));

    ImageMetadataTemplate imageMetadata =
        new ImageMetadataTemplate(
            manifestList,
            Arrays.asList(
                new ManifestAndConfigTemplate(
                    new V22ManifestTemplate(), containerConfigJson1, "sha256:digest1"),
                new ManifestAndConfigTemplate(
                    new V22ManifestTemplate(), containerConfigJson2, "sha256:digest2")));
    Mockito.when(cache.retrieveMetadata(imageReference)).thenReturn(Optional.of(imageMetadata));

    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("arch1", "os1"), new Platform("arch2", "os2")));

    List<Image> images = pullBaseImageStep.getCachedBaseImages();

    Assert.assertEquals(2, images.size());
    Assert.assertEquals("user1", images.get(0).getUser());
    Assert.assertEquals("user2", images.get(1).getUser());
  }

  @Test
  public void testGetCachedBaseImages_v22ManifestListCached_partialMatches()
      throws InvalidImageReferenceException, IOException, CacheCorruptedException,
          UnlistedPlatformInManifestListException, BadContainerConfigurationFormatException,
          LayerCountMismatchException {
    ImageReference imageReference = ImageReference.parse("cat");
    Mockito.when(buildContext.getBaseImageConfiguration())
        .thenReturn(ImageConfiguration.builder(imageReference).build());

    V22ManifestListTemplate manifestList = Mockito.mock(V22ManifestListTemplate.class);
    Mockito.when(manifestList.getDigestsForPlatform("arch1", "os1"))
        .thenReturn(Arrays.asList("sha256:digest1"));
    Mockito.when(manifestList.getDigestsForPlatform("arch2", "os2"))
        .thenReturn(Arrays.asList("sha256:digest2"));

    ImageMetadataTemplate imageMetadata =
        new ImageMetadataTemplate(
            manifestList,
            Arrays.asList(
                new ManifestAndConfigTemplate(
                    new V22ManifestTemplate(),
                    new ContainerConfigurationTemplate(),
                    "sha256:digest1")));
    Mockito.when(cache.retrieveMetadata(imageReference)).thenReturn(Optional.of(imageMetadata));

    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("arch1", "os1"), new Platform("arch2", "os2")));

    Assert.assertEquals(Arrays.asList(), pullBaseImageStep.getCachedBaseImages());
  }

  @Test
  public void testGetCachedBaseImages_v22ManifestListCached_onlyPlatforms()
      throws InvalidImageReferenceException, IOException, CacheCorruptedException,
          UnlistedPlatformInManifestListException, BadContainerConfigurationFormatException,
          LayerCountMismatchException {
    ImageReference imageReference = ImageReference.parse("cat");
    Mockito.when(buildContext.getBaseImageConfiguration())
        .thenReturn(ImageConfiguration.builder(imageReference).build());

    V22ManifestListTemplate manifestList = Mockito.mock(V22ManifestListTemplate.class);
    Mockito.when(manifestList.getDigestsForPlatform("target-arch", "target-os"))
        .thenReturn(Arrays.asList("sha256:target-digest"));

    ContainerConfigurationTemplate containerConfigJson = new ContainerConfigurationTemplate();
    containerConfigJson.setContainerUser("target-user");

    ManifestAndConfigTemplate targetManifestAndConfig =
        new ManifestAndConfigTemplate(
            new V22ManifestTemplate(), containerConfigJson, "sha256:target-digest");
    ManifestAndConfigTemplate unrelatedManifestAndConfig =
        new ManifestAndConfigTemplate(
            new V22ManifestTemplate(),
            new ContainerConfigurationTemplate(),
            "sha256:unrelated-digest");

    ImageMetadataTemplate imageMetadata =
        new ImageMetadataTemplate(
            manifestList,
            Arrays.asList(
                unrelatedManifestAndConfig, targetManifestAndConfig, unrelatedManifestAndConfig));
    Mockito.when(cache.retrieveMetadata(imageReference)).thenReturn(Optional.of(imageMetadata));

    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("target-arch", "target-os")));

    List<Image> images = pullBaseImageStep.getCachedBaseImages();

    Assert.assertEquals(1, images.size());
    Assert.assertEquals("target-user", images.get(0).getUser());
  }
}
