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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.ImageAndRegistryClient;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.hash.Digests;
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
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
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

  private final ContainerConfigurationTemplate containerConfig =
      new ContainerConfigurationTemplate();
  private final ManifestAndConfig manifestAndConfig =
      new ManifestAndConfig(new V22ManifestTemplate(), containerConfig);

  @Mock private ProgressEventDispatcher.Factory progressDispatcherFactory;
  @Mock private BuildContext buildContext;
  @Mock private RegistryClient registryClient;
  @Mock private ImageConfiguration imageConfiguration;
  @Mock private ImageReference imageReference;
  @Mock private ContainerConfiguration containerConfiguration;
  @Mock private Cache cache;

  private PullBaseImageStep pullBaseImageStep;

  @Before
  public void setUp() {
    containerConfig.setOs("fat system");
    Mockito.when(buildContext.getBaseImageConfiguration()).thenReturn(imageConfiguration);
    Mockito.when(buildContext.getBaseImageConfiguration().getImage()).thenReturn(imageReference);
    Mockito.when(buildContext.getEventHandlers()).thenReturn(EventHandlers.NONE);
    Mockito.when(buildContext.getBaseImageLayersCache()).thenReturn(cache);
    RegistryClient.Factory registryClientFactory = Mockito.mock(RegistryClient.Factory.class);
    Mockito.when(buildContext.newBaseImageRegistryClientFactory())
        .thenReturn(registryClientFactory);
    Mockito.when(registryClientFactory.newRegistryClient()).thenReturn(registryClient);

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

    ImageAndRegistryClient result = pullBaseImageStep.call();
    Assert.assertEquals("fat system", result.image.getOs());
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

    ImageAndRegistryClient result = pullBaseImageStep.call();
    Assert.assertEquals("fat system", result.image.getOs());
    Assert.assertNull(result.registryClient);

    Mockito.verify(buildContext, Mockito.never()).newBaseImageRegistryClientFactory();
  }

  @Test
  public void testObtainPlatformSpecificImageManifest()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException,
          RegistryException {
    String testManifestListTemplate =
        " {\n"
            + "   \"schemaVersion\": 2,\n"
            + "   \"mediaType\": \"application/vnd.docker.distribution.manifest.list.v2+json\",\n"
            + "   \"manifests\": [\n"
            + "      {\n"
            + "         \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "         \"size\": 424,\n"
            + "         \"digest\": \"sha256:f67dcc5fc786f04f0743abfe0ee5dae9bd8caf8efa6c8144f7f2a43889dc513b\",\n"
            + "         \"platform\": {\n"
            + "            \"architecture\": \"testArchitecture\",\n"
            + "            \"os\": \"testOS\"\n"
            + "         }\n"
            + "      },\n"
            + "      {\n"
            + "         \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "         \"size\": 425,\n"
            + "         \"digest\": \"sha256:5bb8e50aa2edd408bdf3ddf61efb7338ff34a07b762992c9432f1c02fc0e5e62\",\n"
            + "         \"platform\": {\n"
            + "            \"architecture\": \"Architecture\",\n"
            + "            \"os\": \"OS\"\n"
            + "         }\n"
            + "      }\n"
            + "   ]\n"
            + "}";

    String testManifestTemplate =
        "{\n"
            + "    \"schemaVersion\": 2,\n"
            + "    \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "    \"config\": {\n"
            + "        \"mediaType\": \"application/vnd.docker.container.image.v1+json\",\n"
            + "        \"size\": 424,\n"
            + "        \"digest\": \"sha256:5bb8e50aa2edd408bdf3ddf61efb7338ff34a07b762992c9432f1c02fc0e5e62\"\n"
            + "    }\n"
            + "}";
    Mockito.when(buildContext.getContainerConfiguration()).thenReturn(containerConfiguration);
    Mockito.when(containerConfiguration.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("testArchitecture", "testOS")));
    V22ManifestListTemplate testManifestList =
        JsonTemplateMapper.readJson(testManifestListTemplate, V22ManifestListTemplate.class);

    V22ManifestTemplate testManifest =
        new ObjectMapper().readValue(testManifestTemplate, V22ManifestTemplate.class);

    ManifestAndDigest<?> manifestAndDigest =
        new ManifestAndDigest(testManifest, Digests.computeJsonDigest(testManifest));

    Mockito.<ManifestAndDigest<?>>when(
            registryClient.pullManifest(
                "sha256:f67dcc5fc786f04f0743abfe0ee5dae9bd8caf8efa6c8144f7f2a43889dc513b"))
        .thenReturn(manifestAndDigest);

    ManifestAndDigest<?> returnManifestAndDigest =
        pullBaseImageStep.obtainPlatformSpecificImageManifest(registryClient, testManifestList);

    Assert.assertEquals(
        Digests.computeJsonDigest(testManifest).toString(),
        returnManifestAndDigest.getDigest().toString());

    Assert.assertTrue(returnManifestAndDigest.getManifest() instanceof V22ManifestTemplate);
    V22ManifestTemplate returnedManifest = (V22ManifestTemplate) manifestAndDigest.getManifest();
    Assert.assertEquals(2, returnedManifest.getSchemaVersion());
    Assert.assertEquals(
        "application/vnd.docker.distribution.manifest.v2+json",
        returnedManifest.getManifestMediaType());
    Assert.assertEquals(
        "sha256:5bb8e50aa2edd408bdf3ddf61efb7338ff34a07b762992c9432f1c02fc0e5e62",
        returnedManifest.getContainerConfiguration().getDigest().toString());
    Assert.assertEquals(424, returnedManifest.getContainerConfiguration().getSize());
  }
}
