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
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.OCIManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.base.Preconditions;
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

  @Nullable
  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    // These @Nullable parameters should never be null.
    Preconditions.checkNotNull(project);
    Preconditions.checkNotNull(session);
    Preconditions.checkNotNull(to);
    Preconditions.checkNotNull(to.image);
    Preconditions.checkNotNull(format);

    validateParameters();

    ProjectProperties projectProperties = new ProjectProperties(project, getLog());
    String inferredMainClass = projectProperties.getMainClass(mainClass);

    SourceFilesConfiguration sourceFilesConfiguration =
        projectProperties.getSourceFilesConfiguration();

    // Parses 'from' and 'to' into image reference.
    ImageReference baseImage = getBaseImageReference();
    ImageReference targetImage = getTargetImageReference();

    // Checks Maven settings for registry credentials.
    MavenSettingsServerCredentials mavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(session.getSettings());
    RegistryCredentials knownBaseRegistryCredentials =
        mavenSettingsServerCredentials.retrieve(baseImage.getRegistry());
    RegistryCredentials knownTargetRegistryCredentials =
        mavenSettingsServerCredentials.retrieve(targetImage.getRegistry());

    ImageFormat imageFormatToEnum = ImageFormat.valueOf(format);
    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(new MavenBuildLogger(getLog()))
            .setBaseImage(baseImage)
            .setBaseImageCredentialHelperName(from == null ? null : from.credHelper)
            .setKnownBaseRegistryCredentials(knownBaseRegistryCredentials)
            .setTargetImage(targetImage)
            .setTargetImageCredentialHelperName(to.credHelper)
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
      getLog().info("Pushing image as " + targetImage);
      getLog().info("");

      try {
        buildImageStepsRunner.buildImage();

      } catch (BuildImageStepsExecutionException ex) {
        throw new MojoExecutionException(ex.getMessage(), ex.getCause());
      }

      getLog().info("");
      getLog().info("Built and pushed image as " + targetImage);
      getLog().info("");

    } catch (CacheDirectoryCreationException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }

  /** Checks validity of plugin parameters. */
  private void validateParameters() throws MojoFailureException {
    // Validates 'imageFormat'.
    boolean validFormat = false;
    for (ImageFormat imageFormat : ImageFormat.values()) {
      if (imageFormat.name().equals(format)) {
        validFormat = true;
        break;
      }
    }
    if (!validFormat) {
      throw new MojoFailureException(
          "<imageFormat> parameter is configured with value '"
              + format
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
    Preconditions.checkNotNull(from.image);

    try {
      ImageReference baseImage = ImageReference.parse(from.image);

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

  /** @return the {@link ImageReference} parsed from {@link #to}. */
  private ImageReference getTargetImageReference() throws MojoFailureException {
    Preconditions.checkNotNull(to);
    Preconditions.checkNotNull(to.image);

    try {
      return ImageReference.parse(to.image);

    } catch (InvalidImageReferenceException ex) {
      throw new MojoFailureException("Parameter 'to' is invalid", ex);
    }
  }
}
