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

package com.google.cloud.tools.jib.api.buildplan;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

/** Tests for {@link Port}. */
class PortTest {

  @Test
  void testTcp() {
    Port port = Port.tcp(5555);
    Assert.assertEquals(5555, port.getPort());
    Assert.assertEquals("5555/tcp", port.toString());
  }

  @Test
  void testUdp() {
    Port port = Port.udp(6666);
    Assert.assertEquals(6666, port.getPort());
    Assert.assertEquals("6666/udp", port.toString());
  }

  @Test
  void testParseProtocol() {
    Assert.assertEquals(Port.tcp(1111), Port.parseProtocol(1111, "tcp"));
    Assert.assertEquals(Port.udp(2222), Port.parseProtocol(2222, "udp"));
    Assert.assertEquals(Port.tcp(3333), Port.parseProtocol(3333, ""));
  }
}
