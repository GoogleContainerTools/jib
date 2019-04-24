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

import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

/**
 * Plugin extension for {@link JibPlugin}.
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * jib {
 *   from {
 *     image = 'gcr.io/my-gcp-project/my-base-image'
 *     credHelper = 'gcr'
 *   }
 *   to {
 *     image = 'gcr.io/gcp-project/my-app:built-with-jib'
 *     credHelper = 'ecr-login'
 *   }
 *   container {
 *     jvmFlags = ['-Xms512m', '-Xdebug']
 *     mainClass = 'com.mycompany.myproject.Main'
 *     args = ['arg1', 'arg2']
 *     exposedPorts = ['1000', '2000-2010', '3000']
 *     format = OCI
 *     appRoot = '/app'
 *   }
 *   extraDirectories {
 *     paths = ['/path/to/extra/dir', 'can/be/relative/to/project/root']
 *     permissions = [
 *       '/path/on/container/file1': 744,
 *       '/path/on/container/file2': 123
 *     ]
 *   }
 *   allowInsecureRegistries = false
 * }
 * }</pre>
 */
public class JibExtension {

  // Defines default configuration values.
  private static final boolean DEFAULT_ALLOW_INSECURE_REGISTIRIES = false;

  private final BaseImageParameters from;
  private final TargetImageParameters to;
  private final ContainerParameters container;
  @Deprecated private final ExtraDirectoryParameters extraDirectory;
  private final ExtraDirectoriesParameters extraDirectories;

  private final Property<Boolean> allowInsecureRegistries;

  public JibExtension(Project project) {
    ObjectFactory objectFactory = project.getObjects();

    from = objectFactory.newInstance(BaseImageParameters.class);
    to = objectFactory.newInstance(TargetImageParameters.class);
    container = objectFactory.newInstance(ContainerParameters.class);
    extraDirectory = objectFactory.newInstance(ExtraDirectoryParameters.class);
    extraDirectories = objectFactory.newInstance(ExtraDirectoriesParameters.class, project);

    allowInsecureRegistries = objectFactory.property(Boolean.class);

    // Sets defaults.
    allowInsecureRegistries.set(DEFAULT_ALLOW_INSECURE_REGISTIRIES);
  }

  public void from(Action<? super BaseImageParameters> action) {
    action.execute(from);
  }

  public void to(Action<? super TargetImageParameters> action) {
    action.execute(to);
  }

  public void container(Action<? super ContainerParameters> action) {
    action.execute(container);
  }

  @Deprecated
  public void extraDirectory(Action<? super ExtraDirectoryParameters> action) {
    action.execute(extraDirectory);
  }

  public void extraDirectories(Action<? super ExtraDirectoriesParameters> action) {
    action.execute(extraDirectories);
  }

  @Deprecated
  // for the deprecated "jib.extraDirectory" config parameter
  public void setExtraDirectory(File extraDirectory) {
    this.extraDirectory.setPath(extraDirectory);
  }

  /**
   * Sets extra directory paths. {@code extraDirectories} can be any suitable object describing file
   * paths convertible by {@link Project#files} (such as {@code List<File>} or {@code
   * List<String>}).
   *
   * @param extraDirectories paths to set.
   */
  public void setExtraDirectories(Object extraDirectories) {
    this.extraDirectories.setPaths(extraDirectories);
  }

  public void setAllowInsecureRegistries(boolean allowInsecureRegistries) {
    this.allowInsecureRegistries.set(allowInsecureRegistries);
  }

  @Nested
  @Optional
  public BaseImageParameters getFrom() {
    return from;
  }

  @Nested
  @Optional
  public TargetImageParameters getTo() {
    return to;
  }

  @Nested
  @Optional
  public ContainerParameters getContainer() {
    return container;
  }

  @Nested
  @Optional
  public List<Path> getExtraDirectoriesPaths() {
    Path deprecatedPath = extraDirectory.getPath();
    List<Path> paths = extraDirectories.getPaths();

    if (deprecatedPath != null && !paths.isEmpty()) {
      throw new IllegalArgumentException(
          "You cannot configure both 'jib.extraDirectory' and 'jib.extraDirectories'");
    }
    return deprecatedPath != null ? Collections.singletonList(deprecatedPath) : paths;
  }

  @Nested
  @Optional
  public Map<String, String> getExtraDirectoriesPermissions() {
    Map<String, String> deprecatedPermissions = extraDirectory.getPermissions();
    Map<String, String> permissions = extraDirectories.getPermissions();

    if (!deprecatedPermissions.isEmpty() && !permissions.isEmpty()) {
      throw new IllegalArgumentException(
          "You cannot configure both 'jib.extraDirectory' and 'jib.extraDirectories'");
    }
    return !deprecatedPermissions.isEmpty() ? deprecatedPermissions : permissions;
  }

  @Input
  @Optional
  boolean getAllowInsecureRegistries() {
    if (System.getProperty(PropertyNames.ALLOW_INSECURE_REGISTRIES) != null) {
      return Boolean.getBoolean(PropertyNames.ALLOW_INSECURE_REGISTRIES);
    }
    return allowInsecureRegistries.get();
  }
}
