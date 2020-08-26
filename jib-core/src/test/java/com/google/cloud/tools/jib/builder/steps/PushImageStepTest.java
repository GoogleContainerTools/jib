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

import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate.ManifestDescriptorTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link PushImageStep}. */
@RunWith(MockitoJUnitRunner.class)
public class PushImageStepTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();
  @Mock private ProgressEventDispatcher.Factory progressDispatcherFactory;
  @Mock private ProgressEventDispatcher progressDispatcher;
  @Mock private BuildContext buildContext;
  @Mock private RegistryClient registryClient;
  @Mock private ImageConfiguration imageConfiguration;
  @Mock private EventHandlers eventHandlers;
  @Mock private ContainerConfiguration containerConfig;
  @Mock private JibSystemProperties jibSystemPropeties;

  private V22ManifestListTemplate manifestList;
  private boolean manifestListAlreadyExists = false;
  private ManifestDescriptorTemplate manifest;

  @Before
  public void setUp() {
    Mockito.when(buildContext.getAllTargetImageTags()).thenReturn(ImmutableSet.of("tag1", "tag2"));
    Mockito.when(buildContext.getEventHandlers()).thenReturn(eventHandlers);
    Mockito.when(buildContext.getContainerConfiguration()).thenReturn(containerConfig);
    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(
            ImmutableSet.of(new Platform("amd64", "linux"), new Platform("arm64", "windows")));
    Mockito.when(
            progressDispatcherFactory.create(
                "launching manifest list pushers", buildContext.getAllTargetImageTags().size()))
        .thenReturn(progressDispatcher);
    Mockito.when(progressDispatcher.newChildProducer()).thenReturn(progressDispatcherFactory);

    manifestList = new V22ManifestListTemplate();
    manifest = new ManifestDescriptorTemplate();
    manifest.setMediaType("application/vnd.docker.distribution.manifest.v2+json");
    manifest.setSize(100);
    manifest.setDigest("sha256:1f25787aab4669d252bdae09a72b9c345d2a7b8c64c8dbfba4c82af4834dbccc");
    manifest.setPlatform("amd64", "linux");
    manifestList.addManifest(manifest);
  }

  @Test
  public void testMakeListForManifestList() throws IOException, RegistryException {
    ImmutableList<PushImageStep> pushImageStepList =
        PushImageStep.makeListForManifestList(
            buildContext,
            progressDispatcherFactory,
            registryClient,
            manifestList,
            manifestListAlreadyExists);

    Assert.assertEquals(2, pushImageStepList.size());
    for (PushImageStep pushImageStep : pushImageStepList) {
      BuildResult buildResult = pushImageStep.call();
      Assert.assertEquals(
          "sha256:b16ab9b5979f332e30c60afdfb6771bd5c17ed4f9718e6df1fc3781113385a99",
          buildResult.getImageDigest().toString());
      Assert.assertEquals(
          "sha256:b16ab9b5979f332e30c60afdfb6771bd5c17ed4f9718e6df1fc3781113385a99",
          buildResult.getImageId().toString());
    }
  }

  @Test
  public void testMakeListForManifestList_SinglePlatform() throws IOException, RegistryException {
    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("amd64", "linux")));

    ImmutableList<PushImageStep> pushImageStepList =
        PushImageStep.makeListForManifestList(
            buildContext,
            progressDispatcherFactory,
            registryClient,
            manifestList,
            manifestListAlreadyExists);
    Assert.assertEquals(0, pushImageStepList.size());
  }

  @Test
  public void testMakeListForManifestList_ManifestListAlreadyExists()
      throws IOException, RegistryException {
    manifestListAlreadyExists = true;
    System.setProperty(JibSystemProperties.SKIP_EXISTING_IMAGES, "true");
    ImmutableList<PushImageStep> pushImageStepList =
        PushImageStep.makeListForManifestList(
            buildContext,
            progressDispatcherFactory,
            registryClient,
            manifestList,
            manifestListAlreadyExists);
    Assert.assertEquals(0, pushImageStepList.size());
  }
}
