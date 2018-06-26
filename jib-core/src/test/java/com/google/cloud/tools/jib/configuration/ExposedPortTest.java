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

package com.google.cloud.tools.jib.configuration;

import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link ExposedPort}. */
public class ExposedPortTest {

  @Test
  public void testFromString() {
    ExposedPort exposedPort = ExposedPort.fromString("100");
    Assert.assertEquals(100, exposedPort.getPort());
    Assert.assertEquals(ExposedPort.Protocol.TCP, exposedPort.getProtocol());

    exposedPort = ExposedPort.fromString("2020/tcp");
    Assert.assertEquals(2020, exposedPort.getPort());
    Assert.assertEquals(ExposedPort.Protocol.TCP, exposedPort.getProtocol());

    exposedPort = ExposedPort.fromString("12345/udp");
    Assert.assertEquals(12345, exposedPort.getPort());
    Assert.assertEquals(ExposedPort.Protocol.UDP, exposedPort.getProtocol());
  }

  @Test
  public void testToString() {
    ExposedPort exposedPort = new ExposedPort(2020, ExposedPort.Protocol.TCP);
    Assert.assertEquals("2020/tcp", exposedPort.toString());

    exposedPort = new ExposedPort(12345, ExposedPort.Protocol.UDP);
    Assert.assertEquals("12345/udp", exposedPort.toString());
  }
}
