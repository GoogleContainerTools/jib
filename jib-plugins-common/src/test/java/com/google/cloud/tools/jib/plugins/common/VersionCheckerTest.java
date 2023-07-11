/*
 * Copyright 2019 Google LLC.
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

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link VersionChecker}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VersionCheckerTest {
  private static class TestVersion implements Comparable<TestVersion> {
    private int[] components;

    TestVersion(String version) {
      String[] parts = version.split("\\.", -1);
      components = new int[parts.length];
      for (int i = 0; i < parts.length; i++) {
        components[i] = Integer.parseInt(parts[i]);
      }
    }

    @Override
    public int compareTo(TestVersion other) {
      for (int i = 0; i < Math.max(components.length, other.components.length); i++) {
        int a = i < components.length ? components[i] : 0;
        int b = i < other.components.length ? other.components[i] : 0;
        if (a < b) {
          return -1;
        } else if (a > b) {
          return 1;
        }
      }
      return 0;
    }
  }

  private VersionChecker<TestVersion> checker;

  @BeforeEach
  void setUpBeforeEach() {
    Assert.assertTrue(new TestVersion("1.0").compareTo(new TestVersion("1.1.1")) < 0);
    Assert.assertTrue(new TestVersion("1.1.1").compareTo(new TestVersion("1.0")) > 0);
    Assert.assertTrue(new TestVersion("1.1").compareTo(new TestVersion("1.1.0.0")) == 0);
    checker = new VersionChecker<>(TestVersion::new);
  }

  @Test
  void testComparators_LT() {
    Assert.assertTrue(VersionChecker.lt(0, 1));
    Assert.assertFalse(VersionChecker.lt(1, 1));
    Assert.assertFalse(VersionChecker.lt(2, 1));
  }

  @Test
  void testComparators_LE() {
    Assert.assertTrue(VersionChecker.le(0, 1));
    Assert.assertTrue(VersionChecker.le(1, 1));
    Assert.assertFalse(VersionChecker.le(2, 1));
  }

  @Test
  void testComparators_GE() {
    Assert.assertFalse(VersionChecker.ge(0, 1));
    Assert.assertTrue(VersionChecker.ge(1, 1));
    Assert.assertTrue(VersionChecker.ge(2, 1));
  }

  @Test
  void testComparators_GT() {
    Assert.assertFalse(VersionChecker.gt(0, 1));
    Assert.assertFalse(VersionChecker.gt(1, 1));
    Assert.assertTrue(VersionChecker.gt(2, 1));
  }

  @Test
  void testRange_leftClosed() {
    Assert.assertFalse(checker.compatibleVersion("[2.3,4.3]", "1.0"));
    Assert.assertFalse(checker.compatibleVersion("[2.3,4.3)", "1.0"));
    Assert.assertFalse(checker.compatibleVersion("[2.3,)", "1.0"));
    Assert.assertFalse(checker.compatibleVersion("[2.3,]", "1.0"));
  }

  @Test
  void testRange_leftClosed_exact() {
    Assert.assertTrue(checker.compatibleVersion("[2.3,4.3]", "2.3"));
    Assert.assertTrue(checker.compatibleVersion("[2.3,4.3)", "2.3"));
    Assert.assertTrue(checker.compatibleVersion("[2.3,)", "2.3"));
    Assert.assertTrue(checker.compatibleVersion("[2.3,]", "2.3"));
  }

  @Test
  void testRange_leftOpen() {
    Assert.assertFalse(checker.compatibleVersion("(2.3,4.3]", "1.0"));
    Assert.assertFalse(checker.compatibleVersion("(2.3,4.3)", "1.0"));
    Assert.assertFalse(checker.compatibleVersion("(2.3,)", "1.0"));
    Assert.assertFalse(checker.compatibleVersion("(2.3,]", "1.0"));
  }

  @Test
  void testRange_leftOpen_exact() {
    Assert.assertFalse(checker.compatibleVersion("(2.3,4.3]", "2.3"));
    Assert.assertFalse(checker.compatibleVersion("(2.3,4.3)", "2.3"));
    Assert.assertFalse(checker.compatibleVersion("(2.3,)", "2.3"));
    Assert.assertFalse(checker.compatibleVersion("(2.3,]", "2.3"));
  }

  @Test
  void testRange_rightClosed() {
    Assert.assertFalse(checker.compatibleVersion("[2.3,4.3]", "5.0"));
    Assert.assertFalse(checker.compatibleVersion("(2.3,4.3]", "5.0"));
    Assert.assertFalse(checker.compatibleVersion("[,4.3]", "5.0"));
    Assert.assertFalse(checker.compatibleVersion("(,4.3]", "5.0"));
  }

  @Test
  void testRange_rightClosed_exact() {
    Assert.assertTrue(checker.compatibleVersion("[2.3,4.3]", "4.3"));
    Assert.assertTrue(checker.compatibleVersion("(2.3,4.3]", "4.3"));
    Assert.assertTrue(checker.compatibleVersion("[,4.3]", "4.3"));
    Assert.assertTrue(checker.compatibleVersion("(,4.3]", "4.3"));
  }

  @Test
  void testRange_between() {
    Assert.assertTrue(checker.compatibleVersion("[2.3,4.3]", "2.4"));
    Assert.assertTrue(checker.compatibleVersion("(2.3,4.3]", "4.2"));
    Assert.assertTrue(checker.compatibleVersion("[2.3,4.3)", "2.4"));
    Assert.assertTrue(checker.compatibleVersion("(2.3,4.3)", "4.2"));
  }

  @Test
  void testRange_rightOpen() {
    Assert.assertFalse(checker.compatibleVersion("[2.3,4.3)", "5.0"));
    Assert.assertFalse(checker.compatibleVersion("(2.3,4.3)", "5.0"));
    Assert.assertFalse(checker.compatibleVersion("[,4.3)", "5.0"));
    Assert.assertFalse(checker.compatibleVersion("(,4.3)", "5.0"));
  }

  @Test
  void testRange_rightOpen_exact() {
    Assert.assertFalse(checker.compatibleVersion("[2.3,4.3)", "4.3"));
    Assert.assertFalse(checker.compatibleVersion("(2.3,4.3)", "4.3"));
    Assert.assertFalse(checker.compatibleVersion("[,4.3)", "4.3"));
    Assert.assertFalse(checker.compatibleVersion("(,4.3)", "4.3"));
  }

  @Test
  void testMinimumBound_low() {
    Assert.assertFalse(checker.compatibleVersion("2.3", "1.0"));
    Assert.assertFalse(checker.compatibleVersion("2.3", "2.2"));
  }

  @Test
  void testMinimumBound_exact() {
    Assert.assertTrue(checker.compatibleVersion("2.3", "2.3"));
  }

  @Test
  void testMinimumBound_high() {
    Assert.assertTrue(checker.compatibleVersion("2.3", "2.4"));
    Assert.assertTrue(checker.compatibleVersion("2.3", "4.0"));
  }

  // @SuppressWarnings({"TryFailThrowable", "AssertionFailureIgnored"})
  @Test
  void testRange_invalid() {
    for (String rangeSpec :
        new String[] {"[]", "[,]", "(,]", "[,)", "(,)", "[1,2,3]", "[1]", "foo", "{,2.3)", ""}) {
      try {
        checker.compatibleVersion(rangeSpec, "1.3");
        Assert.fail("should have thrown an exception for " + rangeSpec);
      } catch (IllegalArgumentException ex) {
        // as expected
      }
    }
  }
}
