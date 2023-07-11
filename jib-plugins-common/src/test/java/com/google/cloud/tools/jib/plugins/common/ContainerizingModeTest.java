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
import org.junit.jupiter.api.Test;

/** Tests for {@link ContainerizingMode}. */
class ContainerizingModeTest {

  @Test
  void testFrom_validValues() throws InvalidContainerizingModeException {
    Assert.assertEquals(ContainerizingMode.EXPLODED, ContainerizingMode.from("exploded"));
    Assert.assertEquals(ContainerizingMode.PACKAGED, ContainerizingMode.from("packaged"));
  }

  @Test
  void testFrom_invalidCasing() {
    try {
      ContainerizingMode.from("PACKAGED");
      Assert.fail();
    } catch (InvalidContainerizingModeException ex) {
      Assert.assertEquals("PACKAGED", ex.getInvalidContainerizingMode());
      Assert.assertEquals("PACKAGED", ex.getMessage());
    }
  }

  @Test
  void testFrom_invalidValue() {
    try {
      ContainerizingMode.from("this is wrong");
      Assert.fail();
    } catch (InvalidContainerizingModeException ex) {
      Assert.assertEquals("this is wrong", ex.getInvalidContainerizingMode());
      Assert.assertEquals("this is wrong", ex.getMessage());
    }
  }
}
