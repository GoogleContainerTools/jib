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
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/** Object in {@link JibExtension} that configures the extra directories. */
public class ExtraDirectoriesParameters {

  private final ObjectFactory objects;
  private final Project project;

  private ListProperty<ExtraDirectoryParameters> paths;
  private ExtraDirectoryParametersSpec spec;
  private MapProperty<String, String> permissions;

  @Inject
  public ExtraDirectoriesParameters(ObjectFactory objects, Project project) {
    this.objects = objects;
    this.project = project;
    paths = objects.listProperty(ExtraDirectoryParameters.class).empty();
    spec = objects.newInstance(ExtraDirectoryParametersSpec.class, project, paths);
    permissions = objects.mapProperty(String.class, String.class).empty();
  }

  public void paths(Action<? super ExtraDirectoryParametersSpec> action) {
    action.execute(spec);
  }

  @Input
  public List<String> getPathStrings() {
    // Gradle warns about @Input annotations on File objects, so we have to expose a getter for a
    // String to make them go away.
    return getPaths().stream()
        .map(extraDirectoryParameters -> extraDirectoryParameters.getFrom().toString())
        .collect(Collectors.toList());
  }

  @Internal
  public List<ExtraDirectoryParameters> getPaths() {
    // Gradle warns about @Input annotations on File objects, so we have to expose a getter for a
    // String to make them go away.
    String property = System.getProperty(PropertyNames.EXTRA_DIRECTORIES_PATHS);
    if (property != null) {
      List<String> pathStrings = ConfigurationPropertyValidator.parseListProperty(property);
      return pathStrings.stream()
          .map(path -> new ExtraDirectoryParameters(objects, project, Paths.get(path), "/"))
          .collect(Collectors.toList());
    }
    if (paths.get().isEmpty()) {
      return Collections.singletonList(
          new ExtraDirectoryParameters(
              objects,
              project,
              project.getProjectDir().toPath().resolve("src").resolve("main").resolve("jib"),
              "/"));
    }
    return paths.get();
  }

  /**
   * Sets paths. {@code paths} can be any suitable object describing file paths convertible by
   * {@link Project#files} (such as {@link File}, {@code List<File>}, or {@code List<String>}).
   *
   * @param paths paths to set.
   */
  public void setPaths(Object paths) {
    this.paths.set(
        project.files(paths).getFiles().stream()
            .map(file -> new ExtraDirectoryParameters(objects, project, file.toPath(), "/"))
            .collect(Collectors.toList()));
  }

  /**
   * Sets paths, for lazy evaluation where {@code paths} is a {@link Provider} of a suitable object.
   *
   * @param paths provider of paths to set
   * @see #setPaths(Object)
   */
  public void setPaths(Provider<Object> paths) {
    // Convert Provider<Object> to Provider<List<ExtraDirectoryParameters>>
    this.paths.set(
        paths.map(
            obj ->
                project.files(paths).getFiles().stream()
                    .map(file -> new ExtraDirectoryParameters(objects, project, file.toPath(), "/"))
                    .collect(Collectors.toList())));
  }

  /**
   * Gets the permissions for files in the extra layer on the container. Maps from absolute path on
   * the container to a 3-digit octal string representation of the file permission bits (e.g. {@code
   * "/path/on/container" -> "755"}).
   *
   * @return the permissions map from path on container to file permissions
   */
  @Input
  public MapProperty<String, String> getPermissions() {
    String property = System.getProperty(PropertyNames.EXTRA_DIRECTORIES_PERMISSIONS);
    if (property != null) {
      Map<String, String> parsedPermissions =
          ConfigurationPropertyValidator.parseMapProperty(property);
      if (!parsedPermissions.equals(permissions.get())) {
        permissions.set(parsedPermissions);
      }
    }
    return permissions;
  }
}
