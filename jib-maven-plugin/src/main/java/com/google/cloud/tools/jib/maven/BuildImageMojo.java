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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.image.ImageFormat;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.Optional;
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

  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Build image failed";

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (isSkipped()) {
      getLog().info("Skipping containerization because jib-maven-plugin: skip = true");
      return;
    }
    if ("pom".equals(getProject().getPackaging())) {
      getLog().info("Skipping containerization because packaging is 'pom'...");
      return;
    }

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

    // Parses 'to' into image reference.
    if (Strings.isNullOrEmpty(getTargetImage())) {
      throw new MojoFailureException(
          HelpfulSuggestions.forToNotConfigured(
              "Missing target image parameter",
              "<to><image>",
              "pom.xml",
              "mvn compile jib:build -Dimage=<your image name>"));
    }

    MavenJibLogger mavenJibLogger = new MavenJibLogger(getLog());
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenJibLogger, getExtraDirectory());

    PluginConfigurationProcessor pluginConfigurationProcessor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mavenJibLogger, this, mavenProjectProperties);

    ImageReference targetImage =
        PluginConfigurationProcessor.parseImageReference(getTargetImage(), "to");

    DefaultCredentialRetrievers defaultCredentialRetrievers =
        DefaultCredentialRetrievers.init(
            CredentialRetrieverFactory.forImage(targetImage, mavenJibLogger));
    Optional<Credential> optionalToCredential =
        ConfigurationPropertyValidator.getImageCredential(
            mavenJibLogger, "jib.to.auth.username", "jib.to.auth.password", getTargetImageAuth());
    if (optionalToCredential.isPresent()) {
      defaultCredentialRetrievers.setKnownCredential(
          optionalToCredential.get(), "jib-maven-plugin <to><auth> configuration");
    } else {
      optionalToCredential =
          pluginConfigurationProcessor
              .getMavenSettingsServerCredentials()
              .retrieve(targetImage.getRegistry());
      optionalToCredential.ifPresent(
          toCredential ->
              defaultCredentialRetrievers.setInferredCredential(
                  toCredential, MavenSettingsServerCredentials.CREDENTIAL_SOURCE));
    }
    defaultCredentialRetrievers.setCredentialHelperSuffix(getTargetImageCredentialHelperName());

    ImageConfiguration targetImageConfiguration =
        ImageConfiguration.builder(targetImage)
            .setCredentialRetrievers(defaultCredentialRetrievers.asList())
            .build();

    BuildConfiguration buildConfiguration =
        pluginConfigurationProcessor
            .getBuildConfigurationBuilder()
            .setBaseImageConfiguration(
                pluginConfigurationProcessor.getBaseImageConfigurationBuilder().build())
            .setTargetImageConfiguration(targetImageConfiguration)
            .setContainerConfiguration(
                pluginConfigurationProcessor.getContainerConfigurationBuilder().build())
            .setTargetFormat(ImageFormat.valueOf(getFormat()).getManifestTemplateClass())
            .build();

    HelpfulSuggestions helpfulSuggestions =
        new MavenHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, this)
            .setBaseImageReference(buildConfiguration.getBaseImageConfiguration().getImage())
            .setBaseImageHasConfiguredCredentials(
                pluginConfigurationProcessor.isBaseImageCredentialPresent())
            .setTargetImageReference(buildConfiguration.getTargetImageConfiguration().getImage())
            .setTargetImageHasConfiguredCredentials(optionalToCredential.isPresent())
            .build();

    try {
      BuildStepsRunner.forBuildImage(buildConfiguration).build(helpfulSuggestions);
      getLog().info("");

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
