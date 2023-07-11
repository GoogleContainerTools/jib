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
import static org.junit.Assert.assertThrows;

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.PlatformNotFoundInBaseImageException;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link PlatformChecker}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformCheckerTest {

  @Mock private BuildContext buildContext;
  @Mock private ContainerConfiguration containerConfig;

  @BeforeEach
  void setUp() {
    Mockito.when(buildContext.getBaseImageConfiguration())
        .thenReturn(ImageConfiguration.builder(ImageReference.scratch()).build());
    Mockito.when(buildContext.getContainerConfiguration()).thenReturn(containerConfig);
  }

  @Test
  void testCheckManifestPlatform_mismatch() {
    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("configured arch", "configured OS")));

    ContainerConfigurationTemplate containerConfigJson = new ContainerConfigurationTemplate();
    containerConfigJson.setArchitecture("actual arch");
    containerConfigJson.setOs("actual OS");
    Exception ex =
        assertThrows(
            PlatformNotFoundInBaseImageException.class,
            () -> PlatformChecker.checkManifestPlatform(buildContext, containerConfigJson));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo(
            "the configured platform (configured arch/configured OS) doesn't match the "
                + "platform (actual arch/actual OS) of the base image (scratch)");
  }

  @Test
  void testCheckManifestPlatform_noExceptionIfDefaultAmd64Linux()
      throws PlatformNotFoundInBaseImageException {
    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("amd64", "linux")));

    ContainerConfigurationTemplate containerConfigJson = new ContainerConfigurationTemplate();
    containerConfigJson.setArchitecture("actual arch");
    containerConfigJson.setOs("actual OS");
    PlatformChecker.checkManifestPlatform(buildContext, containerConfigJson);
  }

  @Test
  void testCheckManifestPlatform_multiplePlatformsConfigured() {
    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("amd64", "linux"), new Platform("arch", "os")));
    Exception ex =
        assertThrows(
            PlatformNotFoundInBaseImageException.class,
            () ->
                PlatformChecker.checkManifestPlatform(
                    buildContext, new ContainerConfigurationTemplate()));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo(
            "cannot build for multiple platforms since the base image 'scratch' is not a manifest list.");
  }

  @Test
  void testCheckManifestPlatform_tarBaseImage() {
    Path tar = Paths.get("/foo/bar.tar");
    Mockito.when(buildContext.getBaseImageConfiguration())
        .thenReturn(ImageConfiguration.builder(ImageReference.scratch()).setTarPath(tar).build());
    Mockito.when(containerConfig.getPlatforms())
        .thenReturn(ImmutableSet.of(new Platform("amd64", "linux"), new Platform("arch", "os")));

    Exception ex =
        assertThrows(
            PlatformNotFoundInBaseImageException.class,
            () ->
                PlatformChecker.checkManifestPlatform(
                    buildContext, new ContainerConfigurationTemplate()));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo(
            "cannot build for multiple platforms since the base image '"
                + tar
                + "' is not a manifest list.");
  }
}
