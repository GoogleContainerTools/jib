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

package com.google.cloud.tools.jib.cli.cli2;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

@CommandLine.Command(
    name = "jib",
    versionProvider = VersionInfo.class,
    showAtFileInUsageHelp = true,
    synopsisSubcommandLabel = "COMMAND",
    description = "A tool for creating container images")
public class JibCli {
  @Option(
      names = {"-v", "--version"},
      versionHelp = true,
      description = "display version info")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  boolean versionInfoRequested;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  boolean usageHelpRequested;

  @Option(
      names = "--verbosity",
      paramLabel = "<level>",
      defaultValue = "lifecycle",
      description = "set logging verbosity (error, warn, lifecycle (default), info, debug)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  String verbosity;

  @Option(names = "--stacktrace", description = "display stacktrace on failures")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  boolean stacktrace;

  // Build Configuration
  @Option(
      names = {"-t", "--target"},
      required = true,
      paramLabel = "<target-image>",
      description =
          "The destination image reference or jib style url,%nexamples:%n gcr.io/project/image,%n registry://image-ref,%n docker://image,%n tar://path")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  String targetImage;

  @Option(
      names = {"-c", "--context"},
      defaultValue = ".",
      paramLabel = "<project-root>",
      description = "The context root directory of the build (ex: path/to/my/build/things)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  Path contextRoot;

  @Option(
      names = {"-b", "--build-file"},
      paramLabel = "<build-file>",
      description = "The path to the build file (ex: path/to/other-jib.yaml)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  Path buildFile;

  @Option(
      names = {"-p", "--parameter"},
      paramLabel = "<name>=<value>",
      description =
          "templating parameter to inject into build file, replace $${<name>} with <value> (repeatable)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  Map<String, String> templateParameters = new HashMap<String, String>();

  @Option(
      names = "--tags",
      paramLabel = "<tag>",
      split = ",",
      description = "Additional tags for target image")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  List<String> tags = new ArrayList<String>();

  @Option(
      names = "--base-image-cache",
      paramLabel = "<cache-directory>",
      description = "A path to a base image cache")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  Path baseImageCache;

  @Option(
      names = "--application-cache",
      paramLabel = "<cache-directory>",
      description = "A path to an application cache")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  Path applicationCache;

  // Auth/Security
  @Option(
      names = "--allow-insecure-registries",
      description = "Allow jib to communicate with registries over https")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  boolean allowInsecureRegistries;

  @Option(
      names = "--send-credentials-over-http",
      description = "Allow jib to communicate with registries over https")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  boolean sendCredentialsOverHttp;

  @Option(
      names = {"--credential-helper"},
      paramLabel = "<credential-helper>",
      description =
          "Add a credential helper, either a path to the helper, or a suffix for an executable named `docker-credential-<suffix>` (repeatable)")
  List<String> credentialHelpers = new ArrayList<>();

  @ArgGroup(exclusive = true)
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  UsernamePassword usernamePassword;

  static class UsernamePassword {
    @ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    SingleUsernamePassword single;

    @ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    MultiUsernamePassword multi;
  }

  static class MultiUsernamePassword {
    @ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    ToUsernamePassword to;

    @ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    FromUsernamePassword from;
  }

  static class SingleUsernamePassword {
    @Option(
        names = "--username",
        required = true,
        description = "username for communicating with target/base image registry")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String username;

    @Option(
        names = "--password",
        arity = "0..1",
        required = true,
        interactive = true,
        description = "password for communicating with target/base image registry")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String password;
  }

  static class ToUsernamePassword {
    @Option(
        names = "--to-username",
        required = true,
        description = "username for communicating with target image registry")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String username;

    @Option(
        names = "--to-password",
        arity = "0..1",
        interactive = true,
        required = true,
        description = "password for communicating with target image registry")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String password;
  }

  static class FromUsernamePassword {
    @Option(
        names = "--from-username",
        required = true,
        description = "username for communicating with base image registry")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String username;

    @Option(
        names = "--from-password",
        arity = "0..1",
        required = true,
        interactive = true,
        description = "password for communicating with base image registry")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String password;
  }

  /**
   * The magic starts here.
   *
   * @param args the command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new JibCli()).execute(args);
    System.exit(exitCode);
  }
}
