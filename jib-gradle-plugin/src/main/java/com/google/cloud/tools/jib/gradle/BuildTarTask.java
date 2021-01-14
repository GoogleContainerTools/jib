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
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.impldep.com.google.common.util.concurrent.Futures;

/** Builds a container image to a tarball. */
public class BuildTarTask extends DefaultTask implements JibTask {

  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Building image tarball failed";

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
   * Returns a collection of all the files that jib includes in the image. Only used to calculate
   * UP-TO-DATE.
   *
   * @return a list of paths of input files
   */
  @InputFiles
  public FileCollection getInputFiles() {
    List<Path> extraDirectories =
        Preconditions.checkNotNull(jibExtension)
            .getExtraDirectories()
            .getPaths()
            .stream()
            .map(ExtraDirectoryParameters::getFrom)
            .collect(Collectors.toList());
    return GradleProjectProperties.getInputFiles(getProject(), extraDirectories);
  }

  /**
   * The output file to check for task up-to-date.
   *
   * @return the output path
   */
  @OutputFile
  public String getOutputFile() {
    return Preconditions.checkNotNull(jibExtension).getOutputPaths().getTarPath().toString();
  }

  /**
   * Task Action, builds an image to tar file.
   *
   * @throws IOException if an error occurs creating the jib runner
   * @throws BuildStepsExecutionException if an error occurs while executing build steps
   * @throws CacheDirectoryCreationException if a new cache directory could not be created
   * @throws MainClassInferenceException if a main class could not be found
   * @throws InvalidGlobalConfigException if the global config file is invalid
   */
  @TaskAction
  public void buildTar()
      throws BuildStepsExecutionException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException, InvalidGlobalConfigException {
    // Asserts required @Input parameters are not null.
    Preconditions.checkNotNull(jibExtension);
    TaskCommon.disableHttpLogging();
    TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider();

    GradleProjectProperties projectProperties =
        GradleProjectProperties.getForProject(getProject(), getLogger(), tempDirectoryProvider);
    Future<Optional<String>> updateCheckFuture = Futures.immediateFuture(Optional.empty());
    try {
      GlobalConfig globalConfig = GlobalConfig.readConfig();
      updateCheckFuture = TaskCommon.newUpdateChecker(projectProperties, globalConfig, getLogger());

      PluginConfigurationProcessor.createJibBuildRunnerForTarImage(
              new GradleRawConfiguration(jibExtension),
              ignored -> Optional.empty(),
              projectProperties,
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

    } finally {
      tempDirectoryProvider.close();
      TaskCommon.finishUpdateChecker(projectProperties, updateCheckFuture);
      projectProperties.waitForLoggingThread();
    }
  }

  @Override
  public BuildTarTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }
}
