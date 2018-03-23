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

import com.google.api.client.http.HttpTransport;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.BuildImageSteps;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

/** Builds a container image. */
public class BuildImageTask extends DefaultTask {

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  private static final String CACHE_DIRECTORY_NAME = "jib-cache";

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-gradle-plugin";

  @Nullable private ImageConfiguration from;
  @Nullable private ImageConfiguration to;
  @Nullable private List<String> jvmFlags;
  @Nullable private String mainClass;
  private boolean reproducible;
  @Nullable private Class<? extends BuildableManifestTemplate> format;
  private boolean useOnlyProjectCache;

  @Nested
  @Nullable
  public ImageConfiguration getFrom() {
    return from;
  }

  @Nested
  @Nullable
  public ImageConfiguration getTo() {
    return to;
  }

  @Input
  @Nullable
  public List<String> getJvmFlags() {
    return jvmFlags;
  }

  @Input
  @Nullable
  @Optional
  public String getMainClass() {
    return mainClass;
  }

  @Input
  public boolean getReproducible() {
    return reproducible;
  }

  @Input
  @Nullable
  public Class<? extends BuildableManifestTemplate> getFormat() {
    return format;
  }

  @Input
  public boolean getUseOnlyProjectCache() {
    return useOnlyProjectCache;
  }

  @TaskAction
  public void buildImage() throws InvalidImageReferenceException, IOException {
    // Asserts required inputs are not null.
    Preconditions.checkNotNull(from);
    Preconditions.checkNotNull(to);
    Preconditions.checkNotNull(jvmFlags);
    Preconditions.checkNotNull(format);

    ImageReference baseImageReference =
        ImageReference.parse(Preconditions.checkNotNull(from.getImage()));
    ImageReference targetImageReference =
        ImageReference.parse(Preconditions.checkNotNull(to.getImage()));

    ProjectProperties projectProperties = new ProjectProperties(getProject(), getLogger());

    String mainClass = this.mainClass;
    if (mainClass == null) {
      mainClass = projectProperties.getMainClassFromJarTask();
      if (mainClass == null) {
        throw new GradleException("Could not find main class specified in a 'jar' task");
      }
    }

    SourceFilesConfiguration sourceFilesConfiguration = getSourceFilesConfiguration();

    // TODO: These should be passed separately - one for base image, one for target image.
    List<String> credHelpers = new ArrayList<>();
    if (from.getCredHelper() != null) {
      credHelpers.add(from.getCredHelper());
    }
    if (to.getCredHelper() != null) {
      credHelpers.add(to.getCredHelper());
    }

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(new GradleBuildLogger(getLogger()))
            .setBaseImage(baseImageReference)
            .setTargetImage(targetImageReference)
            .setCredentialHelperNames(credHelpers)
            .setMainClass(mainClass)
            .setEnableReproducibleBuilds(reproducible)
            .setJvmFlags(jvmFlags)
            .setTargetFormat(format)
            .build();

    // Uses a directory in the Gradle build cache as the Jib cache.
    Path cacheDirectory = getProject().getBuildDir().toPath().resolve(CACHE_DIRECTORY_NAME);
    if (!Files.exists(cacheDirectory)) {
      Files.createDirectory(cacheDirectory);
    }
    Caches.Initializer cachesInitializer = Caches.newInitializer(cacheDirectory);
    if (getUseOnlyProjectCache()) {
      cachesInitializer.setBaseCacheDirectory(cacheDirectory);
    }

    getLogger().lifecycle("Pushing image as " + targetImageReference);
    getLogger().lifecycle("");
    getLogger().lifecycle("");

    // TODO: Instead of disabling logging, have authentication credentials be provided
    // Disables annoying Apache HTTP client logging.
    System.setProperty(
        "org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "error");
    // Disables Google HTTP client logging.
    Logger logger = Logger.getLogger(HttpTransport.class.getName());
    logger.setLevel(Level.OFF);

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    doBuildImage(
        new BuildImageSteps(buildConfiguration, sourceFilesConfiguration, cachesInitializer));

    getLogger().lifecycle("");
    getLogger().lifecycle("Built and pushed image as " + targetImageReference);
    getLogger().lifecycle("");
  }

  void applyExtension(JibExtension jibExtension) {
    from = jibExtension.getFrom();
    to = jibExtension.getTo();
    jvmFlags = jibExtension.getJvmFlags();
    mainClass = jibExtension.getMainClass();
    reproducible = jibExtension.getReproducible();
    format = jibExtension.getFormat();
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
  @Internal
  private SourceFilesConfiguration getSourceFilesConfiguration() {
    try {
      SourceFilesConfiguration sourceFilesConfiguration =
          GradleSourceFilesConfiguration.getForProject(getProject());

      // Logs the different source files used.
      getLogger().lifecycle("");
      getLogger().lifecycle("Containerizing application with the following files:");
      getLogger().lifecycle("");

      getLogger().lifecycle("\tDependencies:");
      getLogger().lifecycle("");
      sourceFilesConfiguration
          .getDependenciesFiles()
          .forEach(dependencyFile -> getLogger().lifecycle("\t\t" + dependencyFile));

      getLogger().lifecycle("\tResources:");
      getLogger().lifecycle("");
      sourceFilesConfiguration
          .getResourcesFiles()
          .forEach(resourceFile -> getLogger().lifecycle("\t\t" + resourceFile));

      getLogger().lifecycle("\tClasses:");
      getLogger().lifecycle("");
      sourceFilesConfiguration
          .getClassesFiles()
          .forEach(classesFile -> getLogger().lifecycle("\t\t" + classesFile));

      getLogger().lifecycle("");

      return sourceFilesConfiguration;

    } catch (IOException ex) {
      throw new GradleException("Obtaining project build output files failed", ex);
    }
  }
}
