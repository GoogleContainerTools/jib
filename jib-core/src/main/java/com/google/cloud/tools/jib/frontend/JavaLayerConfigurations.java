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
import java.util.Arrays;
import java.util.List;

/** Builds {@link LayerConfiguration}s for a Java application. */
public class JavaLayerConfigurations extends LayerConfigurations {

  public static final String DEPENDENCIES_LAYER_LABEL = "dependencies";
  public static final String SNAPSHOT_DEPENDENCIES_LAYER_LABEL = "snapshot dependencies";
  public static final String RESOURCES_LAYER_LABEL = "resources";
  public static final String CLASSES_LAYER_LABEL = "classes";
  public static final String EXTRA_FILES_LAYER_LABEL = "extra files";

  private static class Builder {

    private List<Path> dependenciesFiles = new ArrayList<>();
    private List<Path> snapshotDependenciesFiles = new ArrayList<>();
    private List<Path> resourcesFiles = new ArrayList<>();
    private List<Path> classesFiles = new ArrayList<>();
    private List<Path> extraFiles = new ArrayList<>();

    public Builder setDependenciesFiles(List<Path> dependenciesFiles) {
      this.dependenciesFiles = dependenciesFiles;
      return this;
    }

    public Builder setSnapshotDependenciesFiles(List<Path> snapshotDependenciesFiles) {
      this.snapshotDependenciesFiles = snapshotDependenciesFiles;
      return this;
    }

    public Builder setResourcesFiles(List<Path> resourcesFiles) {
      this.resourcesFiles = resourcesFiles;
      return this;
    }

    public Builder setClassesFiles(List<Path> classesFiles) {
      this.classesFiles = classesFiles;
      return this;
    }

    public Builder setExtraFiles(List<Path> extraFiles) {
      this.extraFiles = extraFiles;
      return this;
    }

    public JavaLayerConfigurations build() {
      return new JavaLayerConfigurations(
          Arrays.asList(
              LayerConfiguration.builder()
                  .addEntry(
                      dependenciesFiles,
                      JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE)
                  .setLabel(DEPENDENCIES_LAYER_LABEL)
                  .build(),
              LayerConfiguration.builder()
                  .addEntry(
                      snapshotDependenciesFiles,
                      JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE)
                  .setLabel(SNAPSHOT_DEPENDENCIES_LAYER_LABEL)
                  .build(),
              LayerConfiguration.builder()
                  .addEntry(
                      resourcesFiles, JavaEntrypointConstructor.DEFAULT_RESOURCES_PATH_ON_IMAGE)
                  .setLabel(RESOURCES_LAYER_LABEL)
                  .build(),
              LayerConfiguration.builder()
                  .addEntry(classesFiles, JavaEntrypointConstructor.DEFAULT_CLASSES_PATH_ON_IMAGE)
                  .setLabel(CLASSES_LAYER_LABEL)
                  .build(),
              LayerConfiguration.builder()
                  .addEntry(extraFiles, "/")
                  .setLabel(EXTRA_FILES_LAYER_LABEL)
                  .build()));
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
