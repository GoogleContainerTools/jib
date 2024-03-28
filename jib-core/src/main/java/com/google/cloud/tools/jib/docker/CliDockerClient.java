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

package com.google.cloud.tools.jib.docker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.DockerClient;
import com.google.cloud.tools.jib.api.ImageDetails;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.http.NotifyingOutputStream;
import com.google.cloud.tools.jib.image.ImageTarball;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Calls out to the {@code docker} CLI. */
public class CliDockerClient implements DockerClient {

  /**
   * Contains the size, image ID, and diff IDs of an image inspected with {@code docker inspect}.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class DockerImageDetails implements JsonTemplate, ImageDetails {

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RootFsTemplate implements JsonTemplate {
      @JsonProperty("Layers")
      private final List<String> layers = Collections.emptyList();
    }

    @JsonProperty("Size")
    private long size;

    @JsonProperty("Id")
    private String imageId = "";

    @JsonProperty("RootFS")
    private final RootFsTemplate rootFs = new RootFsTemplate();

    @Override
    public long getSize() {
      return size;
    }

    @Override
    public DescriptorDigest getImageId() throws DigestException {
      return DescriptorDigest.fromDigest(imageId);
    }

    /**
     * Return a list of diff ids of the layers in the image.
     *
     * @return a list of diff ids
     * @throws DigestException if a digest is invalid
     */
    @Override
    public List<DescriptorDigest> getDiffIds() throws DigestException {
      List<DescriptorDigest> processedDiffIds = new ArrayList<>(rootFs.layers.size());
      for (String diffId : rootFs.layers) {
        processedDiffIds.add(DescriptorDigest.fromDigest(diffId.trim()));
      }
      return processedDiffIds;
    }
  }

  /** Default paths to the docker or alternatives executables. */
  public static final List<Path> DEFAULT_DOCKER_CLIENT = Arrays.asList(
          Paths.get("docker"), Paths.get("podman")
  );

  /**
   * Checks if Docker is installed on the user's system by running the `docker` command.
   *
   * @return {@code true} if Docker is installed on the user's system and accessible
   */
  public static boolean isDefaultDockerInstalled() {
    for (Path dockerClient: DEFAULT_DOCKER_CLIENT) {
      try {
        new ProcessBuilder(dockerClient.toString()).start();
        return true;
      } catch (IOException ignore) {
        // wait for all docker clients fails
      }
    }
    return false;
  }

  /**
   * Get existing Docker (or alternative) executable file.
   *
   * @return {@code Path} to Docker or alternative executable file. If both exists returns Docker.
   * If none - returns first one.
   */
  public static Path getExistingDefaultDocker() {
    for (Path dockerClient : DEFAULT_DOCKER_CLIENT) {
      if (isDockerInstalled(dockerClient)) {
        return dockerClient;
      }
    }
    return DEFAULT_DOCKER_CLIENT.get(0);
  }

  /**
   * Checks if Docker is installed on the user's system and by verifying if the executable path
   * provided has the appropriate permissions.
   *
   * @param dockerExecutable path to the executable to test running
   * @return {@code true} if Docker is installed on the user's system and accessible
   */
  public static boolean isDockerInstalled(Path dockerExecutable) {
    return Files.exists(dockerExecutable);
  }

  /**
   * Gets a function that takes a {@code docker} subcommand and gives back a {@link ProcessBuilder}
   * for that {@code docker} command.
   *
   * @param dockerExecutable path to {@code docker}
   * @return the default {@link ProcessBuilder} factory for running a {@code docker} subcommand
   */
  @VisibleForTesting
  static Function<List<String>, ProcessBuilder> defaultProcessBuilderFactory(
      String dockerExecutable, ImmutableMap<String, String> dockerEnvironment) {
    return dockerSubCommand -> {
      List<String> dockerCommand = new ArrayList<>(1 + dockerSubCommand.size());
      dockerCommand.add(dockerExecutable);
      dockerCommand.addAll(dockerSubCommand);

      ProcessBuilder processBuilder = new ProcessBuilder(dockerCommand);
      Map<String, String> environment = processBuilder.environment();
      environment.putAll(dockerEnvironment);

      return processBuilder;
    };
  }

  private static String getStderrOutput(Process process) {
    try (InputStreamReader stderr =
        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
      return CharStreams.toString(stderr);
    } catch (IOException ex) {
      return "unknown (failed to read error message from stderr due to " + ex.getMessage() + ")";
    }
  }

  /** Factory for generating the {@link ProcessBuilder} for running {@code docker} commands. */
  private final Function<List<String>, ProcessBuilder> processBuilderFactory;

  /**
   * Instantiates with a {@code docker} executable and environment variables.
   *
   * @param dockerExecutable path to {@code docker}
   * @param dockerEnvironment environment variables for {@code docker}
   */
  public CliDockerClient(Path dockerExecutable, Map<String, String> dockerEnvironment) {
    this(
        defaultProcessBuilderFactory(
            dockerExecutable.toString(), ImmutableMap.copyOf(dockerEnvironment)));
  }

  @VisibleForTesting
  CliDockerClient(Function<List<String>, ProcessBuilder> processBuilderFactory) {
    this.processBuilderFactory = processBuilderFactory;
  }

  @Override
  public boolean supported(Map<String, String> parameters) {
    return true;
  }

  @Override
  public String load(ImageTarball imageTarball, Consumer<Long> writtenByteCountListener)
      throws InterruptedException, IOException {
    // Runs 'docker load'.
    Process dockerProcess = docker("load");

    try (NotifyingOutputStream stdin =
        new NotifyingOutputStream(dockerProcess.getOutputStream(), writtenByteCountListener)) {
      imageTarball.writeTo(stdin);

    } catch (IOException ex) {
      // Tries to read from stderr. Not using getStderrOutput(), as we want to show the error
      // message from the tarball I/O write failure when reading from stderr fails.
      String error;
      try (InputStreamReader stderr =
          new InputStreamReader(dockerProcess.getErrorStream(), StandardCharsets.UTF_8)) {
        error = CharStreams.toString(stderr);
      } catch (IOException ignored) {
        // This ignores exceptions from reading stderr and uses the original exception from
        // writing to stdin.
        error = ex.getMessage();
      }
      throw new IOException("'docker load' command failed with error: " + error, ex);
    }

    try (InputStreamReader stdout =
        new InputStreamReader(dockerProcess.getInputStream(), StandardCharsets.UTF_8)) {
      String output = CharStreams.toString(stdout);

      if (dockerProcess.waitFor() != 0) {
        throw new IOException(
            "'docker load' command failed with error: " + getStderrOutput(dockerProcess));
      }

      return output;
    }
  }

  @Override
  public void save(
      ImageReference imageReference, Path outputPath, Consumer<Long> writtenByteCountListener)
      throws InterruptedException, IOException {
    Process dockerProcess = docker("save", imageReference.toString());

    try (InputStream stdout = new BufferedInputStream(dockerProcess.getInputStream());
        OutputStream fileStream = new BufferedOutputStream(Files.newOutputStream(outputPath));
        NotifyingOutputStream notifyingFileStream =
            new NotifyingOutputStream(fileStream, writtenByteCountListener)) {
      ByteStreams.copy(stdout, notifyingFileStream);
    }

    if (dockerProcess.waitFor() != 0) {
      throw new IOException(
          "'docker save' command failed with error: " + getStderrOutput(dockerProcess));
    }
  }

  @Override
  public DockerImageDetails inspect(ImageReference imageReference)
      throws IOException, InterruptedException {
    Process inspectProcess =
        docker("inspect", "-f", "{{json .}}", "--type", "image", imageReference.toString());
    if (inspectProcess.waitFor() != 0) {
      throw new IOException(
          "'docker inspect' command failed with error: " + getStderrOutput(inspectProcess));
    }
    return JsonTemplateMapper.readJson(inspectProcess.getInputStream(), DockerImageDetails.class);
  }

  /** Runs a {@code docker} command. */
  private Process docker(String... subCommand) throws IOException {
    return processBuilderFactory.apply(Arrays.asList(subCommand)).start();
  }
}
