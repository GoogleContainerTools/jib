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
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link Validator}. */
public class ValidatorTest {

  @Test
  public void testCheckNotNullAndNotEmpty_stringPass() {
    Validator.checkNotNullAndNotEmpty("value", "ignored");
    // pass
  }

  @Test
  public void testCheckNotNullAndNotEmpty_stringFailNull() {
    try {
      Validator.checkNotNullAndNotEmpty((String) null, "test");
      Assert.fail();
    } catch (NullPointerException npe) {
      Assert.assertEquals("Property 'test' cannot be null", npe.getMessage());
    }
  }

  @Test
  public void testCheckNotNullAndNotEmpty_stringFailEmpty() {
    try {
      Validator.checkNotNullAndNotEmpty("  ", "test");
      Assert.fail();
    } catch (IllegalArgumentException iae) {
      Assert.assertEquals("Property 'test' cannot be an empty string", iae.getMessage());
    }
  }

  @Test
  public void testCheckNullOrNotEmpty_valuePass() {
    Validator.checkNullOrNotEmpty("value", "test");
  }

  @Test
  public void testCheckNullOrNotEmpty_nullPass() {
    Validator.checkNullOrNotEmpty(null, "test");
  }

  @Test
  public void testCheckNullOrNotEmpty_fail() {
    try {
      Validator.checkNullOrNotEmpty("   ", "test");
      Assert.fail();
    } catch (IllegalArgumentException iae) {
      Assert.assertEquals("Property 'test' cannot be an empty string", iae.getMessage());
    }
  }

  @Test
  public void testCheckNotEmpty_collectionPass() {
    Validator.checkNotNullAndNotEmpty(ImmutableList.of("value"), "ignored");
    // pass
  }

  @Test
  public void testCheckNotEmpty_collectionFailNull() {
    try {
      Validator.checkNotNullAndNotEmpty((Collection<?>) null, "test");
      Assert.fail();
    } catch (NullPointerException npe) {
      Assert.assertEquals("Property 'test' cannot be null", npe.getMessage());
    }
  }

  @Test
  public void testCheckNotEmpty_collectionFailEmpty() {
    try {
      Validator.checkNotNullAndNotEmpty(ImmutableList.of(), "test");
      Assert.fail();
    } catch (IllegalArgumentException iae) {
      Assert.assertEquals("Property 'test' cannot be an empty collection", iae.getMessage());
    }
  }

  @Test
  public void testCheckNullOrNonNullNonEmptyEntries_nullMapPass() {
    Validator.checkNullOrNonNullNonEmptyEntries((Map<String, String>) null, "test");
    // pass
  }

  @Test
  public void testCheckNullOrNonNullNonEmptyEntries_emptyMapPass() {
    Validator.checkNullOrNonNullNonEmptyEntries(ImmutableMap.of(), "test");
    // pass
  }

  @Test
  public void testCheckNullOrNonNullNonEmptyEntries_mapWithValuesPass() {
    Validator.checkNullOrNonNullNonEmptyEntries(
        ImmutableMap.of("key1", "val1", "key2", "val2"), "test");
    // pass
  }

  @Test
  public void testCheckNullOrNonNullNonEmptyEntries_mapNullKeyFail() {
    try {
      Validator.checkNullOrNonNullNonEmptyEntries(Collections.singletonMap(null, "val1"), "test");
      Assert.fail();
    } catch (NullPointerException npe) {
      Assert.assertEquals("Property 'test' cannot contain null keys", npe.getMessage());
    }
  }

  @Test
  public void testCheckNullOrNonNullNonEmptyEntries_mapEmptyKeyFail() {
    try {
      Validator.checkNullOrNonNullNonEmptyEntries(Collections.singletonMap(" ", "val1"), "test");
      Assert.fail();
    } catch (IllegalArgumentException iae) {
      Assert.assertEquals("Property 'test' cannot contain empty string keys", iae.getMessage());
    }
  }

