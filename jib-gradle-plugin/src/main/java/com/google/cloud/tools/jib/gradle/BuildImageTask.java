/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.BuildImageSteps;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.OCIManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import groovy.lang.Closure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskValidationException;
import org.gradle.util.ConfigureUtil;

/**
 * Builds a container image.
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
 *   jvmFlags = [‘-Xms512m’, ‘-Xdebug’]
 *   mainClass = ‘com.mycompany.myproject.Main’
 *   reproducible = true
 *   format = OCI
 * }
 * }</pre>
 */
public class BuildImageTask extends DefaultTask {

  // TODO: Consolidate with BuildImageMojo's.
  /** Enumeration of {@link BuildableManifestTemplate}s. */
  private enum ImageFormat {
    DOCKER(V22ManifestTemplate.class),
    OCI(OCIManifestTemplate.class);

    private final Class<? extends BuildableManifestTemplate> manifestTemplateClass;

    ImageFormat(Class<? extends BuildableManifestTemplate> manifestTemplateClass) {
      this.manifestTemplateClass = manifestTemplateClass;
    }
  }

  /**
   * Configures an image to be used in the build steps. This is configurable with Groovy closures.
   */
  @VisibleForTesting
  class ImageConfiguration {

    @Nullable private String image;
    @Nullable private String credHelper;

    private ImageConfiguration(String defaultImage) {
      image = defaultImage;
    }

    private ImageConfiguration() {}

    @VisibleForTesting
    @Nullable
    String getImage() {
      return image;
    }

    @VisibleForTesting
    @Nullable
    String getCredHelper() {
      return credHelper;
    }

    /**
     * @param closureName the name of the method the closure was passed to
     * @param closure the closure to apply
     */
    private ImageConfiguration configure(String closureName, Closure closure) {
      ConfigureUtil.configureSelf(closure, this);

      // 'image' is a required property
      if (image == null) {
        // The wrapping mimics Gradle's built-in task configuration validation.
        throw new TaskValidationException(
            "A problem was found with the configuration of task '" + getName() + "'",
            Collections.singletonList(
                new InvalidUserDataException(
                    "'" + closureName + "' closure must define 'image' property")));
      }

      return this;
    }
  }

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  private static final String CACHE_DIRECTORY_NAME = "jib-cache";

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-gradle-plugin";

  private ImageConfiguration from = new ImageConfiguration("gcr.io/distroless/java");
  @Nullable private ImageConfiguration to;

  private List<String> jvmFlags = new ArrayList<>();
  @Nullable private String mainClass;
  private boolean reproducible = true;
  private ImageFormat format = ImageFormat.DOCKER;
  private boolean useOnlyProjectCache = false;

  /** Configures the base image. */
  public void from(Closure<?> closure) {
    from.configure("from", closure);
  }

  /** Configures the target image. */
  public void to(Closure<?> closure) {
    to = new ImageConfiguration().configure("to", closure);
  }

  @Nullable
  @Input
  public ImageConfiguration getFrom() {
    return from;
  }

  @Nullable
  @Input
  public ImageConfiguration getTo() {
    return to;
  }

  @Input
  public List<String> getJvmFlags() {
    return jvmFlags;
  }

