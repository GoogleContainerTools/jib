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

package com.google.cloud.tools.jib.maven;

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
import com.google.cloud.tools.jib.image.ImageFormat;
import com.google.cloud.tools.jib.image.ImageReference;
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
import java.util.Arrays;
import javax.annotation.Nullable;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Builds a container image. */
@Mojo(
    name = BuildImageMojo.GOAL_NAME,
    requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class BuildImageMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "build";

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-maven-plugin";

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Build image failed");

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    // TODO: Consolidate all of these checks.
    if ("pom".equals(getProject().getPackaging())) {
      getLog().info("Skipping containerization because packaging is 'pom'...");
      return;
    }

    MavenJibLogger mavenJibLogger = new MavenJibLogger(getLog());
    handleDeprecatedParameters(mavenJibLogger);
    SystemPropertyValidator.checkHttpTimeoutProperty(MojoExecutionException::new);

    // Validates 'format'.
    if (Arrays.stream(ImageFormat.values()).noneMatch(value -> value.name().equals(getFormat()))) {
      throw new MojoFailureException(
          "<format> parameter is configured with value '"
              + getFormat()
              + "', but the only valid configuration options are '"
              + ImageFormat.Docker
              + "' and '"
              + ImageFormat.OCI
              + "'.");
    }

    // Parses 'from' into image reference.
    ImageReference baseImage = parseImageReference(getBaseImage(), "from");

    // Parses 'to' into image reference.
    if (Strings.isNullOrEmpty(getTargetImage())) {
      throw new MojoFailureException(
          HelpfulSuggestionsProvider.get("Missing target image parameter")
              .forToNotConfigured(
                  "<to><image>", "pom.xml", "mvn compile jib:build -Dimage=<your image name>"));
    }
    ImageReference targetImage = parseImageReference(getTargetImage(), "to");

    // Checks Maven settings for registry credentials.
    if (Boolean.getBoolean("sendCredentialsOverHttp")) {
      mavenJibLogger.warn(
          "Authentication over HTTP is enabled. It is strongly recommended that you do not enable "
              + "this on a public network!");
    }

    MavenSettingsServerCredentials mavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(
            Preconditions.checkNotNull(session).getSettings(), settingsDecrypter, mavenJibLogger);
    Authorization fromAuthorization =
        getImageAuth(
            mavenJibLogger,
            "from",
            "jib.from.auth.username",
            "jib.from.auth.password",
            getBaseImageAuth());
    RegistryCredentials knownBaseRegistryCredentials =
        fromAuthorization != null
            ? new RegistryCredentials(
                "jib-maven-plugin <from><auth> configuration", fromAuthorization)
            : mavenSettingsServerCredentials.retrieve(baseImage.getRegistry());
    Authorization toAuthorization =
        getImageAuth(
            mavenJibLogger,
            "to",
            "jib.to.auth.username",
            "jib.to.auth.password",
            getTargetImageAuth());
    RegistryCredentials knownTargetRegistryCredentials =
        toAuthorization != null
            ? new RegistryCredentials("jib-maven-plugin <to><auth> configuration", toAuthorization)
            : mavenSettingsServerCredentials.retrieve(targetImage.getRegistry());

    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenJibLogger, getExtraDirectory());
    String mainClass = mavenProjectProperties.getMainClass(this);

    // Builds the BuildConfiguration.
    ImageConfiguration baseImageConfiguration =
        ImageConfiguration.builder(baseImage)
            .setCredentialHelper(getBaseImageCredentialHelperName())
            .setKnownRegistryCredentials(knownBaseRegistryCredentials)
            .build();

    ImageConfiguration targetImageConfiguration =
        ImageConfiguration.builder(targetImage)
            .setCredentialHelper(getTargetImageCredentialHelperName())
            .setKnownRegistryCredentials(knownTargetRegistryCredentials)
            .build();

    ContainerConfiguration.Builder containerConfigurationBuilder =
        ContainerConfiguration.builder()
            .setEntrypoint(
                JavaEntrypointConstructor.makeDefaultEntrypoint(getJvmFlags(), mainClass))
            .setProgramArguments(getArgs())
            .setEnvironment(getEnvironment())
            .setExposedPorts(ExposedPortsParser.parse(getExposedPorts()));
    if (getUseCurrentTimestamp()) {
      mavenJibLogger.warn(
          "Setting image creation time to current time; your image may not be reproducible.");
      containerConfigurationBuilder.setCreationTime(Instant.now());
    }

    BuildConfiguration.Builder buildConfigurationBuilder =
        BuildConfiguration.builder(mavenJibLogger)
            .setBaseImageConfiguration(baseImageConfiguration)
            .setTargetImageConfiguration(targetImageConfiguration)
            .setContainerConfiguration(containerConfigurationBuilder.build())
            .setTargetFormat(ImageFormat.valueOf(getFormat()).getManifestTemplateClass())
            .setAllowInsecureRegistries(getAllowInsecureRegistries())
            .setLayerConfigurations(mavenProjectProperties.getLayerConfigurations());

    CacheConfiguration applicationLayersCacheConfiguration =
        CacheConfiguration.forPath(mavenProjectProperties.getCacheDirectory());
    buildConfigurationBuilder.setApplicationLayersCacheConfiguration(
        applicationLayersCacheConfiguration);
    if (getUseOnlyProjectCache()) {
      buildConfigurationBuilder.setBaseImageLayersCacheConfiguration(
          applicationLayersCacheConfiguration);
    }

    BuildConfiguration buildConfiguration = buildConfigurationBuilder.build();

    // TODO: Instead of disabling logging, have authentication credentials be provided
    MavenJibLogger.disableHttpLogging();

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    try {
      BuildStepsRunner.forBuildImage(buildConfiguration).build(HELPFUL_SUGGESTIONS);
      getLog().info("");

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }

  /**
   * Gets an {@link Authorization} from a username and password. First tries system properties, then
   * tries build configuration, otherwise returns null.
   *
   * <p>TODO: Consolidate with the other mojos.
   *
   * @param logger the {@link JibLogger} used to print warnings messages
   * @param imageProperty the image configuration's name (i.e. "from" or "to")
   * @param usernameProperty the name of the username system property
   * @param passwordProperty the name of the password system property
   * @param auth the configured credentials
   * @return a new {@link Authorization} from the system properties or build configuration, or
   *     {@code null} if neither is configured.
   */
  @VisibleForTesting
  @Nullable
  static Authorization getImageAuth(
      JibLogger logger,
      String imageProperty,
      String usernameProperty,
      String passwordProperty,
      AuthConfiguration auth) {
    // System property takes priority over build configuration
    String commandlineUsername = System.getProperty(usernameProperty);
    String commandlinePassword = System.getProperty(passwordProperty);
    if (!Strings.isNullOrEmpty(commandlineUsername)
        && !Strings.isNullOrEmpty(commandlinePassword)) {
      return Authorizations.withBasicCredentials(commandlineUsername, commandlinePassword);
    }

    // Warn if a system property is missing
    if (!Strings.isNullOrEmpty(commandlinePassword) && Strings.isNullOrEmpty(commandlineUsername)) {
      logger.warn(
          passwordProperty
              + " system property is set, but "
              + usernameProperty
              + " is not; attempting other authentication methods.");
    }
    if (!Strings.isNullOrEmpty(commandlineUsername) && Strings.isNullOrEmpty(commandlinePassword)) {
      logger.warn(
          usernameProperty
              + " system property is set, but "
              + passwordProperty
              + " is not; attempting other authentication methods.");
    }

    // Check auth configuration next; warn if they aren't both set
    if (Strings.isNullOrEmpty(auth.getUsername()) && Strings.isNullOrEmpty(auth.getPassword())) {
      return null;
    }
    if (Strings.isNullOrEmpty(auth.getUsername())) {
      logger.warn(
          "<"
              + imageProperty
              + "><auth><username> is missing from maven configuration; ignoring <"
              + imageProperty
              + "><auth> section.");
      return null;
    }
    if (Strings.isNullOrEmpty(auth.getPassword())) {
      logger.warn(
          "<"
              + imageProperty
              + "><auth><password> is missing from maven configuration; ignoring <"
              + imageProperty
              + "><auth> section.");
      return null;
    }

    return Authorizations.withBasicCredentials(auth.getUsername(), auth.getPassword());
  }
}
