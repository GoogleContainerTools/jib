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

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.image.ImageFormat;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.AppRootInvalidException;
import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.InferredAuthRetrievalException;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Verify;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    MojoCommon.disableHttpLogging();
    try {
      AbsoluteUnixPath appRoot = MojoCommon.getAppRootChecked(this);

      MavenProjectProperties projectProperties =
          MavenProjectProperties.getForProject(
              getProject(),
              getLog(),
              MojoCommon.getExtraDirectoryPath(this),
              MojoCommon.convertPermissionsList(getExtraDirectoryPermissions()),
              appRoot);
      EventDispatcher eventDispatcher =
          new DefaultEventDispatcher(projectProperties.getEventHandlers());
      RawConfiguration rawConfiguration = new MavenRawConfiguration(this, eventDispatcher);

      PluginConfigurationProcessor pluginConfigurationProcessor =
          PluginConfigurationProcessor.processCommonConfiguration(
              rawConfiguration, projectProperties);

      ImageReference targetImageReference = ImageReference.parse(getTargetImage());

      DefaultCredentialRetrievers defaultCredentialRetrievers =
          DefaultCredentialRetrievers.init(
              CredentialRetrieverFactory.forImage(targetImageReference, eventDispatcher));
      Optional<Credential> optionalToCredential =
          ConfigurationPropertyValidator.getImageCredential(
              eventDispatcher,
              PropertyNames.TO_AUTH_USERNAME,
              PropertyNames.TO_AUTH_PASSWORD,
              getTargetImageAuth());
      if (optionalToCredential.isPresent()) {
        defaultCredentialRetrievers.setKnownCredential(
            optionalToCredential.get(), "jib-maven-plugin <to><auth> configuration");
      } else {
        Optional<AuthProperty> optionalInferredAuth =
            rawConfiguration.getInferredAuth(targetImageReference.getRegistry());
        if (optionalInferredAuth.isPresent()) {
          AuthProperty auth = optionalInferredAuth.get();
          String username = Verify.verifyNotNull(auth.getUsername());
          String password = Verify.verifyNotNull(auth.getPassword());
          Credential credential = Credential.basic(username, password);
          defaultCredentialRetrievers.setInferredCredential(
              credential, auth.getPropertyDescriptor());
        }
      }
      defaultCredentialRetrievers.setCredentialHelper(getTargetImageCredentialHelperName());

      RegistryImage targetImage = RegistryImage.named(targetImageReference);
      defaultCredentialRetrievers.asList().forEach(targetImage::addCredentialRetriever);

      JibContainerBuilder jibContainerBuilder =
          pluginConfigurationProcessor
              .getJibContainerBuilder()
              // Only uses possibly non-Docker formats for build to registry.
              .setFormat(ImageFormat.valueOf(getFormat()));

      Containerizer containerizer = Containerizer.to(targetImage);
      PluginConfigurationProcessor.configureContainerizer(
          containerizer, rawConfiguration, projectProperties, MavenProjectProperties.TOOL_NAME);

      HelpfulSuggestions helpfulSuggestions =
          new MavenHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, this)
              .setBaseImageReference(pluginConfigurationProcessor.getBaseImageReference())
              .setBaseImageHasConfiguredCredentials(
                  pluginConfigurationProcessor.isBaseImageCredentialPresent())
              .setTargetImageReference(targetImageReference)
              .setTargetImageHasConfiguredCredentials(optionalToCredential.isPresent())
              .build();

      Path buildOutput = Paths.get(getProject().getBuild().getDirectory());
      BuildStepsRunner.forBuildImage(targetImageReference, getTargetImageAdditionalTags())
          .writeImageDigest(buildOutput.resolve("jib-image.digest"))
          .build(
              jibContainerBuilder,
              containerizer,
              eventDispatcher,
              projectProperties.getJavaLayerConfigurations().getLayerConfigurations(),
              helpfulSuggestions);
      getLog().info("");

    } catch (AppRootInvalidException ex) {
      throw new MojoExecutionException(
          "<container><appRoot> is not an absolute Unix-style path: " + ex.getInvalidAppRoot());

    } catch (InvalidImageReferenceException
        | IOException
        | CacheDirectoryCreationException
        | MainClassInferenceException
        | InferredAuthRetrievalException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);

    } catch (BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
