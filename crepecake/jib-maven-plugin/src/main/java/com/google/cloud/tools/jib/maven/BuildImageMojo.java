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
import com.google.cloud.tools.crepecake.builder.BuildConfiguration;
import com.google.cloud.tools.crepecake.builder.BuildImageSteps;
import com.google.cloud.tools.crepecake.builder.SourceFilesConfiguration;
import com.google.cloud.tools.crepecake.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.LayerCountMismatchException;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.crepecake.registry.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.crepecake.registry.NonexistentServerUrlDockerCredentialHelperException;
import com.google.cloud.tools.crepecake.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.crepecake.registry.RegistryException;
import com.google.cloud.tools.crepecake.registry.RegistryUnauthorizedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Builds a container image. */
@Mojo(name = "build")
public class BuildImageMojo extends AbstractMojo {

  private static class MojoExceptionBuilder {

    private Throwable cause;
    private String suggestion;

    private MojoExceptionBuilder(Throwable cause) {
      this.cause = cause;
    }

    private MojoExceptionBuilder suggest(String suggestion) {
      this.suggestion = suggestion;
      return this;
    }

    private MojoExecutionException build() {
      StringBuilder message = new StringBuilder("Build image failed");
      if (suggestion != null) {
        message.append("\nPerhaps you should ");
        message.append(suggestion);
      }
      return new MojoExecutionException(message.toString(), cause);
    }
  }

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(defaultValue = "gcr.io/distroless/java", required = true)
  private String from;

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
            .setBaseImageServerUrl("registry.hub.docker.com")
            .setBaseImageName("frolvlad/alpine-oraclejdk8")
            .setBaseImageTag("latest")
            .setTargetServerUrl(registry)
            .setTargetImageName(repository)
            .setTargetTag(tag)
            .setCredentialHelperName(credentialHelperName)
            .setMainClass(mainClass)
            .build();

    Path cacheDirectory = Paths.get(project.getBuild().getDirectory(), "jib-cache");
    if (!Files.exists(cacheDirectory)) {
      try {
        Files.createDirectory(cacheDirectory);

      } catch (IOException ex) {
        throw new MojoExecutionException("Could not create cache directory", ex);
      }
    }

    try {
      BuildImageSteps buildImageSteps =
          new BuildImageSteps(buildConfiguration, sourceFilesConfiguration, cacheDirectory);
      buildImageSteps.run();

    } catch (RegistryUnauthorizedException ex) {
      MojoExceptionBuilder mojoExceptionBuilder = new MojoExceptionBuilder(ex);

      if (ex.getHttpResponseException().getStatusCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
        String targetImage = registry + "/" + repository + ":" + tag;
        mojoExceptionBuilder.suggest("make sure your have permission to push to " + targetImage);

      } else if (credentialHelperName == null) {
        mojoExceptionBuilder.suggest("set the configuration 'credentialHelperName'");

      } else {
        mojoExceptionBuilder.suggest("make sure your credential helper is set up correctly");
      }

      throw mojoExceptionBuilder.build();

    } catch (IOException
        | RegistryException
        | CacheMetadataCorruptedException
        | DuplicateLayerException
        | LayerPropertyNotFoundException
        | LayerCountMismatchException
        | NonexistentDockerCredentialHelperException
        | RegistryAuthenticationFailedException
        | NonexistentServerUrlDockerCredentialHelperException ex) {
      throw new MojoExceptionBuilder(ex).build();
    }
  }

  private SourceFilesConfiguration getSourceFilesConfiguration() throws MojoExecutionException {
    try {
      SourceFilesConfiguration sourceFilesConfiguration =
          new MavenSourceFilesConfiguration(project);

      getLog().info("Dependencies:");
      sourceFilesConfiguration
          .getDependenciesFiles()
          .forEach(dependencyFile -> getLog().info("Dependency: " + dependencyFile));

      getLog().info("Resources:");
      sourceFilesConfiguration
          .getResourcesFiles()
          .forEach(resourceFile -> getLog().info("Resource: " + resourceFile));

      getLog().info("Classes:");
      sourceFilesConfiguration
          .getClassesFiles()
          .forEach(classesFile -> getLog().info("Class: " + classesFile));

      return sourceFilesConfiguration;

    } catch (IOException ex) {
      throw new MojoExecutionException("Obtaining project build output files failed", ex);
    }
  }
}
