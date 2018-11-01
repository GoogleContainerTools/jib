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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.common.base.Preconditions;
import java.io.IOException;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/** Builds a container image and exports to the default Docker daemon. */
public class BuildDockerTask extends DefaultTask implements JibTask {

  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Build to Docker daemon failed";

  private static final DockerClient DOCKER_CLIENT = DockerClient.newClient();

  @Nullable private JibExtension jibExtension;

  @Nullable private DockerClient dockerClient;

  /**
   * This will call the property {@code "jib"} so that it is the same name as the extension. This
   * way, the user would see error messages for missing configuration with the prefix {@code jib.}.
   *
   * @return the {@link JibExtension}.
   */
  @Nested
  @Nullable
  public JibExtension getJib() {
    return jibExtension;
  }

  /**
   * The target image can be overridden with the {@code --image} command line option.
   *
   * @param targetImage the name of the 'to' image.
   */
  @Option(option = "image", description = "The image reference for the target image")
  public void setTargetImage(String targetImage) {
    Preconditions.checkNotNull(jibExtension).getTo().setImage(targetImage);
  }

  @TaskAction
  public void buildDocker()
      throws InvalidImageReferenceException, IOException, BuildStepsExecutionException,
          CacheDirectoryCreationException {
    if (!DOCKER_CLIENT.isDockerInstalled()) {
      throw new GradleException(
          HelpfulSuggestions.forDockerNotInstalled(HELPFUL_SUGGESTIONS_PREFIX));
    }

    // Asserts required @Input parameters are not null.
    Preconditions.checkNotNull(jibExtension);
    AbsoluteUnixPath appRoot = PluginConfigurationProcessor.getAppRootChecked(jibExtension);
    GradleProjectProperties gradleProjectProperties =
        GradleProjectProperties.getForProject(
            getProject(), getLogger(), jibExtension.getExtraDirectoryPath(), appRoot);

    GradleHelpfulSuggestionsBuilder gradleHelpfulSuggestionsBuilder =
        new GradleHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, jibExtension);

    EventDispatcher eventDispatcher =
        new DefaultEventDispatcher(gradleProjectProperties.getEventHandlers());
    ImageReference targetImageReference =
        ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
            jibExtension.getTo().getImage(),
            eventDispatcher,
            getProject().getName(),
            getProject().getVersion().toString().equals("unspecified")
                ? "latest"
                : getProject().getVersion().toString(),
            gradleHelpfulSuggestionsBuilder.build());

    DockerDaemonImage targetImage = DockerDaemonImage.named(targetImageReference);
    if (dockerClient != null) {
      targetImage.setDockerClient(dockerClient);
    }

    PluginConfigurationProcessor pluginConfigurationProcessor =
        PluginConfigurationProcessor.processCommonConfiguration(
            getLogger(), jibExtension, gradleProjectProperties);

    JibContainerBuilder jibContainerBuilder = pluginConfigurationProcessor.getJibContainerBuilder();

    Containerizer containerizer = Containerizer.to(targetImage);
    PluginConfigurationProcessor.configureContainerizer(
        containerizer, jibExtension, gradleProjectProperties);

    HelpfulSuggestions helpfulSuggestions =
        gradleHelpfulSuggestionsBuilder
            .setBaseImageReference(pluginConfigurationProcessor.getBaseImageReference())
            .setBaseImageHasConfiguredCredentials(
                pluginConfigurationProcessor.isBaseImageCredentialPresent())
            .setTargetImageReference(targetImageReference)
            .build();

    BuildStepsRunner.forBuildToDockerDaemon(targetImageReference, jibExtension.getTo().getTags())
        .build(
            jibContainerBuilder,
            containerizer,
            eventDispatcher,
            gradleProjectProperties.getJavaLayerConfigurations().getLayerConfigurations(),
            helpfulSuggestions);
  }

  @Override
  public BuildDockerTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }

  @Input
  @Nullable
  public DockerClient getDockerClient() {
    return dockerClient;
  }

  public void setDockerClient(@Nullable DockerClient dockerClient) {
    this.dockerClient = dockerClient;
  }
}
