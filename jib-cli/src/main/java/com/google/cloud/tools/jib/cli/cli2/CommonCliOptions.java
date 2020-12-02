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
import java.util.List;
import java.util.Optional;
import picocli.CommandLine;

public class CommonCliOptions {

  @CommandLine.Spec
  private CommandLine.Model.CommandSpec spec = CommandLine.Model.CommandSpec.create();

  // Build Configuration
  @CommandLine.Option(
      names = {"-t", "--target"},
      required = true,
      paramLabel = "<target-image>",
      description =
          "The destination image reference or jib style url,%nexamples:%n gcr.io/project/image,%n registry://image-ref,%n docker://image,%n tar://path")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  static String targetImage;

  // unfortunately we cannot verify for --target=tar://... this is required, we must do this after
  // pico cli is done parsing
  @CommandLine.Option(
      names = "--name",
      paramLabel = "<image-reference>",
      description =
          "The image reference to inject into the tar configuration (required when using --target tar://...)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private String name;

  @CommandLine.Option(
      names = "--additional-tags",
      paramLabel = "<tag>",
      split = ",",
      description = "Additional tags for target image")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private List<String> additionalTags = new ArrayList<>();

  @CommandLine.Option(
      names = "--base-image-cache",
      paramLabel = "<cache-directory>",
      description = "A path to a base image cache")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Path baseImageCache;

  @CommandLine.Option(
      names = "--application-cache",
      paramLabel = "<cache-directory>",
      description = "A path to an application cache")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Path applicationCache;

  // Auth/Security
  @CommandLine.Option(
      names = "--allow-insecure-registries",
      description = "Allow jib to communicate with registries over http (insecure)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private boolean allowInsecureRegistries;

  @CommandLine.Option(
      names = "--send-credentials-over-http",
      description = "Allow jib to send credentials over http (very insecure)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private boolean sendCredentialsOverHttp;

  @CommandLine.ArgGroup(exclusive = true)
  @SuppressWarnings("NullAway.Init")
  private Credentials credentials;

  private static class Credentials {
    @CommandLine.Option(
        names = {"--credential-helper"},
        paramLabel = "<credential-helper>",
        description =
            "credential helper for communicating with both target and base image registries, either a path to the helper, or a suffix for an executable named `docker-credential-<suffix>`")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private String credentialHelper;

    @CommandLine.ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private SingleUsernamePassword usernamePassword;

    @CommandLine.ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init")
    private SeparateCredentials separate;
  }

  private static class SingleUsernamePassword {
    @CommandLine.Option(
        names = "--username",
        required = true,
        description = "username for communicating with both target and base image registries")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String username;

    @CommandLine.Option(
        names = "--password",
        arity = "0..1",
        required = true,
        interactive = true,
        description = "password for communicating with both target and base image registries")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String password;
  }

  private static class SeparateCredentials {
    @CommandLine.ArgGroup
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private ToCredentials to;

    @CommandLine.ArgGroup
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private FromCredentials from;
  }

  private static class ToCredentials {
    @CommandLine.Option(
        names = {"--to-credential-helper"},
        paramLabel = "<credential-helper>",
        description =
            "credential helper for communicating with target registry, either a path to the helper, or a suffix for an executable named `docker-credential-<suffix>`")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private String credentialHelper;

    @CommandLine.ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private ToUsernamePassword usernamePassword;
  }

  private static class FromCredentials {
    @CommandLine.Option(
        names = {"--from-credential-helper"},
        paramLabel = "<credential-helper>",
        description =
            "credential helper for communicating with base image registry, either a path to the helper, or a suffix for an executable named `docker-credential-<suffix>`")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private String credentialHelper;

    @CommandLine.ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    private FromUsernamePassword usernamePassword;
  }

  private static class ToUsernamePassword {
    @CommandLine.Option(
        names = "--to-username",
        required = true,
        description = "username for communicating with target image registry")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String username;

    @CommandLine.Option(
        names = "--to-password",
        arity = "0..1",
        interactive = true,
        required = true,
        description = "password for communicating with target image registry")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String password;
  }

  private static class FromUsernamePassword {
    @CommandLine.Option(
        names = "--from-username",
        required = true,
        description = "username for communicating with base image registry")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String username;

    @CommandLine.Option(
        names = "--from-password",
        arity = "0..1",
        required = true,
        interactive = true,
        description = "password for communicating with base image registry")
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    String password;
  }

  @CommandLine.Option(
      names = "--verbosity",
      paramLabel = "<level>",
      defaultValue = "lifecycle",
      description =
          "set logging verbosity, candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE}",
      scope = CommandLine.ScopeType.INHERIT)
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Verbosity verbosity;

  @CommandLine.Option(
      names = "--console",
      paramLabel = "<type>",
      defaultValue = "auto",
      description =
          "set console output type, candidates: ${COMPLETION-CANDIDATES}, default: ${DEFAULT-VALUE}",
      scope = CommandLine.ScopeType.INHERIT)
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private ConsoleOutput consoleOutput;

  // Hidden debug parameters
  @CommandLine.Option(names = "--stacktrace", hidden = true, scope = CommandLine.ScopeType.INHERIT)
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private boolean stacktrace;

  @CommandLine.Option(names = "--http-trace", hidden = true, scope = CommandLine.ScopeType.INHERIT)
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private boolean httpTrace;

  @CommandLine.Option(names = "--serialize", hidden = true, scope = CommandLine.ScopeType.INHERIT)
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private boolean serialize;

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

  public boolean isHttpTrace() {
    return httpTrace;
  }

  public boolean isSerialize() {
    return serialize;
  }

  public String getTargetImage() {
    return targetImage;
  }

  public String getName() {
    return name;
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

  public boolean isAllowInsecureRegistries() {
    return allowInsecureRegistries;
  }

  public boolean isSendCredentialsOverHttp() {
    return sendCredentialsOverHttp;
  }

  public Optional<Path> getBaseImageCache() {
    return Optional.ofNullable(baseImageCache);
  }

  public Optional<Path> getApplicationCache() {
    return Optional.ofNullable(applicationCache);
  }

  public List<String> getAdditionalTags() {
    Verify.verifyNotNull(additionalTags);
    return additionalTags;
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
