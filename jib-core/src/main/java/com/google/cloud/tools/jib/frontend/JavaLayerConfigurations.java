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

package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Builds {@link LayerConfiguration}s for a Java application. */
public class JavaLayerConfigurations {

  /** Represents the different types of layers for a Java application. */
  @VisibleForTesting
  enum LayerType {
    DEPENDENCIES("dependencies"),
    SNAPSHOT_DEPENDENCIES("snapshot dependencies"),
    RESOURCES("resources"),
    CLASSES("classes"),
    // TODO: remove this once we put files in WAR into the relevant layers (i.e., dependencies,
    // snapshot dependencies, resources, and classes layers). Should copy files in the right
    EXPLODED_WAR("exploded war"),
    EXTRA_FILES("extra files");

    private final String name;

    /**
     * Initializes with a name for the layer.
     *
     * @param name name to set for the layer; does not affect the contents of the layer
     */
    LayerType(String name) {
      this.name = name;
    }

    @VisibleForTesting
    String getName() {
      return name;
    }
  }

  /** Builds with each layer's files. */
  public static class Builder {

    private final Map<LayerType, List<Path>> layerFilesMap = new EnumMap<>(LayerType.class);
    private final Map<LayerType, String> extractionPathMap = new EnumMap<>(LayerType.class);

    private Builder() {
      for (LayerType layerType : LayerType.values()) {
        layerFilesMap.put(layerType, new ArrayList<>());
        extractionPathMap.put(layerType, "/");
      }
    }

    public Builder setDependencyFiles(List<Path> dependencyFiles, String extractionPath) {
      layerFilesMap.put(LayerType.DEPENDENCIES, dependencyFiles);
      extractionPathMap.put(LayerType.DEPENDENCIES, extractionPath);
      return this;
    }

    public Builder setSnapshotDependencyFiles(
        List<Path> snapshotDependencyFiles, String extractionPath) {
      layerFilesMap.put(LayerType.SNAPSHOT_DEPENDENCIES, snapshotDependencyFiles);
      extractionPathMap.put(LayerType.SNAPSHOT_DEPENDENCIES, extractionPath);
      return this;
    }

    public Builder setResourceFiles(List<Path> resourceFiles, String extractionPath) {
      layerFilesMap.put(LayerType.RESOURCES, resourceFiles);
      extractionPathMap.put(LayerType.RESOURCES, extractionPath);
      return this;
    }

    public Builder setClassFiles(List<Path> classFiles, String extractionPath) {
      layerFilesMap.put(LayerType.CLASSES, classFiles);
      extractionPathMap.put(LayerType.CLASSES, extractionPath);
      return this;
    }

    public Builder setExtraFiles(List<Path> extraFiles, String extractionPath) {
      layerFilesMap.put(LayerType.EXTRA_FILES, extraFiles);
      extractionPathMap.put(LayerType.EXTRA_FILES, extractionPath);
      return this;
    }

    // TODO: remove this and put files in WAR into the relevant layers (i.e., dependencies, snapshot
    // dependencies, resources, and classes layers).
    public Builder setExplodedWarFiles(List<Path> explodedWarFiles, String extractionPath) {
      layerFilesMap.put(LayerType.EXPLODED_WAR, explodedWarFiles);
      extractionPathMap.put(LayerType.EXPLODED_WAR, extractionPath);
      return this;
    }

    public JavaLayerConfigurations build() throws IOException {
      ImmutableMap.Builder<LayerType, LayerConfiguration> layerConfigurationsMap =
          ImmutableMap.builderWithExpectedSize(LayerType.values().length);
      for (LayerType layerType : LayerType.values()) {
        String extractionPath = Preconditions.checkNotNull(extractionPathMap.get(layerType));
        // Windows filenames cannot have "/", so this also blocks Windows-style path.
        Preconditions.checkState(
            extractionPath.startsWith("/"),
            "extractionPath should be an absolute path in Unix-style: " + extractionPath);

        LayerConfiguration.Builder layerConfigurationBuilder =
            LayerConfiguration.builder().setName(layerType.getName());

        // Adds all the layer files recursively.
        List<Path> layerFiles = Preconditions.checkNotNull(layerFilesMap.get(layerType));
        for (Path layerFile : layerFiles) {
          Path pathInContainer = Paths.get(extractionPath).resolve(layerFile.getFileName());
          layerConfigurationBuilder.addEntryRecursive(layerFile, pathInContainer);
        }

        layerConfigurationsMap.put(layerType, layerConfigurationBuilder.build());
      }
      return new JavaLayerConfigurations(
          layerConfigurationsMap.build(), ImmutableMap.copyOf(extractionPathMap));
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * The default app root in the image. For example, if this is set to {@code "/app"}, dependency
   * JARs will be in {@code "/app/libs"}.
   */
  public static final String DEFAULT_APP_ROOT = "/app";

  private final ImmutableMap<LayerType, LayerConfiguration> layerConfigurationMap;
  private final ImmutableMap<LayerType, String> extractionPathMap;

  private JavaLayerConfigurations(
      ImmutableMap<LayerType, LayerConfiguration> layerConfigurationMap,
      ImmutableMap<LayerType, String> extractionPathMap) {
    this.layerConfigurationMap = layerConfigurationMap;
    this.extractionPathMap = extractionPathMap;
  }

  public ImmutableList<LayerConfiguration> getLayerConfigurations() {
    return layerConfigurationMap.values().asList();
  }

  public ImmutableList<LayerEntry> getDependencyLayerEntries() {
    return getLayerEntries(LayerType.DEPENDENCIES);
  }

  public ImmutableList<LayerEntry> getSnapshotDependencyLayerEntries() {
    return getLayerEntries(LayerType.SNAPSHOT_DEPENDENCIES);
  }

  public ImmutableList<LayerEntry> getResourceLayerEntries() {
    return getLayerEntries(LayerType.RESOURCES);
  }

  public ImmutableList<LayerEntry> getClassLayerEntries() {
    return getLayerEntries(LayerType.CLASSES);
  }

  public ImmutableList<LayerEntry> getExtraFilesLayerEntries() {
    return getLayerEntries(LayerType.EXTRA_FILES);
  }

  // TODO: remove this once we put files in WAR into the relevant layers (i.e., dependencies,
  // snapshot dependencies, resources, and classes layers). Should copy files in the right
  public ImmutableList<LayerEntry> getExplodedWarEntries() {
    return getLayerEntries(LayerType.EXPLODED_WAR);
  }

  public String getDependencyExtractionPath() {
    return getExtractionPath(LayerType.DEPENDENCIES);
  }

  public String getSnapshotDependencyExtractionPath() {
    return getExtractionPath(LayerType.SNAPSHOT_DEPENDENCIES);
  }

  public String getResourceExtractionPath() {
    return getExtractionPath(LayerType.RESOURCES);
  }

  public String getClassExtractionPath() {
    return getExtractionPath(LayerType.CLASSES);
  }

  public String getExtraFilesExtractionPath() {
    return getExtractionPath(LayerType.EXTRA_FILES);
  }

  public String getExplodedWarExtractionPath() {
    return getExtractionPath(LayerType.EXPLODED_WAR);
  }

  private ImmutableList<LayerEntry> getLayerEntries(LayerType layerType) {
    return Preconditions.checkNotNull(layerConfigurationMap.get(layerType)).getLayerEntries();
  }

  private String getExtractionPath(LayerType layerType) {
    return Preconditions.checkNotNull(extractionPathMap.get(layerType));
  }
}
