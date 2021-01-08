/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.cli;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilderTestHelper;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ContainerBuilders}. */
@RunWith(MockitoJUnitRunner.class)
public class ContainerBuildersTest {

  @Mock private CommonCliOptions mockCommonCliOptions;

  @Mock private ConsoleLogger mockLogger;

  @Test
  public void testCreate_dockerBaseImage()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    JibContainerBuilder containerBuilder =
        ContainerBuilders.create(
            "docker://docker-image-ref", Collections.emptySet(), mockCommonCliOptions, mockLogger);
    BuildContext buildContext =
        JibContainerBuilderTestHelper.toBuildContext(
            containerBuilder, Containerizer.to(RegistryImage.named("ignored")));
    ImageConfiguration imageConfiguration = buildContext.getBaseImageConfiguration();

    assertThat(imageConfiguration.getImage().toString()).isEqualTo("docker-image-ref");
    assertThat(imageConfiguration.getDockerClient().isPresent()).isTrue();
    assertThat(imageConfiguration.getTarPath().isPresent()).isFalse();
  }

  @Test
  public void testCreate_registry()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    JibContainerBuilder containerBuilder =
        ContainerBuilders.create(
            "registry://registry-image-ref",
            Collections.emptySet(),
            mockCommonCliOptions,
            mockLogger);
    BuildContext buildContext =
        JibContainerBuilderTestHelper.toBuildContext(
            containerBuilder, Containerizer.to(RegistryImage.named("ignored")));
    ImageConfiguration imageConfiguration = buildContext.getBaseImageConfiguration();

    assertThat(imageConfiguration.getImage().toString()).isEqualTo("registry-image-ref");
    assertThat(imageConfiguration.getDockerClient().isPresent()).isFalse();
    assertThat(imageConfiguration.getTarPath().isPresent()).isFalse();
  }

  @Test
  public void testCreate_tarBase()
      throws IOException, InvalidImageReferenceException, CacheDirectoryCreationException {
    JibContainerBuilder containerBuilder =
        ContainerBuilders.create(
            "tar:///path/to.tar", Collections.emptySet(), mockCommonCliOptions, mockLogger);
    BuildContext buildContext =
        JibContainerBuilderTestHelper.toBuildContext(
            containerBuilder, Containerizer.to(RegistryImage.named("ignored")));
    ImageConfiguration imageConfiguration = buildContext.getBaseImageConfiguration();

    assertThat(imageConfiguration.getTarPath()).isEqualTo(Optional.of(Paths.get("/path/to.tar")));
    assertThat(imageConfiguration.getDockerClient().isPresent()).isFalse();
  }

  @Test
  public void testCreate_platforms() throws IOException, InvalidImageReferenceException {
    JibContainerBuilder containerBuilder =
        ContainerBuilders.create(
            "registry://registry-image-ref",
            ImmutableSet.of(new Platform("arch1", "os1"), new Platform("arch2", "os2")),
            mockCommonCliOptions,
            mockLogger);

    assertThat(containerBuilder.toContainerBuildPlan().getPlatforms())
        .isEqualTo(ImmutableSet.of(new Platform("arch1", "os1"), new Platform("arch2", "os2")));
  }
}
