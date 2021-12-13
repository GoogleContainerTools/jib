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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

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
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
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
    when(buildContext.getAllTargetImageTags()).thenReturn(ImmutableSet.of("tag1", "tag2"));
    when(buildContext.getEventHandlers()).thenReturn(EventHandlers.NONE);
    when(buildContext.getContainerConfiguration()).thenReturn(containerConfig);
    doReturn(V22ManifestTemplate.class).when(buildContext).getTargetFormat();
    when(containerConfig.getPlatforms())
        .thenReturn(
            ImmutableSet.of(new Platform("amd64", "linux"), new Platform("arm64", "windows")));
    when(progressDispatcherFactory.create(anyString(), anyLong())).thenReturn(progressDispatcher);
    when(progressDispatcher.newChildProducer()).thenReturn(progressDispatcherFactory);

    ManifestDescriptorTemplate manifest = new ManifestDescriptorTemplate();
    manifest.setSize(100);
    manifest.setDigest("sha256:1f25787aab4669d252bdae09a72b9c345d2a7b8c64c8dbfba4c82af4834dbccc");
    manifestList.addManifest(manifest);
  }

  @Test
  public void testMakeListForManifestList() throws IOException, RegistryException {
    List<PushImageStep> pushImageStepList =
        PushImageStep.makeListForManifestList(
            buildContext, progressDispatcherFactory, registryClient, manifestList, false);

    assertThat(pushImageStepList).hasSize(2);
    for (PushImageStep pushImageStep : pushImageStepList) {
      BuildResult buildResult = pushImageStep.call();
      assertThat(buildResult.getImageDigest().toString())
          .isEqualTo("sha256:64303e82b8a80ef20475dc7f807b81f172cacce1a59191927f3a7ea5222f38ae");
      assertThat(buildResult.getImageId().toString())
          .isEqualTo("sha256:64303e82b8a80ef20475dc7f807b81f172cacce1a59191927f3a7ea5222f38ae");
    }
  }

  @Test
  public void testMakeList_multiPlatform_platformTags() throws IOException, RegistryException {
    Image image = Image.builder(V22ManifestTemplate.class).setArchitecture("wasm").build();

    when(buildContext.getEnablePlatformTags()).thenReturn(true);

    List<PushImageStep> pushImageStepList =
        PushImageStep.makeList(
            buildContext,
            progressDispatcherFactory,
            registryClient,
            new BlobDescriptor(mockDescriptorDigest),
            image,
            false);

    ArgumentCaptor<String> tagCatcher = ArgumentCaptor.forClass(String.class);
    when(registryClient.pushManifest(any(), tagCatcher.capture())).thenReturn(null);

    assertThat(pushImageStepList).hasSize(2);
    pushImageStepList.get(0).call();
    pushImageStepList.get(1).call();

    assertThat(tagCatcher.getAllValues()).containsExactly("tag1-wasm", "tag2-wasm");
  }

  @Test
  public void testMakeList_multiPlatform_nonPlatformTags() throws IOException, RegistryException {
    Image image = Image.builder(V22ManifestTemplate.class).setArchitecture("wasm").build();
    when(buildContext.getEnablePlatformTags()).thenReturn(false);

    List<PushImageStep> pushImageStepList =
        PushImageStep.makeList(
            buildContext,
            progressDispatcherFactory,
            registryClient,
            new BlobDescriptor(mockDescriptorDigest),
            image,
            false);

    ArgumentCaptor<String> tagCatcher = ArgumentCaptor.forClass(String.class);
    when(registryClient.pushManifest(any(), tagCatcher.capture())).thenReturn(null);

    assertThat(pushImageStepList).hasSize(1);
    pushImageStepList.get(0).call();
    assertThat(tagCatcher.getAllValues())
        .containsExactly("sha256:0dd75658cf52608fbd72eb95ff5fc5946966258c3676b35d336bfcc7ac5006f1");
  }

  @Test
  public void testMakeListForManifestList_singlePlatform() throws IOException {
    when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("amd64", "linux")));

    List<PushImageStep> pushImageStepList =
        PushImageStep.makeListForManifestList(
            buildContext, progressDispatcherFactory, registryClient, manifestList, false);
    assertThat(pushImageStepList).isEmpty();
  }

  @Test
  public void testMakeListForManifestList_manifestListAlreadyExists() throws IOException {
    System.setProperty(JibSystemProperties.SKIP_EXISTING_IMAGES, "true");

    List<PushImageStep> pushImageStepList =
        PushImageStep.makeListForManifestList(
            buildContext, progressDispatcherFactory, registryClient, manifestList, true);
    assertThat(pushImageStepList).isEmpty();
  }
}
