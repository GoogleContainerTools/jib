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

import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.configuration.credentials.CredentialRetriever;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.image.ImageFormat;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Build image failed");

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
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
          HelpfulSuggestionsProvider.get("Missing target image parameter")
              .forToNotConfigured(
                  "<to><image>", "pom.xml", "mvn compile jib:build -Dimage=<your image name>"));
    }

    MavenJibLogger mavenJibLogger = new MavenJibLogger(getLog());
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenJibLogger, getExtraDirectory());

    PluginConfigurationProcessor pluginConfigurationProcessor =
        PluginConfigurationProcessor.processCommonConfiguration(
            mavenJibLogger, this, mavenProjectProperties);

    ImageReference targetImage =
        PluginConfigurationProcessor.parseImageReference(getTargetImage(), "to");
    CredentialRetrieverFactory credentialRetrieverFactory =
        CredentialRetrieverFactory.forImage(targetImage, mavenJibLogger);
    Credential toCredential =
        ConfigurationPropertyValidator.getImageCredential(
            mavenJibLogger, "jib.to.auth.username", "jib.to.auth.password", getTargetImageAuth());
    RegistryCredentials knownTargetRegistryCredentials = null;
    CredentialRetriever knownCredentialRetriever = null;
    if (toCredential == null) {
      toCredential =
          pluginConfigurationProcessor
              .getMavenSettingsServerCredentials()
              .retrieve(targetImage.getRegistry());
      if (toCredential != null) {
        knownTargetRegistryCredentials =
            new RegistryCredentials(
                MavenSettingsServerCredentials.CREDENTIAL_SOURCE,
                Authorizations.withBasicCredentials(
                    toCredential.getUsername(), toCredential.getPassword()));
        knownCredentialRetriever =
            credentialRetrieverFactory.known(
                toCredential, MavenSettingsServerCredentials.CREDENTIAL_SOURCE);
      }
    } else {
      knownTargetRegistryCredentials =
          new RegistryCredentials(
              "jib-maven-plugin <to><auth> configuration",
              Authorizations.withBasicCredentials(
                  toCredential.getUsername(), toCredential.getPassword()));
      knownCredentialRetriever =
          credentialRetrieverFactory.known(
              toCredential, "jib-maven-plugin <to><auth> configuration");
    }
    // Makes credential retriever list.
    List<CredentialRetriever> credentialRetrievers = new ArrayList<>();
    String credentialHelperSuffix = getTargetImageCredentialHelperName();
    if (credentialHelperSuffix != null) {
      credentialRetrievers.add(
          credentialRetrieverFactory.dockerCredentialHelper(credentialHelperSuffix));
    }
    if (knownCredentialRetriever != null) {
      credentialRetrievers.add(knownCredentialRetriever);
    }
    credentialRetrievers.add(credentialRetrieverFactory.inferCredentialHelper());
    credentialRetrievers.add(credentialRetrieverFactory.dockerConfig());
    ImageConfiguration targetImageConfiguration =
        ImageConfiguration.builder(targetImage)
            .setCredentialHelper(getTargetImageCredentialHelperName())
            .setKnownRegistryCredentials(knownTargetRegistryCredentials)
            .setCredentialRetrievers(credentialRetrievers)
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

    try {
      BuildStepsRunner.forBuildImage(buildConfiguration).build(HELPFUL_SUGGESTIONS);
      getLog().info("");

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
