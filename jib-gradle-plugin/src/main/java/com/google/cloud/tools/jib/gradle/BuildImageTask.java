/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

import com.google.api.client.http.HttpTransport;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.frontend.BuildImageStepsExecutionException;
import com.google.cloud.tools.jib.frontend.BuildImageStepsRunner;
import com.google.cloud.tools.jib.frontend.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext;
import org.slf4j.LoggerFactory;

/** Builds a container image. */
public class BuildImageTask extends DefaultTask {

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  private static final String CACHE_DIRECTORY_NAME = "jib-cache";

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-gradle-plugin";

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Build image failed");

  /** Converts an {@link ImageConfiguration} to an {@link Authorization}. */
  @Nullable
  private static Authorization getImageAuthorization(ImageConfiguration imageConfiguration) {
    if (imageConfiguration.getAuth().getUsername() == null
        || imageConfiguration.getAuth().getPassword() == null) {
      return null;
    }

    return Authorizations.withBasicCredentials(
        imageConfiguration.getAuth().getUsername(), imageConfiguration.getAuth().getPassword());
  }

  @Nullable private JibExtension jibExtension;

  /**
   * This will call the property {@code "jib"} so that it is the same name as the extension. This
   * way, the user would see error messages for missing configuration with the prefix {@code jib.}.
   */
  @Nested
  @Nullable
  public JibExtension getJib() {
    return jibExtension;
  }

  @TaskAction
  public void buildImage() throws InvalidImageReferenceException {
    // Asserts required @Input parameters are not null.
    Preconditions.checkNotNull(jibExtension);

    GradleBuildLogger gradleBuildLogger = new GradleBuildLogger(getLogger());

    ImageReference baseImageReference = ImageReference.parse(jibExtension.getBaseImage());
    ImageReference targetImageReference = ImageReference.parse(jibExtension.getTargetImage());

    if (baseImageReference.usesDefaultTag()) {
      gradleBuildLogger.warn(
          "Base image '"
              + baseImageReference
              + "' does not use a specific image digest - build may not be reproducible");
    }

    ProjectProperties projectProperties = new ProjectProperties(getProject(), getLogger());
    String mainClass = projectProperties.getMainClass(jibExtension.getMainClass());

    RegistryCredentials knownBaseRegistryCredentials = null;
    RegistryCredentials knownTargetRegistryCredentials = null;
    Authorization fromAuthorization = getImageAuthorization(jibExtension.getFrom());
    if (fromAuthorization != null) {
      knownBaseRegistryCredentials = new RegistryCredentials("jib.from.auth", fromAuthorization);
    }
    Authorization toAuthorization = getImageAuthorization(jibExtension.getTo());
    if (toAuthorization != null) {
      knownTargetRegistryCredentials = new RegistryCredentials("jib.to.auth", toAuthorization);
    }

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(gradleBuildLogger)
            .setBaseImage(baseImageReference)
            .setBaseImageCredentialHelperName(jibExtension.getFrom().getCredHelper())
            .setKnownBaseRegistryCredentials(knownBaseRegistryCredentials)
            .setTargetImage(targetImageReference)
            .setTargetImageCredentialHelperName(jibExtension.getTo().getCredHelper())
            .setKnownTargetRegistryCredentials(knownTargetRegistryCredentials)
            .setMainClass(mainClass)
            .setJvmFlags(jibExtension.getJvmFlags())
            .setTargetFormat(jibExtension.getFormat())
            .build();

    // TODO: Instead of disabling logging, have authentication credentials be provided
    // Disables annoying Apache HTTP client logging.
    OutputEventListenerBackedLoggerContext context =
        (OutputEventListenerBackedLoggerContext) LoggerFactory.getILoggerFactory();
    OutputEventListener defaultOutputEventListener = context.getOutputEventListener();
    context.setOutputEventListener(
        event -> {
          LogEvent logEvent = (LogEvent) event;
          if (!logEvent.getCategory().contains("org.apache")) {
            defaultOutputEventListener.onOutput(event);
          }
        });

    // Disables Google HTTP client logging.
    Logger.getLogger(HttpTransport.class.getName()).setLevel(Level.OFF);

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    // Uses a directory in the Gradle build cache as the Jib cache.
    Path cacheDirectory = getProject().getBuildDir().toPath().resolve(CACHE_DIRECTORY_NAME);
    try {
      BuildImageStepsRunner buildImageStepsRunner =
          BuildImageStepsRunner.newRunner(
              buildConfiguration,
              projectProperties.getSourceFilesConfiguration(),
              cacheDirectory,
              jibExtension.getUseOnlyProjectCache());

      buildImageStepsRunner.buildImage(HELPFUL_SUGGESTIONS);

    } catch (CacheDirectoryCreationException | BuildImageStepsExecutionException ex) {
      throw new GradleException(ex.getMessage(), ex.getCause());
    }
  }

  void setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
  }
}
