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

package com.google.cloud.tools.jib.registry;

import com.google.cloud.tools.jib.api.RegistryAuthenticationFailedException;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link RegistryAuthenticationFailedException}. */
class RegistryAuthenticationFailedExceptionTest {

  @Test
  void testRegistryAuthenticationFailedException_message() {
    RegistryAuthenticationFailedException exception =
        new RegistryAuthenticationFailedException("serverUrl", "imageName", "message");
    Assert.assertEquals("serverUrl", exception.getServerUrl());
    Assert.assertEquals("imageName", exception.getImageName());
    Assert.assertEquals(
        "Failed to authenticate with registry serverUrl/imageName because: message",
        exception.getMessage());
  }

  @Test
  void testRegistryAuthenticationFailedException_exception() {
    Throwable cause = new Throwable("message");
    RegistryAuthenticationFailedException exception =
        new RegistryAuthenticationFailedException("serverUrl", "imageName", cause);
    Assert.assertEquals("serverUrl", exception.getServerUrl());
    Assert.assertEquals("imageName", exception.getImageName());
    Assert.assertSame(cause, exception.getCause());
    Assert.assertEquals(
        "Failed to authenticate with registry serverUrl/imageName because: message",
        exception.getMessage());
  }
}
