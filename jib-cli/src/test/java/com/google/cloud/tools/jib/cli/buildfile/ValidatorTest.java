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

package com.google.cloud.tools.jib.cli.buildfile;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Validator}. */
public class ValidatorTest {

  @Test
  public void testCheckNotNull_pass() {
    Validator.checkNotNull("value", "ignored");
    // pass
  }

  @Test
  public void testCheckNotNull_fail() {
    try {
      Validator.checkNotNull(null, "test");
      Assert.fail();
    } catch (NullPointerException npe) {
      Assert.assertEquals("Property 'test' cannot be null", npe.getMessage());
    }
  }

  @Test
  public void testCheckNotEmpty_stringPass() {
    Validator.checkNotEmpty("value", "ignored");
    // pass
  }

  @Test
  public void testCheckNotEmpty_stringFail() {
    try {
      Validator.checkNotEmpty("  ", "test");
      Assert.fail();
    } catch (IllegalArgumentException iae) {
      Assert.assertEquals("Property 'test' cannot be empty", iae.getMessage());
    }
  }

  @Test
  public void testCheckNotEmpty_collectionPass() {
    Validator.checkNotEmpty(ImmutableList.of("value"), "ignored");
    // pass
  }

  @Test
  public void testCheckNotEmpty_collectionFail() {
    try {
      Validator.checkNotEmpty(ImmutableList.of(), "test");
      Assert.fail();
    } catch (IllegalArgumentException iae) {
      Assert.assertEquals("Property 'test' cannot be an empty collection", iae.getMessage());
    }
  }

  @Test
  public void testCheckEquals_pass() {
    Validator.checkEquals("value", "ignored", "value");
    // pass
  }

  @Test
  public void testCheckEquals_fails() {
    try {
      Validator.checkEquals("somethingElse", "test", "something");
      Assert.fail();
    } catch (IllegalArgumentException iae) {
      Assert.assertEquals(
          "Property 'test' must be 'something' but is 'somethingElse'", iae.getMessage());
    }
  }
}
