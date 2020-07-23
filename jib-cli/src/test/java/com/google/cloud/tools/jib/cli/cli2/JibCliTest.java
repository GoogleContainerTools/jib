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
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import picocli.CommandLine;

public class JibCliTest {
  @Test
  public void testParse_missingRequiredParams() {
    try {
      CommandLine.populateCommand(new JibCli(), "");
      fail();
    } catch (CommandLine.MissingParameterException mpe) {
      assertThat(mpe.getMessage()).isEqualTo("Missing required option: '--target=<target-image>'");
    }
  }

  @Test
  public void testParse_defaults() {
    JibCli jibCli = CommandLine.populateCommand(new JibCli(), "-t", "test-image-ref");

    assertThat(jibCli.targetImage).isEqualTo("test-image-ref");
    assertThat(jibCli.usernamePassword).isNull();
    assertThat(jibCli.credentialHelpers).isEmpty();
    assertThat((Object) jibCli.buildFile).isNull();
    assertThat((Object) jibCli.contextRoot).isEqualTo(Paths.get("."));
    assertThat(jibCli.tags).isEmpty();
    assertThat(jibCli.templateParameters).isEmpty();
    assertThat((Object) jibCli.applicationCache).isNull();
    assertThat((Object) jibCli.baseImageCache).isNull();
    assertThat(jibCli.allowInsecureRegistries).isFalse();
    assertThat(jibCli.sendCredentialsOverHttp).isFalse();
    assertThat(jibCli.verbosity).isEqualTo("lifecycle");
    assertThat(jibCli.stacktrace).isFalse();
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

    assertThat(jibCli.targetImage).isEqualTo("test-image-ref");
    assertThat(jibCli.usernamePassword).isNull();
    assertThat(jibCli.credentialHelpers).isEmpty();
    assertThat((Object) jibCli.buildFile).isEqualTo(Paths.get("test-build-file"));
    assertThat((Object) jibCli.contextRoot).isEqualTo(Paths.get("test-context"));
    assertThat(jibCli.tags).isEmpty();
    assertThat(jibCli.templateParameters)
        .isEqualTo(ImmutableMap.of("param1", "value1", "param2", "value2"));
    assertThat((Object) jibCli.applicationCache).isNull();
    assertThat((Object) jibCli.baseImageCache).isNull();
    assertThat(jibCli.allowInsecureRegistries).isFalse();
    assertThat(jibCli.sendCredentialsOverHttp).isFalse();
    assertThat(jibCli.verbosity).isEqualTo("lifecycle");
    assertThat(jibCli.stacktrace).isFalse();
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
            "--tags",
            "tag1,tag2,tag3",
            "--allow-insecure-registries",
            "--send-credentials-over-http",
            "--application-cache",
            "test-application-cache",
            "--base-image-cache",
            "test-base-image-cache",
            "--verbosity",
            "info",
            "--stacktrace");

    assertThat(jibCli.targetImage).isEqualTo("test-image-ref");
    assertThat(jibCli.usernamePassword).isNull();
    assertThat(jibCli.credentialHelpers).isEqualTo(ImmutableList.of("helper1", "helper2"));
    assertThat((Object) jibCli.buildFile).isEqualTo(Paths.get("test-build-file"));
    assertThat((Object) jibCli.contextRoot).isEqualTo(Paths.get("test-context"));
    assertThat(jibCli.tags).isEqualTo(ImmutableList.of("tag1", "tag2", "tag3"));
    assertThat(jibCli.templateParameters)
        .isEqualTo(ImmutableMap.of("param1", "value1", "param2", "value2"));
    assertThat((Object) jibCli.applicationCache).isEqualTo(Paths.get("test-application-cache"));
    assertThat((Object) jibCli.baseImageCache).isEqualTo(Paths.get("test-base-image-cache"));
    assertThat(jibCli.allowInsecureRegistries).isTrue();
    assertThat(jibCli.sendCredentialsOverHttp).isTrue();
    assertThat(jibCli.verbosity).isEqualTo("info");
    assertThat(jibCli.stacktrace).isTrue();
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

    assertThat(jibCli.usernamePassword.single.username).isEqualTo("test-username");
    assertThat(jibCli.usernamePassword.single.password).isEqualTo("test-password");
    assertThat(jibCli.usernamePassword.multi).isNull();
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

    assertThat(jibCli.usernamePassword.multi.to.username).isEqualTo("test-username");
    assertThat(jibCli.usernamePassword.multi.to.password).isEqualTo("test-password");
    assertThat(jibCli.usernamePassword.multi.from).isNull();
    assertThat(jibCli.usernamePassword.single).isNull();
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

    assertThat(jibCli.usernamePassword.multi.from.username).isEqualTo("test-username");
    assertThat(jibCli.usernamePassword.multi.from.password).isEqualTo("test-password");
    assertThat(jibCli.usernamePassword.multi.to).isNull();
    assertThat(jibCli.usernamePassword.single).isNull();
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

    assertThat(jibCli.usernamePassword.multi.to.username).isEqualTo("test-username-1");
    assertThat(jibCli.usernamePassword.multi.to.password).isEqualTo("test-password-1");
    assertThat(jibCli.usernamePassword.multi.from.username).isEqualTo("test-username-2");
    assertThat(jibCli.usernamePassword.multi.from.password).isEqualTo("test-password-2");
    assertThat(jibCli.usernamePassword.single).isNull();
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
      try {
        CommandLine.populateCommand(
            new JibCli(), "--target", "test-image-ref", usernameField, "test-username");
        fail();
      } catch (CommandLine.MissingParameterException mpe) {
        assertThat(mpe.getMessage())
            .isEqualTo("Error: Missing required argument(s): " + passwordField);
      }
    }

    @Test
    public void testParse_passwordWithoutUsername() {
      try {
        CommandLine.populateCommand(
            new JibCli(), "--target", "test-image-ref", passwordField, "test-password");
        fail();
      } catch (CommandLine.MissingParameterException mpe) {
        assertThat(mpe.getMessage())
            .isEqualTo("Error: Missing required argument(s): " + usernameField + "=<username>");
      }
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
      try {
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
            "test-password");
        fail();
      } catch (CommandLine.MutuallyExclusiveArgsException mpe) {
        assertThat(mpe.getMessage())
            .isEqualTo(
                "Error: [--username=<username> --password[=<password>]] and [[--to-username=<username> --to-password[=<password>]] [--from-username=<username> --from-password[=<password>]]] are mutually exclusive (specify only one)");
      }
    }
  }
}
