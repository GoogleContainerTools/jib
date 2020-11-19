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

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import java.io.FileNotFoundException;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import picocli.CommandLine;

@RunWith(JUnitParamsRunner.class)
public class CredentialsForJarTest {

  private static final String[] DEFAULT_ARGS = {"--target=ignored", "ignore-jar"};
  @Rule public final MockitoRule mockitoJUnit = MockitoJUnit.rule();
  @Mock private DefaultCredentialRetrievers defaultCredentialRetrievers;

  private Object paramsToNone() {
    return new Object[] {
      new String[] {"--from-credential-helper=ignored"},
      new String[] {"--from-username=ignored", "--from-password=ignored"},
    };
  }

  @Test
  @Parameters(method = "paramsToNone")
  public void testGetToCredentialRetriever_none(String[] args) throws FileNotFoundException {
    Jar jarCommand =
        new CommandLine(new JibCli())
            .getSubcommands()
            .get("jar")
            .parseArgs(ArrayUtils.addAll(DEFAULT_ARGS, args))
            .asCommandLineList()
            .get(0)
            .getCommand();
    Credentials.getToCredentialRetrievers(jarCommand.commonCliOptions, defaultCredentialRetrievers);
    Mockito.verify(defaultCredentialRetrievers).asList();
    Mockito.verifyNoMoreInteractions(defaultCredentialRetrievers);
  }

  private Object paramsFromNone() {
    return new Object[] {
      new String[] {"--to-credential-helper=ignored"},
      new String[] {"--to-username=ignored", "--to-password=ignored"},
    };
  }

  @Test
  @Parameters(method = "paramsFromNone")
  public void testGetFromCredentialRetriever_none(String[] args) throws FileNotFoundException {
    Jar jarCommand =
        new CommandLine(new JibCli())
            .getSubcommands()
            .get("jar")
            .parseArgs(ArrayUtils.addAll(DEFAULT_ARGS, args))
            .asCommandLineList()
            .get(0)
            .getCommand();

    Credentials.getFromCredentialRetrievers(
        jarCommand.commonCliOptions, defaultCredentialRetrievers);
    Mockito.verify(defaultCredentialRetrievers).asList();
    Mockito.verifyNoMoreInteractions(defaultCredentialRetrievers);
  }

  private Object paramsToCredHelper() {
    return new Object[] {
      new String[] {"--credential-helper=abc"},
      new String[] {"--to-credential-helper=abc"},
      new String[] {"--to-credential-helper=abc", "--from-credential-helper=ignored"},
      new String[] {
        "--to-credential-helper=abc", "--from-username=ignored", "--from-password=ignored"
      },
    };
  }

  @Test
  @Parameters(method = "paramsToCredHelper")
  public void testGetToCredentialRetriever_credHelper(String[] args) throws FileNotFoundException {
    Jar jarCommand =
        new CommandLine(new JibCli())
            .getSubcommands()
            .get("jar")
            .parseArgs(ArrayUtils.addAll(DEFAULT_ARGS, args))
            .asCommandLineList()
            .get(0)
            .getCommand();
    Credentials.getToCredentialRetrievers(jarCommand.commonCliOptions, defaultCredentialRetrievers);
    Mockito.verify(defaultCredentialRetrievers).setCredentialHelper("abc");
    Mockito.verify(defaultCredentialRetrievers).asList();
    Mockito.verifyNoMoreInteractions(defaultCredentialRetrievers);
  }

  private Object paramsFromCredHelper() {
    return new Object[] {
      new String[] {"--credential-helper=abc"},
      new String[] {"--from-credential-helper=abc"},
      new String[] {"--from-credential-helper=abc", "--to-credential-helper=ignored"},
      new String[] {
        "--from-credential-helper=abc", "--to-username=ignored", "--to-password=ignored"
      },
    };
  }

