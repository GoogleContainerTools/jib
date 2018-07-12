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

package com.google.cloud.tools.jib.builder;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** Implementation of {@link SourceFilesConfiguration} that uses test resources. */
public class TestSourceFilesConfiguration implements SourceFilesConfiguration {

  public static class Builder {
    private ImmutableList<Path> dependenciesSourceFiles = ImmutableList.of();
    private ImmutableList<Path> snapshotDependenciesSourceFiles = ImmutableList.of();
    private ImmutableList<Path> resourcesSourceFiles = ImmutableList.of();
    private ImmutableList<Path> classesSourceFiles = ImmutableList.of();

    public Builder withDependencies() throws IOException, URISyntaxException {
      dependenciesSourceFiles = getFilesList("application/dependencies");
      return this;
    }

    public Builder withSnapshotDependencies() throws IOException, URISyntaxException {
      snapshotDependenciesSourceFiles = getFilesList("application/snapshot-dependencies");
      return this;
    }

    public Builder withResources() throws IOException, URISyntaxException {
      resourcesSourceFiles = getFilesList("application/resources");
      return this;
    }

    public Builder withClasses() throws IOException, URISyntaxException {
      classesSourceFiles = getFilesList("application/classes");
      return this;
    }

    public TestSourceFilesConfiguration build() {
      return new TestSourceFilesConfiguration(
          dependenciesSourceFiles,
          snapshotDependenciesSourceFiles,
          resourcesSourceFiles,
          classesSourceFiles);
    }

    /** Lists the files in the {@code resourcePath} resources directory. */
    private ImmutableList<Path> getFilesList(String resourcePath)
        throws URISyntaxException, IOException {
      try (Stream<Path> fileStream =
          Files.list(Paths.get(Resources.getResource(resourcePath).toURI()))) {
        return fileStream.collect(ImmutableList.toImmutableList());
      }
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private static final String EXTRACTION_PATH = "/some/extraction/path/";

  private final ImmutableList<Path> dependenciesSourceFiles;
  private final ImmutableList<Path> snapshotDependenciesSourceFiles;
  private final ImmutableList<Path> resourcesSourceFiles;
  private final ImmutableList<Path> classesSourceFiles;

  private TestSourceFilesConfiguration(
      ImmutableList<Path> dependenciesSourceFiles,
      ImmutableList<Path> snapshotDependenciesSourceFiles,
      ImmutableList<Path> resourcesSourceFiles,
      ImmutableList<Path> classesSourceFiles) {
    this.dependenciesSourceFiles = dependenciesSourceFiles;
    this.snapshotDependenciesSourceFiles = snapshotDependenciesSourceFiles;
    this.resourcesSourceFiles = resourcesSourceFiles;
    this.classesSourceFiles = classesSourceFiles;
  }

  @Override
  public ImmutableList<Path> getDependenciesFiles() {
    return dependenciesSourceFiles;
  }

  @Override
  public ImmutableList<Path> getSnapshotDependenciesFiles() {
    return snapshotDependenciesSourceFiles;
  }

  @Override
  public ImmutableList<Path> getResourcesFiles() {
    return resourcesSourceFiles;
  }

  @Override
  public ImmutableList<Path> getClassesFiles() {
    return classesSourceFiles;
  }

  @Override
  public String getDependenciesPathOnImage() {
    return EXTRACTION_PATH + "libs/";
  }

  @Override
  public String getResourcesPathOnImage() {
    return EXTRACTION_PATH + "resources/";
  }

  @Override
  public String getClassesPathOnImage() {
    return EXTRACTION_PATH + "classes/";
  }
}
