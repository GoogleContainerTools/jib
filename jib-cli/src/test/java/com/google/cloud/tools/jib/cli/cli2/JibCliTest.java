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

import com.google.cloud.tools.jib.cli.cli2.logging.Verbosity;
import java.nio.file.Paths;
import junitparams.JUnitParamsRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import picocli.CommandLine;
import picocli.CommandLine.MissingParameterException;

@RunWith(JUnitParamsRunner.class)
public class JibCliTest {
  @Test
  public void testParse_missingRequiredParams() {
    MissingParameterException mpe =
        assertThrows(
            MissingParameterException.class, () -> CommandLine.populateCommand(new JibCli(), ""));
    assertThat(mpe.getMessage()).isEqualTo("Missing required option: '--target=<target-image>'");
  }

  @Test
  public void testParse_defaults() {
    JibCli jibCli = CommandLine.populateCommand(new JibCli(), "-t", "test-image-ref");
    CommonCliOptions commonCliOptions = new CommonCliOptions();

    assertThat(commonCliOptions.getTargetImage()).isEqualTo("test-image-ref");
    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
    assertThat(jibCli.getBuildFile()).isEqualTo(Paths.get("./jib.yaml"));
    assertThat(jibCli.getContextRoot()).isEqualTo(Paths.get("."));
    assertThat(commonCliOptions.getAdditionalTags()).isEmpty();
    assertThat(jibCli.getTemplateParameters()).isEmpty();
    assertThat(commonCliOptions.getApplicationCache()).isEmpty();
    assertThat(commonCliOptions.getBaseImageCache()).isEmpty();
    assertThat(commonCliOptions.isAllowInsecureRegistries()).isFalse();
    assertThat(commonCliOptions.isSendCredentialsOverHttp()).isFalse();
    assertThat(jibCli.getVerbosity()).isEqualTo(Verbosity.lifecycle);
    assertThat(jibCli.isStacktrace()).isFalse();
    assertThat(jibCli.isHttpTrace()).isFalse();
    assertThat(jibCli.isSerialize()).isFalse();
  }

