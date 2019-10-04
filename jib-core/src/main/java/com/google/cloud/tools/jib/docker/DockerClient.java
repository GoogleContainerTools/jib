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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.http.NotifyingOutputStream;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Calls out to the {@code docker} CLI. */
public class DockerClient {

  /**
   * Contains the size, image ID, and diff IDs of an image inspected with {@code docker inspect}.
   */
  public static class InspectResults {
    private long size;
    private String imageId;
    private List<String> diffIds;

    InspectResults(long size, String imageId, List<String> diffIds) {
      this.size = size;
      this.imageId = imageId;
      this.diffIds = ImmutableList.copyOf(diffIds);
    }

    public long getSize() {
      return size;
    }

    public String getImageId() {
      return imageId;
    }

    public List<String> getDiffIds() {
      return diffIds;
    }
  }

  /** Default path to the docker executable. */
  public static final Path DEFAULT_DOCKER_CLIENT = Paths.get("docker");

  /**
   * Checks if Docker is installed on the user's system and accessible by running the default {@code
   * docker} command.
   *
   * @return {@code true} if Docker is installed on the user's system and accessible
   */
  public static boolean isDefaultDockerInstalled() {
    return isDockerInstalled(DEFAULT_DOCKER_CLIENT);
  }

  /**
   * Checks if Docker is installed on the user's system and accessible by running the given {@code
   * docker} executable.
   *
   * @param dockerExecutable path to the executable to test running
   * @return {@code true} if Docker is installed on the user's system and accessible
   */
  public static boolean isDockerInstalled(Path dockerExecutable) {
    try {
      new ProcessBuilder(dockerExecutable.toString()).start();
      return true;

    } catch (IOException ex) {
      return false;
    }
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

  /**
   * Parses the results of {@code docker inspect} into an {@link InspectResults}.
   *
   * @param output the output of the {@code docker inspect} command containing the size, image ID,
   *     and diff IDs
   * @return the {@link InspectResults}
   * @throws DigestException if parsing the digests fails
   */
  @VisibleForTesting
  static InspectResults parseInspectResults(String output) throws DigestException {
    List<String> items = Splitter.on(',').splitToList(output);
    Verify.verify(items.size() == 3);

    long size = Long.parseLong(items.get(0));
    String imageId = DescriptorDigest.fromDigest(items.get(1)).getHash();
    List<String> diffIds =
        Splitter.on(' ').splitToList(items.get(2).replace("[", "").replace("]", ""));
    List<String> processedDiffIds = new ArrayList<>(diffIds.size());
    for (String diffId : diffIds) {
      processedDiffIds.add(DescriptorDigest.fromDigest(diffId.trim()).getHash());
    }

    return new InspectResults(size, imageId, processedDiffIds);
  }

  /** Factory for generating the {@link ProcessBuilder} for running {@code docker} commands. */
  private final Function<List<String>, ProcessBuilder> processBuilderFactory;

  /**
   * Instantiates with a {@code docker} executable and environment variables.
   *
   * @param dockerExecutable path to {@code docker}
   * @param dockerEnvironment environment variables for {@code docker}
   */
  public DockerClient(Path dockerExecutable, Map<String, String> dockerEnvironment) {
    this(
        defaultProcessBuilderFactory(
            dockerExecutable.toString(), ImmutableMap.copyOf(dockerEnvironment)));
  }

  @VisibleForTesting
  DockerClient(Function<List<String>, ProcessBuilder> processBuilderFactory) {
    this.processBuilderFactory = processBuilderFactory;
  }

  /**
   * Loads an image tarball into the Docker daemon.
   *
   * @see <a
   *     href="https://docs.docker.com/engine/reference/commandline/load/">https://docs.docker.com/engine/reference/commandline/load</a>
   * @param imageTarball the built container tarball
   * @param writtenByteCountListener callback to call when bytes are loaded
   * @return stdout from {@code docker}
   * @throws InterruptedException if the 'docker load' process is interrupted
   * @throws IOException if streaming the blob to 'docker load' fails
   */
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

  /**
   * Saves an image tarball from the Docker daemon.
   *
   * @see <a
   *     href="https://docs.docker.com/engine/reference/commandline/save/">https://docs.docker.com/engine/reference/commandline/save</a>
   * @param imageReference the image to save
   * @param outputPath the destination path to save the output tarball
   * @param writtenByteCountListener callback to call when bytes are saved
   * @throws InterruptedException if the 'docker save' process is interrupted
   * @throws IOException if creating the tarball fails
   */
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

  /**
   * Tags the image referenced by {@code originalImageReference} with a new image reference {@code
   * newImageReference}.
   *
   * @param originalImageReference the existing image reference on the Docker daemon
   * @param newImageReference the new image reference
   * @see <a
   *     href="https://docs.docker.com/engine/reference/commandline/tag/">https://docs.docker.com/engine/reference/commandline/tag/</a>
   * @throws InterruptedException if the 'docker tag' process is interrupted
   * @throws IOException if an I/O exception occurs or {@code docker tag} failed
   */
  public void tag(ImageReference originalImageReference, ImageReference newImageReference)
      throws IOException, InterruptedException {
    // Runs 'docker tag'.
    Process dockerProcess =
        docker("tag", originalImageReference.toString(), newImageReference.toString());
    if (dockerProcess.waitFor() != 0) {
      throw new IOException(
          "'docker tag' command failed with error: " + getStderrOutput(dockerProcess));
    }
  }

  /**
   * Gets the size, image ID, and diff IDs of an image in the Docker daemon.
   *
   * @param imageReference the image to inspect
   * @return the size, image ID, and diff IDs of the image
   * @throws IOException if an I/O exception occurs or {@code docker inspect} failed
   * @throws InterruptedException if the {@code docker inspect} process was interrupted
   * @throws DigestException if parsing the image ID or diff IDs failed
   */
  public InspectResults inspect(ImageReference imageReference)
      throws IOException, InterruptedException, DigestException {
    Process inspectProcess =
        docker("inspect", "-f", "{{.Size}},{{.Id}},{{.RootFS.Layers}}", imageReference.toString());
    if (inspectProcess.waitFor() != 0) {
      throw new IOException(
          "'docker inspect' command failed with error: " + getStderrOutput(inspectProcess));
    }
    return parseInspectResults(
        CharStreams.toString(
                new InputStreamReader(inspectProcess.getInputStream(), StandardCharsets.UTF_8))
            .trim());
  }

  /** Runs a {@code docker} command. */
  private Process docker(String... subCommand) throws IOException {
    return processBuilderFactory.apply(Arrays.asList(subCommand)).start();
  }

  private static String getStderrOutput(Process process) {
    try (InputStreamReader stderr =
        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
      return CharStreams.toString(stderr);
    } catch (IOException ex) {
      return "unknown (failed to read error message from stderr due to " + ex.getMessage() + ")";
    }
  }
}
