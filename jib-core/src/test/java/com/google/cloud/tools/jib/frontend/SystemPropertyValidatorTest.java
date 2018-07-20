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

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link SystemPropertyValidator}. */
public class SystemPropertyValidatorTest {

  @After
  public void tearDown() {
    System.clearProperty("jib.httpTimeout");
  }

  @Test
  public void testCheckHttpTimeoutSystemProperty_ok() throws Exception {
    Assert.assertNull(System.getProperty("jib.httpTimeout"));
    SystemPropertyValidator.checkHttpTimeoutProperty(Exception::new);
  }

  @Test
  public void testCheckHttpTimeoutSystemProperty_stringValue() {
    System.setProperty("jib.httpTimeout", "random string");
    try {
      SystemPropertyValidator.checkHttpTimeoutProperty(Exception::new);
      Assert.fail("Should error with a non-integer timeout");
    } catch (Exception ex) {
      Assert.assertEquals("jib.httpTimeout must be an integer: random string", ex.getMessage());
    }
  }

  @Test
  public void testCheckHttpTimeoutSystemProperty_negativeValue() {
    System.setProperty("jib.httpTimeout", "-80");
    try {
      SystemPropertyValidator.checkHttpTimeoutProperty(Exception::new);
      Assert.fail("Should error with a negative timeout");
    } catch (Exception ex) {
      Assert.assertEquals("jib.httpTimeout cannot be negative: -80", ex.getMessage());
    }
  }
}
