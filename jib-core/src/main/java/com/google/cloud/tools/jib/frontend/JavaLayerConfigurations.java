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
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
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

    private static class LayerFile {
      private Path sourceFile;
      private AbsoluteUnixPath pathInContainer;
      private boolean recursiveAdd;

      private LayerFile(Path sourceFile, AbsoluteUnixPath pathInContainer, boolean recursiveAdd) {
        this.sourceFile = sourceFile;
        this.pathInContainer = pathInContainer;
        this.recursiveAdd = recursiveAdd;
      }
    }

    private final Map<LayerType, List<LayerFile>> layerFilesMap = new EnumMap<>(LayerType.class);

    private Builder() {
      for (LayerType layerType : LayerType.values()) {
        layerFilesMap.put(layerType, new ArrayList<>());
      }
    }

    /**
     * Adds a file to the dependency layer. Only adds the single source file to the exact path in
     * the container file system. See {@link LayerConfiguration.Builder#addEntry} for concrete
     * examples about how the file will be placed in the image.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    public Builder addDependencyFile(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      addLayerFile(LayerType.DEPENDENCIES, sourceFile, pathInContainer, false);
      return this;
    }

    /**
     * Adds a file to the snapshot dependencies layer. Only adds the single source file to the exact
     * path in the container file system. See {@link LayerConfiguration.Builder#addEntry} for
     * concrete examples about how the file will be placed in the image.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    public Builder addSnapshotDependencyFile(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      addLayerFile(LayerType.SNAPSHOT_DEPENDENCIES, sourceFile, pathInContainer, false);
      return this;
    }

    /**
     * Adds a file to the resources layer. Only adds the single source file to the exact path in the
     * container file system. See {@link LayerConfiguration.Builder#addEntry} for concrete examples
     * about how the file will be placed in the image.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    public Builder addResourceFile(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      addLayerFile(LayerType.RESOURCES, sourceFile, pathInContainer, false);
      return this;
    }

    /**
     * Adds a file to the classes layer. Only adds the single source file to the exact path in the
     * container file system. See {@link LayerConfiguration.Builder#addEntry} for concrete examples
     * about how the file will be placed in the image.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    public Builder addClassFile(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      addLayerFile(LayerType.CLASSES, sourceFile, pathInContainer, false);
      return this;
    }

    /**
     * Adds a file to the extra files layer. Only adds the single source file to the exact path in
     * the container file system. See {@link LayerConfiguration.Builder#addEntry} for concrete
     * examples about how the file will be placed in the image.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    public Builder addExtraFile(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      addLayerFile(LayerType.EXTRA_FILES, sourceFile, pathInContainer, false);
      return this;
    }

    /**
     * Adds a file to the exploded WAR layer. Only adds the single source file to the exact path in
     * the container file system. See {@link LayerConfiguration.Builder#addEntry} for concrete
     * examples about how the file will be placed in the image.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    // TODO: remove this and put files in WAR into the relevant layers (i.e., dependencies, snapshot
    // dependencies, resources, and classes layers).
    public Builder addExplodedWarFile(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      addLayerFile(LayerType.EXPLODED_WAR, sourceFile, pathInContainer, false);
      return this;
    }

    /**
     * Adds a file to the dependency layer. If the source file is a directory, the directory and its
     * contents will be added recursively. See {@link LayerConfiguration.Builder#addEntryRecursive}
     * for concrete examples about how the file will be placed in the image.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    public Builder addDependencyFileRecursive(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      addLayerFile(LayerType.DEPENDENCIES, sourceFile, pathInContainer, true);
      return this;
    }

    /**
     * Adds a file to the snapshot dependency layer. If the source file is a directory, the
     * directory and its contents will be added recursively. See {@link
     * LayerConfiguration.Builder#addEntryRecursive} for concrete examples about how the file will
     * be placed in the image.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    public Builder addSnapshotDependencyFileRecursive(
        Path sourceFile, AbsoluteUnixPath pathInContainer) {
      addLayerFile(LayerType.SNAPSHOT_DEPENDENCIES, sourceFile, pathInContainer, true);
      return this;
    }

    /**
     * Adds a file to the resource layer. If the source file is a directory, the directory and its
     * contents will be added recursively. See {@link LayerConfiguration.Builder#addEntryRecursive}
     * for concrete examples about how the file will be placed in the image.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    public Builder addResourceFileRecursive(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      addLayerFile(LayerType.RESOURCES, sourceFile, pathInContainer, true);
      return this;
    }

    /**
     * Adds a file to the classes layer. If the source file is a directory, the directory and its
     * contents will be added recursively. See {@link LayerConfiguration.Builder#addEntryRecursive}
     * for concrete examples about how the file will be placed in the image.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    public Builder addClassFileRecursive(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      addLayerFile(LayerType.CLASSES, sourceFile, pathInContainer, true);
      return this;
    }

    /**
     * Adds a file to the extra files layer. If the source file is a directory, the directory and
     * its contents will be added recursively. See {@link
     * LayerConfiguration.Builder#addEntryRecursive} for concrete examples about how the file will
     * be placed in the image.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    public Builder addExtraFileRecursive(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      addLayerFile(LayerType.EXTRA_FILES, sourceFile, pathInContainer, true);
      return this;
    }

    /**
     * Adds a file to the exploded WAR layer. If the source file is a directory, the directory and
     * its contents will be added recursively. See {@link
     * LayerConfiguration.Builder#addEntryRecursive} for concrete examples about how the file will
     * be placed in the image.
     *
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     */
    // TODO: remove this and put files in WAR into the relevant layers (i.e., dependencies, snapshot
    // dependencies, resources, and classes layers).
    public Builder addExplodedWarFileRecursive(Path sourceFile, AbsoluteUnixPath pathInContainer) {
      addLayerFile(LayerType.EXPLODED_WAR, sourceFile, pathInContainer, true);
      return this;
    }

    public JavaLayerConfigurations build() throws IOException {
      ImmutableMap.Builder<LayerType, LayerConfiguration> layerConfigurationsMap =
          ImmutableMap.builderWithExpectedSize(LayerType.values().length);

      for (LayerType layerType : LayerType.values()) {
        LayerConfiguration.Builder layerConfigurationBuilder =
            LayerConfiguration.builder().setName(layerType.getName());

        for (LayerFile file : layerFilesMap.get(layerType)) {
          if (file.recursiveAdd) {
            layerConfigurationBuilder.addEntryRecursive(file.sourceFile, file.pathInContainer);
          } else {
            layerConfigurationBuilder.addEntry(file.sourceFile, file.pathInContainer);
          }
        }
        layerConfigurationsMap.put(layerType, layerConfigurationBuilder.build());
      }
      return new JavaLayerConfigurations(layerConfigurationsMap.build());
    }

    private void addLayerFile(
        LayerType type, Path sourceFile, AbsoluteUnixPath pathInContainer, boolean recursiveAdd) {
      List<LayerFile> filesToAdd = Preconditions.checkNotNull(layerFilesMap.get(type));
      filesToAdd.add(new LayerFile(sourceFile, pathInContainer, recursiveAdd));
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

  private JavaLayerConfigurations(
      ImmutableMap<LayerType, LayerConfiguration> layerConfigurationMap) {
    this.layerConfigurationMap = layerConfigurationMap;
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

  private ImmutableList<LayerEntry> getLayerEntries(LayerType layerType) {
    return Preconditions.checkNotNull(layerConfigurationMap.get(layerType)).getLayerEntries();
  }
}
