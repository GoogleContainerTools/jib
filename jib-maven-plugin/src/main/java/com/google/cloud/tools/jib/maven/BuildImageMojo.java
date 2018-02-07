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
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedException;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/** Builds a container image. */
@Mojo(name = "build", requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class BuildImageMojo extends AbstractMojo {

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  private static final String CACHE_DIRECTORY_NAME = "jib-cache";

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-maven-plugin";

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  // TODO: Replace the separate base image parameters with this.
  @Parameter(defaultValue = "gcr.io/distroless/java", required = true)
  private String from;

  @Parameter(required = true)
  private String registry;

  @Parameter(required = true)
  private String repository;

  @Parameter(defaultValue = "latest", required = true)
  private String tag;

  @Parameter private List<String> credentialHelperNames;

  @Parameter private List<String> jvmFlags;

  @Parameter private Map<String, String> environment;

  @Parameter private String mainClass;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    // Extracts main class from 'maven-jar-plugin' configuration if available.
    if (mainClass == null) {
      Plugin mavenJarPlugin = project.getPlugin("org.apache.maven.plugins:maven-jar-plugin");
      if (mavenJarPlugin != null) {
        mainClass = getMainClassFromMavenJarPlugin(mavenJarPlugin);
        if (mainClass == null) {
          throwMojoExecutionExceptionWithHelpMessage(
              new MojoFailureException("Could not find main class specified in maven-jar-plugin"),
              "add a `mainClass` configuration to jib-maven-plugin");
        }

        getLog().info("Using main class from maven-jar-plugin: " + mainClass);
      }
    }

    SourceFilesConfiguration sourceFilesConfiguration = getSourceFilesConfiguration();

    // Parse 'from' into image reference.
    ImageReference baseImage = getImageReference();

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder()
            .setBuildLogger(new MavenBuildLogger(getLog()))
            .setBaseImageServerUrl(baseImage.getRegistry())
            .setBaseImageName(baseImage.getRepository())
            .setBaseImageTag(baseImage.getTag())
            .setTargetServerUrl(registry)
            .setTargetImageName(repository)
            .setTargetTag(tag)
            .setCredentialHelperNames(credentialHelperNames)
            .setMainClass(mainClass)
            .setJvmFlags(jvmFlags)
            .setEnvironment(environment)
            .build();

    // Uses a directory in the Maven build cache as the Jib cache.
    Path cacheDirectory = Paths.get(project.getBuild().getDirectory(), CACHE_DIRECTORY_NAME);
    if (!Files.exists(cacheDirectory)) {
      try {
        Files.createDirectory(cacheDirectory);

      } catch (IOException ex) {
        throw new MojoExecutionException("Could not create cache directory: " + cacheDirectory, ex);
      }
    }

    getLog().info("");
    getLog().info("Pushing image as " + registry + "/" + repository + ":" + tag);
    getLog().info("");

    // TODO: Instead of disabling logging, have authentication credentials be provided
    // Disables annoying Apache HTTP client logging.
    System.setProperty(
        "org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "error");

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    buildImage(new BuildImageSteps(buildConfiguration, sourceFilesConfiguration, cacheDirectory));

    getLog().info("");
    getLog().info("Built and pushed image as " + registry + "/" + repository + ":" + tag);
    getLog().info("");
  }

  @VisibleForTesting
  void setCredentiaHelperNames(List<String> credentialHelperNames) {
    this.credentialHelperNames = credentialHelperNames;
  }

  @VisibleForTesting
  void buildImage(BuildImageSteps buildImageSteps) throws MojoExecutionException {
    try {
      buildImageSteps.run();

    } catch (CacheMetadataCorruptedException cacheMetadataCorruptedException) {
      throwMojoExecutionExceptionWithHelpMessage(
          cacheMetadataCorruptedException, "run 'mvn clean' to clear the cache");

    } catch (ExecutionException executionException) {
      if (executionException.getCause() instanceof HttpHostConnectException) {
        // Failed to connect to registry.
        throwMojoExecutionExceptionWithHelpMessage(
            executionException.getCause(),
            "make sure your Internet is up and that the registry you are pushing to exists");

      } else if (executionException.getCause() instanceof RegistryUnauthorizedException) {
        RegistryUnauthorizedException registryUnauthorizedException =
            (RegistryUnauthorizedException) executionException.getCause();
        if (registryUnauthorizedException.getHttpResponseException().getStatusCode()
            == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
          // No permissions for registry/repository.
          throwMojoExecutionExceptionWithHelpMessage(
              registryUnauthorizedException,
              "make sure your have permissions for "
                  + registryUnauthorizedException.getImageReference());

        } else if (credentialHelperNames == null || credentialHelperNames.isEmpty()) {
          // No credential helpers not defined.
          throwMojoExecutionExceptionWithHelpMessage(
              registryUnauthorizedException,
              "set a credential helper name with the configuration 'credentialHelperNames'");

        } else {
          // Credential helper probably was not configured correctly or did not have the necessary
          // credentials.
          throwMojoExecutionExceptionWithHelpMessage(
              registryUnauthorizedException,
              "make sure your credential helper for '"
                  + registryUnauthorizedException.getImageReference()
                  + "' is set up correctly");
        }

      } else {
        throwMojoExecutionExceptionWithHelpMessage(executionException.getCause(), null);
      }

    } catch (InterruptedException | IOException ex) {
      getLog().error(ex);
      // TODO: Add more suggestions for various build failures.
      throwMojoExecutionExceptionWithHelpMessage(ex, null);
    }
  }

  /** @return the {@link SourceFilesConfiguration} based on the current project */
  private SourceFilesConfiguration getSourceFilesConfiguration() throws MojoExecutionException {
    try {
      SourceFilesConfiguration sourceFilesConfiguration =
          new MavenSourceFilesConfiguration(project);

      // Logs the different source files used.
      getLog().info("");
      getLog().info("Containerizing application with the following files:");
      getLog().info("");

      getLog().info("\tDependencies:");
      getLog().info("");
      sourceFilesConfiguration
          .getDependenciesFiles()
          .forEach(dependencyFile -> getLog().info("\t\t" + dependencyFile));

      getLog().info("\tResources:");
      getLog().info("");
      sourceFilesConfiguration
          .getResourcesFiles()
          .forEach(resourceFile -> getLog().info("\t\t" + resourceFile));

      getLog().info("\tClasses:");
      getLog().info("");
      sourceFilesConfiguration
          .getClassesFiles()
          .forEach(classesFile -> getLog().info("\t\t" + classesFile));

      getLog().info("");

      return sourceFilesConfiguration;

    } catch (IOException ex) {
      throw new MojoExecutionException("Obtaining project build output files failed", ex);
    }
  }

  /** Gets the {@code mainClass} configuration from {@code maven-jar-plugin}. */
  @Nullable
  private String getMainClassFromMavenJarPlugin(Plugin mavenJarPlugin) {
    Xpp3Dom jarConfiguration = (Xpp3Dom) mavenJarPlugin.getConfiguration();
    if (jarConfiguration == null) {
      return null;
    }
    Xpp3Dom archiveObject = jarConfiguration.getChild("archive");
    if (archiveObject == null) {
      return null;
    }
    Xpp3Dom manifestObject = archiveObject.getChild("manifest");
    if (manifestObject == null) {
      return null;
    }
    Xpp3Dom mainClassObject = manifestObject.getChild("mainClass");
    if (mainClassObject == null) {
      return null;
    }
    return mainClassObject.getValue();
  }

  private ImageReference getImageReference() throws MojoFailureException {
    try {
      return ImageReference.parse(from);

    } catch (InvalidImageReferenceException ex) {
      throw new MojoFailureException("Parameter 'from' is invalid", ex);
    }
  }

  /**
   * Wraps an exception in a {@link MojoExecutionException} and provides a suggestion on how to fix
   * the error.
   */
  private <T extends Throwable> void throwMojoExecutionExceptionWithHelpMessage(
      T ex, @Nullable String suggestion) throws MojoExecutionException {
    StringBuilder message = new StringBuilder("Build image failed");
    if (suggestion != null) {
      message.append(", perhaps you should ");
      message.append(suggestion);
    }
    throw new MojoExecutionException(message.toString(), ex);
  }
}
