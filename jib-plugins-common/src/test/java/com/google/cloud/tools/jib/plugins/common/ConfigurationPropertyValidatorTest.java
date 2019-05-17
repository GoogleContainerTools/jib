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

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ConfigurationPropertyValidator}. */
@RunWith(MockitoJUnitRunner.class)
public class ConfigurationPropertyValidatorTest {

  @Mock private EventHandlers mockEventHandlers;
  @Mock private AuthProperty mockAuth;
  @Mock private ImageReference mockImageReference;
  @Mock private RawConfiguration mockConfiguration;

  @Test
  public void testGetImageAuth() {
    Mockito.when(mockAuth.getUsernameDescriptor()).thenReturn("user");
    Mockito.when(mockAuth.getPasswordDescriptor()).thenReturn("pass");
    Mockito.when(mockAuth.getUsername()).thenReturn("vwxyz");
    Mockito.when(mockAuth.getPassword()).thenReturn("98765");

    // System properties set
    Mockito.when(mockConfiguration.getProperty("jib.test.auth.user"))
        .thenReturn(Optional.of("abcde"));
    Mockito.when(mockConfiguration.getProperty("jib.test.auth.pass"))
        .thenReturn(Optional.of("12345"));
    Credential expected = Credential.from("abcde", "12345");
    Optional<Credential> actual =
        ConfigurationPropertyValidator.getImageCredential(
            mockEventHandlers,
            "jib.test.auth.user",
            "jib.test.auth.pass",
            mockAuth,
            mockConfiguration);
    Assert.assertTrue(actual.isPresent());
    Assert.assertEquals(expected.toString(), actual.get().toString());

    // Auth set in configuration
    Mockito.when(mockConfiguration.getProperty("jib.test.auth.user")).thenReturn(Optional.empty());
    Mockito.when(mockConfiguration.getProperty("jib.test.auth.pass")).thenReturn(Optional.empty());
    expected = Credential.from("vwxyz", "98765");
    actual =
        ConfigurationPropertyValidator.getImageCredential(
            mockEventHandlers,
            "jib.test.auth.user",
            "jib.test.auth.pass",
            mockAuth,
            mockConfiguration);
    Assert.assertTrue(actual.isPresent());
    Assert.assertEquals(expected.toString(), actual.get().toString());
    Mockito.verify(mockEventHandlers, Mockito.never()).dispatch(LogEvent.warn(Mockito.any()));

    // Auth completely missing
    Mockito.when(mockAuth.getUsername()).thenReturn(null);
    Mockito.when(mockAuth.getPassword()).thenReturn(null);
    actual =
        ConfigurationPropertyValidator.getImageCredential(
            mockEventHandlers,
            "jib.test.auth.user",
            "jib.test.auth.pass",
            mockAuth,
            mockConfiguration);
    Assert.assertFalse(actual.isPresent());

    // Password missing
    Mockito.when(mockAuth.getUsername()).thenReturn("vwxyz");
    Mockito.when(mockAuth.getPassword()).thenReturn(null);
    actual =
        ConfigurationPropertyValidator.getImageCredential(
            mockEventHandlers,
            "jib.test.auth.user",
            "jib.test.auth.pass",
            mockAuth,
            mockConfiguration);
    Assert.assertFalse(actual.isPresent());
    Mockito.verify(mockEventHandlers)
        .dispatch(
            LogEvent.warn("pass is missing from build configuration; ignoring auth section."));

    // Username missing
    Mockito.when(mockAuth.getUsername()).thenReturn(null);
    Mockito.when(mockAuth.getPassword()).thenReturn("98765");
    actual =
        ConfigurationPropertyValidator.getImageCredential(
            mockEventHandlers,
            "jib.test.auth.user",
            "jib.test.auth.pass",
            mockAuth,
            mockConfiguration);
    Assert.assertFalse(actual.isPresent());
    Mockito.verify(mockEventHandlers)
        .dispatch(
            LogEvent.warn("user is missing from build configuration; ignoring auth section."));
  }

  @Test
  public void testGetGeneratedTargetDockerTag() throws InvalidImageReferenceException {
    HelpfulSuggestions helpfulSuggestions =
        new HelpfulSuggestions(
            "",
            "",
            mockImageReference,
            false,
            mockImageReference,
            false,
            "to",
            "--to",
            "build.txt");

    // Target configured
    ImageReference result =
        ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
            "a/b:c", mockEventHandlers, "project-name", "project-version", helpfulSuggestions);
    Assert.assertEquals("a/b", result.getRepository());
    Assert.assertEquals("c", result.getTag());
    Mockito.verify(mockEventHandlers, Mockito.never()).dispatch(LogEvent.lifecycle(Mockito.any()));

    // Target not configured
    result =
        ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
            null, mockEventHandlers, "project-name", "project-version", helpfulSuggestions);
    Assert.assertEquals("project-name", result.getRepository());
    Assert.assertEquals("project-version", result.getTag());
    Mockito.verify(mockEventHandlers)
        .dispatch(
            LogEvent.lifecycle(
                "Tagging image with generated image reference project-name:project-version. If you'd "
                    + "like to specify a different tag, you can set the to parameter in your "
                    + "build.txt, or use the --to=<MY IMAGE> commandline flag."));

    // Generated tag invalid
    try {
      ConfigurationPropertyValidator.getGeneratedTargetDockerTag(
          null, mockEventHandlers, "%#&///*@(", "%$#//&*@($", helpfulSuggestions);
      Assert.fail();
    } catch (InvalidImageReferenceException ignored) {
    }
  }

  @Test
  public void testParseListProperty() {
    Assert.assertEquals(
        ImmutableList.of("abc"), ConfigurationPropertyValidator.parseListProperty("abc"));
    Assert.assertEquals(
        ImmutableList.of("abcd", "efg\\,hi\\\\", "", "\\jkl\\,", "\\\\\\,mnop", ""),
        ConfigurationPropertyValidator.parseListProperty(
            "abcd,efg\\,hi\\\\,,\\jkl\\,,\\\\\\,mnop,"));
    Assert.assertEquals(ImmutableList.of(""), ConfigurationPropertyValidator.parseListProperty(""));
  }

  @Test
  public void testParseMapProperty() {
    Assert.assertEquals(
        ImmutableMap.of("abc", "def"), ConfigurationPropertyValidator.parseMapProperty("abc=def"));
    Assert.assertEquals(
        ImmutableMap.of("abc", "def", "gh\\,i", "j\\\\\\,kl", "mno", "", "pqr", "stu"),
        ConfigurationPropertyValidator.parseMapProperty("abc=def,gh\\,i=j\\\\\\,kl,mno=,pqr=stu"));
    try {
      ConfigurationPropertyValidator.parseMapProperty("not valid");
      Assert.fail();
    } catch (IllegalArgumentException ignored) {
      // pass
    }
  }
}
