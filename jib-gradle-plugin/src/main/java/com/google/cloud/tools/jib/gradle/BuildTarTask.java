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

import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
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
    return Preconditions.checkNotNull(jibExtension).getOutputPaths().getTarPath().toString();
  }

  @TaskAction
  public void buildTar() {
    // Asserts required @Input parameters are not null.
    Preconditions.checkNotNull(jibExtension);
    TaskCommon.checkDeprecatedUsage(jibExtension, getLogger());
    TaskCommon.disableHttpLogging();

    GradleProjectProperties projectProperties =
        GradleProjectProperties.getForProject(getProject(), getLogger());
    try {
      PluginConfigurationProcessor.createJibBuildRunnerForTarImage(
              new GradleRawConfiguration(jibExtension),
              ignored -> Optional.empty(),
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
  public BuildTarTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }
}
