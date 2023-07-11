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

package com.google.cloud.tools.jib.buildplan;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link UnixPathParser}. */
class UnixPathParserTest {

  @Test
  void testParse() {
    Assert.assertEquals(ImmutableList.of("some", "path"), UnixPathParser.parse("/some/path"));
    Assert.assertEquals(ImmutableList.of("some", "path"), UnixPathParser.parse("some/path/"));
    Assert.assertEquals(ImmutableList.of("some", "path"), UnixPathParser.parse("some///path///"));
    // Windows-style paths are resolved in Unix semantics.
    Assert.assertEquals(
        ImmutableList.of("\\windows\\path"), UnixPathParser.parse("\\windows\\path"));
    Assert.assertEquals(ImmutableList.of("T:\\dir"), UnixPathParser.parse("T:\\dir"));
    Assert.assertEquals(
        ImmutableList.of("T:\\dir", "real", "path"), UnixPathParser.parse("T:\\dir/real/path"));
  }
}
