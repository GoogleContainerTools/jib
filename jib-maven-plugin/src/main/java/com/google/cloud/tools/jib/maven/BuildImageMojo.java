/*
 * Copyright 2018 Google Inc.
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

import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.BuildImageSteps;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.image.DuplicateLayerException;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.registry.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.NonexistentServerUrlDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/** Builds a container image. */
@Mojo(name = "build", requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class BuildImageMojo extends AbstractMojo {

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  private static final String CACHE_DIRECTORY_NAME = "jib-cache";

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  // TODO: Replace the separate base image parameters with this.
  @Parameter(defaultValue = "gcr.io/distroless/java", required = true)
  private String from;

  @Parameter(defaultValue = "gcr.io", required = true)
  private String baseImageRegistry;

  @Parameter(defaultValue = "distroless/java", required = true)
  private String baseImageRepository;

  @Parameter(defaultValue = "latest", required = true)
  private String baseImageTag;

  @Parameter(required = true)
  private String registry;

  @Parameter(required = true)
  private String repository;

  @Parameter(defaultValue = "latest", required = true)
  private String tag;

  @Parameter private String credentialHelperName;

  @Parameter private List<String> jvmFlags;

  @Parameter(required = true)
  private String mainClass;

  @Override
  public void execute() throws MojoExecutionException {
    SourceFilesConfiguration sourceFilesConfiguration = getSourceFilesConfiguration();

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder()
            .setBaseImageServerUrl(baseImageRegistry)
            .setBaseImageName(baseImageRepository)
            .setBaseImageTag(baseImageTag)
            .setTargetServerUrl(registry)
            .setTargetImageName(repository)
            .setTargetTag(tag)
            .setCredentialHelperName(credentialHelperName)
            .setMainClass(mainClass)
            .build();

    Path cacheDirectory = Paths.get(project.getBuild().getDirectory(), CACHE_DIRECTORY_NAME);
    if (!Files.exists(cacheDirectory)) {
      try {
        Files.createDirectory(cacheDirectory);

      } catch (IOException ex) {
        throw new MojoExecutionException("Could not create cache directory: " + cacheDirectory, ex);
      }
    }

    try {
      BuildImageSteps buildImageSteps =
          new BuildImageSteps(buildConfiguration, sourceFilesConfiguration, cacheDirectory);
      buildImageSteps.run();

    } catch (RegistryUnauthorizedException ex) {
      String suggestion = null;

      if (ex.getHttpResponseException().getStatusCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
        // No permissions to push to target image.
        String targetImage = registry + "/" + repository + ":" + tag;
        suggestion = "make sure your have permission to push to " + targetImage;

      } else if (credentialHelperName == null) {
        // Credential helper not defined.
        suggestion = "set the configuration 'credentialHelperName'";

      } else {
        // Credential helper probably was not configured correctly or did not have the necessary
        // credentials.
        suggestion = "make sure your credential helper is set up correctly";
      }

      StringBuilder message = new StringBuilder("Build image failed");
      if (suggestion != null) {
        message.append("\n\tPerhaps you should ");
        message.append(suggestion);
      }
      throw new MojoExecutionException(message.toString(), ex);

    } catch (IOException
        | RegistryException
        | CacheMetadataCorruptedException
        | DuplicateLayerException
        | LayerPropertyNotFoundException
        | LayerCountMismatchException
        | NonexistentDockerCredentialHelperException
        | RegistryAuthenticationFailedException
        | NonexistentServerUrlDockerCredentialHelperException ex) {
      throw new MojoExecutionException("Build image failed", ex);
    }
  }

  /** @return the {@link SourceFilesConfiguration} based on the current project */
  private SourceFilesConfiguration getSourceFilesConfiguration() throws MojoExecutionException {
    try {
      SourceFilesConfiguration sourceFilesConfiguration =
          new MavenSourceFilesConfiguration(project);

      // Logs the different source files used.
      getLog().debug("Dependencies:");
      sourceFilesConfiguration
          .getDependenciesFiles()
          .forEach(dependencyFile -> getLog().debug("Dependency: " + dependencyFile));

      getLog().debug("Resources:");
      sourceFilesConfiguration
          .getResourcesFiles()
          .forEach(resourceFile -> getLog().debug("Resource: " + resourceFile));

      getLog().debug("Classes:");
      sourceFilesConfiguration
          .getClassesFiles()
          .forEach(classesFile -> getLog().debug("Class: " + classesFile));

      return sourceFilesConfiguration;

    } catch (IOException ex) {
      throw new MojoExecutionException("Obtaining project build output files failed", ex);
    }
  }
}
