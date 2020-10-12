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

import com.google.cloud.tools.jib.api.Credential;
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
    description = "A tool for creating container images")
public class JibCli {
  @Option(
      names = "--verbosity",
      paramLabel = "<level>",
      defaultValue = "lifecycle",
      description = "set logging verbosity (error, warn, lifecycle (default), info, debug)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private String verbosity;

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
  private Map<String, String> templateParameters = new HashMap<String, String>();

  @Option(
      names = "--additional-tags",
      paramLabel = "<tag>",
      split = ",",
      description = "Additional tags for target image")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private List<String> additionalTags = new ArrayList<String>();

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

  @Option(
      names = {"--credential-helper"},
      paramLabel = "<credential-helper>",
      description =
          "Add a credential helper, either a path to the helper, or a suffix for an executable named `docker-credential-<suffix>` (repeatable)")
  private List<String> credentialHelpers = new ArrayList<>();

  @ArgGroup(exclusive = true)
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private UsernamePassword usernamePassword;

  private static class UsernamePassword {
    @ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    SingleUsernamePassword single;

    @ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    MultiUsernamePassword multi;
  }

  private static class MultiUsernamePassword {
    @ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    ToUsernamePassword to;

    @ArgGroup(exclusive = false)
    @SuppressWarnings("NullAway.Init") // initialized by picocli
    FromUsernamePassword from;
  }

  private static class SingleUsernamePassword {
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

  public String getVerbosity() {
    Verify.verifyNotNull(verbosity);
    return verbosity;
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

  public List<String> getCredentialHelpers() {
    return credentialHelpers;
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
    if (usernamePassword != null && usernamePassword.single != null) {
      Verify.verifyNotNull(usernamePassword.single.username);
      Verify.verifyNotNull(usernamePassword.single.password);
      return Optional.of(
          Credential.from(usernamePassword.single.username, usernamePassword.single.password));
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
    if (usernamePassword != null
        && usernamePassword.multi != null
        && usernamePassword.multi.to != null) {
      Verify.verifyNotNull(usernamePassword.multi.to.username);
      Verify.verifyNotNull(usernamePassword.multi.to.password);
      return Optional.of(
          Credential.from(usernamePassword.multi.to.username, usernamePassword.multi.to.password));
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
    if (usernamePassword != null
        && usernamePassword.multi != null
        && usernamePassword.multi.from != null) {
      Verify.verifyNotNull(usernamePassword.multi.from.username);
      Verify.verifyNotNull(usernamePassword.multi.from.password);
      return Optional.of(
          Credential.from(
              usernamePassword.multi.from.username, usernamePassword.multi.from.password));
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
}
