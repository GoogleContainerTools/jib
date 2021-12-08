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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate.ManifestDescriptorTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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
  @Mock private ContainerConfiguration containerConfig;
  @Mock private DescriptorDigest mockDescriptorDigest;


  private final V22ManifestListTemplate manifestList = new V22ManifestListTemplate();

  @Before
  public void setUp() {
    Mockito.when(buildContext.getAllTargetImageTags()).thenReturn(ImmutableSet.of("tag1", "tag2"));
    Mockito.when(buildContext.getEventHandlers()).thenReturn(EventHandlers.NONE);
    Mockito.when(buildContext.getContainerConfiguration()).thenReturn(containerConfig);
    Mockito.doReturn(V22ManifestTemplate.class).when(buildContext).getTargetFormat();
    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(
            ImmutableSet.of(new Platform("amd64", "linux"), new Platform("arm64", "windows")));
    Mockito.when(progressDispatcherFactory.create(Mockito.anyString(), Mockito.anyLong()))
        .thenReturn(progressDispatcher);
    Mockito.when(progressDispatcher.newChildProducer()).thenReturn(progressDispatcherFactory);

    ManifestDescriptorTemplate manifest = new ManifestDescriptorTemplate();
    manifest.setSize(100);
    manifest.setDigest("sha256:1f25787aab4669d252bdae09a72b9c345d2a7b8c64c8dbfba4c82af4834dbccc");
    manifestList.addManifest(manifest);
  }

  @Test
  public void testMakeListForManifestList() throws IOException, RegistryException {
    ImmutableList<PushImageStep> pushImageStepList =
        PushImageStep.makeListForManifestList(
            buildContext, progressDispatcherFactory, registryClient, manifestList, false);

    Assert.assertEquals(2, pushImageStepList.size());
    for (PushImageStep pushImageStep : pushImageStepList) {
      BuildResult buildResult = pushImageStep.call();
      Assert.assertEquals(
          "sha256:64303e82b8a80ef20475dc7f807b81f172cacce1a59191927f3a7ea5222f38ae",
          buildResult.getImageDigest().toString());
      Assert.assertEquals(
          "sha256:64303e82b8a80ef20475dc7f807b81f172cacce1a59191927f3a7ea5222f38ae",
          buildResult.getImageId().toString());
    }
  }

  @Test
  public void testMakeList_multiPlatform_enabled() throws IOException, RegistryException {
    Image asd = Image.builder(V22ManifestTemplate.class)
            .setArchitecture("wasm").build();

    Mockito.when(containerConfig.isPlatformTag()).thenReturn(true);

    ImmutableList<PushImageStep> pushImageStepList =
        PushImageStep.makeList(
            buildContext, progressDispatcherFactory, registryClient, new BlobDescriptor(mockDescriptorDigest), asd, false);

    ArgumentCaptor<String> tagCAtcher = ArgumentCaptor.forClass(String.class);
    Mockito.when(registryClient.pushManifest(Mockito.any(),tagCAtcher.capture())).thenReturn(null);

    Assert.assertEquals(2, pushImageStepList.size());
    for (PushImageStep pushImageStep : pushImageStepList) {
      BuildResult buildResult = pushImageStep.call();
      Assert.assertEquals(
          "sha256:0dd75658cf52608fbd72eb95ff5fc5946966258c3676b35d336bfcc7ac5006f1",
          buildResult.getImageDigest().toString());
      Assert.assertEquals(
          "mockDescriptorDigest",
          buildResult.getImageId().toString());
    }
    Set<String> allValues = ImmutableSet.copyOf(tagCAtcher.getAllValues());
    Set<String> expectedTags = ImmutableSet.of("tag1-wasm", "tag2-wasm");
    Assert.assertEquals(expectedTags, allValues);

  }

  @Test
  public void testMakeList_multiPlatform_disabled() throws IOException, RegistryException {
    Image asd = Image.builder(V22ManifestTemplate.class)
            .setArchitecture("wasm").build();
    Mockito.when(containerConfig.isPlatformTag()).thenReturn(false);

    ImmutableList<PushImageStep> pushImageStepList =
        PushImageStep.makeList(
            buildContext, progressDispatcherFactory, registryClient, new BlobDescriptor(mockDescriptorDigest), asd, false);

    ArgumentCaptor<String> tagCAtcher = ArgumentCaptor.forClass(String.class);
    Mockito.when(registryClient.pushManifest(Mockito.any(),tagCAtcher.capture())).thenReturn(null);

    Assert.assertEquals(1, pushImageStepList.size());
    for (PushImageStep pushImageStep : pushImageStepList) {
      BuildResult buildResult = pushImageStep.call();
      Assert.assertEquals(
          "sha256:0dd75658cf52608fbd72eb95ff5fc5946966258c3676b35d336bfcc7ac5006f1",
          buildResult.getImageDigest().toString());
      Assert.assertEquals(
          "mockDescriptorDigest",
          buildResult.getImageId().toString());
    }
    Set<String> allValues = ImmutableSet.copyOf(tagCAtcher.getAllValues());
    Set<String> expectedTags = ImmutableSet.of("sha256:0dd75658cf52608fbd72eb95ff5fc5946966258c3676b35d336bfcc7ac5006f1");
    Assert.assertEquals(expectedTags, allValues);

  }

  @Test
  public void testMakeListForManifestList_singlePlatform() throws IOException {
    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("amd64", "linux")));

    ImmutableList<PushImageStep> pushImageStepList =
        PushImageStep.makeListForManifestList(
            buildContext, progressDispatcherFactory, registryClient, manifestList, false);
    Assert.assertEquals(0, pushImageStepList.size());
  }

  @Test
  public void testMakeListForManifestList_manifestListAlreadyExists() throws IOException {
    System.setProperty(JibSystemProperties.SKIP_EXISTING_IMAGES, "true");

    ImmutableList<PushImageStep> pushImageStepList =
        PushImageStep.makeListForManifestList(
            buildContext, progressDispatcherFactory, registryClient, manifestList, true);
    Assert.assertEquals(0, pushImageStepList.size());
  }
}
