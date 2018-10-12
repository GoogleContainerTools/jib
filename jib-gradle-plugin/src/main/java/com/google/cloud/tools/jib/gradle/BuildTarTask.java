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

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.TarImage;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
   * @return the input files to this task are all the output files for all the dependencies of the
   *     {@code classes} task.
   */
  @InputFiles
  public FileCollection getInputFiles() {
    return GradleProjectProperties.getInputFiles(
        Preconditions.checkNotNull(jibExtension).getExtraDirectoryPath().toFile(), getProject());
  }

  /**
   * The output file to check for task up-to-date.
   *
   * @return the output path
   */
  @OutputFile
  public String getOutputFile() {
    return getTargetPath();
  }

  /**
   * Returns the output directory for the tarball. By default, it is {@code build/jib-image.tar}.
   *
   * @return the output directory
   */
  private String getTargetPath() {
    return getProject().getBuildDir().toPath().resolve("jib-image.tar").toString();
  }

  @TaskAction
  public void buildTar()
      throws InvalidImageReferenceException, BuildStepsExecutionException, IOException,
          CacheDirectoryCreationException {
    // Asserts required @Input parameters are not null.
    Preconditions.checkNotNull(jibExtension);
    AbsoluteUnixPath appRoot = PluginConfigurationProcessor.getAppRootChecked(jibExtension);
    GradleProjectProperties gradleProjectProperties =
        GradleProjectProperties.getForProject(
            getProject(), getLogger(), jibExtension.getExtraDirectoryPath(), appRoot);

    GradleHelpfulSuggestionsBuilder gradleHelpfulSuggestionsBuilder =
        new GradleHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, jibExtension);

    EventDispatcher eventDispatcher =
        new DefaultEventDispatcher(gradleProjectProperties.getEventHandlers());
    ImageReference targetImageReference =
        ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
            jibExtension.getTo().getImage(),
            eventDispatcher,
            getProject().getName(),
            getProject().getVersion().toString(),
            gradleHelpfulSuggestionsBuilder.build());

    Path tarOutputPath = Paths.get(getTargetPath());
    TarImage targetImage = TarImage.named(targetImageReference).saveTo(tarOutputPath);

    PluginConfigurationProcessor pluginConfigurationProcessor =
        PluginConfigurationProcessor.processCommonConfiguration(
            getLogger(), jibExtension, gradleProjectProperties);

    JibContainerBuilder jibContainerBuilder = pluginConfigurationProcessor.getJibContainerBuilder();

    Containerizer containerizer = Containerizer.to(targetImage);
    PluginConfigurationProcessor.configureContainerizer(
        containerizer, jibExtension, gradleProjectProperties);

    HelpfulSuggestions helpfulSuggestions =
        gradleHelpfulSuggestionsBuilder
            .setBaseImageReference(pluginConfigurationProcessor.getBaseImageReference())
            .setBaseImageHasConfiguredCredentials(
                pluginConfigurationProcessor.isBaseImageCredentialPresent())
            .setTargetImageReference(targetImageReference)
            .build();

    BuildStepsRunner.forBuildTar(tarOutputPath)
        .build(
            jibContainerBuilder,
            containerizer,
            eventDispatcher,
            gradleProjectProperties.getJavaLayerConfigurations().getLayerConfigurations(),
            helpfulSuggestions);
  }

  @Override
  public BuildTarTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }
}
