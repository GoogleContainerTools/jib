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
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.IncompatibleBaseImageJavaVersionException;
import com.google.cloud.tools.jib.plugins.common.InvalidAppRootException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerVolumeException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerizingModeException;
import com.google.cloud.tools.jib.plugins.common.InvalidWorkingDirectoryException;
import com.google.cloud.tools.jib.plugins.common.JibBuildRunner;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

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
   * @return a collection of all the files that jib includes in the image. Only used to calculate
   *     UP-TO-DATE.
   */
  @InputFiles
  public FileCollection getInputFiles() {
    List<Path> extraDirectories =
        Preconditions.checkNotNull(jibExtension).getExtraDirectories().getPaths();
    return GradleProjectProperties.getInputFiles(getProject(), extraDirectories);
  }

  /**
   * The output file to check for task up-to-date.
   *
   * @return the output path
   */
  @OutputFile
  public String getOutputFile() {
    return getTargetPath().toString();
  }

  @TaskAction
  public void buildTar()
      throws BuildStepsExecutionException, IOException, CacheDirectoryCreationException,
          MainClassInferenceException {
    // Asserts required @Input parameters are not null.
    Preconditions.checkNotNull(jibExtension);
    TaskCommon.checkDeprecatedUsage(jibExtension, getLogger());
    TaskCommon.disableHttpLogging();

    try {
      RawConfiguration gradleRawConfiguration = new GradleRawConfiguration(jibExtension);
      GradleProjectProperties projectProperties =
          GradleProjectProperties.getForProject(getProject(), getLogger());
      GradleHelpfulSuggestionsBuilder gradleHelpfulSuggestionsBuilder =
          new GradleHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, jibExtension);

      Path tarOutputPath = getTargetPath();
      PluginConfigurationProcessor pluginConfigurationProcessor =
          PluginConfigurationProcessor.processCommonConfigurationForTarImage(
              gradleRawConfiguration,
              ignored -> Optional.empty(),
              projectProperties,
              tarOutputPath,
              gradleHelpfulSuggestionsBuilder.build());

      HelpfulSuggestions helpfulSuggestions =
          gradleHelpfulSuggestionsBuilder
              .setBaseImageReference(pluginConfigurationProcessor.getBaseImageReference())
              .setBaseImageHasConfiguredCredentials(
                  pluginConfigurationProcessor.isBaseImageCredentialPresent())
              .setTargetImageReference(pluginConfigurationProcessor.getTargetImageReference())
              .build();

      Path buildOutput = getProject().getBuildDir().toPath();

      try {
        JibBuildRunner.forBuildTar(tarOutputPath)
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
      }

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

    } catch (InvalidContainerVolumeException ex) {
      throw new GradleException(
          "container.volumes is not an absolute Unix-style path: " + ex.getInvalidVolume(), ex);

    } catch (IncompatibleBaseImageJavaVersionException ex) {
      throw new GradleException(
          HelpfulSuggestions.forIncompatibleBaseImageJavaVesionForGradle(
              ex.getBaseImageMajorJavaVersion(), ex.getProjectMajorJavaVersion()),
          ex);

    } catch (InvalidImageReferenceException ex) {
      throw new GradleException(
          HelpfulSuggestions.forInvalidImageReference(ex.getInvalidReference()), ex);
    }
  }

  @Override
  public BuildTarTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }

  /**
   * Returns the output directory for the tarball. By default, it is {@code build/jib-image.tar}.
   *
   * @return the output directory
   */
  private Path getTargetPath() {
    return getProject().getBuildDir().toPath().resolve("jib-image.tar");
  }
}
