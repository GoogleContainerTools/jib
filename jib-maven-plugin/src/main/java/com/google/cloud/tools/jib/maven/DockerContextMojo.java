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

import com.google.cloud.tools.jib.builder.EntrypointBuilder;
import com.google.cloud.tools.jib.builder.SourceFilesConfiguration;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.filesystem.PathConsumer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.MoreFiles;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import javax.annotation.Nullable;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/** Exports to a Docker context. This is an <b>incubating</b> feature. */
@Mojo(name = "dockercontext", requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class DockerContextMojo extends AbstractMojo {

  /** Copies {@code sourceFiles} to the {@code destDir} directory. */
  @VisibleForTesting
  static void copyFiles(List<Path> sourceFiles, Path destDir) throws IOException {
    for (Path sourceFile : sourceFiles) {
      PathConsumer copyPathConsumer =
          path -> {
            // Creates the same path in the destDir.
            Path destPath = destDir.resolve(sourceFile.getParent().relativize(path));
            if (Files.isDirectory(path)) {
              Files.createDirectory(destPath);
            } else {
              Files.copy(path, destPath);
            }
          };

      if (Files.isDirectory(sourceFile)) {
        new DirectoryWalker(sourceFile).walk(copyPathConsumer);
      } else {
        copyPathConsumer.accept(sourceFile);
      }
    }
  }

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

  @Parameter private List<String> jvmFlags = Collections.emptyList();

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

    createDockerContext(projectProperties);
  }

  @VisibleForTesting
  DockerContextMojo setJvmFlags(List<String> jvmFlags) {
    this.jvmFlags = jvmFlags;
    return this;
  }

  @VisibleForTesting
  DockerContextMojo setMainClass(String mainClass) {
    this.mainClass = mainClass;
    return this;
  }

  @VisibleForTesting
  DockerContextMojo setTargetDir(String targetDir) {
    this.targetDir = targetDir;
    return this;
  }

  @VisibleForTesting
  DockerContextMojo setFrom(String baseImage) {
    from = baseImage;
    return this;
  }

  @VisibleForTesting
  /** Makes a {@code Dockerfile} from the {@code DockerfileTemplate}. */
  String makeDockerfile(SourceFilesConfiguration sourceFilesConfiguration)
      throws IOException, URISyntaxException {
    Path dockerfileTemplate = Paths.get(Resources.getResource("DockerfileTemplate").toURI());

    String dockerfile = new String(Files.readAllBytes(dockerfileTemplate), StandardCharsets.UTF_8);
    dockerfile =
        dockerfile
            .replace("@@BASE_IMAGE@@", from)
            .replace(
                "@@DEPENDENCIES_PATH_ON_IMAGE@@",
                sourceFilesConfiguration.getDependenciesPathOnImage())
            .replace(
                "@@RESOURCES_PATH_ON_IMAGE@@", sourceFilesConfiguration.getResourcesPathOnImage())
            .replace("@@CLASSES_PATH_ON_IMAGE@@", sourceFilesConfiguration.getClassesPathOnImage())
            .replace("@@ENTRYPOINT@@", getEntrypoint(sourceFilesConfiguration));
    return dockerfile;
  }

  /**
   * Gets the Dockerfile ENTRYPOINT in exec-form.
   *
   * @see <a
   *     href="https://docs.docker.com/engine/reference/builder/#exec-form-entrypoint-example">https://docs.docker.com/engine/reference/builder/#exec-form-entrypoint-example</a>
   */
  @VisibleForTesting
  String getEntrypoint(SourceFilesConfiguration sourceFilesConfiguration) {
    List<String> entrypoint =
        EntrypointBuilder.makeEntrypoint(sourceFilesConfiguration, jvmFlags, mainClass);

    StringBuilder entrypointString = new StringBuilder("[");
    boolean firstComponent = true;
    for (String entrypointComponent : entrypoint) {
      if (!firstComponent) {
        entrypointString.append(',');
      }

      // Escapes quotes.
      entrypointComponent = entrypointComponent.replaceAll("\"", Matcher.quoteReplacement("\\\""));

      entrypointString.append('"').append(entrypointComponent).append('"');
      firstComponent = false;
    }
    entrypointString.append(']');

    return entrypointString.toString();
  }

  // TODO: Move most of this to jib-core.
  /** Creates the Docker context in {@link #targetDir}. */
  private void createDockerContext(ProjectProperties projectProperties)
      throws MojoExecutionException, MojoFailureException {
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
      Files.write(
          targetDirPath.resolve("Dockerfile"),
          makeDockerfile(sourceFilesConfiguration).getBytes(StandardCharsets.UTF_8));

      projectProperties.getLog().info("Created Docker context at " + targetDir);

    } catch (IOException ex) {
      throwMojoExecutionExceptionWithHelpMessage(ex, "check if `targetDir` is set correctly");

    } catch (URISyntaxException ex) {
      throw new MojoFailureException("Unexpected URISyntaxException", ex);
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
