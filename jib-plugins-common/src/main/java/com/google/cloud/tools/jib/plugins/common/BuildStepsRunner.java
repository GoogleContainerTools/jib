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

package com.google.cloud.tools.jib.plugins.common;

import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.builder.BuildSteps;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.cloud.tools.jib.registry.InsecureRegistryException;
import com.google.cloud.tools.jib.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.registry.RegistryCredentialsNotSentException;
import com.google.cloud.tools.jib.registry.RegistryErrorException;
import com.google.cloud.tools.jib.registry.RegistryUnauthorizedException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Verify;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.apache.http.conn.HttpHostConnectException;

/** Runs a {@link BuildSteps} and builds helpful error messages. */
public class BuildStepsRunner {

  private static final String STARTUP_MESSAGE_PREFIX_FOR_DOCKER_REGISTRY =
      "Containerizing application to ";
  private static final String SUCCESS_MESSAGE_PREFIX_FOR_DOCKER_REGISTRY =
      "Built and pushed image as ";

  private static final String STARTUP_MESSAGE_PREFIX_FOR_DOCKER_DAEMON =
      "Containerizing application to Docker daemon as ";
  private static final String SUCCESS_MESSAGE_PREFIX_FOR_DOCKER_DAEMON =
      "Built image to Docker daemon as ";

  private static final String STARTUP_MESSAGE_FORMAT_FOR_TARBALL =
      "Containerizing application to file at '%s'...";
  private static final String SUCCESS_MESSAGE_FORMAT_FOR_TARBALL =
      "Built image tarball at \u001B[36m%s\u001B[0m";

  private static CharSequence colorCyan(CharSequence innerText) {
    return new StringBuilder().append("\u001B[36m").append(innerText).append("\u001B[0m");
  }

  private static String buildMessageWithTargetImageReferences(
      ImageReference targetImageReference,
      Set<String> additionalTags,
      String prefix,
      String suffix) {
    StringJoiner successMessageBuilder = new StringJoiner(", ", prefix, suffix);
    successMessageBuilder.add(colorCyan(targetImageReference.toString()));
    for (String tag : additionalTags) {
      successMessageBuilder.add(colorCyan(targetImageReference.withTag(tag).toString()));
    }
    return successMessageBuilder.toString();
  }

  /**
   * Creates a runner to build an image. Creates a directory for the cache, if needed.
   *
   * @param targetImageReference the target image reference
   * @param additionalTags additional tags to push to
   * @return a {@link BuildStepsRunner} for building to a registry
   */
  public static BuildStepsRunner forBuildImage(
      ImageReference targetImageReference, Set<String> additionalTags) {
    return new BuildStepsRunner(
        buildMessageWithTargetImageReferences(
            targetImageReference,
            additionalTags,
            STARTUP_MESSAGE_PREFIX_FOR_DOCKER_REGISTRY,
            "..."),
        buildMessageWithTargetImageReferences(
            targetImageReference, additionalTags, SUCCESS_MESSAGE_PREFIX_FOR_DOCKER_REGISTRY, ""));
  }

  /**
   * Creates a runner to build to the Docker daemon. Creates a directory for the cache, if needed.
   *
   * @param targetImageReference the target image reference
   * @param additionalTags additional tags to push to
   * @return a {@link BuildStepsRunner} for building to a Docker daemon
   */
  public static BuildStepsRunner forBuildToDockerDaemon(
      ImageReference targetImageReference, Set<String> additionalTags) {
    return new BuildStepsRunner(
        buildMessageWithTargetImageReferences(
            targetImageReference, additionalTags, STARTUP_MESSAGE_PREFIX_FOR_DOCKER_DAEMON, "..."),
        buildMessageWithTargetImageReferences(
            targetImageReference, additionalTags, SUCCESS_MESSAGE_PREFIX_FOR_DOCKER_DAEMON, ""));
  }

  /**
   * Creates a runner to build an image tarball. Creates a directory for the cache, if needed.
   *
   * @param outputPath the path to output the tarball to
   * @return a {@link BuildStepsRunner} for building a tarball
   */
  public static BuildStepsRunner forBuildTar(Path outputPath) {
    return new BuildStepsRunner(
        String.format(STARTUP_MESSAGE_FORMAT_FOR_TARBALL, outputPath.toString()),
        String.format(SUCCESS_MESSAGE_FORMAT_FOR_TARBALL, outputPath.toString()));
  }

  private static void handleRegistryUnauthorizedException(
      RegistryUnauthorizedException registryUnauthorizedException,
      HelpfulSuggestions helpfulSuggestions)
      throws BuildStepsExecutionException {
    if (registryUnauthorizedException.getHttpResponseException().getStatusCode()
        == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
      // No permissions for registry/repository.
      throw new BuildStepsExecutionException(
          helpfulSuggestions.forHttpStatusCodeForbidden(
              registryUnauthorizedException.getImageReference()),
          registryUnauthorizedException);

    } else {
      throw new BuildStepsExecutionException(
          helpfulSuggestions.forNoCredentialsDefined(
              registryUnauthorizedException.getRegistry(),
              registryUnauthorizedException.getRepository()),
          registryUnauthorizedException);
    }
  }

  private static String capitalizeFirstLetter(String string) {
    if (string.length() == 0) {
      return string;
    }
    return Character.toUpperCase(string.charAt(0)) + string.substring(1);
  }

  private final String startupMessage;
  private final String successMessage;
  @Nullable private Path imageDigestOutputPath;
  @Nullable private Path imageIdOutputPath;

  @VisibleForTesting
  BuildStepsRunner(String startupMessage, String successMessage) {
    this.startupMessage = startupMessage;
    this.successMessage = successMessage;
  }

