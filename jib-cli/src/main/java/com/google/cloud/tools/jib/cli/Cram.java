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

package com.google.cloud.tools.jib.cli;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.Port;
import com.google.cloud.tools.jib.api.Ports;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** A simple command-line container builder. */
@Command(name = "jib", description = "A tool for creating container images.")
public class Cram implements Callable<Integer> {

  /** Parses a port specification like {@code 25/tcp} into a {@link Port} objects. */
  @VisibleForTesting
  static class PortParser implements CommandLine.ITypeConverter<Collection<Port>> {

    @Override
    public Set<Port> convert(String value) throws Exception {
      return Ports.parse(Collections.singletonList(value));
    }
  }

  /** Transforms an image specification to an {@link ImageReference}. */
  @VisibleForTesting
  static class ImageReferenceParser implements CommandLine.ITypeConverter<ImageReference> {

    @Override
    public ImageReference convert(String imageSpec) throws Exception {
      if ("scratch".equals(imageSpec)) {
        return ImageReference.scratch();
      }
      return ImageReference.parse(imageSpec);
    }
  }

  /** Parses a path specification like {@code 25/tcp} into a {@link Port} object. */
  @VisibleForTesting
  static class PathParser implements CommandLine.ITypeConverter<AbsoluteUnixPath> {

    @Override
    public AbsoluteUnixPath convert(String unixPath) throws Exception {
      return AbsoluteUnixPath.get(unixPath);
    }
  }

  /**
   * The magic starts here.
   *
   * @param args the command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new Cram()).execute(args);
    System.exit(exitCode);
  }

  /** The Picocli command object. */
  @Spec
  @SuppressWarnings("NullAway.Init")
  private CommandSpec commandSpec;

  @Option(
      names = {"-d", "--docker"},
      paramLabel = "image",
      description = "push result to local Docker daemon")
  @VisibleForTesting
  boolean toDocker = false;

  @Option(
      names = {"-r", "--registry"},
      description = "push to registry")
  @VisibleForTesting
  boolean toRegistry = false;

  @Option(
      names = {"-c", "--creation-time"},
      description = "set the image creation time")
  @VisibleForTesting
  Instant creationTime = Instant.now();

  @Option(
      names = {"-v", "--verbose"},
      description = "be verbose")
  @VisibleForTesting
  boolean verbose = false;

  @Option(
      names = {"-e", "--entrypoint"},
      paramLabel = "arg",
      split = ",",
      description = "set the container entrypoint")
  @VisibleForTesting
  @Nullable
  List<String> entrypoint;

  @Option(
      names = {"-a", "--arguments"},
      split = ",",
      description = "set the container entrypoint's default arguments")
  @VisibleForTesting
  @Nullable
  List<String> arguments;

  @Option(
      names = {"-E", "--environment"},
      split = ",",
      paramLabel = "key=value",
      description = "add environment pairs")
  @VisibleForTesting
  @Nullable
  Map<String, String> environment;

  @Option(
      names = {"-l", "--label"},
      split = ",",
      paramLabel = "key=value",
      description = "add image labels")
  @VisibleForTesting
  @Nullable
  Map<String, String> labels;

  @Option(
      names = {"-C", "--credential-helper"},
      paramLabel = "helper",
      description =
          "add a credential helper, either a path to the helper,"
              + "or a `docker-credential-<suffix>`")
  @VisibleForTesting
  List<String> credentialHelpers = new ArrayList<>();

  @Option(
      names = {"-p", "--port"},
      split = ",",
      paramLabel = "port",
      description = "expose port/type (e.g., 25 or 25/tcp)",
      converter = PortParser.class)
  @VisibleForTesting
  @Nullable
  List<Port> ports;

  @Option(
      names = {"-V", "--volume"},
      split = ",",
      paramLabel = "path",
      description = "configure specified paths as volumes",
      converter = PathParser.class)
  @VisibleForTesting
  @Nullable
  List<AbsoluteUnixPath> volumes;

  @Option(
      names = {"-u", "--user"},
      paramLabel = "user",
      description = "set user for execution (uid or existing user id)")
  @VisibleForTesting
  @Nullable
  String user;

  @Option(
      names = {"-k", "--insecure"},
      description = "allow connecting to insecure registries")
  @VisibleForTesting
  boolean insecure = false;

  @Parameters(
      index = "0",
      paramLabel = "base-image",
      description = "the base image (e.g., busybox, nginx, gcr.io/distroless/java)",
      converter = ImageReferenceParser.class)
  @VisibleForTesting
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  ImageReference baseImage;

  @Parameters(
      index = "1",
      paramLabel = "destination-image",
      description =
          "the destination image (e.g., localhost:5000/image:1.0, "
              + "gcr.io/project/image:latest)",
      converter = ImageReferenceParser.class)
  @VisibleForTesting
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  ImageReference destinationImage;

