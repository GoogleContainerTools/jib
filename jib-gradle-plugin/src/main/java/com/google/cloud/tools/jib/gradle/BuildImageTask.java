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
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

/** Builds a container image. */
public class BuildImageTask extends DefaultTask {

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  private static final String CACHE_DIRECTORY_NAME = "jib-cache";

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-gradle-plugin";

  /** Linked extension that configures this task. Must be set before the task is executed. */
  @Nullable private JibExtension extension;

  @Input
  @Nullable
  public String getFromImage() {
    return Preconditions.checkNotNull(extension).getFrom().getImage();
  }

  @Input
  @Nullable
  @Optional
  public String getFromCredHelper() {
    return Preconditions.checkNotNull(extension).getFrom().getCredHelper();
  }

  @Input
  @Nullable
  public String getToImage() {
    return Preconditions.checkNotNull(extension).getTo().getImage();
  }

  @Input
  @Nullable
  @Optional
  public String getToCredHelper() {
    return Preconditions.checkNotNull(extension).getTo().getCredHelper();
  }

  @Input
  public List<String> getJvmFlags() {
    return Preconditions.checkNotNull(extension).getJvmFlags();
  }

  @Input
  @Nullable
  @Optional
  public String getMainClass() {
    return Preconditions.checkNotNull(extension).getMainClass();
  }

  @Input
  public boolean getReproducible() {
    return Preconditions.checkNotNull(extension).getReproducible();
  }

  @Input
  public Class<? extends BuildableManifestTemplate> getFormat() {
    return Preconditions.checkNotNull(extension).getFormat();
  }

  @Input
  public boolean getUseOnlyProjectCache() {
    return Preconditions.checkNotNull(extension).getUseOnlyProjectCache();
  }

  @TaskAction
  public void buildImage() throws InvalidImageReferenceException, IOException {
    ImageReference baseImageReference =
        ImageReference.parse(Preconditions.checkNotNull(getFromImage()));
    ImageReference targetImageReference =
        ImageReference.parse(Preconditions.checkNotNull(getToImage()));

    ProjectProperties projectProperties = new ProjectProperties(getProject(), getLogger());

    String mainClass = getMainClass();
    if (mainClass == null) {
      mainClass = projectProperties.getMainClassFromJarTask();
      if (mainClass == null) {
        throw new GradleException("Could not find main class specified in a 'jar' task");
      }
    }

    SourceFilesConfiguration sourceFilesConfiguration = getSourceFilesConfiguration();

    // TODO: These should be passed separately - one for base image, one for target image.
    List<String> credHelpers = new ArrayList<>();
    if (getFromCredHelper() != null) {
      credHelpers.add(getFromCredHelper());
    }
    if (getToCredHelper() != null) {
      credHelpers.add(getToCredHelper());
    }

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(new GradleBuildLogger(getLogger()))
            .setBaseImage(baseImageReference)
            .setTargetImage(targetImageReference)
            .setCredentialHelperNames(credHelpers)
            .setMainClass(mainClass)
            .setEnableReproducibleBuilds(getReproducible())
            .setJvmFlags(getJvmFlags())
            .setTargetFormat(getFormat())
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

  void setExtension(JibExtension jibExtension) {
    extension = jibExtension;
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