  /**
   * Runs the {@link BuildSteps}.
   *
   * @param jibContainerBuilder the {@link JibContainerBuilder}
   * @param containerizer the {@link Containerizer}
   * @param eventDispatcher the {@link EventDispatcher}
   * @param layerConfigurations the list of {@link LayerConfiguration}s
   * @param helpfulSuggestions suggestions to use in help messages for exceptions
   * @return the built {@link JibContainer}
   * @throws BuildStepsExecutionException if another exception is thrown during the build
   * @throws IOException if an I/O exception occurs
   * @throws CacheDirectoryCreationException if the cache directory could not be created
   */
  public JibContainer build(
      JibContainerBuilder jibContainerBuilder,
      Containerizer containerizer,
      EventDispatcher eventDispatcher,
      List<LayerConfiguration> layerConfigurations,
      HelpfulSuggestions helpfulSuggestions)
      throws BuildStepsExecutionException, IOException, CacheDirectoryCreationException {
    try {
      eventDispatcher.dispatch(LogEvent.lifecycle(""));
      eventDispatcher.dispatch(LogEvent.lifecycle(startupMessage));

      // Logs the different source files used.
      eventDispatcher.dispatch(
          LogEvent.info("Containerizing application with the following files:"));

      for (LayerConfiguration layerConfiguration : layerConfigurations) {
        if (layerConfiguration.getLayerEntries().isEmpty()) {
          continue;
        }

        eventDispatcher.dispatch(
            LogEvent.info("\t" + capitalizeFirstLetter(layerConfiguration.getName()) + ":"));

        for (LayerEntry layerEntry : layerConfiguration.getLayerEntries()) {
          eventDispatcher.dispatch(LogEvent.info("\t\t" + layerEntry.getSourceFile()));
        }
      }

      JibContainer jibContainer = jibContainerBuilder.containerize(containerizer);

      eventDispatcher.dispatch(LogEvent.lifecycle(""));
      eventDispatcher.dispatch(LogEvent.lifecycle(successMessage));

      // when an image is built, write out the digest and id
      if (imageDigestOutputPath != null) {
        String imageDigest = jibContainer.getDigest().toString();
        Files.write(imageDigestOutputPath, imageDigest.getBytes(StandardCharsets.UTF_8));
      }
      if (imageIdOutputPath != null) {
        String imageId = jibContainer.getImageId().toString();
        Files.write(imageIdOutputPath, imageId.getBytes(StandardCharsets.UTF_8));
      }

      return jibContainer;

    } catch (ExecutionException executionException) {
      Throwable exceptionDuringBuildSteps = executionException.getCause();

      if (exceptionDuringBuildSteps instanceof HttpHostConnectException) {
        // Failed to connect to registry.
        throw new BuildStepsExecutionException(
            helpfulSuggestions.forHttpHostConnect(), exceptionDuringBuildSteps);

      } else if (exceptionDuringBuildSteps instanceof RegistryUnauthorizedException) {
        handleRegistryUnauthorizedException(
            (RegistryUnauthorizedException) exceptionDuringBuildSteps, helpfulSuggestions);

      } else if (exceptionDuringBuildSteps instanceof RegistryCredentialsNotSentException) {
        throw new BuildStepsExecutionException(
            helpfulSuggestions.forCredentialsNotSent(), exceptionDuringBuildSteps);

      } else if (exceptionDuringBuildSteps instanceof RegistryAuthenticationFailedException
          && exceptionDuringBuildSteps.getCause() instanceof HttpResponseException) {
        RegistryAuthenticationFailedException failureException =
            (RegistryAuthenticationFailedException) exceptionDuringBuildSteps;
        handleRegistryUnauthorizedException(
            new RegistryUnauthorizedException(
                failureException.getServerUrl(),
                failureException.getImageName(),
                (HttpResponseException) exceptionDuringBuildSteps.getCause()),
            helpfulSuggestions);

      } else if (exceptionDuringBuildSteps instanceof UnknownHostException) {
        throw new BuildStepsExecutionException(
            helpfulSuggestions.forUnknownHost(), exceptionDuringBuildSteps);

      } else if (exceptionDuringBuildSteps instanceof InsecureRegistryException) {
        throw new BuildStepsExecutionException(
            helpfulSuggestions.forInsecureRegistry(), exceptionDuringBuildSteps);

      } else if (exceptionDuringBuildSteps instanceof RegistryErrorException) {
        // RegistryErrorExceptions have good messages
        RegistryErrorException registryErrorException =
            (RegistryErrorException) exceptionDuringBuildSteps;
        String message =
            Verify.verifyNotNull(registryErrorException.getMessage()); // keep null-away happy
        throw new BuildStepsExecutionException(message, exceptionDuringBuildSteps);

      } else {
        throw new BuildStepsExecutionException(
            helpfulSuggestions.none(), executionException.getCause());
      }

    } catch (InterruptedException ex) {
      // TODO: Add more suggestions for various build failures.
      throw new BuildStepsExecutionException(helpfulSuggestions.none(), ex);
    }

    throw new IllegalStateException("unreachable");
  }

  /**
   * Set the location where the image digest will be saved. If {@code null} then digest is not
   * saved.
   *
   * @param imageDigestOutputPath the location to write the image digest or {@code null} to skip
   * @return this
   */
  public BuildStepsRunner writeImageDigest(@Nullable Path imageDigestOutputPath) {
    this.imageDigestOutputPath = imageDigestOutputPath;
    return this;
  }

  /**
   * Set the location where the image id will be saved. If {@code null} then digest is not saved.
   *
   * @param imageIdOutputPath the location to write the image id or {@code null} to skip
   * @return this
   */
  public BuildStepsRunner writeImageId(@Nullable Path imageIdOutputPath) {
    this.imageIdOutputPath = imageIdOutputPath;
    return this;
  }
}
