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

import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link HelpfulSuggestions}. */
class HelpfulSuggestionsTest {

  private static final HelpfulSuggestions TEST_HELPFUL_SUGGESTIONS =
      new HelpfulSuggestions(
          "messagePrefix", "clearCacheCommand", "toProperty", "toFlag", "buildFile");

  @Test
  void testSuggestions_smoke() {
    Assert.assertEquals(
        "messagePrefix, perhaps you should make sure your Internet is up and that the registry you are pushing to exists",
        TEST_HELPFUL_SUGGESTIONS.forHttpHostConnect());
    Assert.assertEquals(
        "messagePrefix, perhaps you should make sure that the registry you configured exists/is spelled properly",
        TEST_HELPFUL_SUGGESTIONS.forUnknownHost());
    Assert.assertEquals(
        "messagePrefix, perhaps you should run 'clearCacheCommand' to clear your build cache",
        TEST_HELPFUL_SUGGESTIONS.forCacheNeedsClean());
    Assert.assertEquals(
        "messagePrefix, perhaps you should check that 'cacheDirectory' is not used by another application or set the `jib.useOnlyProjectCache` system property",
        TEST_HELPFUL_SUGGESTIONS.forCacheDirectoryNotOwned(Paths.get("cacheDirectory")));
    Assert.assertEquals(
        "messagePrefix, perhaps you should make sure you have permissions for imageReference and set correct credentials. See https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#what-should-i-do-when-the-registry-responds-with-forbidden-or-denied for help",
        TEST_HELPFUL_SUGGESTIONS.forHttpStatusCodeForbidden("imageReference"));
    Assert.assertEquals(
        "messagePrefix, perhaps you should make sure your credentials for 'registry/repository' are set up correctly. See https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#what-should-i-do-when-the-registry-responds-with-unauthorized for help",
        TEST_HELPFUL_SUGGESTIONS.forNoCredentialsDefined("registry/repository"));
    Assert.assertEquals(
        "messagePrefix, perhaps you should add a `mainClass` configuration to plugin",
        HelpfulSuggestions.forMainClassNotFound("messagePrefix", "plugin"));
    Assert.assertEquals(
        "messagePrefix, perhaps you should add a parameter configuration parameter to your buildFile or set the parameter via the commandline (e.g. 'command').",
        HelpfulSuggestions.forToNotConfigured(
            "messagePrefix", "parameter", "buildFile", "command"));
    Assert.assertEquals(
        "Your project is using Java 11 but the base image is for Java 8, perhaps you should "
            + "configure a Java 11-compatible base image using the 'jib.from.image' "
            + "parameter, or set targetCompatibility = 8 or below in your build "
            + "configuration",
        HelpfulSuggestions.forIncompatibleBaseImageJavaVersionForGradle(8, 11));
    Assert.assertEquals(
        "Your project is using Java 11 but the base image is for Java 8, perhaps you should "
            + "configure a Java 11-compatible base image using the '<from><image>' "
            + "parameter, or set maven-compiler-plugin's '<target>' or '<release>' version "
            + "to 8 or below in your build configuration",
        HelpfulSuggestions.forIncompatibleBaseImageJavaVersionForMaven(8, 11));
    Assert.assertEquals(
        "Invalid image reference gcr.io/invalid_REF, perhaps you should check that the reference "
            + "is formatted correctly according to https://docs.docker.com/engine/reference/commandline/tag/#extended-description\n"
            + "For example, slash-separated name components cannot have uppercase letters",
        HelpfulSuggestions.forInvalidImageReference("gcr.io/invalid_REF"));
    Assert.assertEquals("messagePrefix", TEST_HELPFUL_SUGGESTIONS.none());
    Assert.assertEquals(
        "messagePrefix, perhaps you should use a registry that supports HTTPS so credentials can be sent safely, or set the 'sendCredentialsOverHttp' system property to true",
        TEST_HELPFUL_SUGGESTIONS.forCredentialsNotSent());
    Assert.assertEquals(
        "Tagging image with generated image reference project-name:project-version. If you'd like to specify a different tag, you can set the toProperty parameter in your buildFile, or use the toFlag=<MY IMAGE> commandline flag.",
        TEST_HELPFUL_SUGGESTIONS.forGeneratedTag("project-name", "project-version"));
  }
}
