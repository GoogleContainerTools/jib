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
import java.nio.file.Paths;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
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
 *     image = ‘gcr.io/my-gcp-project/my-base-image’
 *     credHelper = ‘gcr’
 *   }
 *   to {
 *     image = ‘gcr.io/gcp-project/my-app:built-with-jib’
 *     credHelper = ‘ecr-login’
 *   }
 *   container {
 *     jvmFlags = [‘-Xms512m’, ‘-Xdebug’]
 *     mainClass = ‘com.mycompany.myproject.Main’
 *     args = ['arg1', 'arg2']
 *     exposedPorts = ['1000', '2000-2010', '3000']
 *     format = OCI
 *     appRoot = "/app";
 *   }
 * }
 * }</pre>
 */
public class JibExtension {

  // Defines default configuration values.
  private static final boolean DEFAULT_USE_ONLY_PROJECT_CACHE = false;
  private static final boolean DEFAULT_ALLOW_INSECURE_REGISTIRIES = false;

  private static Path resolveDefaultExtraDirectory(Path projectDirectory) {
    return projectDirectory.resolve("src").resolve("main").resolve("jib");
  }

  private final BaseImageParameters from;
  private final TargetImageParameters to;
  private final ContainerParameters container;
  private final Property<Boolean> useOnlyProjectCache;
  private final Property<Boolean> allowInsecureRegistries;
  private final Property<Path> extraDirectory;

  public JibExtension(Project project) {
    ObjectFactory objectFactory = project.getObjects();

    from = objectFactory.newInstance(BaseImageParameters.class, "jib.from");
    to = objectFactory.newInstance(TargetImageParameters.class, "jib.to");
    container = objectFactory.newInstance(ContainerParameters.class);

    useOnlyProjectCache = objectFactory.property(Boolean.class);
    allowInsecureRegistries = objectFactory.property(Boolean.class);
    extraDirectory = objectFactory.property(Path.class);

    // Sets defaults.
    useOnlyProjectCache.set(DEFAULT_USE_ONLY_PROJECT_CACHE);
    allowInsecureRegistries.set(DEFAULT_ALLOW_INSECURE_REGISTIRIES);
    extraDirectory.set(resolveDefaultExtraDirectory(project.getProjectDir().toPath()));
  }

  public void from(Action<? super ImageParameters> action) {
    action.execute(from);
  }

  public void to(Action<? super ImageParameters> action) {
    action.execute(to);
  }

  public void container(Action<? super ContainerParameters> action) {
    action.execute(container);
  }

  public void setAllowInsecureRegistries(boolean allowInsecureRegistries) {
    this.allowInsecureRegistries.set(allowInsecureRegistries);
  }

  public void setExtraDirectory(File extraDirectory) {
    this.extraDirectory.set(extraDirectory.toPath());
  }

  void setUseOnlyProjectCache(boolean useOnlyProjectCache) {
    this.useOnlyProjectCache.set(useOnlyProjectCache);
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

  @Input
  @Optional
  boolean getUseOnlyProjectCache() {
    if (System.getProperty(PropertyNames.useOnlyProjectCache) != null) {
      return Boolean.getBoolean(PropertyNames.useOnlyProjectCache);
    }
    return useOnlyProjectCache.get();
  }

  @Input
  @Optional
  boolean getAllowInsecureRegistries() {
    if (System.getProperty(PropertyNames.allowInsecureRegistries) != null) {
      return Boolean.getBoolean(PropertyNames.allowInsecureRegistries);
    }
    return allowInsecureRegistries.get();
  }

  @Input
  String getExtraDirectory() {
    // Gradle warns about @Input annotations on File objects, so we have to expose a getter for a
    // String to make them go away.
    if (System.getProperty(PropertyNames.extraDirectory) != null) {
      return System.getProperty(PropertyNames.extraDirectory);
    }
    return extraDirectory.get().toString();
  }

  @Internal
  Path getExtraDirectoryPath() {
    // TODO: Should inform user about nonexistent directory if using custom directory.
    if (System.getProperty(PropertyNames.extraDirectory) != null) {
      return Paths.get(System.getProperty(PropertyNames.extraDirectory));
    }
    return extraDirectory.get();
  }
}