  //  @Test
  //  public void testParse_shortFormParams() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(),
  //            "-t=test-image-ref",
  //            "-c=test-context",
  //            "-b=test-build-file",
  //            "-p=param1=value1",
  //            "-p=param2=value2");
  //    CommonCliOptions commonCliOptions = new CommonCliOptions();
  //
  //    assertThat(commonCliOptions.getTargetImage()).isEqualTo("test-image-ref");
  //    assertThat(commonCliOptions.getUsernamePassword()).isEmpty();
  //    assertThat(commonCliOptions.getToUsernamePassword()).isEmpty();
  //    assertThat(commonCliOptions.getFromUsernamePassword()).isEmpty();
  //    assertThat(commonCliOptions.getCredentialHelper()).isEmpty();
  //    assertThat(commonCliOptions.getToCredentialHelper()).isEmpty();
  //    assertThat(commonCliOptions.getFromCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getBuildFile()).isEqualTo(Paths.get("test-build-file"));
  //    assertThat(jibCli.getContextRoot()).isEqualTo(Paths.get("test-context"));
  //    assertThat(commonCliOptions.getAdditionalTags()).isEmpty();
  //    assertThat(jibCli.getTemplateParameters())
  //        .isEqualTo(ImmutableMap.of("param1", "value1", "param2", "value2"));
  //    assertThat(commonCliOptions.getApplicationCache()).isEmpty();
  //    assertThat(commonCliOptions.getBaseImageCache()).isEmpty();
  //    assertThat(commonCliOptions.isAllowInsecureRegistries()).isFalse();
  //    assertThat(commonCliOptions.isSendCredentialsOverHttp()).isFalse();
  //    assertThat(jibCli.getVerbosity()).isEqualTo(Verbosity.lifecycle);
  //    assertThat(jibCli.isStacktrace()).isFalse();
  //    assertThat(jibCli.isStacktrace()).isFalse();
  //    assertThat(jibCli.isHttpTrace()).isFalse();
  //    assertThat(jibCli.isSerialize()).isFalse();
  //  }
  //
  //  @Test
  //  public void testParse_longFormParams() {
  //    // this test does not check credential helpers, scroll down for specialized credential
  // helper
  //    // tests
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(),
  //            "--target=test-image-ref",
  //            "--context=test-context",
  //            "--build-file=test-build-file",
  //            "--parameter=param1=value1",
  //            "--parameter=param2=value2",
  //            "--additional-tags=tag1,tag2,tag3",
  //            "--allow-insecure-registries",
  //            "--send-credentials-over-http",
  //            "--application-cache=test-application-cache",
  //            "--base-image-cache=test-base-image-cache",
  //            "--verbosity=info",
  //            "--stacktrace",
  //            "--http-trace",
  //            "--serialize");
  //    CommonCliOptions commonCliOptions = new CommonCliOptions();
  //
  //    assertThat(jibCli.getTargetImage()).isEqualTo("test-image-ref");
  //    assertThat(jibCli.getUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getToUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getToCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getFromCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getBuildFile()).isEqualTo(Paths.get("test-build-file"));
  //    assertThat(jibCli.getContextRoot()).isEqualTo(Paths.get("test-context"));
  //    assertThat(jibCli.getAdditionalTags()).isEqualTo(ImmutableList.of("tag1", "tag2", "tag3"));
  //    assertThat(jibCli.getTemplateParameters())
  //        .isEqualTo(ImmutableMap.of("param1", "value1", "param2", "value2"));
  //    assertThat(jibCli.getApplicationCache()).hasValue(Paths.get("test-application-cache"));
  //    assertThat(jibCli.getBaseImageCache()).hasValue(Paths.get("test-base-image-cache"));
  //    assertThat(jibCli.isAllowInsecureRegistries()).isTrue();
  //    assertThat(jibCli.isSendCredentialsOverHttp()).isTrue();
  //    assertThat(jibCli.getVerbosity()).isEqualTo(Verbosity.info);
  //    assertThat(jibCli.isStacktrace()).isTrue();
  //    assertThat(jibCli.isHttpTrace()).isTrue();
  //    assertThat(jibCli.isSerialize()).isTrue();
  //  }
  //
  //  @Test
  //  public void testParse_buildFileDefaultForContext() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(), "--target", "test-image-ref", "--context", "test-context");
  //
  //    assertThat(jibCli.getBuildFile()).isEqualTo(Paths.get("test-context/jib.yaml"));
  //    assertThat(jibCli.getContextRoot()).isEqualTo(Paths.get("test-context"));
  //  }
  //
  //  @Test
  //  public void testParse_credentialHelper() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(), "--target=test-image-ref", "--credential-helper=test-cred-helper");
  //
  //    assertThat(jibCli.getCredentialHelper()).hasValue("test-cred-helper");
  //    assertThat(jibCli.getToCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getFromCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getToUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
  //  }
  //
  //  @Test
  //  public void testParse_toCredentialHelper() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(), "--target=test-image-ref", "--to-credential-helper=test-cred-helper");
  //
  //    assertThat(jibCli.getCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getToCredentialHelper()).hasValue("test-cred-helper");
  //    assertThat(jibCli.getFromCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getToUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
  //  }
  //
  //  @Test
  //  public void testParse_fromCredentialHelper() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(), "--target=test-image-ref",
  // "--from-credential-helper=test-cred-helper");
  //
  //    assertThat(jibCli.getCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getToCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getFromCredentialHelper()).hasValue("test-cred-helper");
  //    assertThat(jibCli.getUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getToUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
  //  }
  //
  //  @Test
  //  public void testParse_usernamePassword() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(),
  //            "--target=test-image-ref",
  //            "--username=test-username",
  //            "--password=test-password");
  //
  //    assertThat(jibCli.getCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getToCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getFromCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getUsernamePassword())
  //        .hasValue(Credential.from("test-username", "test-password"));
  //    assertThat(jibCli.getToUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
  //  }
  //
  //  @Test
  //  public void testParse_toUsernamePassword() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(),
  //            "--target=test-image-ref",
  //            "--to-username=test-username",
  //            "--to-password=test-password");
  //
  //    assertThat(jibCli.getCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getToCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getFromCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getToUsernamePassword())
  //        .hasValue(Credential.from("test-username", "test-password"));
  //    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
  //  }
  //
  //  @Test
  //  public void testParse_fromUsernamePassword() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(),
  //            "--target=test-image-ref",
  //            "--from-username=test-username",
  //            "--from-password=test-password");
  //
  //    assertThat(jibCli.getCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getToCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getFromCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getToUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getFromUsernamePassword())
  //        .hasValue(Credential.from("test-username", "test-password"));
  //  }
  //
  //  @Test
  //  public void testParse_toAndFromUsernamePassword() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(),
  //            "--target=test-image-ref",
  //            "--to-username=test-username-1",
  //            "--to-password=test-password-1",
  //            "--from-username=test-username-2",
  //            "--from-password=test-password-2");
  //
  //    assertThat(jibCli.getCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getToCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getFromCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getToUsernamePassword())
  //        .hasValue(Credential.from("test-username-1", "test-password-1"));
  //    assertThat(jibCli.getFromUsernamePassword())
  //        .hasValue(Credential.from("test-username-2", "test-password-2"));
  //  }
  //
  //  @Test
  //  public void testParse_toAndFromCredentialHelper() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(),
  //            "--target=test-image-ref",
  //            "--to-credential-helper=to-test-helper",
  //            "--from-credential-helper=from-test-helper");
  //
  //    assertThat(jibCli.getCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getToCredentialHelper()).hasValue("to-test-helper");
  //    assertThat(jibCli.getFromCredentialHelper()).hasValue("from-test-helper");
  //    assertThat(jibCli.getUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getToUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
  //  }
  //
  //  @Test
  //  public void testParse_toUsernamePasswordAndFromCredentialHelper() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(),
  //            "--target=test-image-ref",
  //            "--to-username=test-username",
  //            "--to-password=test-password",
  //            "--from-credential-helper=test-cred-helper");
  //
  //    assertThat(jibCli.getCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getToCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getFromCredentialHelper()).hasValue("test-cred-helper");
  //    assertThat(jibCli.getUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getToUsernamePassword())
  //        .hasValue(Credential.from("test-username", "test-password"));
  //    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
  //  }
  //
  //  @Test
  //  public void testParse_toCredentialHelperAndFromUsernamePassword() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(),
  //            "--target=test-image-ref",
  //            "--to-credential-helper=test-cred-helper",
  //            "--from-username=test-username",
  //            "--from-password=test-password");
  //
  //    assertThat(jibCli.getCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getToCredentialHelper()).hasValue("test-cred-helper");
  //    assertThat(jibCli.getFromCredentialHelper()).isEmpty();
  //    assertThat(jibCli.getUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getToUsernamePassword()).isEmpty();
  //    assertThat(jibCli.getFromUsernamePassword())
  //        .hasValue(Credential.from("test-username", "test-password"));
  //  }
  //
  //  private Object usernamePasswordPairs() {
  //    return new Object[][] {
  //      {"--username", "--password"},
  //      {"--to-username", "--to-password"},
  //      {"--from-username", "--from-password"}
  //    };
  //  }
  //
  //  @Test
  //  @Parameters(method = "usernamePasswordPairs")
  //  public void testParse_usernameWithoutPassword(String usernameField, String passwordField) {
  //    MissingParameterException mpe =
  //        assertThrows(
  //            MissingParameterException.class,
  //            () ->
  //                CommandLine.populateCommand(
  //                    new JibCli(), "--target", "test-image-ref", usernameField,
  // "test-username"));
  //    assertThat(mpe.getMessage()).isEqualTo("Error: Missing required argument(s): " +
  // passwordField);
  //  }
  //
  //  @Test
  //  @Parameters(method = "usernamePasswordPairs")
  //  public void testParse_passwordWithoutUsername(String usernameField, String passwordField) {
  //    MissingParameterException mpe =
  //        assertThrows(
  //            MissingParameterException.class,
  //            () ->
  //                CommandLine.populateCommand(
  //                    new JibCli(), "--target", "test-image-ref", passwordField,
  // "test-password"));
  //    assertThat(mpe.getMessage())
  //        .isEqualTo("Error: Missing required argument(s): " + usernameField + "=<username>");
  //  }
  //
  //  public Object incompatibleCredentialOptions() {
  //    return new Object[] {
  //      new String[] {"--credential-helper=x", "--to-credential-helper=x"},
  //      new String[] {"--credential-helper=x", "--from-credential-helper=x"},
  //      new String[] {"--credential-helper=x", "--username=x", "--password=x"},
  //      new String[] {"--credential-helper=x", "--from-username=x", "--from-password=x"},
  //      new String[] {"--credential-helper=x", "--to-username=x", "--to-password=x"},
  //      new String[] {"--username=x", "--password=x", "--from-username=x", "--from-password=x"},
  //      new String[] {"--username=x", "--password=x", "--to-username=x", "--to-password=x"},
  //      new String[] {"--username=x", "--password=x", "--to-credential-helper=x"},
  //      new String[] {"--username=x", "--password=x", "--from-credential-helper=x"},
  //      new String[] {"--from-credential-helper=x", "--from-username=x", "--from-password=x"},
  //      new String[] {"--to-credential-helper=x", "--to-password=x", "--to-username=x"},
  //    };
  //  }
  //
  //  @Test
  //  @Parameters(method = "incompatibleCredentialOptions")
  //  public void testParse_incompatibleCredentialOptions(String[] authArgs) {
  //    MutuallyExclusiveArgsException meae =
  //        assertThrows(
  //            MutuallyExclusiveArgsException.class,
  //            () ->
  //                CommandLine.populateCommand(
  //                    new JibCli(), ArrayUtils.add(authArgs, "--target=ignored")));
  //    assertThat(meae)
  //        .hasMessageThat()
  //        .containsMatch("^Error: (--(from-|to-)?credential-helper|\\[--username)");
  //  }
  //
  //  @Test
  //  public void testValidate_nameMissingFail() {
  //    JibCli jibCli = CommandLine.populateCommand(new JibCli(), "--target=tar://sometar.tar");
  //
  //    CommandLine.ParameterException pex =
  //        assertThrows(CommandLine.ParameterException.class, jibCli::validate);
  //    assertThat(pex.getMessage())
  //        .isEqualTo("Missing option: --name must be specified when using --target=tar://....");
  //  }
  //
  //  @Test
  //  public void testValidate_pass() {
  //    JibCli jibCli =
  //        CommandLine.populateCommand(
  //            new JibCli(), "--target=tar://sometar.tar", "--name=test.io/test/test");
  //
  //    jibCli.validate();
  //    // pass
  //  }
}
