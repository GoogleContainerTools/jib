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

import static com.google.cloud.tools.jib.api.Jib.TAR_IMAGE_PREFIX;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.cli.cli2.logging.ConsoleOutput;
import com.google.cloud.tools.jib.cli.cli2.logging.Verbosity;
import com.google.common.base.Verify;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

@CommandLine.Command(
    name = "jib",
    versionProvider = VersionInfo.class,
    mixinStandardHelpOptions = true,
    showAtFileInUsageHelp = true,
    synopsisSubcommandLabel = "COMMAND",
    description = "A tool for creating container images",
    subcommands = {Build.class})
public class JibCli {

  @CommandLine.Spec
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private CommandLine.Model.CommandSpec spec;

  @Option(
      names = "--verbosity",
      paramLabel = "<level>",
      defaultValue = "lifecycle",
      description =
          "set logging verbosity, candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE}")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Verbosity verbosity;

  @Option(
      names = "--console",
      paramLabel = "<type>",
      defaultValue = "auto",
      description =
          "set console output type, candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE}")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private ConsoleOutput consoleOutput;

  // Hidden debug parameters
  @Option(names = "--stacktrace", hidden = true)
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private boolean stacktrace;

  @Option(names = "--http-trace", hidden = true)
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private boolean httpTrace;

  @Option(names = "--serialize", hidden = true)
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private boolean serialize;

  // Build Configuration
  @Option(
      names = {"-t", "--target"},
      required = true,
      paramLabel = "<target-image>",
      description =
          "The destination image reference or jib style url,%nexamples:%n gcr.io/project/image,%n registry://image-ref,%n docker://image,%n tar://path")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private String targetImage;