  @Test
  @Parameters(method = "paramsFromCredHelper")
  public void testGetFromCredentialHelper(String[] args) throws FileNotFoundException {
    Jar jarCommand =
        new CommandLine(new JibCli())
            .getSubcommands()
            .get("jar")
            .parseArgs(ArrayUtils.addAll(DEFAULT_ARGS, args))
            .asCommandLineList()
            .get(0)
            .getCommand();

    Credentials.getFromCredentialRetrievers(
        jarCommand.commonCliOptions, defaultCredentialRetrievers);
    Mockito.verify(defaultCredentialRetrievers).setCredentialHelper("abc");
    Mockito.verify(defaultCredentialRetrievers).asList();
    Mockito.verifyNoMoreInteractions(defaultCredentialRetrievers);
  }

  public Object paramsToUsernamePassword() {
    return new Object[][] {
      {"--username/--password", new String[] {"--username=abc", "--password=xyz"}},
      {"--to-username/--to-password", new String[] {"--to-username=abc", "--to-password=xyz"}},
      {
        "--to-username/--to-password",
        new String[] {
          "--to-username=abc",
          "--to-password=xyz",
          "--from-username=ignored",
          "--from-password=ignored"
        }
      },
      {
        "--to-username/--to-password",
        new String[] {"--to-username=abc", "--to-password=xyz", "--from-credential-helper=ignored"}
      }
    };
  }

  @Test
  @Parameters(method = "paramsToUsernamePassword")
  public void testGetToUsernamePassword(String expectedSource, String[] args)
      throws FileNotFoundException {
    Jar jarCommand =
        new CommandLine(new JibCli())
            .getSubcommands()
            .get("jar")
            .parseArgs(ArrayUtils.addAll(DEFAULT_ARGS, args))
            .asCommandLineList()
            .get(0)
            .getCommand();

    Credentials.getToCredentialRetrievers(jarCommand.commonCliOptions, defaultCredentialRetrievers);
    ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
    Mockito.verify(defaultCredentialRetrievers)
        .setKnownCredential(captor.capture(), ArgumentMatchers.eq(expectedSource));
    assertThat(captor.getValue()).isEqualTo(Credential.from("abc", "xyz"));
    Mockito.verify(defaultCredentialRetrievers).asList();
    Mockito.verifyNoMoreInteractions(defaultCredentialRetrievers);
  }

  public Object paramsFromUsernamePassword() {
    return new Object[][] {
      {"--username/--password", new String[] {"--username=abc", "--password=xyz"}},
      {
        "--from-username/--from-password",
        new String[] {"--from-username=abc", "--from-password=xyz"}
      },
      {
        "--from-username/--from-password",
        new String[] {
          "--from-username=abc",
          "--from-password=xyz",
          "--to-username=ignored",
          "--to-password=ignored"
        }
      },
      {
        "--from-username/--from-password",
        new String[] {
          "--from-username=abc", "--from-password=xyz", "--to-credential-helper=ignored"
        }
      },
    };
  }

  @Test
  @Parameters(method = "paramsFromUsernamePassword")
  public void testGetFromUsernamePassword(String expectedSource, String[] args)
      throws FileNotFoundException {
    Jar jarCommand =
        new CommandLine(new JibCli())
            .getSubcommands()
            .get("jar")
            .parseArgs(ArrayUtils.addAll(DEFAULT_ARGS, args))
            .asCommandLineList()
            .get(0)
            .getCommand();

    Credentials.getFromCredentialRetrievers(
        jarCommand.commonCliOptions, defaultCredentialRetrievers);
    ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
    Mockito.verify(defaultCredentialRetrievers)
        .setKnownCredential(captor.capture(), ArgumentMatchers.eq(expectedSource));
    assertThat(captor.getValue()).isEqualTo(Credential.from("abc", "xyz"));
    Mockito.verify(defaultCredentialRetrievers).asList();
    Mockito.verifyNoMoreInteractions(defaultCredentialRetrievers);
  }
}
