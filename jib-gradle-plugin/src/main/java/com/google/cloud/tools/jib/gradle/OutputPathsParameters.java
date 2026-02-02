/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/** Object that configures where Jib should create its build output files. */
public class OutputPathsParameters {

  private final Project project;

  private Path digest;
  private Path tar;
  private Path imageId;
  private Path imageJson;

  @Inject
  public OutputPathsParameters(Project project) {
    this.project = project;
    Path buildDir = getBuildDirPath(project);
    digest = buildDir.resolve("jib-image.digest");
    imageId = buildDir.resolve("jib-image.id");
    imageJson = buildDir.resolve("jib-image.json");
    tar = buildDir.resolve("jib-image.tar");
  }

  /**
   * Gets the build directory in a Gradle version-compatible way.
   * Uses layout.buildDirectory for Gradle 7+ and getBuildDir() for Gradle 6.x.
   */
  @SuppressWarnings("deprecation")
  private static Path getBuildDirPath(Project project) {
    try {
      // Try Gradle 7+ API: project.getLayout().getBuildDirectory().get().getAsFile()
      Object layout = project.getClass().getMethod("getLayout").invoke(project);
      Object buildDirectory = layout.getClass().getMethod("getBuildDirectory").invoke(layout);
      Object provider = buildDirectory.getClass().getMethod("get").invoke(buildDirectory);
      java.io.File file = (java.io.File) provider.getClass().getMethod("getAsFile").invoke(provider);
      return file.toPath();
    } catch (Exception e) {
      // Fall back to Gradle 6.x API
      return project.getBuildDir().toPath();
    }
  }

  @Input
  public String getDigest() {
    return getRelativeToProjectRoot(digest, PropertyNames.OUTPUT_PATHS_DIGEST).toString();
  }

  @Internal
  Path getDigestPath() {
    return getRelativeToProjectRoot(digest, PropertyNames.OUTPUT_PATHS_DIGEST);
  }

  public void setDigest(String digest) {
    this.digest = Paths.get(digest);
  }

  @Input
  public String getImageId() {
    return getRelativeToProjectRoot(imageId, PropertyNames.OUTPUT_PATHS_IMAGE_ID).toString();
  }

  @Internal
  Path getImageIdPath() {
    return getRelativeToProjectRoot(imageId, PropertyNames.OUTPUT_PATHS_IMAGE_ID);
  }

  public void setImageId(String id) {
    this.imageId = Paths.get(id);
  }

  @Input
  public String getImageJson() {
    return getRelativeToProjectRoot(imageJson, PropertyNames.OUTPUT_PATHS_IMAGE_JSON).toString();
  }

  @Internal
  Path getImageJsonPath() {
    return getRelativeToProjectRoot(imageJson, PropertyNames.OUTPUT_PATHS_IMAGE_JSON);
  }

  public void setImageJson(String imageJson) {
    this.imageJson = Paths.get(imageJson);
  }

  @Input
  public String getTar() {
    return getRelativeToProjectRoot(tar, PropertyNames.OUTPUT_PATHS_TAR).toString();
  }

  @Internal
  Path getTarPath() {
    return getRelativeToProjectRoot(tar, PropertyNames.OUTPUT_PATHS_TAR);
  }

  public void setTar(String tar) {
    this.tar = Paths.get(tar);
  }

  private Path getRelativeToProjectRoot(Path configuration, String propertyName) {
    String property = System.getProperty(propertyName);
    Path path = property != null ? Paths.get(property) : configuration;
    return path.isAbsolute() ? path : project.getProjectDir().toPath().resolve(path);
  }
}
