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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.cli.cli2.logging.Verbosity;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import picocli.CommandLine;
import picocli.CommandLine.MissingParameterException;

@RunWith(JUnitParamsRunner.class)
public class JarTest {

  @Test
  public void testParse_missingRequiredParams_targetImage() {
    MissingParameterException mpe =
        assertThrows(
            MissingParameterException.class,
            () -> CommandLine.populateCommand(new Jar(), "my-app.jar"));
    assertThat(mpe.getMessage()).isEqualTo("Missing required option: '--target=<target-image>'");
  }

  @Test
  public void testParse_missingRequiredParams_jarfile() {
    MissingParameterException mpe =
        assertThrows(
            MissingParameterException.class,
            () -> CommandLine.populateCommand(new Jar(), "--target=test-image-ref"));
    assertThat(mpe.getMessage()).isEqualTo("Missing required parameter: '<jarFile>'");
  }

  @Test
  public void testParse_defaults() {
    Jar jarCommand = CommandLine.populateCommand(new Jar(), "-t", "test-image-ref", "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;
    assertThat(commonCliOptions.getTargetImage()).isEqualTo("test-image-ref");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getAdditionalTags()).isEmpty();
    assertThat(commonCliOptions.getProjectCache()).isEmpty();
    assertThat(commonCliOptions.getBaseImageCache()).isEmpty();
    assertThat(commonCliOptions.isAllowInsecureRegistries()).isFalse();
    assertThat(commonCliOptions.isSendCredentialsOverHttp()).isFalse();
    assertThat(commonCliOptions.getVerbosity()).isEqualTo(Verbosity.lifecycle);
    assertThat(commonCliOptions.isStacktrace()).isFalse();
    assertThat(commonCliOptions.isHttpTrace()).isFalse();
    assertThat(commonCliOptions.isSerialize()).isFalse();
  }

  @Test
  public void testParse_shortFormParams() {
    Jar jarCommand = CommandLine.populateCommand(new Jar(), "-t=test-image-ref", "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;
    assertThat(commonCliOptions.getTargetImage()).isEqualTo("test-image-ref");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getAdditionalTags()).isEmpty();
    assertThat(commonCliOptions.getProjectCache()).isEmpty();
    assertThat(commonCliOptions.getBaseImageCache()).isEmpty();
    assertThat(commonCliOptions.isAllowInsecureRegistries()).isFalse();
    assertThat(commonCliOptions.isSendCredentialsOverHttp()).isFalse();
    assertThat(commonCliOptions.getVerbosity()).isEqualTo(Verbosity.lifecycle);
    assertThat(commonCliOptions.isStacktrace()).isFalse();
    assertThat(commonCliOptions.isStacktrace()).isFalse();
    assertThat(commonCliOptions.isHttpTrace()).isFalse();
    assertThat(commonCliOptions.isSerialize()).isFalse();
  }

  @Test
  public void testParse_longFormParams() {
    // this test does not check credential helpers, scroll down for specialized credential helper
    // tests
    Jar jarCommand =
        CommandLine.populateCommand(
            new Jar(),
            "--target=test-image-ref",
            "--additional-tags=tag1,tag2,tag3",
            "--allow-insecure-registries",
            "--send-credentials-over-http",
            "--project-cache=test-project-cache",
            "--base-image-cache=test-base-image-cache",
            "--verbosity=info",
            "--stacktrace",
            "--http-trace",
            "--serialize",
            "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;
    assertThat(commonCliOptions.getTargetImage()).isEqualTo("test-image-ref");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getAdditionalTags())
        .isEqualTo(ImmutableList.of("tag1", "tag2", "tag3"));
    assertThat(commonCliOptions.getProjectCache()).hasValue(Paths.get("test-project-cache"));
    assertThat(commonCliOptions.getBaseImageCache()).hasValue(Paths.get("test-base-image-cache"));
    assertThat(commonCliOptions.isAllowInsecureRegistries()).isTrue();
    assertThat(commonCliOptions.isSendCredentialsOverHttp()).isTrue();
    assertThat(commonCliOptions.getVerbosity()).isEqualTo(Verbosity.info);
    assertThat(commonCliOptions.isStacktrace()).isTrue();
    assertThat(commonCliOptions.isHttpTrace()).isTrue();
    assertThat(commonCliOptions.isSerialize()).isTrue();
  }

