/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.LogEvent;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ConfigurationPropertyValidator}. */
@RunWith(MockitoJUnitRunner.class)
public class ConfigurationPropertyValidatorTest {

  @Mock private Consumer<LogEvent> mockLogger;
  @Mock private AuthProperty mockAuth;
  @Mock private RawConfiguration mockConfiguration;

  @Test
  public void testGetImageAuth() {
    when(mockAuth.getUsernameDescriptor()).thenReturn("user");
    when(mockAuth.getPasswordDescriptor()).thenReturn("pass");
    when(mockAuth.getUsername()).thenReturn("vwxyz");
    when(mockAuth.getPassword()).thenReturn("98765");

    // System properties set
    when(mockConfiguration.getProperty("jib.test.auth.user")).thenReturn(Optional.of("abcde"));
    when(mockConfiguration.getProperty("jib.test.auth.pass")).thenReturn(Optional.of("12345"));
    Credential expected = Credential.from("abcde", "12345");
    Optional<Credential> actual =
        ConfigurationPropertyValidator.getImageCredential(
            mockLogger, "jib.test.auth.user", "jib.test.auth.pass", mockAuth, mockConfiguration);
    assertThat(actual).hasValue(expected);

    // Auth set in configuration
    when(mockConfiguration.getProperty("jib.test.auth.user")).thenReturn(Optional.empty());
    when(mockConfiguration.getProperty("jib.test.auth.pass")).thenReturn(Optional.empty());
    expected = Credential.from("vwxyz", "98765");
    actual =
        ConfigurationPropertyValidator.getImageCredential(
            mockLogger, "jib.test.auth.user", "jib.test.auth.pass", mockAuth, mockConfiguration);
    assertThat(actual).hasValue(expected);
    verify(mockLogger, never()).accept(LogEvent.warn(any()));

    // Auth completely missing
    when(mockAuth.getUsername()).thenReturn(null);
    when(mockAuth.getPassword()).thenReturn(null);
    actual =
        ConfigurationPropertyValidator.getImageCredential(
            mockLogger, "jib.test.auth.user", "jib.test.auth.pass", mockAuth, mockConfiguration);
    assertThat(actual).isEmpty();

    // Password missing
    when(mockAuth.getUsername()).thenReturn("vwxyz");
    when(mockAuth.getPassword()).thenReturn(null);
    actual =
        ConfigurationPropertyValidator.getImageCredential(
            mockLogger, "jib.test.auth.user", "jib.test.auth.pass", mockAuth, mockConfiguration);
    assertThat(actual).isEmpty();
    verify(mockLogger)
        .accept(LogEvent.warn("pass is missing from build configuration; ignoring auth section."));

    // Username missing
    when(mockAuth.getUsername()).thenReturn(null);
    when(mockAuth.getPassword()).thenReturn("98765");
    actual =
        ConfigurationPropertyValidator.getImageCredential(
            mockLogger, "jib.test.auth.user", "jib.test.auth.pass", mockAuth, mockConfiguration);
    assertThat(actual).isEmpty();
    verify(mockLogger)
        .accept(LogEvent.warn("user is missing from build configuration; ignoring auth section."));
  }

  @Test
  public void testGetGeneratedTargetDockerTag() throws InvalidImageReferenceException {
    HelpfulSuggestions helpfulSuggestions =
        new HelpfulSuggestions("", "", "to", "--to", "build.txt");

    // Target configured
    ProjectProperties mockProjectProperties = mock(ProjectProperties.class);
    when(mockProjectProperties.getName()).thenReturn("project-name");
    when(mockProjectProperties.getVersion()).thenReturn("project-version");

    ImageReference result =
        ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
            "a/b:c", mockProjectProperties, helpfulSuggestions);
    assertThat(result.getRepository()).isEqualTo("a/b");
    assertThat(result.getTag()).hasValue("c");
    verify(mockLogger, never()).accept(LogEvent.lifecycle(any()));

    // Target not configured
    result =
        ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
            null, mockProjectProperties, helpfulSuggestions);
    assertThat(result.getRepository()).isEqualTo("project-name");
    assertThat(result.getTag()).hasValue("project-version");
    verify(mockProjectProperties)
        .log(
            LogEvent.lifecycle(
                "Tagging image with generated image reference project-name:project-version. If you'd "
                    + "like to specify a different tag, you can set the to parameter in your "
                    + "build.txt, or use the --to=<MY IMAGE> commandline flag."));

    // Generated tag invalid
    when(mockProjectProperties.getName()).thenReturn("%#&///*@(");
    when(mockProjectProperties.getVersion()).thenReturn("%$#//&*@($");
    assertThrows(
        InvalidImageReferenceException.class,
        () ->
            ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
                null, mockProjectProperties, helpfulSuggestions));
  }

  @Test
  public void testParseListProperty() {
    assertThat(ConfigurationPropertyValidator.parseListProperty("abc")).containsExactly("abc");
    assertThat(
            ConfigurationPropertyValidator.parseListProperty(
                "abcd,efg\\,hi\\\\,,\\jkl\\,,\\\\\\,mnop,"))
        .containsExactly("abcd", "efg,hi\\,", "\\jkl,", "\\\\,mnop", "")
        .inOrder();
    assertThat(ConfigurationPropertyValidator.parseListProperty("")).containsExactly("");

    assertThat(
            ConfigurationPropertyValidator.parseListProperty(
                "-Xmx2g,-agentlib:jdwp=transport=dt_socket\\,server=y\\,address=*:5005"))
        .containsExactly("-Xmx2g", "-agentlib:jdwp=transport=dt_socket,server=y,address=*:5005")
        .inOrder();
  }

  @Test
  public void testParseMapProperty() {
    assertThat(ConfigurationPropertyValidator.parseMapProperty("abc=def"))
        .containsExactly("abc", "def");
    assertThat(
            ConfigurationPropertyValidator.parseMapProperty(
                "abc=def,gh\\,i=j\\\\\\,kl,mno=,pqr=stu"))
        .containsExactly("abc", "def", "gh,i", "j\\\\,kl", "mno", "", "pqr", "stu")
        .inOrder();
    assertThrows(
        IllegalArgumentException.class,
        () -> ConfigurationPropertyValidator.parseMapProperty("not valid"));
  }
}
