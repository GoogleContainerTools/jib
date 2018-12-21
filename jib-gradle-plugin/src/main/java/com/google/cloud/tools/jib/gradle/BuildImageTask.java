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

import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.InferredAuthRetrievalException;
import com.google.cloud.tools.jib.plugins.common.InvalidAppRootException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerVolumeException;
import com.google.cloud.tools.jib.plugins.common.InvalidWorkingDirectoryException;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

/** Builds a container image. */
public class BuildImageTask extends DefaultTask implements JibTask {

  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Build image failed";

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

  @TaskAction
  public void buildImage()
      throws InvalidImageReferenceException, IOException, BuildStepsExecutionException,
          CacheDirectoryCreationException, MainClassInferenceException,
          InferredAuthRetrievalException {
    // Asserts required @Input parameters are not null.
    Preconditions.checkNotNull(jibExtension);
    TaskCommon.disableHttpLogging();

    try {
      GradleRawConfiguration rawConfiguration = new GradleRawConfiguration(jibExtension);
      boolean containerizeWar = TaskCommon.isWarContainerization(getProject(), rawConfiguration);
      AbsoluteUnixPath appRoot =
          PluginConfigurationProcessor.getAppRootChecked(rawConfiguration, containerizeWar);

      GradleProjectProperties projectProperties =
          GradleProjectProperties.getForProject(
              getProject(),
              containerizeWar,
              getLogger(),
              jibExtension.getExtraDirectory().getPath(),
              jibExtension.getExtraDirectory().getPermissions(),
              appRoot);

      if (Strings.isNullOrEmpty(jibExtension.getTo().getImage())) {
        throw new GradleException(
            HelpfulSuggestions.forToNotConfigured(
                "Missing target image parameter",
                "'jib.to.image'",
                "build.gradle",
                "gradle jib --image <your image name>"));
      }

      PluginConfigurationProcessor pluginConfigurationProcessor =
          PluginConfigurationProcessor.processCommonConfigurationForRegistryImage(
              rawConfiguration, containerizeWar, ignored -> Optional.empty(), projectProperties);

      ImageReference targetImageReference = pluginConfigurationProcessor.getTargetImageReference();
      HelpfulSuggestions helpfulSuggestions =
          new GradleHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, jibExtension)
              .setBaseImageReference(pluginConfigurationProcessor.getBaseImageReference())
              .setBaseImageHasConfiguredCredentials(
                  pluginConfigurationProcessor.isBaseImageCredentialPresent())
              .setTargetImageReference(targetImageReference)
              .setTargetImageHasConfiguredCredentials(
                  pluginConfigurationProcessor.isTargetImageCredentialPresent())
              .build();

      Path buildOutput = getProject().getBuildDir().toPath();

      try {
        BuildStepsRunner.forBuildImage(targetImageReference, jibExtension.getTo().getTags())
            .writeImageDigest(buildOutput.resolve("jib-image.digest"))
            .writeImageId(buildOutput.resolve("jib-image.id"))
            .build(
                pluginConfigurationProcessor.getJibContainerBuilder(),
                pluginConfigurationProcessor.getContainerizer(),
                new DefaultEventDispatcher(projectProperties.getEventHandlers()),
                projectProperties.getJavaLayerConfigurations().getLayerConfigurations(),
                helpfulSuggestions);

      } finally {
        // TODO: This should not be called on projectProperties.
        projectProperties.waitForLoggingThread();
      }

    } catch (InvalidAppRootException ex) {
      throw new GradleException(
          "container.appRoot is not an absolute Unix-style path: " + ex.getInvalidPathValue(), ex);

    } catch (InvalidWorkingDirectoryException ex) {
      throw new GradleException(
          "container.workingDirectory is not an absolute Unix-style path: "
              + ex.getInvalidPathValue(),
          ex);

    } catch (InvalidContainerVolumeException ex) {
      throw new GradleException(
          "container.volumes is not an absolute Unix-style path: " + ex.getInvalidVolume(), ex);
    }
  }

  @Override
  public BuildImageTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }
}
