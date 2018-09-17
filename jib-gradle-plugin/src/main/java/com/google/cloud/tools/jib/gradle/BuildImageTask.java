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
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.Optional;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/** Builds a container image. */
public class BuildImageTask extends DefaultTask implements JibTask {

  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Build image failed";

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
  public void buildImage() throws InvalidImageReferenceException {
    // Asserts required @Input parameters are not null.
    Preconditions.checkNotNull(jibExtension);
    GradleJibLogger gradleJibLogger = new GradleJibLogger(getLogger());
    GradleProjectProperties gradleProjectProperties =
        GradleProjectProperties.getForProject(
            getProject(), gradleJibLogger, jibExtension.getExtraDirectoryPath());

    if (Strings.isNullOrEmpty(jibExtension.getTargetImage())) {
      throw new GradleException(
          HelpfulSuggestions.forToNotConfigured(
              "Missing target image parameter",
              "'jib.to.image'",
              "build.gradle",
              "gradle jib --image <your image name>"));
    }

    ImageReference targetImage = ImageReference.parse(jibExtension.getTargetImage());

    DefaultCredentialRetrievers defaultCredentialRetrievers =
        DefaultCredentialRetrievers.init(
            CredentialRetrieverFactory.forImage(targetImage, gradleJibLogger));
    Optional<Credential> optionalToCredential =
        ConfigurationPropertyValidator.getImageCredential(
            gradleJibLogger,
            "jib.to.auth.username",
            "jib.to.auth.password",
            jibExtension.getTo().getAuth());
    optionalToCredential.ifPresent(
        toCredential ->
            defaultCredentialRetrievers.setKnownCredential(toCredential, "jib.to.auth"));
    defaultCredentialRetrievers.setCredentialHelperSuffix(jibExtension.getTo().getCredHelper());

    ImageConfiguration targetImageConfiguration =
        ImageConfiguration.builder(targetImage)
            .setCredentialRetrievers(defaultCredentialRetrievers.asList())
            .build();

    PluginConfigurationProcessor pluginConfigurationProcessor =
        PluginConfigurationProcessor.processCommonConfiguration(
            gradleJibLogger, jibExtension, gradleProjectProperties);

    BuildConfiguration buildConfiguration =
        pluginConfigurationProcessor
            .getBuildConfigurationBuilder()
            .setBaseImageConfiguration(
                pluginConfigurationProcessor.getBaseImageConfigurationBuilder().build())
            .setTargetImageConfiguration(targetImageConfiguration)
            .setContainerConfiguration(
                pluginConfigurationProcessor.getContainerConfigurationBuilder().build())
            .setTargetFormat(jibExtension.getFormat())
            .build();

    HelpfulSuggestions helpfulSuggestions =
        new GradleHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, jibExtension)
            .setBaseImageReference(buildConfiguration.getBaseImageConfiguration().getImage())
            .setBaseImageHasConfiguredCredentials(
                pluginConfigurationProcessor.isBaseImageCredentialPresent())
            .setTargetImageReference(buildConfiguration.getTargetImageConfiguration().getImage())
            .setTargetImageHasConfiguredCredentials(optionalToCredential.isPresent())
            .build();

    try {
      BuildStepsRunner.forBuildImage(buildConfiguration).build(helpfulSuggestions);

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new GradleException(ex.getMessage(), ex.getCause());
    }
  }

  @Override
  public BuildImageTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }
}
