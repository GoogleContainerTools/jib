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

import com.google.api.client.http.HttpTransport;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.docker.DockerClient;
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

/** Builds a container image and exports to the default Docker daemon. */
public class BuildDockerTask extends DefaultTask {

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-gradle-plugin";

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Build to Docker daemon failed");

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
  public void buildDocker() throws InvalidImageReferenceException {
    if (!new DockerClient().isDockerInstalled()) {
      throw new GradleException(HELPFUL_SUGGESTIONS.forDockerNotInstalled());
    }

    // Asserts required @Input parameters are not null.
    Preconditions.checkNotNull(jibExtension);
    RegistryCredentials knownBaseRegistryCredentials = null;
    Authorization fromAuthorization = jibExtension.getFrom().getImageAuthorization();
    if (fromAuthorization != null) {
      knownBaseRegistryCredentials = new RegistryCredentials("jib.from.auth", fromAuthorization);
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
            .setMainClass(mainClass)
            .setJvmFlags(jibExtension.getJvmFlags())
            .build();

    // TODO: Consolidate with BuildImageTask
    // TODO: Instead of disabling logging, have authentication credentials be provided
    // Disables annoying Apache HTTP client logging.
    // Note that this is a hack and depends on internal Gradle classes
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
    try {
      BuildStepsRunner.forBuildToDockerDaemon(
              buildConfiguration,
              gradleProjectProperties.getSourceFilesConfiguration(),
              gradleProjectProperties.getCacheDirectory(),
              jibExtension.getUseOnlyProjectCache())
          .build(HELPFUL_SUGGESTIONS);

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new GradleException(ex.getMessage(), ex.getCause());
    }
  }

  void setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
  }
}
