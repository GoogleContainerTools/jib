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
    DEPENDENCIES("dependencies", JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE),
    SNAPSHOT_DEPENDENCIES(
        "snapshot dependencies", JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE),
    RESOURCES("resources", JavaEntrypointConstructor.DEFAULT_RESOURCES_PATH_ON_IMAGE),
    CLASSES("classes", JavaEntrypointConstructor.DEFAULT_CLASSES_PATH_ON_IMAGE),
    EXTRA_FILES("extra files", "/");

    private final String label;
    private final String extractionPath;

    /** Initializes with a label for the layer and the layer files' default extraction path root. */
    LayerType(String label, String extractionPath) {
      this.label = label;
      this.extractionPath = extractionPath;
    }

    @VisibleForTesting
    String getLabel() {
      return label;
    }

    @VisibleForTesting
    Path getExtractionPath() {
      return Paths.get(extractionPath);
    }
  }

  /** Builds with each layer's files. */
  public static class Builder {

    // TODO: Change to use Multimap.
    private final Map<LayerType, List<Path>> layerFilesMap = new EnumMap<>(LayerType.class);

    private Builder() {
      for (LayerType layerType : LayerType.values()) {
        layerFilesMap.put(layerType, new ArrayList<>());
      }
    }

    public Builder setDependencyFiles(List<Path> dependencyFiles) {
      layerFilesMap.put(LayerType.DEPENDENCIES, dependencyFiles);
      return this;
    }

    public Builder setSnapshotDependencyFiles(List<Path> snapshotDependencyFiles) {
      layerFilesMap.put(LayerType.SNAPSHOT_DEPENDENCIES, snapshotDependencyFiles);
      return this;
    }

    public Builder setResourceFiles(List<Path> resourceFiles) {
      layerFilesMap.put(LayerType.RESOURCES, resourceFiles);
      return this;
    }

    public Builder setClassFiles(List<Path> classFiles) {
      layerFilesMap.put(LayerType.CLASSES, classFiles);
      return this;
    }

    public Builder setExtraFiles(List<Path> extraFiles) {
      layerFilesMap.put(LayerType.EXTRA_FILES, extraFiles);
      return this;
    }

    public JavaLayerConfigurations build() throws IOException {
      ImmutableMap.Builder<LayerType, LayerConfiguration> layerConfigurationsMap =
          ImmutableMap.builderWithExpectedSize(LayerType.values().length);
      for (LayerType layerType : LayerType.values()) {
        LayerConfiguration.Builder layerConfigurationBuilder =
            LayerConfiguration.builder().setLabel(layerType.getLabel());

        // Adds all the layer files recursively.
        List<Path> layerFiles = Preconditions.checkNotNull(layerFilesMap.get(layerType));
        for (Path layerFile : layerFiles) {
          layerConfigurationBuilder.addEntryRecursive(
              layerFile, layerType.getExtractionPath().resolve(layerFile.getFileName()));
        }

        layerConfigurationsMap.put(layerType, layerConfigurationBuilder.build());
      }
      return new JavaLayerConfigurations(layerConfigurationsMap.build());
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final ImmutableMap<LayerType, LayerConfiguration> layerConfigurationMap;

  private JavaLayerConfigurations(
      ImmutableMap<LayerType, LayerConfiguration> layerConfigurationsMap) {
    this.layerConfigurationMap = layerConfigurationsMap;
  }

  public ImmutableList<LayerConfiguration> getLayerConfigurations() {
    return layerConfigurationMap.values().asList();
  }

  public ImmutableList<LayerEntry> getDependenciesLayerEntry() {
    return getLayerEntry(LayerType.DEPENDENCIES);
  }

  public ImmutableList<LayerEntry> getSnapshotDependenciesLayerEntry() {
    return getLayerEntry(LayerType.SNAPSHOT_DEPENDENCIES);
  }

  public ImmutableList<LayerEntry> getResourcesLayerEntry() {
    return getLayerEntry(LayerType.RESOURCES);
  }

  public ImmutableList<LayerEntry> getClassesLayerEntry() {
    return getLayerEntry(LayerType.CLASSES);
  }

  public ImmutableList<LayerEntry> getExtraFilesLayerEntry() {
    return getLayerEntry(LayerType.EXTRA_FILES);
  }

  private ImmutableList<LayerEntry> getLayerEntry(LayerType layerType) {
    return Preconditions.checkNotNull(layerConfigurationMap.get(layerType)).getLayerEntries();
  }
}
