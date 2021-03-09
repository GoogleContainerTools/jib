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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JavaContainerBuilder.LayerType;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.ModificationTimeProvider;
import com.google.cloud.tools.jib.api.buildplan.RelativeUnixPath;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Helper for constructing {@link JavaContainerBuilder}-based {@link JibContainerBuilder}s. */
public class JavaContainerBuilderHelper {

  /**
   * Returns a {@link FileEntriesLayer} for adding the extra directory to the container.
   *
   * @param sourceDirectory the source extra directory path
   * @param targetDirectory the root directory on the container to place the files in
   * @param extraDirectoryPermissions map from path on container to file permissions
   * @param modificationTimeProvider file modification time provider
   * @return a {@link FileEntriesLayer} for adding the extra directory to the container
   * @throws IOException if walking the extra directory fails
   */
  public static FileEntriesLayer extraDirectoryLayerConfiguration(
      Path sourceDirectory,
      AbsoluteUnixPath targetDirectory,
      Map<String, FilePermissions> extraDirectoryPermissions,
      ModificationTimeProvider modificationTimeProvider)
      throws IOException {
    FileEntriesLayer.Builder builder =
        FileEntriesLayer.builder().setName(LayerType.EXTRA_FILES.getName());
    Map<PathMatcher, FilePermissions> pathMatchers = new LinkedHashMap<>();
    for (Map.Entry<String, FilePermissions> entry : extraDirectoryPermissions.entrySet()) {
      pathMatchers.put(
          FileSystems.getDefault().getPathMatcher("glob:" + entry.getKey()), entry.getValue());
    }

    new DirectoryWalker(sourceDirectory)
        .filterRoot()
        .walk(
            localPath -> {
              AbsoluteUnixPath pathOnContainer =
                  targetDirectory.resolve(sourceDirectory.relativize(localPath));
              Instant modificationTime = modificationTimeProvider.get(localPath, pathOnContainer);
              FilePermissions permissions =
                  extraDirectoryPermissions.get(pathOnContainer.toString());
              if (permissions == null) {
                // Check for matching globs
                Path containerPath = Paths.get(pathOnContainer.toString());
                for (Map.Entry<PathMatcher, FilePermissions> entry : pathMatchers.entrySet()) {
                  if (entry.getKey().matches(containerPath)) {
                    builder.addEntry(
                        localPath, pathOnContainer, entry.getValue(), modificationTime);
                    return;
                  }
                }

                // Add with default permissions
                builder.addEntry(localPath, pathOnContainer, modificationTime);
              } else {
                // Add with explicit permissions
                builder.addEntry(localPath, pathOnContainer, permissions, modificationTime);
              }
            });
    return builder.build();
  }

  /**
   * Constructs a new {@link JibContainerBuilder} for a WAR project.
   *
   * @param javaContainerBuilder Java container builder to start with
   * @param explodedWar the exploded WAR directory
   * @param projectArtifactFilename the file names of project artifacts for project dependencies
   * @return {@link JibContainerBuilder} containing the layers for the exploded WAR
   * @throws IOException if adding layer contents fails
   */
  public static JibContainerBuilder fromExplodedWar(
      JavaContainerBuilder javaContainerBuilder,
      Path explodedWar,
      Set<String> projectArtifactFilename)
      throws IOException {
    Path webInfLib = explodedWar.resolve("WEB-INF/lib");
    Path webInfClasses = explodedWar.resolve("WEB-INF/classes");
    Predicate<Path> isDependency = path -> path.startsWith(webInfLib);
    Predicate<Path> isClassFile =
        // Don't use Path.endsWith(), since Path works on path elements.
        path -> path.startsWith(webInfClasses) && path.getFileName().toString().endsWith(".class");
    Predicate<Path> isResource = isDependency.or(isClassFile).negate();
    Predicate<Path> isSnapshot = path -> path.getFileName().toString().contains("SNAPSHOT");
    Predicate<Path> isProjectDependency =
        path -> projectArtifactFilename.contains(path.getFileName().toString());

    javaContainerBuilder
        .setResourcesDestination(RelativeUnixPath.get(""))
        .setClassesDestination(RelativeUnixPath.get("WEB-INF/classes"))
        .setDependenciesDestination(RelativeUnixPath.get("WEB-INF/lib"));

    if (Files.exists(explodedWar)) {
      javaContainerBuilder.addResources(explodedWar, isResource);
    }
    if (Files.exists(webInfClasses)) {
      javaContainerBuilder.addClasses(webInfClasses, isClassFile);
    }
    if (Files.exists(webInfLib)) {
      javaContainerBuilder.addDependencies(
          new DirectoryWalker(webInfLib)
              .filterRoot()
              .filter(isSnapshot.negate())
              .filter(isProjectDependency.negate())
              .walk());
      javaContainerBuilder.addSnapshotDependencies(
          new DirectoryWalker(webInfLib).filterRoot().filter(isSnapshot).walk());
      javaContainerBuilder.addProjectDependencies(
          new DirectoryWalker(webInfLib).filterRoot().filter(isProjectDependency).walk());
    }
    return javaContainerBuilder.toContainerBuilder();
  }

  private JavaContainerBuilderHelper() {}
}
