/*
 * Copyright 2020 Google LLC.
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

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.Ports;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/** A simple command-line container builder. */
@Command(
    name = "jib",
    synopsisSubcommandLabel = "COMMAND", // pretend that `jib` cannot be run standalone
    description = "A tool for creating container images",
    subcommands = {Build.class})
public class JibCli implements Runnable {

  /** Parses a port specification like {@code 25/tcp} into a {@link Port} objects. */
  static class PortParser implements CommandLine.ITypeConverter<Collection<Port>> {

    @Override
    public Set<Port> convert(String value) throws Exception {
      return Ports.parse(Collections.singletonList(value));
    }
  }

  /** Transforms an image specification to an {@link ImageReference}. */
  static class ImageReferenceParser implements CommandLine.ITypeConverter<ImageReference> {

    @Override
    public ImageReference convert(String imageSpec) throws Exception {
      if ("scratch".equals(imageSpec)) {
        return ImageReference.scratch();
      }
      return ImageReference.parse(imageSpec);
    }
  }

  /** Parses a unix-style path into an {@link AbsoluteUnixPath} object. */
  static class PathParser implements CommandLine.ITypeConverter<AbsoluteUnixPath> {

    @Override
    public AbsoluteUnixPath convert(String unixPath) throws Exception {
      return AbsoluteUnixPath.get(unixPath);
    }
  }

  @Spec
  @SuppressWarnings("NullAway.Init")
  private CommandSpec spec; // the Picocli command

  /**
   * The magic starts here.
   *
   * @param args the command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new JibCli()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public void run() {
    throw new ParameterException(spec.commandLine(), "Missing required subcommand");
  }

  @Option(
      names = {"-v", "--verbose"},
      description = "Be verbose")
  boolean verbose = false;

  @Option(
      names = {"-C", "--credential-helper"},
      paramLabel = "helper",
      description =
          "Add a credential helper, either a path to the helper, "
              + "or a suffix for an executable named `docker-credential-<suffix>`")
  List<String> credentialHelpers = new ArrayList<>();

  @Option(
      names = {"-k", "--insecure"},
      description = "Allow connecting to insecure registries")
  boolean insecure = false;

  /** Create a {@link RegistryImage} with credential retrievers. */
  RegistryImage toCredentialedImage(ImageReference reference, Consumer<LogEvent> logger) {
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
}
