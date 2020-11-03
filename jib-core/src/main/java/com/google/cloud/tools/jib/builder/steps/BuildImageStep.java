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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.configuration.ContainerConfiguration;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.HistoryEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** Builds a model {@link Image}. */
class BuildImageStep implements Callable<Image> {

  private static final String DESCRIPTION = "Building container configuration";

  @VisibleForTesting
  static String truncateLongClasspath(ImmutableList<String> imageEntrypoint) {
    List<String> truncated = new ArrayList<>();
    UnmodifiableIterator<String> iterator = imageEntrypoint.iterator();
    while (iterator.hasNext()) {
      String element = iterator.next();
      truncated.add(element);

      if (element.equals("-cp") || element.equals("-classpath")) {
        String classpath = iterator.next();
        if (classpath.length() > 200) {
          truncated.add(classpath.substring(0, 200) + "<... classpath truncated ...>");
        } else {
          truncated.add(classpath);
        }
      }
    }
    return truncated.toString();
  }

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;
  private final Image baseImage;
  private final List<PreparedLayer> baseImageLayers;
  private final List<PreparedLayer> applicationLayers;

  BuildImageStep(
      BuildContext buildContext,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      Image baseImage,
      List<PreparedLayer> baseImageLayers,
      List<PreparedLayer> applicationLayers) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.baseImage = baseImage;
    this.baseImageLayers = baseImageLayers;
    this.applicationLayers = applicationLayers;
  }

  @Override
  public Image call() throws LayerPropertyNotFoundException {
    try (ProgressEventDispatcher ignored =
            progressEventDispatcherFactory.create("building image format", 1);
        TimerEventDispatcher ignored2 =
            new TimerEventDispatcher(buildContext.getEventHandlers(), DESCRIPTION)) {
      // Constructs the image.
      Image.Builder imageBuilder = Image.builder(buildContext.getTargetFormat());

      // Base image layers
      baseImageLayers.forEach(imageBuilder::addLayer);

      // Passthrough config and count non-empty history entries
      int nonEmptyLayerCount = 0;
      for (HistoryEntry historyObject : baseImage.getHistory()) {
        imageBuilder.addHistory(historyObject);
        if (!historyObject.hasCorrespondingLayer()) {
          nonEmptyLayerCount++;
        }
      }
      imageBuilder
          .setArchitecture(baseImage.getArchitecture())
          .setOs(baseImage.getOs())
          .addEnvironment(baseImage.getEnvironment())
          .addLabels(baseImage.getLabels())
          .setHealthCheck(baseImage.getHealthCheck())
          .addExposedPorts(baseImage.getExposedPorts())
          .addVolumes(baseImage.getVolumes())
          .setUser(baseImage.getUser())
          .setWorkingDirectory(baseImage.getWorkingDirectory());

      ContainerConfiguration containerConfiguration = buildContext.getContainerConfiguration();
      // Add history elements for non-empty layers that don't have one yet
      Instant layerCreationTime = containerConfiguration.getCreationTime();
      for (int count = 0; count < baseImageLayers.size() - nonEmptyLayerCount; count++) {
        imageBuilder.addHistory(
            HistoryEntry.builder()
                .setCreationTimestamp(layerCreationTime)
                .setComment("auto-generated by Jib")
                .build());
      }

      // Add built layers/configuration
      for (PreparedLayer applicationLayer : applicationLayers) {
        imageBuilder
            .addLayer(applicationLayer)
            .addHistory(
                HistoryEntry.builder()
                    .setCreationTimestamp(layerCreationTime)
                    .setAuthor("Jib")
                    .setCreatedBy(buildContext.getToolName() + ":" + buildContext.getToolVersion())
                    .setComment(applicationLayer.getName())
                    .build());
      }

      imageBuilder
          .addEnvironment(containerConfiguration.getEnvironmentMap())
          .setCreated(containerConfiguration.getCreationTime())
          .setEntrypoint(computeEntrypoint(baseImage, containerConfiguration))
          .setProgramArguments(computeProgramArguments(baseImage, containerConfiguration))
          .addExposedPorts(containerConfiguration.getExposedPorts())
          .addVolumes(containerConfiguration.getVolumes())
          .addLabels(containerConfiguration.getLabels());
      if (containerConfiguration.getUser() != null) {
        imageBuilder.setUser(containerConfiguration.getUser());
      }
      if (containerConfiguration.getWorkingDirectory() != null) {
        imageBuilder.setWorkingDirectory(containerConfiguration.getWorkingDirectory().toString());
      }

      // Gets the container configuration content descriptor.
      return imageBuilder.build();
    }
  }

  /**
   * Computes the image entrypoint. If {@link ContainerConfiguration#getEntrypoint()} is null, the
   * entrypoint is inherited from the base image. Otherwise {@link
   * ContainerConfiguration#getEntrypoint()} is returned.
   *
   * @param baseImage the base image
   * @param containerConfiguration the container configuration
   * @return the container entrypoint
   */
  @Nullable
  private ImmutableList<String> computeEntrypoint(
      Image baseImage, ContainerConfiguration containerConfiguration) {
    boolean shouldInherit =
        baseImage.getEntrypoint() != null && containerConfiguration.getEntrypoint() == null;

    ImmutableList<String> entrypointToUse =
        shouldInherit ? baseImage.getEntrypoint() : containerConfiguration.getEntrypoint();

    if (entrypointToUse != null) {
      buildContext.getEventHandlers().dispatch(LogEvent.lifecycle(""));
      if (shouldInherit) {
        String message =
            "Container entrypoint set to " + entrypointToUse + " (inherited from base image)";
        buildContext.getEventHandlers().dispatch(LogEvent.lifecycle(message));
      } else {
        String message = "Container entrypoint set to " + truncateLongClasspath(entrypointToUse);
        buildContext.getEventHandlers().dispatch(LogEvent.lifecycle(message));
      }
    }

    return entrypointToUse;
  }

  /**
   * Computes the image program arguments. If {@link ContainerConfiguration#getEntrypoint()} and
   * {@link ContainerConfiguration#getProgramArguments()} are null, the program arguments are
   * inherited from the base image. Otherwise {@link ContainerConfiguration#getProgramArguments()}
   * is returned.
   *
   * @param baseImage the base image
   * @param containerConfiguration the container configuration
   * @return the container program arguments
   */
  @Nullable
  private ImmutableList<String> computeProgramArguments(
      Image baseImage, ContainerConfiguration containerConfiguration) {
    boolean shouldInherit =
        baseImage.getProgramArguments() != null
            // Inherit CMD only when inheriting ENTRYPOINT.
            && containerConfiguration.getEntrypoint() == null
            && containerConfiguration.getProgramArguments() == null;

    ImmutableList<String> programArgumentsToUse =
        shouldInherit
            ? baseImage.getProgramArguments()
            : containerConfiguration.getProgramArguments();

    if (programArgumentsToUse != null) {
      String logSuffix = shouldInherit ? " (inherited from base image)" : "";
      String message = "Container program arguments set to " + programArgumentsToUse + logSuffix;
      buildContext.getEventHandlers().dispatch(LogEvent.lifecycle(message));
    }

    return programArgumentsToUse;
  }
}
