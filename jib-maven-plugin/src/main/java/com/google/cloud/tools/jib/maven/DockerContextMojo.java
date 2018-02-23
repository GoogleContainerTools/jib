/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/** Exports to a Docker context. */
@Mojo(name = "dockercontext", requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class DockerContextMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(
    property = "jib.dockerDir",
    defaultValue = "${project.build.directory}/jib-dockercontext",
    required = true
  )
  private String targetDir;

  @Parameter(defaultValue = "gcr.io/distroless/java", required = true)
  private String from;

  @Parameter private List<String> jvmFlags;

  @Parameter private Map<String, String> environment;

  @Parameter private String mainClass;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    ProjectProperties projectProperties = new ProjectProperties(project, getLog());

    if (mainClass == null) {
      mainClass = projectProperties.getMainClassFromMavenJarPlugin();
      if (mainClass == null) {
        throwMojoExecutionExceptionWithHelpMessage(
            new MojoFailureException("Could not find main class specified in maven-jar-plugin"),
            "add a `mainClass` configuration to jib-maven-plugin");
      }
    }

    SourceFilesConfiguration sourceFilesConfiguration =
        projectProperties.getSourceFilesConfiguration();

    try {
      Path targetDirPath = Paths.get(targetDir);

      // Deletes the targetDir if it exists.
      if (Files.exists(targetDirPath)) {
        MoreFiles.deleteDirectoryContents(targetDirPath);
      }

      Files.createDirectory(targetDirPath);

      // Creates the directories.
      Path dependenciesDir = targetDirPath.resolve("libs");
      Path resourcesDIr = targetDirPath.resolve("resources");
      Path classesDir = targetDirPath.resolve("classes");
      Files.createDirectory(dependenciesDir);
      Files.createDirectory(resourcesDIr);
      Files.createDirectory(classesDir);

      // Copies dependencies.
      copyFiles(sourceFilesConfiguration.getDependenciesFiles(), dependenciesDir);
      copyFiles(sourceFilesConfiguration.getResourcesFiles(), resourcesDIr);
      copyFiles(sourceFilesConfiguration.getClassesFiles(), classesDir);

      // Creates the Dockerfile.
      StringBuilder dockerfileString = new StringBuilder();
      dockerfileString
          .append("FROM ")
          .append(from)
          .append("\n\nCOPY libs ")
          .append(sourceFilesConfiguration.getDependenciesPathOnImage())
          .append("\nCOPY resources ")
          .append(sourceFilesConfiguration.getResourcesPathOnImage())
          .append("\nCOPY classes ")
          .append(sourceFilesConfiguration.getClassesFiles())
          .append("\n\n")
          .append(getEntrypoint(sourceFilesConfiguration));
      Files.write(
          targetDirPath.resolve("Dockerfile"),
          dockerfileString.toString().getBytes(StandardCharsets.UTF_8));

    } catch (IOException ex) {
      throwMojoExecutionExceptionWithHelpMessage(ex, "check if `targetDir` is set correctly");
    }
  }

  /**
   * Gets the container entrypoint.
   *
   * <p>The entrypoint is {@code java -cp [classpaths] [main class]}.
   */
  private List<String> getEntrypoint(SourceFilesConfiguration sourceFilesConfiguration) {
    List<String> classPaths = new ArrayList<>();
    classPaths.add(sourceFilesConfiguration.getDependenciesPathOnImage() + "*");
    classPaths.add(sourceFilesConfiguration.getResourcesPathOnImage());
    classPaths.add(sourceFilesConfiguration.getClassesPathOnImage());

    String classPathsString = String.join(":", classPaths);

    List<String> entrypoint = new ArrayList<>(4 + jvmFlags.size());
    entrypoint.add("java");
    entrypoint.addAll(jvmFlags);
    entrypoint.add("-cp");
    entrypoint.add(classPathsString);
    entrypoint.add(mainClass);
    return entrypoint;
  }

  private void copyFiles(List<Path> sourceFiles, Path destDir) throws IOException {
    for (Path sourceFile : sourceFiles) {
      copyFile(sourceFile, destDir);
    }
  }

  private void copyFile(Path sourceFile, Path destDir) throws IOException {
    if (Files.isDirectory(sourceFile)) {
      Path destDirForSourceFile = destDir.resolve(sourceFile.getFileName());
      Files.createDirectory(destDirForSourceFile);
      try {
        Files.walk(sourceFile)
            .filter(path -> !path.equals(sourceFile))
            .forEach(
                path -> {
                  try {
                    copyFile(path, destDirForSourceFile);

                  } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                  }
                });

      } catch (UncheckedIOException ex) {
        throw ex.getCause();
      }

    } else {
      Files.copy(sourceFile, destDir.resolve(sourceFile.getFileName()));
    }
  }

  /** @return the {@link ImageReference} parsed from {@link #from}. */
  private ImageReference getBaseImageReference() throws MojoFailureException {
    try {
      return ImageReference.parse(from);

    } catch (InvalidImageReferenceException ex) {
      throw new MojoFailureException("Parameter 'from' is invalid", ex);
    }
  }

  /**
   * Wraps an exception in a {@link MojoExecutionException} and provides a suggestion on how to fix
   * the error.
   */
  private <T extends Throwable> void throwMojoExecutionExceptionWithHelpMessage(
      T ex, @Nullable String suggestion) throws MojoExecutionException {
    StringBuilder message = new StringBuilder("Export Docker context failed");
    if (suggestion != null) {
      message.append(", perhaps you should ");
      message.append(suggestion);
    }
    throw new MojoExecutionException(message.toString(), ex);
  }
}
