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

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheConfiguration;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.SystemPropertyValidator;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.time.Instant;
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
    GradleJibLogger gradleJibLogger = new GradleJibLogger(getLogger());
    jibExtension.handleDeprecatedParameters(gradleJibLogger);
    SystemPropertyValidator.checkHttpTimeoutProperty(GradleException::new);

    if (Strings.isNullOrEmpty(jibExtension.getTargetImage())) {
      throw new GradleException(
          HelpfulSuggestionsProvider.get("Missing target image parameter")
              .forToNotConfigured(
                  "'jib.to.image'", "build.gradle", "gradle jib --image <your image name>"));
    }

    if (Boolean.getBoolean("sendCredentialsOverHttp")) {
      gradleJibLogger.warn(
          "Authentication over HTTP is enabled. It is strongly recommended that you do not enable "
              + "this on a public network!");
    }
    RegistryCredentials knownBaseRegistryCredentials = null;
    RegistryCredentials knownTargetRegistryCredentials = null;
    Authorization fromAuthorization =
        getImageAuthorization(gradleJibLogger, "from", jibExtension.getFrom().getAuth());
    if (fromAuthorization != null) {
      knownBaseRegistryCredentials = new RegistryCredentials("jib.from.auth", fromAuthorization);
    }
    Authorization toAuthorization =
        getImageAuthorization(gradleJibLogger, "to", jibExtension.getFrom().getAuth());
    if (toAuthorization != null) {
      knownTargetRegistryCredentials = new RegistryCredentials("jib.to.auth", toAuthorization);
    }

    GradleProjectProperties gradleProjectProperties =
        GradleProjectProperties.getForProject(
            getProject(), gradleJibLogger, jibExtension.getExtraDirectoryPath());
    String mainClass = gradleProjectProperties.getMainClass(jibExtension);

    // Builds the BuildConfiguration.
    ImageConfiguration baseImageConfiguration =
        ImageConfiguration.builder(ImageReference.parse(jibExtension.getBaseImage()))
            .setCredentialHelper(jibExtension.getFrom().getCredHelper())
            .setKnownRegistryCredentials(knownBaseRegistryCredentials)
            .build();

    ImageConfiguration targetImageConfiguration =
        ImageConfiguration.builder(ImageReference.parse(jibExtension.getTargetImage()))
            .setCredentialHelper(jibExtension.getTo().getCredHelper())
            .setKnownRegistryCredentials(knownTargetRegistryCredentials)
            .build();

    ContainerConfiguration.Builder containerConfigurationBuilder =
        ContainerConfiguration.builder()
            .setEntrypoint(
                JavaEntrypointConstructor.makeDefaultEntrypoint(
                    jibExtension.getJvmFlags(), mainClass))
            .setProgramArguments(jibExtension.getArgs())
            .setExposedPorts(ExposedPortsParser.parse(jibExtension.getExposedPorts()));
    if (jibExtension.getUseCurrentTimestamp()) {
      gradleJibLogger.warn(
          "Setting image creation time to current time; your image may not be reproducible.");
      containerConfigurationBuilder.setCreationTime(Instant.now());
    }

    BuildConfiguration.Builder buildConfigurationBuilder =
        BuildConfiguration.builder(gradleJibLogger)
            .setBaseImageConfiguration(baseImageConfiguration)
            .setTargetImageConfiguration(targetImageConfiguration)
            .setContainerConfiguration(containerConfigurationBuilder.build())
            .setTargetFormat(jibExtension.getFormat())
            .setAllowInsecureRegistries(jibExtension.getAllowInsecureRegistries())
            .setLayerConfigurations(gradleProjectProperties.getLayerConfigurations());

    CacheConfiguration applicationLayersCacheConfiguration =
        CacheConfiguration.forPath(gradleProjectProperties.getCacheDirectory());
    buildConfigurationBuilder.setApplicationLayersCacheConfiguration(
        applicationLayersCacheConfiguration);
    if (jibExtension.getUseOnlyProjectCache()) {
      buildConfigurationBuilder.setBaseImageLayersCacheConfiguration(
          applicationLayersCacheConfiguration);
    }

    BuildConfiguration buildConfiguration = buildConfigurationBuilder.build();

    // TODO: Instead of disabling logging, have authentication credentials be provided
    GradleJibLogger.disableHttpLogging();

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    try {
      BuildStepsRunner.forBuildImage(buildConfiguration).build(HELPFUL_SUGGESTIONS);

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new GradleException(ex.getMessage(), ex.getCause());
    }
  }

  BuildImageTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }

  /**
   * Validates and returns an {@link Authorization} from a configured {@link AuthParameters}.
   *
   * <p>TODO: Consolidate with other tasks.
   *
   * @param logger the {@link JibLogger} used to print warnings
   * @param imageProperty the image configuration's name (i.e. "from" or "to")
   * @param auth the auth configuration to get the {@link Authorization} from
   * @return the {@link Authorization}, or null if the username and password aren't both configured
   */
  @VisibleForTesting
  @Nullable
  static Authorization getImageAuthorization(
      JibLogger logger, String imageProperty, AuthParameters auth) {
    if (Strings.isNullOrEmpty(auth.getUsername()) && Strings.isNullOrEmpty(auth.getPassword())) {
      return null;
    }
    if (Strings.isNullOrEmpty(auth.getUsername())) {
      logger.warn(
          "jib."
              + imageProperty
              + ".auth.username is null; ignoring jib."
              + imageProperty
              + ".auth section.");
      return null;
    }
    if (Strings.isNullOrEmpty(auth.getPassword())) {
      logger.warn(
          "jib."
              + imageProperty
              + ".auth.password is null; ignoring jib."
              + imageProperty
              + ".auth section.");
      return null;
    }
    return Authorizations.withBasicCredentials(auth.getUsername(), auth.getPassword());
  }
}