  @Option(
      names = {"-c", "--context"},
      defaultValue = ".",
      paramLabel = "<project-root>",
      description = "The context root directory of the build (ex: path/to/my/build/things)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Path contextRoot;

  @Option(
      names = {"-b", "--build-file"},
      paramLabel = "<build-file>",
      description = "The path to the build file (ex: path/to/other-jib.yaml)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Path buildFile;

  // unfortunately we cannot verify for --target=tar://... this is required, we must do this after
  // pico cli is done parsing
  @Option(
      names = "--name",
      paramLabel = "<image-reference>",
      description =
          "The image reference to inject into the tar configuration (required when using --target tar://...)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private String name;

  @Option(
      names = {"-p", "--parameter"},
      paramLabel = "<name>=<value>",
      description =
          "templating parameter to inject into build file, replace $${<name>} with <value> (repeatable)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Map<String, String> templateParameters = new HashMap<>();

  @Option(
      names = "--additional-tags",
      paramLabel = "<tag>",
      split = ",",
      description = "Additional tags for target image")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private List<String> additionalTags = new ArrayList<>();

  @Option(
      names = "--base-image-cache",
      paramLabel = "<cache-directory>",
      description = "A path to a base image cache")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Path baseImageCache;

  @Option(
      names = "--application-cache",
      paramLabel = "<cache-directory>",
      description = "A path to an application cache")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Path applicationCache;

  // Auth/Security
  @Option(
      names = "--allow-insecure-registries",
      description = "Allow jib to communicate with registries over http (insecure)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private boolean allowInsecureRegistries;

  @Option(
      names = "--send-credentials-over-http",
      description = "Allow jib to send credentials over http (very insecure)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private boolean sendCredentialsOverHttp;

  @ArgGroup(exclusive = true)
  @SuppressWarnings("NullAway.Init")
  private Credentials credentials;

  private static class Credentials {
    @Option(
        names = {"--credential-helper"},
        paramLabel = "<credential-helper>",
        description =
            "credential helper for communicating with both target and base image registries, either a path to the helper, or a suffix for an executable named `docker-credential-<suffix>`")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private String credentialHelper;

    @ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private SingleUsernamePassword usernamePassword;

    @ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init")
    private SeparateCredentials separate;
  }

  private static class SingleUsernamePassword {
    @Option(
        names = "--username",
        required = true,
        description = "username for communicating with both target and base image registries")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String username;

    @Option(
        names = "--password",
        arity = "0..1",
        required = true,
        interactive = true,
        description = "password for communicating with both target and base image registries")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String password;
  }

  private static class SeparateCredentials {
    @ArgGroup
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private ToCredentials to;

    @ArgGroup
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private FromCredentials from;
  }

  private static class ToCredentials {
    @Option(
        names = {"--to-credential-helper"},
        paramLabel = "<credential-helper>",
        description =
            "credential helper for communicating with target registry, either a path to the helper, or a suffix for an executable named `docker-credential-<suffix>`")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private String credentialHelper;

    @ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private ToUsernamePassword usernamePassword;
  }

  private static class FromCredentials {
    @Option(
        names = {"--from-credential-helper"},
        paramLabel = "<credential-helper>",
        description =
            "credential helper for communicating with base image registry, either a path to the helper, or a suffix for an executable named `docker-credential-<suffix>`")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private String credentialHelper;

    @ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private FromUsernamePassword usernamePassword;
  }

  private static class ToUsernamePassword {
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

  private static class FromUsernamePassword {
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

  public Verbosity getVerbosity() {
    Verify.verifyNotNull(verbosity);
    return verbosity;
  }

  public ConsoleOutput getConsoleOutput() {
    Verify.verifyNotNull(consoleOutput);
    return consoleOutput;
  }

  public boolean isStacktrace() {
    return stacktrace;
  }

  public String getTargetImage() {
    return targetImage;
  }

  public Path getContextRoot() {
    Verify.verifyNotNull(contextRoot);
    return contextRoot;
  }

  /**
   * Returns a user configured Path to a buildfile and if none is configured returns jib.yaml in
   * {@link #getContextRoot()}.
   *
   * @return a path to a bulidfile
   */
  public Path getBuildFile() {
    if (buildFile == null) {
      return getContextRoot().resolve("jib.yaml");
    }
    return buildFile;
  }

  public Map<String, String> getTemplateParameters() {
    Verify.verifyNotNull(templateParameters);
    return templateParameters;
  }

  public List<String> getAdditionalTags() {
    Verify.verifyNotNull(additionalTags);
    return additionalTags;
  }

  public Optional<Path> getBaseImageCache() {
    return Optional.ofNullable(baseImageCache);
  }

  public Optional<Path> getApplicationCache() {
    return Optional.ofNullable(applicationCache);
  }

  public boolean isAllowInsecureRegistries() {
    return allowInsecureRegistries;
  }

  public boolean isSendCredentialsOverHttp() {
    return sendCredentialsOverHttp;
  }

  /**
   * Returns the user configured credential helper for any registry during the build, this can be
   * interpreted as a path or a string.
   *
   * @return a Optional string reference to the credential helper
   */
  public Optional<String> getCredentialHelper() {
    if (credentials != null && credentials.credentialHelper != null) {
      return Optional.of(credentials.credentialHelper);
    }
    return Optional.empty();
  }

  /**
   * Returns the user configured credential helper for the target image registry, this can be
   * interpreted as a path or a string.
   *
   * @return a Optional string reference to the credential helper
   */
  public Optional<String> getToCredentialHelper() {
    if (credentials != null
        && credentials.separate != null
        && credentials.separate.to != null
        && credentials.separate.to.credentialHelper != null) {
      return Optional.of(credentials.separate.to.credentialHelper);
    }
    return Optional.empty();
  }

  /**
   * Returns the user configured credential helper for the base image registry, this can be
   * interpreted as a path or a string.
   *
   * @return a Optional string reference to the credential helper
   */
  public Optional<String> getFromCredentialHelper() {
    if (credentials != null
        && credentials.separate != null
        && credentials.separate.from != null
        && credentials.separate.from.credentialHelper != null) {
      return Optional.of(credentials.separate.from.credentialHelper);
    }
    return Optional.empty();
  }

  public boolean isHttpTrace() {
    return httpTrace;
  }

  public boolean isSerialize() {
    return serialize;
  }

  public String getName() {
    return name;
  }

  /**
   * If configured, returns a {@link Credential} created from user configured username/password.
   *
   * @return an optional Credential
   */
  public Optional<Credential> getUsernamePassword() {
    if (credentials != null && credentials.usernamePassword != null) {
      Verify.verifyNotNull(credentials.usernamePassword.username);
      Verify.verifyNotNull(credentials.usernamePassword.password);
      return Optional.of(
          Credential.from(
              credentials.usernamePassword.username, credentials.usernamePassword.password));
    }
    return Optional.empty();
  }

  /**
   * If configured, returns a {@link Credential} created from user configured "to"
   * username/password.
   *
   * @return a optional Credential
   */
  public Optional<Credential> getToUsernamePassword() {
    if (credentials != null
        && credentials.separate != null
        && credentials.separate.to != null
        && credentials.separate.to.usernamePassword != null) {
      Verify.verifyNotNull(credentials.separate.to.usernamePassword.username);
      Verify.verifyNotNull(credentials.separate.to.usernamePassword.password);
      return Optional.of(
          Credential.from(
              credentials.separate.to.usernamePassword.username,
              credentials.separate.to.usernamePassword.password));
    }
    return Optional.empty();
  }

  /**
   * If configured, returns a {@link Credential} created from user configured "from"
   * username/password.
   *
   * @return a optional Credential
   */
  public Optional<Credential> getFromUsernamePassword() {
    if (credentials != null
        && credentials.separate != null
        && credentials.separate.from != null
        && credentials.separate.from.usernamePassword != null) {
      Verify.verifyNotNull(credentials.separate.from.usernamePassword.username);
      Verify.verifyNotNull(credentials.separate.from.usernamePassword.password);
      return Optional.of(
          Credential.from(
              credentials.separate.from.usernamePassword.username,
              credentials.separate.from.usernamePassword.password));
    }
    return Optional.empty();
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

  /** Validates parameters defined in this class that could not be done declaratively. */
  public void validate() {
    if (targetImage.startsWith(TAR_IMAGE_PREFIX) && name == null) {
      throw new CommandLine.ParameterException(
          spec.commandLine(),
          "Missing option: --name must be specified when using --target=tar://....");
    }
  }
}
