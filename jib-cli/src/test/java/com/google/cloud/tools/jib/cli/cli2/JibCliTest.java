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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import picocli.CommandLine;
import picocli.CommandLine.MissingParameterException;
import picocli.CommandLine.MutuallyExclusiveArgsException;

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

    assertThat(jibCli.getTargetImage()).isEqualTo("test-image-ref");
    assertThat(jibCli.getUsernamePassword()).isEmpty();
    assertThat(jibCli.getToUsernamePassword()).isEmpty();
    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
    assertThat(jibCli.getCredentialHelpers()).isEmpty();
    assertThat(jibCli.getBuildFile()).isEqualTo(Paths.get("./jib.yaml"));
    assertThat(jibCli.getContextRoot()).isEqualTo(Paths.get("."));
    assertThat(jibCli.getAdditionalTags()).isEmpty();
    assertThat(jibCli.getTemplateParameters()).isEmpty();
    assertThat(jibCli.getApplicationCache()).isEmpty();
    assertThat(jibCli.getBaseImageCache()).isEmpty();
    assertThat(jibCli.isAllowInsecureRegistries()).isFalse();
    assertThat(jibCli.isSendCredentialsOverHttp()).isFalse();
    assertThat(jibCli.getVerbosity()).isEqualTo("lifecycle");
    assertThat(jibCli.isStacktrace()).isFalse();
    assertThat(jibCli.isHttpTrace()).isFalse();
    assertThat(jibCli.isSerialize()).isFalse();
  }

  @Test
  public void testParse_shortFormParams() {
    JibCli jibCli =
        CommandLine.populateCommand(
            new JibCli(),
            "-t",
            "test-image-ref",
            "-c",
            "test-context",
            "-b",
            "test-build-file",
            "-p",
            "param1=value1",
            "-p",
            "param2=value2");

    assertThat(jibCli.getTargetImage()).isEqualTo("test-image-ref");
    assertThat(jibCli.getUsernamePassword()).isEmpty();
    assertThat(jibCli.getToUsernamePassword()).isEmpty();
    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
    assertThat(jibCli.getCredentialHelpers()).isEmpty();
    assertThat(jibCli.getBuildFile()).isEqualTo(Paths.get("test-build-file"));
    assertThat(jibCli.getContextRoot()).isEqualTo(Paths.get("test-context"));
    assertThat(jibCli.getAdditionalTags()).isEmpty();
    assertThat(jibCli.getTemplateParameters())
        .isEqualTo(ImmutableMap.of("param1", "value1", "param2", "value2"));
    assertThat(jibCli.getApplicationCache()).isEmpty();
    assertThat(jibCli.getBaseImageCache()).isEmpty();
    assertThat(jibCli.isAllowInsecureRegistries()).isFalse();
    assertThat(jibCli.isSendCredentialsOverHttp()).isFalse();
    assertThat(jibCli.getVerbosity()).isEqualTo("lifecycle");
    assertThat(jibCli.isStacktrace()).isFalse();
    assertThat(jibCli.isStacktrace()).isFalse();
    assertThat(jibCli.isHttpTrace()).isFalse();
    assertThat(jibCli.isSerialize()).isFalse();
  }

  @Test
  public void testParse_longFormParams() {
    JibCli jibCli =
        CommandLine.populateCommand(
            new JibCli(),
            "--target",
            "test-image-ref",
            "--context",
            "test-context",
            "--build-file",
            "test-build-file",
            "--parameter",
            "param1=value1",
            "--parameter",
            "param2=value2",
            "--credential-helper",
            "helper1",
            "--credential-helper",
            "helper2",
            "--additional-tags",
            "tag1,tag2,tag3",
            "--allow-insecure-registries",
            "--send-credentials-over-http",
            "--application-cache",
            "test-application-cache",
            "--base-image-cache",
            "test-base-image-cache",
            "--verbosity",
            "info",
            "--stacktrace",
            "--http-trace",
            "--serialize");

    assertThat(jibCli.getTargetImage()).isEqualTo("test-image-ref");
    assertThat(jibCli.getUsernamePassword()).isEmpty();
    assertThat(jibCli.getToUsernamePassword()).isEmpty();
    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
    assertThat(jibCli.getCredentialHelpers()).isEqualTo(ImmutableList.of("helper1", "helper2"));
    assertThat(jibCli.getBuildFile()).isEqualTo(Paths.get("test-build-file"));
    assertThat(jibCli.getContextRoot()).isEqualTo(Paths.get("test-context"));
    assertThat(jibCli.getAdditionalTags()).isEqualTo(ImmutableList.of("tag1", "tag2", "tag3"));
    assertThat(jibCli.getTemplateParameters())
        .isEqualTo(ImmutableMap.of("param1", "value1", "param2", "value2"));
    assertThat(jibCli.getApplicationCache()).hasValue(Paths.get("test-application-cache"));
    assertThat(jibCli.getBaseImageCache()).hasValue(Paths.get("test-base-image-cache"));
    assertThat(jibCli.isAllowInsecureRegistries()).isTrue();
    assertThat(jibCli.isSendCredentialsOverHttp()).isTrue();
    assertThat(jibCli.getVerbosity()).isEqualTo("info");
    assertThat(jibCli.isStacktrace()).isTrue();
    assertThat(jibCli.isHttpTrace()).isTrue();
    assertThat(jibCli.isSerialize()).isTrue();
  }

  @Test
  public void testParse_buildFileDefaultForContext() {
    JibCli jibCli =
        CommandLine.populateCommand(
            new JibCli(), "--target", "test-image-ref", "--context", "test-context");

    assertThat(jibCli.getBuildFile()).isEqualTo(Paths.get("test-context/jib.yaml"));
    assertThat(jibCli.getContextRoot()).isEqualTo(Paths.get("test-context"));
  }

  @Test
  public void testParse_usernamePassword() {
    JibCli jibCli =
        CommandLine.populateCommand(
            new JibCli(),
            "--target",
            "test-image-ref",
            "--username",
            "test-username",
            "--password",
            "test-password");

    assertThat(jibCli.getUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
    assertThat(jibCli.getToUsernamePassword()).isEmpty();
    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_toUsernamePassword() {
    JibCli jibCli =
        CommandLine.populateCommand(
            new JibCli(),
            "--target",
            "test-image-ref",
            "--to-username",
            "test-username",
            "--to-password",
            "test-password");

    assertThat(jibCli.getToUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
    assertThat(jibCli.getFromUsernamePassword()).isEmpty();
    assertThat(jibCli.getUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_fromUsernamePassword() {
    JibCli jibCli =
        CommandLine.populateCommand(
            new JibCli(),
            "--target",
            "test-image-ref",
            "--from-username",
            "test-username",
            "--from-password",
            "test-password");

    assertThat(jibCli.getFromUsernamePassword())
        .hasValue(Credential.from("test-username", "test-password"));
    assertThat(jibCli.getToUsernamePassword()).isEmpty();
    assertThat(jibCli.getUsernamePassword()).isEmpty();
  }

  @Test
  public void testParse_toAndFromUsernamePassword() {
    JibCli jibCli =
        CommandLine.populateCommand(
            new JibCli(),
            "--target",
            "test-image-ref",
            "--to-username",
            "test-username-1",
            "--to-password",
            "test-password-1",
            "--from-username",
            "test-username-2",
            "--from-password",
            "test-password-2");

    assertThat(jibCli.getToUsernamePassword())
        .hasValue(Credential.from("test-username-1", "test-password-1"));
    assertThat(jibCli.getFromUsernamePassword())
        .hasValue(Credential.from("test-username-2", "test-password-2"));
    assertThat(jibCli.getUsernamePassword()).isEmpty();
  }

  @RunWith(Parameterized.class)
  public static class UsernamePasswordBothRequired {
    @Parameterized.Parameters(name = "{0},{1}")
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {"--username", "--password"},
            {"--to-username", "--to-password"},
            {"--from-username", "--from-password"}
          });
    }

    @Parameterized.Parameter(0)
    public String usernameField;

    @Parameterized.Parameter(1)
    public String passwordField;

    @Test
    public void testParse_usernameWithoutPassword() {
      MissingParameterException mpe =
          assertThrows(
              MissingParameterException.class,
              () ->
                  CommandLine.populateCommand(
                      new JibCli(), "--target", "test-image-ref", usernameField, "test-username"));
      assertThat(mpe.getMessage())
          .isEqualTo("Error: Missing required argument(s): " + passwordField);
    }

    @Test
    public void testParse_passwordWithoutUsername() {
      MissingParameterException mpe =
          assertThrows(
              MissingParameterException.class,
              () ->
                  CommandLine.populateCommand(
                      new JibCli(), "--target", "test-image-ref", passwordField, "test-password"));
      assertThat(mpe.getMessage())
          .isEqualTo("Error: Missing required argument(s): " + usernameField + "=<username>");
    }
  }

  @RunWith(Parameterized.class)
  public static class UsernamePasswordNotAllowedWithToAndFrom {
    @Parameterized.Parameters(name = "{0},{1}")
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {"--to-username", "--to-password"},
            {"--from-username", "--from-password"}
          });
    }

    @Parameterized.Parameter(0)
    public String usernameField;

    @Parameterized.Parameter(1)
    public String passwordField;

    @Test
    public void testParse_usernameWithoutPassword() {
      MutuallyExclusiveArgsException meae =
          assertThrows(
              MutuallyExclusiveArgsException.class,
              () ->
                  CommandLine.populateCommand(
                      new JibCli(),
                      "--target",
                      "test-image-ref",
                      "--username",
                      "test-username",
                      "--password",
                      "test-password",
                      usernameField,
                      "test-username",
                      passwordField,
                      "test-password"));
      assertThat(meae.getMessage())
          .isEqualTo(
              "Error: [--username=<username> --password[=<password>]] and [[--to-username=<username> --to-password[=<password>]] [--from-username=<username> --from-password[=<password>]]] are mutually exclusive (specify only one)");
    }
  }
}
