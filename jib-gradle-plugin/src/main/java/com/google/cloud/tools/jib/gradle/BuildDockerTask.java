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

import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/** Builds a container image and exports to the default Docker daemon. */
public class BuildDockerTask extends DefaultTask implements JibTask {

  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Build to Docker daemon failed";

  @Nullable private JibExtension jibExtension;

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
  public void buildDocker() throws InvalidImageReferenceException {
    if (!new DockerClient().isDockerInstalled()) {
      throw new GradleException(
          HelpfulSuggestions.forDockerNotInstalled(HELPFUL_SUGGESTIONS_PREFIX));
    }

    // Asserts required @Input parameters are not null.
    Preconditions.checkNotNull(jibExtension);
    GradleJibLogger gradleJibLogger = new GradleJibLogger(getLogger());
    GradleProjectProperties gradleProjectProperties =
        GradleProjectProperties.getForProject(
            getProject(), gradleJibLogger, jibExtension.getExtraDirectoryPath());

    GradleHelpfulSuggestionsBuilder gradleHelpfulSuggestionsBuilder =
        new GradleHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, jibExtension);

    ImageReference targetImage =
        ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
            jibExtension.getTargetImage(),
            gradleJibLogger,
            getProject().getName(),
            getProject().getVersion().toString(),
            gradleHelpfulSuggestionsBuilder.build());

    PluginConfigurationProcessor pluginConfigurationProcessor =
        PluginConfigurationProcessor.processCommonConfiguration(
            gradleJibLogger, jibExtension, gradleProjectProperties);

    BuildConfiguration buildConfiguration =
        pluginConfigurationProcessor
            .getBuildConfigurationBuilder()
            .setBaseImageConfiguration(
                pluginConfigurationProcessor.getBaseImageConfigurationBuilder().build())
            .setTargetImageConfiguration(ImageConfiguration.builder(targetImage).build())
            .setContainerConfiguration(
                pluginConfigurationProcessor.getContainerConfigurationBuilder().build())
            .build();

    HelpfulSuggestions helpfulSuggestions =
        gradleHelpfulSuggestionsBuilder
            .setBaseImageReference(buildConfiguration.getBaseImageConfiguration().getImage())
            .setBaseImageHasConfiguredCredentials(
                pluginConfigurationProcessor.isBaseImageCredentialPresent())
            .setTargetImageReference(buildConfiguration.getTargetImageConfiguration().getImage())
            .build();

    // Uses a directory in the Gradle build cache as the Jib cache.
    try {
      BuildStepsRunner.forBuildToDockerDaemon(buildConfiguration).build(helpfulSuggestions);

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new GradleException(ex.getMessage(), ex.getCause());
    }
  }

  @Override
  public BuildDockerTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }
}
