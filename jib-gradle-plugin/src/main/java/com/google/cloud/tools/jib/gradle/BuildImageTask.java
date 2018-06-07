/*
 * Copyright 2018 Google LLC. All rights reserved.
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

import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.frontend.BuildStepsExecutionException;
import com.google.cloud.tools.jib.frontend.BuildStepsRunner;
import com.google.cloud.tools.jib.frontend.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/** Builds a container image. */
public class BuildImageTask extends DefaultTask {

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-gradle-plugin";

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Build image failed");

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

    if (Strings.isNullOrEmpty(jibExtension.getTargetImage())) {
      throw new GradleException(
          HelpfulSuggestionsProvider.get("Missing target image parameter")
              .forToNotConfigured(
                  "'jib.to.image'", "build.gradle", "gradle jib --image <your image name>"));
    }

    RegistryCredentials knownBaseRegistryCredentials = null;
    RegistryCredentials knownTargetRegistryCredentials = null;
    Authorization fromAuthorization = jibExtension.getFrom().getImageAuthorization();
    if (fromAuthorization != null) {
      knownBaseRegistryCredentials = new RegistryCredentials("jib.from.auth", fromAuthorization);
    }
    Authorization toAuthorization = jibExtension.getTo().getImageAuthorization();
    if (toAuthorization != null) {
      knownTargetRegistryCredentials = new RegistryCredentials("jib.to.auth", toAuthorization);
    }

    GradleBuildLogger gradleBuildLogger = new GradleBuildLogger(getLogger());
    GradleProjectProperties gradleProjectProperties =
        GradleProjectProperties.getForProject(getProject(), gradleBuildLogger);
    String mainClass = gradleProjectProperties.getMainClass(jibExtension);

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(gradleBuildLogger)
            .setBaseImage(ImageReference.parse(jibExtension.getBaseImage()))
            .setTargetImage(ImageReference.parse(jibExtension.getTargetImage()))
            .setBaseImageCredentialHelperName(jibExtension.getFrom().getCredHelper())
            .setKnownBaseRegistryCredentials(knownBaseRegistryCredentials)
            .setTargetImageCredentialHelperName(jibExtension.getTo().getCredHelper())
            .setKnownTargetRegistryCredentials(knownTargetRegistryCredentials)
            .setMainClass(mainClass)
            .setJavaArguments(jibExtension.getArgs())
            .setJvmFlags(jibExtension.getJvmFlags())
            .setTargetFormat(jibExtension.getFormat())
            .build();

    // TODO: Instead of disabling logging, have authentication credentials be provided
    GradleBuildLogger.disableHttpLogging();

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    try {
      BuildStepsRunner.forBuildImage(
              buildConfiguration,
              gradleProjectProperties.getSourceFilesConfiguration(),
              gradleProjectProperties.getCacheDirectory(),
              jibExtension.getUseOnlyProjectCache())
          .build(HELPFUL_SUGGESTIONS);

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new GradleException(ex.getMessage(), ex.getCause());
    }
  }

  BuildImageTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }
}