  @Parameters(
      index = "2..*",
      paramLabel = "local/path[,/container/path[,directive1,...]]",
      description =
          "Copies content from the local file system as a new layer. "
              + "Container path defaults to '/' if omitted. "
              + "Directives include:\n"
              + "  name=xxx      to set the layer name\n"
              + "  p=perms       to set file and directory permissions:\n"
              + "      actual    use actual values in file-system\n"
              + "      fff:ddd   octal file and directory permissions (octal)\n"
              + "  ts=timestamp  to set last-modified timestamps:\n"
              + "      actual    use last-modified timestamps in file-system\n"
              + "      number    seconds since Unix epoch\n"
              + "      xxx       date in ISO8601 format\n"
              + "File permission default to 0644 and directories to 0755. "
              + "Timestamps default to 1 second after Unix epoch (1970-01-01T00:00:01Z)",
      converter = LayerDefinitionParser.class)
  @VisibleForTesting
  @Nullable
  List<LayerConfiguration> layers;

  @Override
  public Integer call() throws Exception {
    Consumer<LogEvent> logger = System.out::println;
    if (toDocker == toRegistry) {
      throw new CommandLine.ParameterException(
          commandSpec.commandLine(), "One of --docker or --registry is required");
    }
    JibContainerBuilder builder = Jib.from(toCredentialedImage(baseImage, logger));
    verbose("FROM " + baseImage);
    builder.setCreationTime(creationTime);
    if (entrypoint != null) {
      verbose("ENTRYPOINT [" + Joiner.on(",").join(entrypoint) + "]");
      builder.setEntrypoint(entrypoint);
    }
    if (arguments != null) {
      verbose("CMD [" + Joiner.on(",").join(arguments) + "]");
      builder.setProgramArguments(arguments);
    }
    if (environment != null) {
      for (Entry<String, String> pair : environment.entrySet()) {
        verbose("ENV " + pair.getKey() + "=" + pair.getValue());
        builder.addEnvironmentVariable(pair.getKey(), pair.getValue());
      }
    }
    if (labels != null) {
      for (Entry<String, String> pair : labels.entrySet()) {
        verbose("LABEL " + pair.getKey() + "=" + pair.getValue());
        builder.addLabel(pair.getKey(), pair.getValue());
      }
    }
    if (ports != null) {
      for (Port port : ports) {
        verbose("EXPOSE " + port);
        builder.addExposedPort(port);
      }
    }
    if (volumes != null) {
      for (AbsoluteUnixPath volume : volumes) {
        verbose("VOLUME " + volume);
        builder.addVolume(volume);
      }
    }
    if (user != null) {
      verbose("USER " + environment);
      builder.setUser(user);
    }
    if (layers != null) {
      for (LayerConfiguration layer : layers) {
        builder.addLayer(layer);
      }
    }
    Containerizer containerizer =
        toDocker
            ? Containerizer.to(DockerDaemonImage.named(destinationImage))
            : Containerizer.to(toCredentialedImage(destinationImage, logger));
    containerizer.setAllowInsecureRegistries(insecure);
    containerizer.setToolName("cram");
    containerizer.addEventHandler(LogEvent.class, logger);

    ExecutorService executor = Executors.newCachedThreadPool();
    try {
      containerizer.setExecutorService(executor);

      JibContainer result = builder.containerize(containerizer);
      System.out.printf("Containerized to %s (%s)\n", destinationImage, result.getDigest());
      return 0;
    } finally {
      executor.shutdown();
    }
  }

  /** Create a {@link RegistryImage} with credential retrievers. */
  private RegistryImage toCredentialedImage(ImageReference reference, Consumer<LogEvent> logger) {
    RegistryImage registryImage = RegistryImage.named(reference);

    // first add any explicitly specified credential helpers
    CredentialRetrieverFactory factory = CredentialRetrieverFactory.forImage(reference, logger);
    for (String credentialHelper : credentialHelpers) {
      Path path = Paths.get(credentialHelper);
      if (Files.exists(path)) {
        registryImage.addCredentialRetriever(factory.dockerCredentialHelper(path));
      } else {
        registryImage.addCredentialRetriever(factory.dockerCredentialHelper(credentialHelper));
      }
    }
    // then add any other known helpers
    registryImage.addCredentialRetriever(factory.dockerConfig());
    registryImage.addCredentialRetriever(factory.wellKnownCredentialHelpers());
    registryImage.addCredentialRetriever(factory.googleApplicationDefaultCredentials());

    return registryImage;
  }

  private void verbose(String message) {
    if (verbose) {
      System.out.println(message);
    }
  }
}
