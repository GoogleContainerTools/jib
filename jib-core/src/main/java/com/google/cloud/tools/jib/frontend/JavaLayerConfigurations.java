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

import com.google.cloud.tools.jib.configuration.FilePermissions;
import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Builds {@link LayerConfiguration}s for a Java application. */
public class JavaLayerConfigurations {

  /** Represents the different types of layers for a Java application. */
  public enum LayerType {
    DEPENDENCIES("dependencies"),
    SNAPSHOT_DEPENDENCIES("snapshot dependencies"),
    RESOURCES("resources"),
    CLASSES("classes"),
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

    private final Map<LayerType, LayerConfiguration.Builder> layerBuilders =
        new EnumMap<>(LayerType.class);

    private Builder() {
      for (LayerType layerType : LayerType.values()) {
        layerBuilders.put(layerType, LayerConfiguration.builder());
      }
    }

    /**
     * Adds a file to a layer. Only adds the single source file to the exact path in the container
     * file system. (If the source file is a directory, does not copy its contents but creates only
     * the directory.) See {@link LayerConfiguration.Builder#addEntry} for concrete examples about
     * how the file will be placed in the image.
     *
     * @param layerType the layer to add files into
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @return this
     * @see LayerConfiguration.Builder#addEntry(Path, AbsoluteUnixPath)
     */
    public Builder addFile(LayerType layerType, Path sourceFile, AbsoluteUnixPath pathInContainer) {
      return addFile(layerType, sourceFile, pathInContainer, null);
    }

    /**
     * Adds a file to a layer. Only adds the single source file to the exact path in the container
     * file system. (If the source file is a directory, does not copy its contents but creates only
     * the directory.) See {@link LayerConfiguration.Builder#addEntry} for concrete examples about
     * how the file will be placed in the image.
     *
     * @param layerType the layer to add files into
     * @param sourceFile the source file to add to the layer
     * @param pathInContainer the path in the container file system corresponding to the {@code
     *     sourceFile}
     * @param permissions the file permissions on the container. Use {@code null} for defaults (644
     *     for files, 755 for directories)
     * @return this
     * @see LayerConfiguration.Builder#addEntry(Path, AbsoluteUnixPath, FilePermissions)
     */
    public Builder addFile(
        LayerType layerType,
        Path sourceFile,
        AbsoluteUnixPath pathInContainer,
        @Nullable FilePermissions permissions) {
      Preconditions.checkNotNull(layerBuilders.get(layerType))
          .addEntry(sourceFile, pathInContainer, permissions);
      return this;
    }

    /**
     * Adds directory contents to a layer selectively (via {@code pathFilter}) and recursively.
     * {@code sourceRoot} must be a directory. Empty directories will always be added regardless of
     * {@code pathFilter}, except that {@code sourceRoot} is never added.
     *
     * <p>The contents of {@code sourceRoot} will be placed into {@code basePathInContainer}. For
     * example, if {@code sourceRoot} is {@code /usr/home}, {@code /usr/home/passwd} exists locally,
     * and {@code basePathInContainer} is {@code /etc}, then the image will have {@code
     * /etc/passwd}.
     *
     * @param layerType the layer to add files into
     * @param sourceRoot root directory whose contents will be added
     * @param pathFilter filter that determines which files (not directories) should be added
     * @param basePathInContainer directory in the layer into which the source contents are added
     * @return this
     * @throws IOException error while listing directories
     * @throws NotDirectoryException if {@code sourceRoot} is not a directory
     */
    public Builder addDirectoryContents(
        LayerType layerType,
        Path sourceRoot,
        Predicate<Path> pathFilter,
        AbsoluteUnixPath basePathInContainer)
        throws IOException {
      return addDirectoryContents(
          layerType, sourceRoot, pathFilter, basePathInContainer, ImmutableMap.of());
    }

    /**
     * Adds directory contents to a layer selectively (via {@code pathFilter}) and recursively.
     * {@code sourceRoot} must be a directory. Empty directories will always be added regardless of
     * {@code pathFilter}, except that {@code sourceRoot} is never added. Permissions are specified
     * via {@code permissionsMap}, which maps from extraction path on the container to file
     * permissions.
     *
     * @param layerType the layer to add files into
     * @param sourceRoot root directory whose contents will be added
     * @param pathFilter filter that determines which files (not directories) should be added
     * @param basePathInContainer directory in the layer into which the source contents are added
     * @param permissionsMap the map from absolute path on container to file permission
     * @return this
     * @throws IOException error while listing directories
     * @throws NotDirectoryException if {@code sourceRoot} is not a directory
     */
    public Builder addDirectoryContents(
        LayerType layerType,
        Path sourceRoot,
        Predicate<Path> pathFilter,
        AbsoluteUnixPath basePathInContainer,
        Map<AbsoluteUnixPath, FilePermissions> permissionsMap)
        throws IOException {
      LayerConfiguration.Builder builder = Preconditions.checkNotNull(layerBuilders.get(layerType));

      new DirectoryWalker(sourceRoot)
          .filterRoot()
          .filter(path -> Files.isDirectory(path) || pathFilter.test(path))
          .walk(
              path -> {
                AbsoluteUnixPath pathOnContainer =
                    basePathInContainer.resolve(sourceRoot.relativize(path));
                builder.addEntry(path, pathOnContainer, permissionsMap.get(pathOnContainer));
              });
      return this;
    }

    public JavaLayerConfigurations build() {
      ImmutableMap.Builder<LayerType, LayerConfiguration> layerConfigurationsMap =
          ImmutableMap.builderWithExpectedSize(layerBuilders.size());

      layerBuilders.forEach(
          (type, builder) ->
              layerConfigurationsMap.put(type, builder.setName(type.getName()).build()));
      return new JavaLayerConfigurations(layerConfigurationsMap.build());
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

  /**
   * The default webapp root in the image. For example, if this is set to {@code
   * "/jetty/webapps/ROOT"}, dependency JARs will be in {@code "/jetty/webapps/ROOT/WEB-INF/lib"}.
   */
  public static final String DEFAULT_WEB_APP_ROOT = "/jetty/webapps/ROOT";

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

  private ImmutableList<LayerEntry> getLayerEntries(LayerType layerType) {
    return Preconditions.checkNotNull(layerConfigurationMap.get(layerType)).getLayerEntries();
  }
}
