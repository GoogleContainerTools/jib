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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.plugins.common.ConfigurationPropertyValidator;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/** Object in {@link JibExtension} that configures the extra directories. */
public class ExtraDirectoriesParameters {

  private final Project project;
  @Deprecated private final JibExtension jibExtension;

  private List<Path> paths;
  private Map<String, String> permissions = Collections.emptyMap();

  @Inject
  public ExtraDirectoriesParameters(Project project, JibExtension jibExtension) {
    this.project = project;
    this.jibExtension = jibExtension;
    paths =
        Collections.singletonList(
            project.getProjectDir().toPath().resolve("src").resolve("main").resolve("jib"));
  }

  @Input
  public List<String> getPathStrings() {
    // Gradle warns about @Input annotations on File objects, so we have to expose a getter for a
    // String to make them go away.
    return getPaths().stream().map(Path::toString).collect(Collectors.toList());
  }

  @Internal
  public List<Path> getPaths() {
    // Gradle warns about @Input annotations on File objects, so we have to expose a getter for a
    // String to make them go away.
    String deprecatedProperty = System.getProperty(PropertyNames.EXTRA_DIRECTORY_PATH);
    String newProperty = System.getProperty(PropertyNames.EXTRA_DIRECTORIES_PATHS);
    String property = newProperty != null ? newProperty : deprecatedProperty;
    if (property != null) {
      List<String> pathStrings = ConfigurationPropertyValidator.parseListProperty(property);
      return pathStrings.stream().map(Paths::get).collect(Collectors.toList());
    }
    return paths;
  }

  /**
   * Sets paths. {@code paths} can be any suitable object describing file paths convertible by
   * {@link Project#files} (such as {@link File}, {@code List<File>}, or {@code List<String>}).
   *
   * @param paths paths to set.
   */
  public void setPaths(Object paths) {
    jibExtension.extraDirectoriesConfigured = true;
    this.paths =
        project.files(paths).getFiles().stream().map(File::toPath).collect(Collectors.toList());
  }

  @Deprecated
  public void setPath(File path) {
    jibExtension.extraDirectoryConfigured = true;
    this.paths = Collections.singletonList(path.toPath());
  }

  /**
   * Gets the permissions for files in the extra layer on the container. Maps from absolute path on
   * the container to a 3-digit octal string representation of the file permission bits (e.g. {@code
   * "/path/on/container" -> "755"}).
   *
   * @return the permissions map from path on container to file permissions
   */
  @Input
  public Map<String, String> getPermissions() {
    String deprecatedProperty = System.getProperty(PropertyNames.EXTRA_DIRECTORY_PERMISSIONS);
    String newProperty = System.getProperty(PropertyNames.EXTRA_DIRECTORIES_PERMISSIONS);
    String property = newProperty != null ? newProperty : deprecatedProperty;
    if (property != null) {
      return ConfigurationPropertyValidator.parseMapProperty(property);
    }
    return permissions;
  }

  public void setPermissions(Map<String, String> permissions) {
    this.permissions = permissions;
  }
}
