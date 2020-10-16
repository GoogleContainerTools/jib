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
import java.util.Arrays;
import java.util.Collection;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import picocli.CommandLine;

public class CredentialsTest {

  private static final String[] DEFAULT_ARGS = {"--target=ignored"};

  @RunWith(Parameterized.class)
  public static class GetToCredentialRetrieversNone {
    @Rule public MockitoRule mockitoJUnit = MockitoJUnit.rule();
    @Mock private DefaultCredentialRetrievers defaultCredentialRetrievers;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {new String[] {"--from-credential-helper=ignored"}},
            {new String[] {"--from-username=ignored", "--from-password=ignored"}},
          });
    }

    @Parameterized.Parameter(0)
    public String[] args;

    @Test
    public void testGetToCredentialRetriever() throws FileNotFoundException {
      JibCli buildOptions =
          CommandLine.populateCommand(new JibCli(), ArrayUtils.addAll(DEFAULT_ARGS, args));
      Credentials.getToCredentialRetrievers(buildOptions, defaultCredentialRetrievers);
      Mockito.verify(defaultCredentialRetrievers).asList();
      Mockito.verifyNoMoreInteractions(defaultCredentialRetrievers);
    }
  }

  @RunWith(Parameterized.class)
  public static class GetFromCredentialRetrieversNone {
    @Rule public MockitoRule mockitoJUnit = MockitoJUnit.rule();
    @Mock private DefaultCredentialRetrievers defaultCredentialRetrievers;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {new String[] {"--to-credential-helper=ignored"}},
            {new String[] {"--to-username=ignored", "--to-password=ignored"}},
          });
    }

    @Parameterized.Parameter(0)
    public String[] args;

    @Test
    public void testGetFromCredentialRetriever() throws FileNotFoundException {
      JibCli buildOptions =
          CommandLine.populateCommand(new JibCli(), ArrayUtils.addAll(DEFAULT_ARGS, args));
      Credentials.getFromCredentialRetrievers(buildOptions, defaultCredentialRetrievers);
      Mockito.verify(defaultCredentialRetrievers).asList();
      Mockito.verifyNoMoreInteractions(defaultCredentialRetrievers);
    }
  }

  @RunWith(Parameterized.class)
  public static class GetToCredentialRetrieversCredentialHelpers {
    @Rule public MockitoRule mockitoJUnit = MockitoJUnit.rule();
    @Mock private DefaultCredentialRetrievers defaultCredentialRetrievers;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {new String[] {"--credential-helper=abc"}},
            {new String[] {"--to-credential-helper=abc"}},
            {new String[] {"--to-credential-helper=abc", "--from-credential-helper=ignored"}},
            {
              new String[] {
                "--to-credential-helper=abc", "--from-username=ignored", "--from-password=ignored"
              }
            },
          });
    }

    @Parameterized.Parameter public String[] args;

    @Test
    public void testGetToCredentialRetriever() throws FileNotFoundException {
      JibCli buildOptions =
          CommandLine.populateCommand(new JibCli(), ArrayUtils.addAll(DEFAULT_ARGS, args));
      Credentials.getToCredentialRetrievers(buildOptions, defaultCredentialRetrievers);
      Mockito.verify(defaultCredentialRetrievers).setCredentialHelper("abc");
      Mockito.verify(defaultCredentialRetrievers).asList();
      Mockito.verifyNoMoreInteractions(defaultCredentialRetrievers);
    }
  }

  @RunWith(Parameterized.class)
  public static class GetFromCredentialRetrieverCredentialHelper {
    @Rule public MockitoRule mockitoJUnit = MockitoJUnit.rule();
    @Mock private DefaultCredentialRetrievers defaultCredentialRetrievers;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {new String[] {"--credential-helper=abc"}},
            {new String[] {"--from-credential-helper=abc"}},
            {new String[] {"--from-credential-helper=abc", "--to-credential-helper=ignored"}},
            {
              new String[] {
                "--from-credential-helper=abc", "--to-username=ignored", "--to-password=ignored"
              }
            },
          });
    }

    @Parameterized.Parameter public String[] args;

    @Test
    public void testGetFromCredentialRetriever() throws FileNotFoundException {
      JibCli buildOptions =
          CommandLine.populateCommand(new JibCli(), ArrayUtils.addAll(DEFAULT_ARGS, args));
      Credentials.getFromCredentialRetrievers(buildOptions, defaultCredentialRetrievers);
      Mockito.verify(defaultCredentialRetrievers).setCredentialHelper("abc");
      Mockito.verify(defaultCredentialRetrievers).asList();
      Mockito.verifyNoMoreInteractions(defaultCredentialRetrievers);
    }
  }

  @RunWith(Parameterized.class)
  public static class GetToCredentialRetrieverUsernamePassword {
    @Rule public MockitoRule mockitoJUnit = MockitoJUnit.rule();
    @Mock private DefaultCredentialRetrievers defaultCredentialRetrievers;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
            {"--username/--password", new String[] {"--username=abc", "--password=xyz"}},
            {
              "--to-username/--to-password", new String[] {"--to-username=abc", "--to-password=xyz"}
            },
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
              new String[] {
                "--to-username=abc", "--to-password=xyz", "--from-credential-helper=ignored"
              }
            },
          });
    }

    @Parameterized.Parameter(0)
    public String expectedSource;

    @Parameterized.Parameter(1)
    public String[] args;

    @Test
    public void testGetToCredentialRetriever() throws FileNotFoundException {
      JibCli buildOptions =
          CommandLine.populateCommand(new JibCli(), ArrayUtils.addAll(DEFAULT_ARGS, args));
      Credentials.getToCredentialRetrievers(buildOptions, defaultCredentialRetrievers);
      ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
      Mockito.verify(defaultCredentialRetrievers)
          .setKnownCredential(captor.capture(), ArgumentMatchers.eq(expectedSource));
      assertThat(captor.getValue()).isEqualTo(Credential.from("abc", "xyz"));
      Mockito.verify(defaultCredentialRetrievers).asList();
      Mockito.verifyNoMoreInteractions(defaultCredentialRetrievers);
    }
  }

  @RunWith(Parameterized.class)
  public static class GetFromCredentialRetrieverUsernamePassword {
    @Rule public MockitoRule mockitoJUnit = MockitoJUnit.rule();
    @Mock private DefaultCredentialRetrievers defaultCredentialRetrievers;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
      return Arrays.asList(
          new Object[][] {
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
          });
    }

    @Parameterized.Parameter(0)
    public String expectedSource;

    @Parameterized.Parameter(1)
    public String[] args;

    @Test
    public void testGetFromCredentialRetriever() throws FileNotFoundException {
      JibCli buildOptions =
          CommandLine.populateCommand(new JibCli(), ArrayUtils.addAll(DEFAULT_ARGS, args));
      Credentials.getFromCredentialRetrievers(buildOptions, defaultCredentialRetrievers);
      ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
      Mockito.verify(defaultCredentialRetrievers)
          .setKnownCredential(captor.capture(), ArgumentMatchers.eq(expectedSource));
      assertThat(captor.getValue()).isEqualTo(Credential.from("abc", "xyz"));
      Mockito.verify(defaultCredentialRetrievers).asList();
      Mockito.verifyNoMoreInteractions(defaultCredentialRetrievers);
    }
  }
}
