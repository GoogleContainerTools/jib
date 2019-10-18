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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/** Builds a container image and exports to the default Docker daemon. */
public class BuildDockerTask extends DefaultTask implements JibTask {

  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Build to Docker daemon failed";

  @Nullable private JibExtension jibExtension;

  private final DockerClientParameters dockerClientParameters = new DockerClientParameters();

  /**
   * This will call the property {@code "jib"} so that it is the same name as the extension. This
   * way, the user would see error messages for missing configuration with the prefix {@code jib.}.
   *
   * @return the {@link JibExtension}.
   */
  @Nested
  @Nullable
  public JibExtension getJib() {
    return jibExtension;
  }

  /**
   * The target image can be overridden with the {@code --image} command line option.
   *
   * @param targetImage the name of the 'to' image.
   */
  @Option(option = "image", description = "The image reference for the target image")
  public void setTargetImage(String targetImage) {
    Preconditions.checkNotNull(jibExtension).getTo().setImage(targetImage);
  }

  @Nested
  @Optional
  @Deprecated
  public DockerClientParameters getDockerClient() {
    return dockerClientParameters;
  }

  @Deprecated
  public void dockerClient(Action<? super DockerClientParameters> action) {
    action.execute(dockerClientParameters);
  }

  @TaskAction
  public void buildDocker() {
    Preconditions.checkNotNull(jibExtension);

    // Check deprecated parameters
    Path dockerExecutable = jibExtension.getDockerClient().getExecutablePath();
    Map<String, String> dockerEnvironment = jibExtension.getDockerClient().getEnvironment();
    if (getDockerClient().getExecutable() != null) {
      jibExtension.getDockerClient().setExecutable(getDockerClient().getExecutable());
      getProject()
          .getLogger()
          .warn(
              "'jibDockerBuild.dockerClient.executable' is deprecated; use 'jib.dockerClient.executable' instead.");
    }
    if (!getDockerClient().getEnvironment().isEmpty()) {
      jibExtension.getDockerClient().setEnvironment(getDockerClient().getEnvironment());
      getProject()
          .getLogger()
          .warn(
              "'jibDockerBuild.dockerClient.environment' is deprecated; use 'jib.dockerClient.environment' instead.");
    }
    if ((getDockerClient().getExecutable() != null && dockerExecutable != null)
        || (!getDockerClient().getEnvironment().isEmpty() && !dockerEnvironment.isEmpty())) {
      throw new GradleException(
          "Cannot configure 'jibDockerBuild.dockerClient' and 'jib.dockerClient' simultaneously");
    }

    boolean isDockerInstalled =
        dockerExecutable == null
            ? DockerClient.isDefaultDockerInstalled()
            : DockerClient.isDockerInstalled(dockerExecutable);
    if (!isDockerInstalled) {
      throw new GradleException(
          HelpfulSuggestions.forDockerNotInstalled(HELPFUL_SUGGESTIONS_PREFIX));
    }

    TaskCommon.checkDeprecatedUsage(jibExtension, getLogger());
    TaskCommon.disableHttpLogging();

    GradleProjectProperties projectProperties =
        GradleProjectProperties.getForProject(getProject(), getLogger());
    try {
      PluginConfigurationProcessor.createJibBuildRunnerForDockerDaemonImage(
              new GradleRawConfiguration(jibExtension),
              ignored -> java.util.Optional.empty(),
              projectProperties,
              new GradleHelpfulSuggestions(HELPFUL_SUGGESTIONS_PREFIX))
          .runBuild();

    } catch (Exception ex) {
      TaskCommon.rethrowJibException(ex);

    } finally {
      projectProperties.waitForLoggingThread();
    }
  }

  @Override
  public BuildDockerTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }
}
