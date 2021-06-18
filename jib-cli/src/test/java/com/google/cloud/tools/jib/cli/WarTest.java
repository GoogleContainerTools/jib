/*
 * Copyright 2021 Google LLC.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Ports;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.cli.logging.HttpTraceLevel;
import com.google.cloud.tools.jib.cli.logging.Verbosity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.time.Instant;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import picocli.CommandLine;
import picocli.CommandLine.MissingParameterException;

@RunWith(JUnitParamsRunner.class)
public class WarTest {

  @Test
  public void testParse_missingRequiredParams_targetImage() {
    MissingParameterException mpe =
        assertThrows(
            MissingParameterException.class,
            () -> CommandLine.populateCommand(new War(), "my-app.war"));
    assertThat(mpe)
        .hasMessageThat()
        .isEqualTo("Missing required option: '--target=<target-image>'");
  }

  @Test
  public void testParse_missingRequiredParams_warfile() {
    MissingParameterException mpe =
        assertThrows(
            MissingParameterException.class,
            () -> CommandLine.populateCommand(new War(), "--target=test-image-ref"));
    assertThat(mpe).hasMessageThat().isEqualTo("Missing required parameter: '<warFile>'");
  }

  @Test
  public void testParse_defaults() {
    War warCommand = CommandLine.populateCommand(new War(), "-t", "test-image-ref", "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;
    CommonContainerConfigCliOptions commonContainerConfigCliOptions =
        warCommand.commonContainerConfigCliOptions;

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
    assertThat(commonCliOptions.getHttpTrace()).isEqualTo(HttpTraceLevel.off);
    assertThat(commonCliOptions.isSerialize()).isFalse();
    assertThat(commonCliOptions.getImageJsonPath()).isEmpty();
    assertThat(commonContainerConfigCliOptions.getFrom()).isEmpty();
    assertThat(commonContainerConfigCliOptions.getExposedPorts()).isEmpty();
    assertThat(commonContainerConfigCliOptions.getVolumes()).isEmpty();
    assertThat(commonContainerConfigCliOptions.getEnvironment()).isEmpty();
    assertThat(commonContainerConfigCliOptions.getLabels()).isEmpty();
    assertThat(commonContainerConfigCliOptions.getUser()).isEmpty();
    assertThat(commonContainerConfigCliOptions.getFormat()).hasValue(ImageFormat.Docker);
    assertThat(commonContainerConfigCliOptions.getProgramArguments()).isEmpty();
    assertThat(commonContainerConfigCliOptions.getEntrypoint()).isEmpty();
    assertThat(commonContainerConfigCliOptions.getCreationTime()).isEmpty();
    assertThat(warCommand.getAppRoot()).isEmpty();
  }

  @Test
  public void testParse_shortFormParams() {
    War warCommand = CommandLine.populateCommand(new War(), "-t=test-image-ref", "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;
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
    assertThat(commonCliOptions.getHttpTrace()).isEqualTo(HttpTraceLevel.off);
    assertThat(commonCliOptions.isSerialize()).isFalse();
    assertThat(commonCliOptions.getImageJsonPath()).isEmpty();
  }

  @Test
  public void testParse_longFormParams() {
    // this test does not check credential helpers, scroll down for specialized credential helper
    // tests
    War warCommand =
        CommandLine.populateCommand(
            new War(),
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
            "--image-metadata-out=path/to/json/jib-image.json",
            "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;
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
    assertThat(commonCliOptions.getHttpTrace()).isEqualTo(HttpTraceLevel.config);
    assertThat(commonCliOptions.isSerialize()).isTrue();
    assertThat(commonCliOptions.getImageJsonPath())
        .hasValue(Paths.get("path/to/json/jib-image.json"));
  }

  @Test
  public void testParse_credentialHelper() {
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--credential-helper=test-cred-helper",
            "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;
    assertThat(commonCliOptions.getCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_toCredentialHelper() {
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--to-credential-helper=test-cred-helper",
            "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_fromCredentialHelper() {
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--from-credential-helper=test-cred-helper",
            "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).hasValue("test-cred-helper");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_usernamePassword() {
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--username=test-username",
            "--password=test-password",
            "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;

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
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--to-username=test-username",
            "--to-password=test-password",
            "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;
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
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--from-username=test-username",
            "--from-password=test-password",
            "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;

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
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--to-username=test-username-1",
            "--to-password=test-password-1",
            "--from-username=test-username-2",
            "--from-password=test-password-2",
            "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;

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
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--to-credential-helper=to-test-helper",
            "--from-credential-helper=from-test-helper",
            "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;

    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).hasValue("to-test-helper");
    assertThat(commonCliOptions.getFromCredentialHelper()).hasValue("from-test-helper");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_toUsernamePasswordAndFromCredentialHelper() {
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--to-username=test-username",
            "--to-password=test-password",
            "--from-credential-helper=test-cred-helper",
            "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;

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
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--to-credential-helper=test-cred-helper",
            "--from-username=test-username",
            "--from-password=test-password",
            "my-app.war");
    CommonCliOptions commonCliOptions = warCommand.commonCliOptions;

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
                    new War(),
                    "--target=test-image-ref",
                    usernameField,
                    "test-username",
                    "my-app.war"));
    assertThat(mpe)
        .hasMessageThat()
        .isEqualTo("Error: Missing required argument(s): " + passwordField);
  }

  @Test
  @Parameters(method = "usernamePasswordPairs")
  public void testParse_passwordWithoutUsername(String usernameField, String passwordField) {
    MissingParameterException mpe =
        assertThrows(
            MissingParameterException.class,
            () ->
                CommandLine.populateCommand(
                    new War(),
                    "--target=test-image-ref",
                    passwordField,
                    "test-password",
                    "my-app.war"));
    assertThat(mpe)
        .hasMessageThat()
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
                    new War(), ArrayUtils.addAll(authArgs, "--target=ignored", "my-app.war")));
    assertThat(meae)
        .hasMessageThat()
        .containsMatch("^Error: (--(from-|to-)?credential-helper|\\[--username)");
  }

  @Test
  public void testParse_from() {
    War warCommand =
        CommandLine.populateCommand(
            new War(), "--target=test-image-ref", "--from=base-image-ref", "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.getFrom()).hasValue("base-image-ref");
  }

  @Test
  public void testParse_appRoot() {
    War warCommand =
        CommandLine.populateCommand(
            new War(), "--target=test-image-ref", "--app-root=/path/to/app", "my-app.war");
    assertThat(warCommand.getAppRoot()).hasValue(AbsoluteUnixPath.get("/path/to/app"));
  }

  @Test
  public void testParse_exposedPorts() {
    War warCommand =
        CommandLine.populateCommand(
            new War(), "--target=test-image-ref", "--expose=8080,3306", "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.getExposedPorts())
        .isEqualTo(Ports.parse(ImmutableList.of("8080", "3306")));
  }

  @Test
  public void testParse_volumes() {
    War warCommand =
        CommandLine.populateCommand(
            new War(), "--target=test-image-ref", "--volumes=/volume1,/volume2", "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.getVolumes())
        .isEqualTo(
            ImmutableSet.of(AbsoluteUnixPath.get("/volume1"), AbsoluteUnixPath.get("/volume2")));
  }

  @Test
  public void testParse_environment() {
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--environment-variables=ENV_VAR1=value1,ENV_VAR2=value2",
            "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.getEnvironment())
        .isEqualTo(ImmutableMap.of("ENV_VAR1", "value1", "ENV_VAR2", "value2"));
  }

  @Test
  public void testParse_labels() {
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--labels=label1=value2,label2=value2",
            "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.getLabels())
        .isEqualTo(ImmutableMap.of("label1", "value2", "label2", "value2"));
  }

  @Test
  public void testParse_user() {
    War warCommand =
        CommandLine.populateCommand(
            new War(), "--target=test-image-ref", "--user=customUser", "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.getUser()).hasValue("customUser");
  }

  @Test
  public void testParse_imageFormat() {
    War warCommand =
        CommandLine.populateCommand(
            new War(), "--target=test-image-ref", "--image-format=OCI", "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.getFormat()).hasValue(ImageFormat.OCI);
  }

  @Test
  public void testParse_invalidImageFormat() {
    CommandLine.ParameterException exception =
        assertThrows(
            CommandLine.ParameterException.class,
            () ->
                CommandLine.populateCommand(
                    new War(), "--target=test-image-ref", "--image-format=unknown", "my-app.war"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            "Invalid value for option '--image-format': expected one of [Docker, OCI] (case-sensitive) but was 'unknown'");
  }

  @Test
  public void testParse_programArguments() {
    War warCommand =
        CommandLine.populateCommand(
            new War(), "--target=test-image-ref", "--program-args=arg1,arg2", "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.getProgramArguments())
        .isEqualTo(ImmutableList.of("arg1", "arg2"));
  }

  @Test
  public void testParse_entrypoint() {
    War warCommand =
        CommandLine.populateCommand(
            new War(), "--target=test-image-ref", "--entrypoint=java -cp myClass", "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.getEntrypoint())
        .isEqualTo(ImmutableList.of("java", "-cp", "myClass"));
  }

  @Test
  public void testParse_creationTime_milliseconds() {
    War warCommand =
        CommandLine.populateCommand(
            new War(), "--target=test-image-ref", "--creation-time=23", "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.getCreationTime())
        .hasValue(Instant.ofEpochMilli(23));
  }

  @Test
  public void testParse_creationTime_iso8601() {
    War warCommand =
        CommandLine.populateCommand(
            new War(),
            "--target=test-image-ref",
            "--creation-time=2011-12-03T22:42:05Z",
            "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.getCreationTime())
        .hasValue(Instant.parse("2011-12-03T22:42:05Z"));
  }

  @Test
  public void testValidate_nameMissingFail() {
    War warCommand =
        CommandLine.populateCommand(new War(), "--target=tar://sometar.tar", "my-app.war");
    CommandLine.ParameterException pex =
        assertThrows(CommandLine.ParameterException.class, warCommand.commonCliOptions::validate);
    assertThat(pex)
        .hasMessageThat()
        .isEqualTo("Missing option: --name must be specified when using --target=tar://....");
  }

  @Test
  public void testValidate_pass() {
    War warCommand =
        CommandLine.populateCommand(
            new War(), "--target=tar://sometar.tar", "--name=test.io/test/test", "my-app.war");
    warCommand.commonCliOptions.validate();
    // pass
  }

  @Test
  public void testIsJetty_noCustomBaseImage() throws InvalidImageReferenceException {
    War warCommand =
        CommandLine.populateCommand(new War(), "--target=test-image-ref", "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.isJettyBaseimage()).isTrue();
  }

  @Test
  public void testIsJetty_nonJetty() throws InvalidImageReferenceException {
    War warCommand =
        CommandLine.populateCommand(
            new War(), "--target=test-image-ref", "--from=base-image", "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.isJettyBaseimage()).isFalse();
  }

  @Test
  public void testIsJetty_customJetty() throws InvalidImageReferenceException {
    War warCommand =
        CommandLine.populateCommand(
            new War(), "--target=test-image-ref", "--from=jetty:tag", "my-app.war");
    assertThat(warCommand.commonContainerConfigCliOptions.isJettyBaseimage()).isTrue();
  }
}
