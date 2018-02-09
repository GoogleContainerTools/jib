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

package com.google.cloud.tools.jib.builder;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Implementation of {@link SourceFilesConfiguration} that uses test resources. */
class TestSourceFilesConfiguration implements SourceFilesConfiguration {

  private static final String EXTRACTION_PATH = "/some/extraction/path/";

  private final List<Path> dependenciesSourceFiles;
  private final List<Path> resourcesSourceFiles;
  private final List<Path> classesSourceFiles;

  TestSourceFilesConfiguration() throws URISyntaxException, IOException {
    dependenciesSourceFiles =
        Collections.singletonList(
            Paths.get(
                Resources.getResource("application/dependencies/dependency-1.0.0.jar").toURI()));
    resourcesSourceFiles =
        Files.list(Paths.get(Resources.getResource("application/resources/").toURI()))
            .collect(Collectors.toList());
    classesSourceFiles =
        Files.list(Paths.get(Resources.getResource("application/classes/").toURI()))
            .collect(Collectors.toList());
  }

  @Override
  public List<Path> getDependenciesFiles() {
    return dependenciesSourceFiles;
  }

  @Override
  public List<Path> getResourcesFiles() {
    return resourcesSourceFiles;
  }

  @Override
  public List<Path> getClassesFiles() {
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
