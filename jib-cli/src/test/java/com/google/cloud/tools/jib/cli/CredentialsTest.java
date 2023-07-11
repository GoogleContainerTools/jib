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

package com.google.cloud.tools.jib.cli;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.plugins.common.DefaultCredentialRetrievers;
import java.io.FileNotFoundException;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Rule;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CredentialsTest {

  private static final String[] DEFAULT_ARGS = {"--target=ignored"};
  @Rule public final MockitoRule mockitoJUnit = MockitoJUnit.rule();
  @Mock private DefaultCredentialRetrievers defaultCredentialRetrievers;

  private static Stream<Arguments> paramsToNone() {
    return Stream.of(
        Arguments.of((Object) new String[] {"--from-credential-helper=ignored"}),
        Arguments.of((Object) new String[] {"--from-username=ignored", "--from-password=ignored"}));
  }

  @ParameterizedTest
  @MethodSource("paramsToNone")
  void testGetToCredentialRetriever_none(String[] args) throws FileNotFoundException {
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(new CommonCliOptions(), ArrayUtils.addAll(DEFAULT_ARGS, args));
    Credentials.getToCredentialRetrievers(commonCliOptions, defaultCredentialRetrievers);
    verify(defaultCredentialRetrievers).asList();
    verifyNoMoreInteractions(defaultCredentialRetrievers);
  }

  private static Stream<Arguments> paramsFromNone() {
    return Stream.of(
        Arguments.of((Object) new String[] {"--to-credential-helper=ignored"}),
        Arguments.of((Object) new String[] {"--to-username=ignored", "--to-password=ignored"}));
  }

  @ParameterizedTest
  @MethodSource("paramsFromNone")
  void testGetFromCredentialRetriever_none(String[] args) throws FileNotFoundException {
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(new CommonCliOptions(), ArrayUtils.addAll(DEFAULT_ARGS, args));
    Credentials.getFromCredentialRetrievers(commonCliOptions, defaultCredentialRetrievers);
    verify(defaultCredentialRetrievers).asList();
    verifyNoMoreInteractions(defaultCredentialRetrievers);
  }

  private static Stream<Arguments> paramsToCredHelper() {
    return Stream.of(
        Arguments.of((Object) new String[] {"--credential-helper=abc"}),
        Arguments.of((Object) new String[] {"--to-credential-helper=abc"}),
        Arguments.of(
            (Object)
                new String[] {"--to-credential-helper=abc", "--from-credential-helper=ignored"}),
        Arguments.of(
            (Object)
                new String[] {
                  "--to-credential-helper=abc", "--from-username=ignored", "--from-password=ignored"
                }));
  }

  @ParameterizedTest
  @MethodSource("paramsToCredHelper")
  void testGetToCredentialRetriever_credHelper(String[] args) throws FileNotFoundException {
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(new CommonCliOptions(), ArrayUtils.addAll(DEFAULT_ARGS, args));
    Credentials.getToCredentialRetrievers(commonCliOptions, defaultCredentialRetrievers);
    verify(defaultCredentialRetrievers).setCredentialHelper("abc");
    verify(defaultCredentialRetrievers).asList();
    verifyNoMoreInteractions(defaultCredentialRetrievers);
  }

  private static Stream<Arguments> paramsFromCredHelper() {
    return Stream.of(
        Arguments.of((Object) new String[] {"--credential-helper=abc"}),
        Arguments.of((Object) new String[] {"--from-credential-helper=abc"}),
        Arguments.of(
            (Object)
                new String[] {"--from-credential-helper=abc", "--to-credential-helper=ignored"}),
        Arguments.of(
            (Object)
                new String[] {
                  "--from-credential-helper=abc", "--to-username=ignored", "--to-password=ignored"
                }));
  }

  @ParameterizedTest
  @MethodSource("paramsFromCredHelper")
  void testGetFromCredentialHelper(String[] args) throws FileNotFoundException {
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(new CommonCliOptions(), ArrayUtils.addAll(DEFAULT_ARGS, args));
    Credentials.getFromCredentialRetrievers(commonCliOptions, defaultCredentialRetrievers);
    verify(defaultCredentialRetrievers).setCredentialHelper("abc");
    verify(defaultCredentialRetrievers).asList();
    verifyNoMoreInteractions(defaultCredentialRetrievers);
  }

  public static Stream<Arguments> paramsToUsernamePassword() {
    return Stream.of(
        Arguments.of(
            new Object[] {
              "--username/--password", new String[] {"--username=abc", "--password=xyz"}
            }),
        Arguments.of(
            new Object[] {
              "--to-username/--to-password", new String[] {"--to-username=abc", "--to-password=xyz"}
            }),
        Arguments.of(
            new Object[] {
              "--to-username/--to-password",
              new String[] {
                "--to-username=abc",
                "--to-password=xyz",
                "--from-username=ignored",
                "--from-password=ignored"
              }
            }),
        Arguments.of(
            new Object[] {
              "--to-username/--to-password",
              new String[] {
                "--to-username=abc", "--to-password=xyz", "--from-credential-helper=ignored"
              }
            }));
  }

  @ParameterizedTest
  @MethodSource("paramsToUsernamePassword")
  void testGetToUsernamePassword(String expectedSource, String[] args)
      throws FileNotFoundException {
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(new CommonCliOptions(), ArrayUtils.addAll(DEFAULT_ARGS, args));
    Credentials.getToCredentialRetrievers(commonCliOptions, defaultCredentialRetrievers);
    ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
    verify(defaultCredentialRetrievers)
        .setKnownCredential(captor.capture(), ArgumentMatchers.eq(expectedSource));
    assertThat(captor.getValue()).isEqualTo(Credential.from("abc", "xyz"));
    verify(defaultCredentialRetrievers).asList();
    verifyNoMoreInteractions(defaultCredentialRetrievers);
  }

  public static Stream<Arguments> paramsFromUsernamePassword() {
    return Stream.of(
        Arguments.of(
            new Object[] {
              "--username/--password", new String[] {"--username=abc", "--password=xyz"}
            }),
        Arguments.of(
            new Object[] {
              "--from-username/--from-password",
              new String[] {"--from-username=abc", "--from-password=xyz"}
            }),
        Arguments.of(
            new Object[] {
              "--from-username/--from-password",
              new String[] {
                "--from-username=abc",
                "--from-password=xyz",
                "--to-username=ignored",
                "--to-password=ignored"
              }
            }),
        Arguments.of(
            new Object[] {
              "--from-username/--from-password",
              new String[] {
                "--from-username=abc", "--from-password=xyz", "--to-credential-helper=ignored"
              }
            }));
  }

  @ParameterizedTest
  @MethodSource("paramsFromUsernamePassword")
  void testGetFromUsernamePassword(String expectedSource, String[] args)
      throws FileNotFoundException {
    CommonCliOptions commonCliOptions =
        CommandLine.populateCommand(new CommonCliOptions(), ArrayUtils.addAll(DEFAULT_ARGS, args));
    Credentials.getFromCredentialRetrievers(commonCliOptions, defaultCredentialRetrievers);
    ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
    verify(defaultCredentialRetrievers)
        .setKnownCredential(captor.capture(), ArgumentMatchers.eq(expectedSource));
    assertThat(captor.getValue()).isEqualTo(Credential.from("abc", "xyz"));
    verify(defaultCredentialRetrievers).asList();
    verifyNoMoreInteractions(defaultCredentialRetrievers);
  }
}
