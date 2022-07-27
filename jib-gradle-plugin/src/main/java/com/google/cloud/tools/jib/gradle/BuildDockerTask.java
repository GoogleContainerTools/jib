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

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.docker.CliDockerClient;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.ExtraDirectoryNotFoundException;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.IncompatibleBaseImageJavaVersionException;
import com.google.cloud.tools.jib.plugins.common.InvalidAppRootException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerVolumeException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerizingModeException;
import com.google.cloud.tools.jib.plugins.common.InvalidCreationTimeException;
import com.google.cloud.tools.jib.plugins.common.InvalidFilesModificationTimeException;
import com.google.cloud.tools.jib.plugins.common.InvalidPlatformException;
import com.google.cloud.tools.jib.plugins.common.InvalidWorkingDirectoryException;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.cloud.tools.jib.plugins.common.globalconfig.GlobalConfig;
import com.google.cloud.tools.jib.plugins.common.globalconfig.InvalidGlobalConfigException;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/** Builds a container image and exports to the default Docker daemon. */
public class BuildDockerTask extends DefaultTask implements JibTask {

  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Build to Docker daemon failed";

  @Nullable private JibExtension jibExtension;

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

  /**
   * Task Action, builds an image to docker daemon.
   *
   * @throws IOException if an error occurs creating the jib runner
   * @throws BuildStepsExecutionException if an error occurs while executing build steps
   * @throws CacheDirectoryCreationException if a new cache directory could not be created
   * @throws MainClassInferenceException if a main class could not be found
   * @throws InvalidGlobalConfigException if the global config file is invalid
   */
  @TaskAction
  public void buildDocker()
      throws IOException, BuildStepsExecutionException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidGlobalConfigException {
    Preconditions.checkNotNull(jibExtension);

    // Check deprecated parameters
    Path dockerExecutable = jibExtension.getDockerClient().getExecutablePath();
    boolean isDockerInstalled =
        dockerExecutable == null
            ? CliDockerClient.isDefaultDockerInstalled()
            : CliDockerClient.isDockerInstalled(dockerExecutable);
    if (!isDockerInstalled) {
      throw new GradleException(
          HelpfulSuggestions.forDockerNotInstalled(HELPFUL_SUGGESTIONS_PREFIX));
    }

    TaskCommon.disableHttpLogging();
    TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider();

    GradleProjectProperties projectProperties =
        GradleProjectProperties.getForProject(
            getProject(),
            getLogger(),
            tempDirectoryProvider,
            jibExtension.getConfigurationName().get());
    GlobalConfig globalConfig = GlobalConfig.readConfig();
    Future<Optional<String>> updateCheckFuture =
        TaskCommon.newUpdateChecker(projectProperties, globalConfig, getLogger());
    try {

      PluginConfigurationProcessor.createJibBuildRunnerForDockerDaemonImage(
              new GradleRawConfiguration(jibExtension),
              ignored -> java.util.Optional.empty(),
              projectProperties,
              globalConfig,
              new GradleHelpfulSuggestions(HELPFUL_SUGGESTIONS_PREFIX))
          .runBuild();

    } catch (InvalidAppRootException ex) {
      throw new GradleException(
          "container.appRoot is not an absolute Unix-style path: " + ex.getInvalidPathValue(), ex);

    } catch (InvalidContainerizingModeException ex) {
      throw new GradleException(
          "invalid value for containerizingMode: " + ex.getInvalidContainerizingMode(), ex);

    } catch (InvalidWorkingDirectoryException ex) {
      throw new GradleException(
          "container.workingDirectory is not an absolute Unix-style path: "
              + ex.getInvalidPathValue(),
          ex);

    } catch (InvalidPlatformException ex) {
      throw new GradleException(
          "from.platforms contains a platform configuration that is missing required values or has invalid values: "
              + ex.getMessage()
              + ": "
              + ex.getInvalidPlatform(),
          ex);

    } catch (InvalidContainerVolumeException ex) {
      throw new GradleException(
          "container.volumes is not an absolute Unix-style path: " + ex.getInvalidVolume(), ex);

    } catch (InvalidFilesModificationTimeException ex) {
      throw new GradleException(
          "container.filesModificationTime should be an ISO 8601 date-time (see "
              + "DateTimeFormatter.ISO_DATE_TIME) or special keyword \"EPOCH_PLUS_SECOND\": "
              + ex.getInvalidFilesModificationTime(),
          ex);

    } catch (InvalidCreationTimeException ex) {
      throw new GradleException(
          "container.creationTime should be an ISO 8601 date-time (see "
              + "DateTimeFormatter.ISO_DATE_TIME) or a special keyword (\"EPOCH\", "
              + "\"USE_CURRENT_TIMESTAMP\"): "
              + ex.getInvalidCreationTime(),
          ex);

    } catch (JibPluginExtensionException ex) {
      String extensionName = ex.getExtensionClass().getName();
      throw new GradleException(
          "error running extension '" + extensionName + "': " + ex.getMessage(), ex);

    } catch (IncompatibleBaseImageJavaVersionException ex) {
      throw new GradleException(
          HelpfulSuggestions.forIncompatibleBaseImageJavaVersionForGradle(
              ex.getBaseImageMajorJavaVersion(), ex.getProjectMajorJavaVersion()),
          ex);

    } catch (InvalidImageReferenceException ex) {
      throw new GradleException(
          HelpfulSuggestions.forInvalidImageReference(ex.getInvalidReference()), ex);

    } catch (ExtraDirectoryNotFoundException ex) {
      throw new GradleException(
          "extraDirectories.paths contain \"from\" directory that doesn't exist locally: "
              + ex.getPath(),
          ex);
    } finally {
      tempDirectoryProvider.close();
      TaskCommon.finishUpdateChecker(projectProperties, updateCheckFuture);
      projectProperties.waitForLoggingThread();
    }
  }

  @Override
  public BuildDockerTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }
}