  @Test
  public void testCheckNullOrNonNullNonEmptyEntries_mapNullValueFail() {
    try {
      Validator.checkNullOrNonNullNonEmptyEntries(Collections.singletonMap("key1", null), "test");
      Assert.fail();
    } catch (NullPointerException npe) {
      Assert.assertEquals("Property 'test' cannot contain null values", npe.getMessage());
    }
  }

  @Test
  public void testCheckNullOrNonNullNonEmptyEntries_mapEmptyValueFail() {
    try {
      Validator.checkNullOrNonNullNonEmptyEntries(Collections.singletonMap("key1", " "), "test");
      Assert.fail();
    } catch (IllegalArgumentException iae) {
      Assert.assertEquals("Property 'test' cannot contain empty string values", iae.getMessage());
    }
  }

  @Test
  public void testCheckNullOrNonNullNonEmptyEntries_nullPass() {
    Validator.checkNullOrNonNullNonEmptyEntries((List<String>) null, "test");
    // pass
  }

  @Test
  public void testCheckNullOrNonNullNonEmptyEntries_emptyPass() {
    Validator.checkNullOrNonNullNonEmptyEntries(ImmutableList.of(), "test");
    // pass
  }

  @Test
  public void testCheckNullNonNullNonEmptyEntries_valuesPass() {
    Validator.checkNullOrNonNullNonEmptyEntries(ImmutableList.of("first", "second"), "test");
    // pass
  }

  @Test
  public void testCheckNullNonNullNonEmptyEntries_nullValueFail() {
    try {
      Validator.checkNullOrNonNullNonEmptyEntries(Arrays.asList("first", null), "test");
      Assert.fail();
    } catch (NullPointerException npe) {
      Assert.assertEquals("Property 'test' cannot contain null entries", npe.getMessage());
    }
  }

  @Test
  public void testCheckNullOrNonNullNonEmptyEntries_emptyValueFail() {
    try {
      Validator.checkNullOrNonNullNonEmptyEntries(ImmutableList.of("first", "  "), "test");
      Assert.fail();
    } catch (IllegalArgumentException iae) {
      Assert.assertEquals("Property 'test' cannot contain empty strings", iae.getMessage());
    }
  }

  @Test
  public void testCheckNullOrNonNullEntries_nullPass() {
    Validator.checkNullOrNonNullEntries(null, "test");
    // pass
  }

  @Test
  public void testCheckNullOrNonNullEntries_emptyPass() {
    Validator.checkNullOrNonNullEntries(ImmutableList.of(), "test");
    // pass
  }

  @Test
  public void testCheckNullOrNonNullEntries_valuesPass() {
    Validator.checkNullOrNonNullEntries(ImmutableList.of(new Object(), new Object()), "test");
    // pass
  }

  @Test
  public void testCheckNullOrNonNullEntries_nullFail() {
    try {
      Validator.checkNullOrNonNullEntries(Arrays.asList(new Object(), null), "test");
      Assert.fail();
    } catch (NullPointerException npe) {
      Assert.assertEquals("Property 'test' cannot contain null entries", npe.getMessage());
    }
    // pass
  }

  @Test
  public void testCheckEquals_pass() {
    Validator.checkEquals("value", "ignored", "value");
    // pass
  }

  @Test
  public void testCheckEquals_failsNull() {
    try {
      Validator.checkEquals(null, "test", "something");
      Assert.fail();
    } catch (NullPointerException npe) {
      Assert.assertEquals("Property 'test' cannot be null", npe.getMessage());
    }
  }

  @Test
  public void testCheckEquals_failsNotEquals() {
    try {
      Validator.checkEquals("somethingElse", "test", "something");
      Assert.fail();
    } catch (IllegalArgumentException iae) {
      Assert.assertEquals(
          "Property 'test' must be 'something' but is 'somethingElse'", iae.getMessage());
    }
  }
}
