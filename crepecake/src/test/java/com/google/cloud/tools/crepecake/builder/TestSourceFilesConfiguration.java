/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.crepecake.builder;

import com.google.common.io.Resources;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Implementation of {@link SourceFilesConfiguration} that uses test resources. */
class TestSourceFilesConfiguration implements SourceFilesConfiguration {

  private static final Path EXTRACTION_PATH = Paths.get("some", "extraction", "path");

  private final Set<Path> dependenciesSourceFiles;
  private final Set<Path> resourcesSourceFiles;
  private final Set<Path> classesSourceFiles;

  TestSourceFilesConfiguration() throws URISyntaxException {
    dependenciesSourceFiles =
        new HashSet<>(
            Collections.singletonList(
                Paths.get(Resources.getResource("application/dependencies").toURI())));
    resourcesSourceFiles =
        new HashSet<>(
            Collections.singletonList(
                Paths.get(Resources.getResource("application/resources").toURI())));
    classesSourceFiles =
        new HashSet<>(
            Collections.singletonList(
                Paths.get(Resources.getResource("application/classes").toURI())));
  }

  @Override
  public Set<Path> getDependenciesFiles() {
    return dependenciesSourceFiles;
  }

  @Override
  public Set<Path> getResourcesFiles() {
    return resourcesSourceFiles;
  }

  @Override
  public Set<Path> getClassesFiles() {
    return classesSourceFiles;
  }

  @Override
  public Path getDependenciesPathOnImage() {
    return EXTRACTION_PATH;
  }

  @Override
  public Path getResourcesPathOnImage() {
    return EXTRACTION_PATH;
  }

  @Override
  public Path getClassesPathOnImage() {
    return EXTRACTION_PATH;
  }
}
