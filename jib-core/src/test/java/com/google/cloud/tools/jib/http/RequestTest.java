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

package com.google.cloud.tools.jib.http;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link Request}. */
class RequestTest {

  @Test
  void testGetHttpTimeout() {
    Request request = Request.builder().build();

    Assert.assertNull(request.getHttpTimeout());
  }

  @Test
  void testSetHttpTimeout() {
    Request request = Request.builder().setHttpTimeout(3000).build();

    Assert.assertEquals(Integer.valueOf(3000), request.getHttpTimeout());
  }
}
