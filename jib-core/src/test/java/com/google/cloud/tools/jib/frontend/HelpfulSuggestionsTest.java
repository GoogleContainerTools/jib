/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.frontend;

import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link HelpfulSuggestions}. */
public class HelpfulSuggestionsTest {

  private static final HelpfulSuggestions TEST_HELPFUL_SUGGESTIONS =
      new HelpfulSuggestions(
          "clearCacheCommand",
          "baseImageCredHelperConfiguration",
          registry -> "baseImageAuthConfiguration " + registry,
          "targetImageCredHelperConfiguration",
          registry -> "targetImageAuthConfiguration " + registry);

  @Test
  public void testSuggestions_smoke() {
    Assert.assertEquals(
        "Build image failed, perhaps you should run 'clearCacheCommand' to clear the cache",
        TEST_HELPFUL_SUGGESTIONS.forCacheMetadataCorrupted());
    Assert.assertEquals(
        "Build image failed, perhaps you should make sure your Internet is up and that the registry you are pushing to exists",
        TEST_HELPFUL_SUGGESTIONS.forHttpHostConnect());
    Assert.assertEquals(
        "Build image failed, perhaps you should make sure that the registry you configured exists/is spelled properly",
        TEST_HELPFUL_SUGGESTIONS.forUnknownHost());
    Assert.assertEquals(
        "Build image failed, perhaps you should check that 'cacheDirectory' is not used by another application or set the `useOnlyProjectCache` configuration",
        TEST_HELPFUL_SUGGESTIONS.forCacheDirectoryNotOwned(Paths.get("cacheDirectory")));
    Assert.assertEquals(
        "Build image failed, perhaps you should make sure you have permissions for imageReference",
        TEST_HELPFUL_SUGGESTIONS.forHttpStatusCodeForbidden("imageReference"));
    Assert.assertEquals(
        "Build image failed, perhaps you should set a credential helper name with the configuration 'baseImageCredHelperConfiguration' or baseImageAuthConfiguration registry",
        TEST_HELPFUL_SUGGESTIONS.forNoCredentialHelpersDefinedForBaseImage("registry"));
    Assert.assertEquals(
        "Build image failed, perhaps you should set a credential helper name with the configuration 'targetImageCredHelperConfiguration' or targetImageAuthConfiguration registry",
        TEST_HELPFUL_SUGGESTIONS.forNoCredentialHelpersDefinedForTargetImage("registry"));
    Assert.assertEquals(
        "Build image failed, perhaps you should make sure your credentials for 'registry' are set up correctly",
        TEST_HELPFUL_SUGGESTIONS.forCredentialsNotCorrect("registry"));
    Assert.assertEquals("Build image failed", TEST_HELPFUL_SUGGESTIONS.none());
  }
}
