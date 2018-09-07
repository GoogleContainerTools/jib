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

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.image.ImageFormat;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
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
 *   }
 * }
 * }</pre>
 */
public class JibExtension {

  // Defines default configuration values.
  private static final String DEFAULT_FROM_IMAGE = "gcr.io/distroless/java";
  private static final boolean DEFAULT_USE_ONLY_PROJECT_CACHE = false;
  private static final boolean DEFAULT_ALLOW_INSECURE_REGISTIRIES = false;

  private static Path resolveDefaultExtraDirectory(Path projectDirectory) {
    return projectDirectory.resolve("src").resolve("main").resolve("jib");
  }

  private final ImageParameters from;
  private final ImageParameters to;
  private final ContainerParameters container;
  private final Property<Boolean> useOnlyProjectCache;
  private final Property<Boolean> allowInsecureRegistries;
  private final Property<Path> extraDirectory;

  private final Path projectDir;

  // TODO: Deprecated parameters; remove these 4
  private final ListProperty<String> jvmFlags;
  private final Property<String> mainClass;
  private final ListProperty<String> args;
  private final Property<ImageFormat> format;

  public JibExtension(Project project) {
    projectDir = project.getProjectDir().toPath();
    ObjectFactory objectFactory = project.getObjects();

    from = objectFactory.newInstance(ImageParameters.class, "jib.from");
    to = objectFactory.newInstance(ImageParameters.class, "jib.to");
    container = objectFactory.newInstance(ContainerParameters.class);

    jvmFlags = objectFactory.listProperty(String.class);
    mainClass = objectFactory.property(String.class);
    args = objectFactory.listProperty(String.class);
    format = objectFactory.property(ImageFormat.class);

    useOnlyProjectCache = objectFactory.property(Boolean.class);
    allowInsecureRegistries = objectFactory.property(Boolean.class);
    extraDirectory = objectFactory.property(Path.class);

    // Sets defaults.
    from.setImage(DEFAULT_FROM_IMAGE);
    jvmFlags.set(Collections.emptyList());
    args.set(Collections.emptyList());
    useOnlyProjectCache.set(DEFAULT_USE_ONLY_PROJECT_CACHE);
    allowInsecureRegistries.set(DEFAULT_ALLOW_INSECURE_REGISTIRIES);
    extraDirectory.set(resolveDefaultExtraDirectory(projectDir));
  }

  /**
   * Warns about deprecated parameters in use.
   *
   * @param logger The logger used to print the warnings
   */
  void handleDeprecatedParameters(JibLogger logger) {
    StringBuilder deprecatedParams = new StringBuilder();
    if (!jvmFlags.get().isEmpty()) {
      deprecatedParams.append("  jvmFlags -> container.jvmFlags\n");
      if (container.getJvmFlags().isEmpty()) {
        container.setJvmFlags(jvmFlags.get());
      }
    }
    if (!Strings.isNullOrEmpty(mainClass.getOrNull())) {
      deprecatedParams.append("  mainClass -> container.mainClass\n");
      if (Strings.isNullOrEmpty(container.getMainClass())) {
        container.setMainClass(mainClass.getOrNull());
      }
    }
    if (!args.get().isEmpty()) {
      deprecatedParams.append("  args -> container.args\n");
      if (container.getArgs().isEmpty()) {
        container.setArgs(args.get());
      }
    }
    if (format.getOrNull() != null) {
      deprecatedParams.append("  format -> container.format\n");
      container.setFormat(format.get());
    }

    if (deprecatedParams.length() > 0) {
      logger.warn(
          "There are deprecated parameters used in the build configuration. Please make the "
              + "following changes to your build.gradle to avoid issues in the future:\n"
              + deprecatedParams
              + "You may also wrap the parameters in a container{} block.");
    }
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

  public void setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags.set(jvmFlags);
  }

  public void setMainClass(String mainClass) {
    this.mainClass.set(mainClass);
  }

  public void setArgs(List<String> args) {
    this.args.set(args);
  }

  public void setFormat(ImageFormat format) {
    this.format.set(format);
  }

  public void setUseOnlyProjectCache(boolean useOnlyProjectCache) {
    this.useOnlyProjectCache.set(useOnlyProjectCache);
  }

  public void setAllowInsecureRegistries(boolean allowInsecureRegistries) {
    this.allowInsecureRegistries.set(allowInsecureRegistries);
  }

  public void setExtraDirectory(File extraDirectory) {
    this.extraDirectory.set(extraDirectory.toPath());
  }

  @Internal
  String getBaseImage() {
    return Preconditions.checkNotNull(from.getImage());
  }

  @Internal
  @Nullable
  String getTargetImage() {
    return to.getImage();
  }

  @Nested
  @Optional
  public ImageParameters getFrom() {
    return from;
  }

  @Nested
  @Optional
  public ImageParameters getTo() {
    return to;
  }

  @Nested
  @Optional
  public ContainerParameters getContainer() {
    return container;
  }

  // TODO: Make @Internal (deprecated)
  @Input
  @Optional
  List<String> getJvmFlags() {
    return container.getJvmFlags();
  }

  @Internal
  @Optional
  Map<String, String> getEnvironment() {
    return container.getEnvironment();
  }

  // TODO: Make @Internal (deprecated)
  @Input
  @Nullable
  @Optional
  String getMainClass() {
    return container.getMainClass();
  }

  // TODO: Make @Internal (deprecated)
  @Input
  @Optional
  List<String> getArgs() {
    return container.getArgs();
  }

  // TODO: Make @Internal (deprecated)
  @Input
  @Optional
  Class<? extends BuildableManifestTemplate> getFormat() {
    return container.getFormat();
  }

  @Internal
  @Optional
  List<String> getExposedPorts() {
    return container.getPorts();
  }

  @Internal
  @Optional
  Map<String, String> getLabels() {
    return container.getLabels();
  }

  @Internal
  @Optional
  boolean getUseCurrentTimestamp() {
    return container.getUseCurrentTimestamp();
  }

  @Input
  @Optional
  boolean getUseOnlyProjectCache() {
    return useOnlyProjectCache.get();
  }

  @Input
  @Optional
  boolean getAllowInsecureRegistries() {
    return allowInsecureRegistries.get();
  }

  @Input
  String getExtraDirectory() {
    // Gradle warns about @Input annotations on File objects, so we have to expose a getter for a
    // String to make them go away.
    return extraDirectory.get().toString();
  }

  @Internal
  Path getExtraDirectoryPath() {
    // TODO: Should inform user about nonexistent directory if using custom directory.
    return extraDirectory.get();
  }
}
