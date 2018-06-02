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

package com.google.cloud.tools.jib.regex;

import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link RegexBuilder}. */
public class RegexBuilderTest extends RegexBuilder {

  @Test
  public void test_grouping() {
    Assert.assertEquals("(INSIDE)", match("INSIDE"));
    Assert.assertEquals("(?:FIRSTSECOND)", group("FIRST", "SECOND"));
  }

  @Test
  public void test_characterClasses() {
    Assert.assertEquals("[ab]", chars('a', 'b'));
    Assert.assertEquals("[\\wab]", wordChars('a', 'b'));
    Assert.assertEquals("[a-zA-Z\\dab]", alphanum('a', 'b'));
  }

  @Test
  public void test_quantifiers() {
    Assert.assertEquals("INSIDE*", any("INSIDE"));
    Assert.assertEquals("INSIDE?", optional("INSIDE"));
    Assert.assertEquals("INSIDE+", repeated("INSIDE"));
    Assert.assertEquals("INSIDE{0,100}", range("INSIDE", 0, 100));
  }

  @Test
  public void test_logical() {
    Assert.assertEquals("FIRSTSECONDTHIRD", sequence("FIRST", "SECOND", "THIRD"));
    Assert.assertEquals("FIRST|SECOND|THIRD", or("FIRST", "SECOND", "THIRD"));
  }

  @Test
  public void test_literals() {
    Assert.assertEquals("^\\.a$", sequence(begin(), literal('.'), literal('a'), end()));
  }
}
