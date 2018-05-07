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

import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.frontend.BuildImageStepsExecutionException;
import com.google.cloud.tools.jib.frontend.BuildImageStepsRunner;
import com.google.cloud.tools.jib.frontend.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.OCIManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Builds a container image. */
@Mojo(name = "build", requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class BuildImageMojo extends JibPluginConfiguration {

  /** Enumeration of {@link BuildableManifestTemplate}s. */
  public enum ImageFormat {
    Docker(V22ManifestTemplate.class),
    OCI(OCIManifestTemplate.class);

    private final Class<? extends BuildableManifestTemplate> manifestTemplateClass;

    ImageFormat(Class<? extends BuildableManifestTemplate> manifestTemplateClass) {
      this.manifestTemplateClass = manifestTemplateClass;
    }

    private Class<? extends BuildableManifestTemplate> getManifestTemplateClass() {
      return manifestTemplateClass;
    }
  }

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  private static final String CACHE_DIRECTORY_NAME = "jib-cache";

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-maven-plugin";

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Build image failed");

  @Nullable
  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    // These @Nullable parameters should never be null.
    Preconditions.checkNotNull(project);
    Preconditions.checkNotNull(session);
    Preconditions.checkNotNull(repository);
    Preconditions.checkNotNull(imageFormat);

    validateParameters();

    ProjectProperties projectProperties = new ProjectProperties(project, getLog());
    String inferredMainClass = projectProperties.getMainClass(mainClass);

    SourceFilesConfiguration sourceFilesConfiguration =
        projectProperties.getSourceFilesConfiguration();

    // Parses 'from' into image reference.
    ImageReference baseImage = getBaseImageReference();

    // Checks Maven settings for registry credentials.
    MavenSettingsServerCredentials mavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(session.getSettings());
    RegistryCredentials knownBaseRegistryCredentials =
        mavenSettingsServerCredentials.retrieve(baseImage.getRegistry());
    RegistryCredentials knownTargetRegistryCredentials =
        mavenSettingsServerCredentials.retrieve(registry);

    ImageReference targetImageReference = ImageReference.of(registry, repository, tag);
    ImageFormat imageFormatToEnum = ImageFormat.valueOf(imageFormat);
    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(new MavenBuildLogger(getLog()))
            .setBaseImage(baseImage)
            // TODO: This is a temporary hack that will be fixed in an immediate follow-up PR. Do
            // NOT release.
            .setBaseImageCredentialHelperName(Preconditions.checkNotNull(credHelpers).get(0))
            .setKnownBaseRegistryCredentials(knownBaseRegistryCredentials)
            .setTargetImage(targetImageReference)
            .setTargetImageCredentialHelperName(Preconditions.checkNotNull(credHelpers).get(0))
            .setKnownTargetRegistryCredentials(knownTargetRegistryCredentials)
            .setMainClass(inferredMainClass)
            .setJvmFlags(jvmFlags)
            .setEnvironment(environment)
            .setTargetFormat(imageFormatToEnum.getManifestTemplateClass())
            .build();

    // TODO: Instead of disabling logging, have authentication credentials be provided
    // Disables annoying Apache HTTP client logging.
    System.setProperty(
        "org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "error");

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    // Uses a directory in the Maven build cache as the Jib cache.
    Path cacheDirectory = Paths.get(project.getBuild().getDirectory(), CACHE_DIRECTORY_NAME);
    try {
      BuildImageStepsRunner buildImageStepsRunner =
          BuildImageStepsRunner.newRunner(
              buildConfiguration, sourceFilesConfiguration, cacheDirectory, useOnlyProjectCache);

      getLog().info("");
      getLog().info("Pushing image as " + targetImageReference);
      getLog().info("");

      buildImageStepsRunner.buildImage(HELPFUL_SUGGESTIONS);

      getLog().info("");
      getLog().info("Built and pushed image as " + targetImageReference);
      getLog().info("");

    } catch (CacheDirectoryCreationException | BuildImageStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }

  /** Checks validity of plugin parameters. */
  private void validateParameters() throws MojoFailureException {
    // These @Nullable parameters should never be null.
    Preconditions.checkNotNull(repository);
    Preconditions.checkNotNull(imageFormat);

    // Validates 'registry'.
    if (!Strings.isNullOrEmpty(registry) && !ImageReference.isValidRegistry(registry)) {
      getLog().error("Invalid format for 'registry'");
    }
    // Validates 'repository'.
    if (!ImageReference.isValidRepository(repository)) {
      getLog().error("Invalid format for 'repository'");
    }
    // Validates 'tag'.
    if (!Strings.isNullOrEmpty(tag)) {
      if (!ImageReference.isValidTag(tag)) {
        getLog().error("Invalid format for 'tag'");
      }

      // 'tag' must not contain forward slashes.
      if (tag.indexOf('/') >= 0) {
        getLog().error("'tag' cannot contain '/'");
        throw new MojoFailureException("Invalid configuration parameters");
      }
    }
    // Validates 'imageFormat'.
    boolean validFormat = false;
    for (ImageFormat format : ImageFormat.values()) {
      if (imageFormat.equals(format.name())) {
        validFormat = true;
        break;
      }
    }
    if (!validFormat) {
      throw new MojoFailureException(
          "<imageFormat> parameter is configured with value '"
              + imageFormat
              + "', but the only valid configuration options are '"
              + ImageFormat.Docker
              + "' and '"
              + ImageFormat.OCI
              + "'.");
    }
  }

  /** @return the {@link ImageReference} parsed from {@link #from}. */
  private ImageReference getBaseImageReference() throws MojoFailureException {
    Preconditions.checkNotNull(from);

    try {
      ImageReference baseImage = ImageReference.parse(from);

      if (baseImage.usesDefaultTag()) {
        getLog()
            .warn(
                "Base image '"
                    + baseImage
                    + "' does not use a specific image digest - build may not be reproducible");
      }

      return baseImage;

    } catch (InvalidImageReferenceException ex) {
      throw new MojoFailureException("Parameter 'from' is invalid", ex);
    }
  }
}
