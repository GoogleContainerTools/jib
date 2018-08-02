/*
 * Copyright 2018 Google LLC. All rights reserved.
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
import com.google.cloud.tools.jib.configuration.LayerConfigurations;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Builds {@link LayerConfiguration}s for a Java application. */
public class JavaLayerConfigurations extends LayerConfigurations {

  public static final String DEPENDENCIES_LAYER_LABEL = "dependencies";
  public static final String SNAPSHOT_DEPENDENCIES_LAYER_LABEL = "snapshot dependencies";
  public static final String RESOURCES_LAYER_LABEL = "resources";
  public static final String CLASSES_LAYER_LABEL = "classes";
  public static final String EXTRA_FILES_LAYER_LABEL = "extra files";

  private static class Builder {

    @Nullable private List<Path> dependenciesFiles;
    @Nullable private List<Path> snapshotDependenciesFiles;
    @Nullable private List<Path> resourcesFiles;
    @Nullable private List<Path> classesFiles;
    @Nullable private List<Path> extraFiles;

    public Builder setDependenciesFiles(@Nullable List<Path> dependenciesFiles) {
      this.dependenciesFiles = dependenciesFiles;
      return this;
    }

    public Builder setSnapshotDependenciesFiles(@Nullable List<Path> snapshotDependenciesFiles) {
      this.snapshotDependenciesFiles = snapshotDependenciesFiles;
      return this;
    }

    public Builder setResourcesFiles(@Nullable List<Path> resourcesFiles) {
      this.resourcesFiles = resourcesFiles;
      return this;
    }

    public Builder setClassesFiles(@Nullable List<Path> classesFiles) {
      this.classesFiles = classesFiles;
      return this;
    }

    public Builder setExtraFiles(@Nullable List<Path> extraFiles) {
      this.extraFiles = extraFiles;
      return this;
    }

    public JavaLayerConfigurations build() {
      List<LayerConfiguration> layerConfigurations = new ArrayList<>();
      if (dependenciesFiles != null) {
        layerConfigurations.add(
            LayerConfiguration.builder()
                .addEntry(
                    dependenciesFiles, JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE)
                .setLabel(DEPENDENCIES_LAYER_LABEL)
                .build());
      }
      if (snapshotDependenciesFiles != null) {
        layerConfigurations.add(
            LayerConfiguration.builder()
                .addEntry(
                    snapshotDependenciesFiles,
                    JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE)
                .setLabel(SNAPSHOT_DEPENDENCIES_LAYER_LABEL)
                .build());
      }
      if (resourcesFiles != null) {
        layerConfigurations.add(
            LayerConfiguration.builder()
                .addEntry(resourcesFiles, JavaEntrypointConstructor.DEFAULT_RESOURCES_PATH_ON_IMAGE)
                .setLabel(RESOURCES_LAYER_LABEL)
                .build());
      }
      if (classesFiles != null) {
        layerConfigurations.add(
            LayerConfiguration.builder()
                .addEntry(classesFiles, JavaEntrypointConstructor.DEFAULT_CLASSES_PATH_ON_IMAGE)
                .setLabel(CLASSES_LAYER_LABEL)
                .build());
      }
      if (extraFiles != null) {
        layerConfigurations.add(
            LayerConfiguration.builder()
                .addEntry(extraFiles, "/")
                .setLabel(EXTRA_FILES_LAYER_LABEL)
                .build());
      }
      return new JavaLayerConfigurations(layerConfigurations);
    }
  }

  public LayerEntry getDependenciesLayerEntry() {
    return Preconditions.checkNotNull(getByLabel(DEPENDENCIES_LAYER_LABEL))
        .getLayerEntries()
        .get(0);
  }

  public LayerEntry getSnapshotDependenciesLayerEntry() {
    return Preconditions.checkNotNull(getByLabel(SNAPSHOT_DEPENDENCIES_LAYER_LABEL))
        .getLayerEntries()
        .get(0);
  }

  public LayerEntry getResourcesLayerEntry() {
    return Preconditions.checkNotNull(getByLabel(RESOURCES_LAYER_LABEL)).getLayerEntries().get(0);
  }

  public LayerEntry getClassesLayerEntry() {
    return Preconditions.checkNotNull(getByLabel(CLASSES_LAYER_LABEL)).getLayerEntries().get(0);
  }

  public LayerEntry getExtraFilesLayerEntry() {
    return Preconditions.checkNotNull(getByLabel(EXTRA_FILES_LAYER_LABEL)).getLayerEntries().get(0);
  }

  private JavaLayerConfigurations(List<LayerConfiguration> layerConfigurations) {
    super(layerConfigurations);
  }
}
