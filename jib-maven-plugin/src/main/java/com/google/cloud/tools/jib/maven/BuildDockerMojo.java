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

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.IncompatibleBaseImageJavaVersionException;
import com.google.cloud.tools.jib.plugins.common.InvalidAppRootException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerVolumeException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerizingModeException;
import com.google.cloud.tools.jib.plugins.common.InvalidWorkingDirectoryException;
import com.google.cloud.tools.jib.plugins.common.JibBuildRunner;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Builds a container image and exports to the default Docker daemon. */
@Mojo(
    name = BuildDockerMojo.GOAL_NAME,
    requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class BuildDockerMojo extends JibPluginConfiguration {

  /**
   * Object that configures the Docker executable and the additional environment variables to use
   * when executing the executable.
   */
  public static class DockerClientConfiguration {

    @Nullable @Parameter private File executable;
    @Nullable @Parameter private Map<String, String> environment;
  }

  @VisibleForTesting static final String GOAL_NAME = "dockerBuild";
  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Build to Docker daemon failed";

  @Parameter private DockerClientConfiguration dockerClient = new DockerClientConfiguration();

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (isSkipped()) {
      getLog().info("Skipping containerization because jib-maven-plugin: skip = true");
      return;
    } else if (!isContainerizable()) {
      getLog()
          .info(
              "Skipping containerization of this module (not specified in "
                  + PropertyNames.CONTAINERIZE
                  + ")");
      return;
    }
    if ("pom".equals(getProject().getPackaging())) {
      getLog().info("Skipping containerization because packaging is 'pom'...");
      return;
    }

    Path dockerExecutable = getDockerClientExecutable();
    boolean isDockerInstalled =
        dockerExecutable == null
            ? DockerClient.isDefaultDockerInstalled()
            : DockerClient.isDockerInstalled(dockerExecutable);
    if (!isDockerInstalled) {
      throw new MojoExecutionException(
          HelpfulSuggestions.forDockerNotInstalled(HELPFUL_SUGGESTIONS_PREFIX));
    }

    try {
      RawConfiguration mavenRawConfiguration = new MavenRawConfiguration(this);
      MavenProjectProperties projectProperties =
          MavenProjectProperties.getForProject(getProject(), getSession(), getLog());

      MavenHelpfulSuggestionsBuilder mavenHelpfulSuggestionsBuilder =
          new MavenHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, this);

      PluginConfigurationProcessor pluginConfigurationProcessor =
          PluginConfigurationProcessor.processCommonConfigurationForDockerDaemonImage(
              mavenRawConfiguration,
              new MavenSettingsServerCredentials(
                  getSession().getSettings(), getSettingsDecrypter()),
              projectProperties,
              dockerExecutable,
              getDockerClientEnvironment(),
              mavenHelpfulSuggestionsBuilder.build());
      MavenSettingsProxyProvider.activateHttpAndHttpsProxies(
          getSession().getSettings(), getSettingsDecrypter());

      ImageReference targetImageReference = pluginConfigurationProcessor.getTargetImageReference();
      HelpfulSuggestions helpfulSuggestions =
          mavenHelpfulSuggestionsBuilder
              .setBaseImageReference(pluginConfigurationProcessor.getBaseImageReference())
              .setBaseImageHasConfiguredCredentials(
                  pluginConfigurationProcessor.isBaseImageCredentialPresent())
              .setTargetImageReference(targetImageReference)
              .build();

      Path buildOutput = Paths.get(getProject().getBuild().getDirectory());

      try {
        JibBuildRunner.forBuildToDockerDaemon(targetImageReference, getTargetImageAdditionalTags())
            .writeImageDigest(buildOutput.resolve("jib-image.digest"))
            .writeImageId(buildOutput.resolve("jib-image.id"))
            .build(
                pluginConfigurationProcessor.getJibContainerBuilder(),
                pluginConfigurationProcessor.getContainerizer(),
                projectProperties::log,
                helpfulSuggestions);

      } finally {
        // TODO: This should not be called on projectProperties.
        projectProperties.waitForLoggingThread();
        getLog().info("");
      }

    } catch (InvalidAppRootException ex) {
      throw new MojoExecutionException(
          "<container><appRoot> is not an absolute Unix-style path: " + ex.getInvalidPathValue(),
          ex);

    } catch (InvalidContainerizingModeException ex) {
      throw new MojoExecutionException(
          "invalid value for <containerizingMode>: " + ex.getInvalidContainerizingMode(), ex);

    } catch (InvalidWorkingDirectoryException ex) {
      throw new MojoExecutionException(
          "<container><workingDirectory> is not an absolute Unix-style path: "
              + ex.getInvalidPathValue(),
          ex);

    } catch (InvalidContainerVolumeException ex) {
      throw new MojoExecutionException(
          "<container><volumes> is not an absolute Unix-style path: " + ex.getInvalidVolume(), ex);

    } catch (IncompatibleBaseImageJavaVersionException ex) {
      throw new MojoExecutionException(
          HelpfulSuggestions.forIncompatibleBaseImageJavaVesionForMaven(
              ex.getBaseImageMajorJavaVersion(), ex.getProjectMajorJavaVersion()),
          ex);

    } catch (InvalidImageReferenceException ex) {
      throw new MojoExecutionException(
          HelpfulSuggestions.forInvalidImageReference(ex.getInvalidReference()), ex);

    } catch (IOException | CacheDirectoryCreationException | MainClassInferenceException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);

    } catch (BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }

  @Nullable
  private Path getDockerClientExecutable() {
    String property = getProperty(PropertyNames.DOCKER_CLIENT_EXECUTABLE);
    if (property != null) {
      return Paths.get(property);
    }
    return dockerClient.executable == null ? null : dockerClient.executable.toPath();
  }

  @Nullable
  private Map<String, String> getDockerClientEnvironment() {
    String property = getProperty(PropertyNames.DOCKER_CLIENT_ENVIRONMENT);
    if (property != null) {
      return ConfigurationPropertyValidator.parseMapProperty(property);
    }
    return dockerClient.environment;
  }
}