  public void setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags = jvmFlags;
  }

  @Input
  @Optional
  @Nullable
  public String getMainClass() {
    return mainClass;
  }

  public void setMainClass(@Nullable String mainClass) {
    this.mainClass = mainClass;
  }

  @Input
  public boolean getReproducible() {
    return reproducible;
  }

  public void setReproducible(boolean isEnabled) {
    reproducible = isEnabled;
  }

  @Input
  public ImageFormat getFormat() {
    return format;
  }

  public void setFormat(ImageFormat format) {
    this.format = format;
  }

  @Input
  public boolean getUseOnlyProjectCache() {
    return useOnlyProjectCache;
  }

  public void setUseOnlyProjectCache(boolean useOnlyProjectCache) {
    this.useOnlyProjectCache = useOnlyProjectCache;
  }

  @TaskAction
  public void buildImage() throws InvalidImageReferenceException, IOException {
    ImageReference baseImageReference =
        ImageReference.parse(Preconditions.checkNotNull(from.image));
    ImageReference targetImageReference =
        ImageReference.parse(Preconditions.checkNotNull(Preconditions.checkNotNull(to).image));

    ProjectProperties projectProperties = new ProjectProperties(getProject(), getLogger());

    if (mainClass == null) {
      mainClass = projectProperties.getMainClassFromJarTask();
      if (mainClass == null) {
        throw new GradleException("Could not find main class specified in a 'jar' task");
      }
    }

    SourceFilesConfiguration sourceFilesConfiguration = getSourceFilesConfiguration();

    // TODO: These should be passed separately - one for base image, one for target image.
    List<String> credHelpers = new ArrayList<>();
    if (from.credHelper != null) {
      credHelpers.add(from.credHelper);
    }
    if (to.credHelper != null) {
      credHelpers.add(to.credHelper);
    }

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(new GradleBuildLogger(getLogger()))
            .setBaseImage(baseImageReference)
            .setTargetImage(targetImageReference)
            .setCredentialHelperNames(credHelpers)
            .setMainClass(mainClass)
            .setEnableReproducibleBuilds(reproducible)
            .setJvmFlags(jvmFlags)
            .setTargetFormat(format.manifestTemplateClass)
            .build();

    // Uses a directory in the Gradle build cache as the Jib cache.
    Path cacheDirectory = getProject().getBuildDir().toPath().resolve(CACHE_DIRECTORY_NAME);
    if (!Files.exists(cacheDirectory)) {
      Files.createDirectory(cacheDirectory);
    }
    Caches.Initializer cachesInitializer = Caches.newInitializer(cacheDirectory);
    if (useOnlyProjectCache) {
      cachesInitializer.setBaseCacheDirectory(cacheDirectory);
    }

    getLogger().info("Pushing image as " + targetImageReference);
    getLogger().info("");
    getLogger().info("");

    // TODO: Instead of disabling logging, have authentication credentials be provided
    // Disables annoying Apache HTTP client logging.
    System.setProperty(
        "org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "error");

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    doBuildImage(
        new BuildImageSteps(buildConfiguration, sourceFilesConfiguration, cachesInitializer));

    getLogger().info("");
    getLogger().info("Built and pushed image as " + targetImageReference);
    getLogger().info("");
  }

  private void doBuildImage(BuildImageSteps buildImageSteps) {
    try {
      buildImageSteps.run();

    } catch (Throwable ex) {
      throw new GradleException("Build image failed", ex);
    }
    // TODO: Catch and handle exceptions.
  }

  /** @return the {@link SourceFilesConfiguration} based on the current project */
  private SourceFilesConfiguration getSourceFilesConfiguration() {
    try {
      SourceFilesConfiguration sourceFilesConfiguration =
          GradleSourceFilesConfiguration.getForProject(getProject());

      // Logs the different source files used.
      getLogger().info("");
      getLogger().info("Containerizing application with the following files:");
      getLogger().info("");

      getLogger().info("\tDependencies:");
      getLogger().info("");
      sourceFilesConfiguration
          .getDependenciesFiles()
          .forEach(dependencyFile -> getLogger().info("\t\t" + dependencyFile));

      getLogger().info("\tResources:");
      getLogger().info("");
      sourceFilesConfiguration
          .getResourcesFiles()
          .forEach(resourceFile -> getLogger().info("\t\t" + resourceFile));

      getLogger().info("\tClasses:");
      getLogger().info("");
      sourceFilesConfiguration
          .getClassesFiles()
          .forEach(classesFile -> getLogger().info("\t\t" + classesFile));

      getLogger().info("");

      return sourceFilesConfiguration;

    } catch (IOException ex) {
      throw new GradleException("Obtaining project build output files failed", ex);
    }
  }
}