  @Test
  public void testParse_credentialHelper() {
    Jar jarCommand =
        CommandLine.populateCommand(
            new Jar(),
            "--target=test-image-ref",
            "--credential-helper=test-cred-helper",
            "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;
    assertThat(commonCliOptions.getCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_toCredentialHelper() {
    Jar jarCommand =
        CommandLine.populateCommand(
            new Jar(),
            "--target=test-image-ref",
            "--to-credential-helper=test-cred-helper",
            "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_fromCredentialHelper() {
    Jar jarCommand =
        CommandLine.populateCommand(
            new Jar(),
            "--target=test-image-ref",
            "--from-credential-helper=test-cred-helper",
            "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_usernamePassword() {
    Jar jarCommand =
        CommandLine.populateCommand(
            new Jar(),
            "--target=test-image-ref",
            "--username=test-username",
            "--password=test-password",
            "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_toUsernamePassword() {
    Jar jarCommand =
        CommandLine.populateCommand(
            new Jar(),
            "--target=test-image-ref",
            "--to-username=test-username",
            "--to-password=test-password",
            "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;
    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_fromUsernamePassword() {
    Jar jarCommand =
        CommandLine.populateCommand(
            new Jar(),
            "--target=test-image-ref",
            "--from-username=test-username",
            "--from-password=test-password",
            "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
  }

  @Test
  public void testParse_toAndFromUsernamePassword() {
    Jar jarCommand =
        CommandLine.populateCommand(
            new Jar(),
            "--target=test-image-ref",
            "--to-username=test-username-1",
            "--to-password=test-password-1",
            "--from-username=test-username-2",
            "--from-password=test-password-2",
            "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword())
        .hasValue(Credential.from("test-username-1", "test-password-1"));
    assertThat(commonCliOptions.getFromUsernamePassword())
        .hasValue(Credential.from("test-username-2", "test-password-2"));
  }

  @Test
  public void testParse_toAndFromCredentialHelper() {
    Jar jarCommand =
        CommandLine.populateCommand(
            new Jar(),
            "--target=test-image-ref",
            "--to-credential-helper=to-test-helper",
            "--from-credential-helper=from-test-helper",
            "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).hasValue("to-test-helper");
    assertThat(commonCliOptions.getFromCredentialHelper()).hasValue("from-test-helper");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_toUsernamePasswordAndFromCredentialHelper() {
    Jar jarCommand =
        CommandLine.populateCommand(
            new Jar(),
            "--target=test-image-ref",
            "--to-username=test-username",
            "--to-password=test-password",
            "--from-credential-helper=test-cred-helper",
            "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_toCredentialHelperAndFromUsernamePassword() {
    Jar jarCommand =
        CommandLine.populateCommand(
            new Jar(),
            "--target=test-image-ref",
            "--to-credential-helper=test-cred-helper",
            "--from-username=test-username",
            "--from-password=test-password",
            "my-app.jar");
    CommonCliOptions commonCliOptions = jarCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
  }

  private Object usernamePasswordPairs() {
    return new Object[][] {
      {"--username", "--password"},
      {"--to-username", "--to-password"},
      {"--from-username", "--from-password"}
    };
  }

  @Test
  @Parameters(method = "usernamePasswordPairs")
  public void testParse_usernameWithoutPassword(String usernameField, String passwordField) {
    MissingParameterException mpe =
        assertThrows(
            MissingParameterException.class,
            () ->
                CommandLine.populateCommand(
                    new Jar(),
                    "--target=test-image-ref",
                    usernameField,
                    "test-username",
                    "my-app.jar"));
    assertThat(mpe.getMessage()).isEqualTo("Error: Missing required argument(s): " + passwordField);
  }

  @Test
  @Parameters(method = "usernamePasswordPairs")
  public void testParse_passwordWithoutUsername(String usernameField, String passwordField) {
    MissingParameterException mpe =
        assertThrows(
            MissingParameterException.class,
            () ->
                CommandLine.populateCommand(
                    new Jar(),
                    "--target=test-image-ref",
                    passwordField,
                    "test-password",
                    "my-app.jar"));
    assertThat(mpe.getMessage())
        .isEqualTo("Error: Missing required argument(s): " + usernameField + "=<username>");
  }

  public Object incompatibleCredentialOptions() {
    return new Object[] {
      new String[] {"--credential-helper=x", "--to-credential-helper=x"},
      new String[] {"--credential-helper=x", "--from-credential-helper=x"},
      new String[] {"--credential-helper=x", "--username=x", "--password=x"},
      new String[] {"--credential-helper=x", "--from-username=x", "--from-password=x"},
      new String[] {"--credential-helper=x", "--to-username=x", "--to-password=x"},
      new String[] {"--username=x", "--password=x", "--from-username=x", "--from-password=x"},
      new String[] {"--username=x", "--password=x", "--to-username=x", "--to-password=x"},
      new String[] {"--username=x", "--password=x", "--to-credential-helper=x"},
      new String[] {"--username=x", "--password=x", "--from-credential-helper=x"},
      new String[] {"--from-credential-helper=x", "--from-username=x", "--from-password=x"},
      new String[] {"--to-credential-helper=x", "--to-password=x", "--to-username=x"},
    };
  }

  @Test
  @Parameters(method = "incompatibleCredentialOptions")
  public void testParse_incompatibleCredentialOptions(String[] authArgs) {
    CommandLine.MutuallyExclusiveArgsException meae =
        assertThrows(
            CommandLine.MutuallyExclusiveArgsException.class,
            () ->
                CommandLine.populateCommand(
                    new Jar(), ArrayUtils.addAll(authArgs, "--target=ignored", "my-app.jar")));
    assertThat(meae)
        .hasMessageThat()
        .containsMatch("^Error: (--(from-|to-)?credential-helper|\\[--username)");
  }

  @Test
  public void testValidate_nameMissingFail() {
    Jar jarCommand =
        CommandLine.populateCommand(new Jar(), "--target=tar://sometar.tar", "my-app.jar");
    CommandLine.ParameterException pex =
        assertThrows(CommandLine.ParameterException.class, jarCommand.commonCliOptions::validate);
    assertThat(pex.getMessage())
        .isEqualTo("Missing option: --name must be specified when using --target=tar://....");
  }

  @Test
  public void testValidate_pass() {
    Jar jarCommand =
        CommandLine.populateCommand(
            new Jar(), "--target=tar://sometar.tar", "--name=test.io/test/test", "my-app.jar");
    jarCommand.commonCliOptions.validate();
    // pass
  }
}
