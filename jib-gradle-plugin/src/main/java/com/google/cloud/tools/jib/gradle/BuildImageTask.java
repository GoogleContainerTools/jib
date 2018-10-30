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
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.plugins.common.AppRootInvalidException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsRunner;
import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.InferredAuthRetrievalException;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration;
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
      AbsoluteUnixPath appRoot = TaskCommon.getAppRootChecked(jibExtension, getProject());
      GradleProjectProperties projectProperties =
          GradleProjectProperties.getForProject(
              getProject(),
              getLogger(),
              jibExtension.getExtraDirectory().getPath(),
              jibExtension.getExtraDirectory().getPermissions(),
              appRoot);
      RawConfiguration rawConfiguration = new GradleRawConfiguration(jibExtension);

      if (Strings.isNullOrEmpty(jibExtension.getTo().getImage())) {
        throw new GradleException(
            HelpfulSuggestions.forToNotConfigured(
                "Missing target image parameter",
                "'jib.to.image'",
                "build.gradle",
                "gradle jib --image <your image name>"));
      }

      ImageReference targetImageReference = ImageReference.parse(jibExtension.getTo().getImage());

      EventDispatcher eventDispatcher =
          new DefaultEventDispatcher(projectProperties.getEventHandlers());
      DefaultCredentialRetrievers defaultCredentialRetrievers =
          DefaultCredentialRetrievers.init(
              CredentialRetrieverFactory.forImage(targetImageReference, eventDispatcher));
      Optional<Credential> optionalToCredential =
          ConfigurationPropertyValidator.getImageCredential(
              eventDispatcher,
              PropertyNames.TO_AUTH_USERNAME,
              PropertyNames.TO_AUTH_PASSWORD,
              jibExtension.getTo().getAuth());
      optionalToCredential.ifPresent(
          toCredential ->
              defaultCredentialRetrievers.setKnownCredential(toCredential, "jib.to.auth"));
      defaultCredentialRetrievers.setCredentialHelper(jibExtension.getTo().getCredHelper());

      RegistryImage targetImage = RegistryImage.named(targetImageReference);
      defaultCredentialRetrievers.asList().forEach(targetImage::addCredentialRetriever);

      PluginConfigurationProcessor pluginConfigurationProcessor =
          PluginConfigurationProcessor.processCommonConfiguration(
              rawConfiguration, projectProperties);

      JibContainerBuilder jibContainerBuilder =
          pluginConfigurationProcessor
              .getJibContainerBuilder()
              // Only uses possibly non-Docker formats for build to registry.
              .setFormat(jibExtension.getContainer().getFormat());

      Containerizer containerizer = Containerizer.to(targetImage);
      PluginConfigurationProcessor.configureContainerizer(
          containerizer, rawConfiguration, projectProperties, GradleProjectProperties.TOOL_NAME);

      HelpfulSuggestions helpfulSuggestions =
          new GradleHelpfulSuggestionsBuilder(HELPFUL_SUGGESTIONS_PREFIX, jibExtension)
              .setBaseImageReference(pluginConfigurationProcessor.getBaseImageReference())
              .setBaseImageHasConfiguredCredentials(
                  pluginConfigurationProcessor.isBaseImageCredentialPresent())
              .setTargetImageReference(targetImageReference)
              .setTargetImageHasConfiguredCredentials(optionalToCredential.isPresent())
              .build();

      Path buildOutput = getProject().getBuildDir().toPath();
      BuildStepsRunner.forBuildImage(targetImageReference, jibExtension.getTo().getTags())
          .writeImageDigest(buildOutput.resolve("jib-image.digest"))
          .build(
              jibContainerBuilder,
              containerizer,
              eventDispatcher,
              projectProperties.getJavaLayerConfigurations().getLayerConfigurations(),
              helpfulSuggestions);

    } catch (AppRootInvalidException ex) {
      throw new GradleException(
          "container.appRoot is not an absolute Unix-style path: " + ex.getInvalidAppRoot());
    }
  }

  @Override
  public BuildImageTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }
}
