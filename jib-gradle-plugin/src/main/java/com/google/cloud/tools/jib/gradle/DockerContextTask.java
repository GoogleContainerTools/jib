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

import com.google.cloud.tools.jib.frontend.ExposedPortsParser;
import com.google.cloud.tools.jib.frontend.JavaDockerContextGenerator;
import com.google.cloud.tools.jib.frontend.JavaEntrypointConstructor;
import com.google.cloud.tools.jib.global.JibSystemProperties;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.common.base.Preconditions;
import com.google.common.io.InsecureRecursiveDeleteException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class DockerContextTask extends DefaultTask implements JibTask {

  @Nullable private String targetDir;
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
   * @return the input files to this task are all the output files for all the dependencies of the
   *     {@code classes} task.
   */
  @InputFiles
  public FileCollection getInputFiles() {
    return GradleProjectProperties.getInputFiles(
        Preconditions.checkNotNull(jibExtension).getExtraDirectoryPath().toFile(), getProject());
  }

  /**
   * The output directory to check for task up-to-date.
   *
   * @return the output directory
   */
  @OutputDirectory
  public String getOutputDirectory() {
    return getTargetDir();
  }

  /**
   * Returns the output directory for the Docker context. By default, it is {@code
   * build/jib-docker-context}.
   *
   * @return the output directory
   */
  @Input
  public String getTargetDir() {
    if (targetDir == null) {
      return getProject().getBuildDir().toPath().resolve("jib-docker-context").toString();
    }
    return targetDir;
  }

  /**
   * The output directory can be overriden with the {@code --jibTargetDir} command line option.
   *
   * @param targetDir the output directory.
   */
  @Option(option = "jibTargetDir", description = "Directory to output the Docker context to")
  public void setTargetDir(String targetDir) {
    this.targetDir = targetDir;
  }

  @TaskAction
  public void generateDockerContext() {
    Preconditions.checkNotNull(jibExtension);

    GradleJibLogger gradleJibLogger = new GradleJibLogger(getLogger());
    jibExtension.handleDeprecatedParameters(gradleJibLogger);
    JibSystemProperties.checkHttpTimeoutProperty();

    GradleProjectProperties gradleProjectProperties =
        GradleProjectProperties.getForProject(
            getProject(), gradleJibLogger, jibExtension.getExtraDirectoryPath());
    String targetDir = getTargetDir();

    List<String> entrypoint = jibExtension.getContainer().getEntrypoint();
    if (entrypoint.isEmpty()) {
      String mainClass = gradleProjectProperties.getMainClass(jibExtension);
      entrypoint =
          JavaEntrypointConstructor.makeDefaultEntrypoint(jibExtension.getJvmFlags(), mainClass);
    } else if (jibExtension.getMainClass() != null || !jibExtension.getJvmFlags().isEmpty()) {
      gradleJibLogger.warn("mainClass and jvmFlags are ignored when entrypoint is specified");
    }

    try {
      // Validate port input, but don't save the output because we don't want the ranges expanded
      // here.
      ExposedPortsParser.parse(jibExtension.getExposedPorts());

      new JavaDockerContextGenerator(gradleProjectProperties.getJavaLayerConfigurations())
          .setBaseImage(jibExtension.getBaseImage())
          .setEntrypoint(entrypoint)
          .setJavaArguments(jibExtension.getArgs())
          .setExposedPorts(jibExtension.getExposedPorts())
          .setLabels(jibExtension.getLabels())
          .generate(Paths.get(targetDir));

      gradleJibLogger.lifecycle("Created Docker context at " + targetDir);

    } catch (InsecureRecursiveDeleteException ex) {
      throw new GradleException(
          HelpfulSuggestions.forDockerContextInsecureRecursiveDelete(
              "Export Docker context failed because cannot clear directory '"
                  + getTargetDir()
                  + "' safely",
              getTargetDir()),
          ex);

    } catch (IOException ex) {
      throw new GradleException(
          HelpfulSuggestions.suggest(
              "Export Docker context failed",
              "check if the command-line option `--jibTargetDir` is set correctly"),
          ex);
    }
  }

  @Override
  public DockerContextTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }
}
