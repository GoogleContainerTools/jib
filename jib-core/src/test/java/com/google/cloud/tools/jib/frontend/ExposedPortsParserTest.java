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

package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.JibLogger;
import com.google.cloud.tools.jib.configuration.Port;
import com.google.cloud.tools.jib.configuration.Port.Protocol;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ExposedPortsParser}. */
@RunWith(MockitoJUnitRunner.class)
public class ExposedPortsParserTest {

  @Mock private JibLogger mockLogger;

  @Test
  public void testParse() {
    List<String> goodInputs =
        Arrays.asList("1000", "2000-2003", "3000-3000", "4000/tcp", "5000/udp", "6000-6002/udp");
    ImmutableList<Port> expected =
        new ImmutableList.Builder<Port>()
            .add(
                new Port(1000, Protocol.TCP),
                new Port(2000, Protocol.TCP),
                new Port(2001, Protocol.TCP),
                new Port(2002, Protocol.TCP),
                new Port(2003, Protocol.TCP),
                new Port(3000, Protocol.TCP),
                new Port(4000, Protocol.TCP),
                new Port(5000, Protocol.UDP),
                new Port(6000, Protocol.UDP),
                new Port(6001, Protocol.UDP),
                new Port(6002, Protocol.UDP))
            .build();
    ImmutableList<Port> result = ExposedPortsParser.parse(goodInputs);
    Assert.assertEquals(expected, result);

    List<String> badInputs = Arrays.asList("abc", "/udp", "1000/abc", "a100/tcp", "20/udpabc");
    for (String input : badInputs) {
      try {
        ExposedPortsParser.parse(Collections.singletonList(input));
        Assert.fail();
      } catch (NumberFormatException ex) {
        Assert.assertEquals(
            "Invalid port configuration: '"
                + input
                + "'. Make sure the port is a single number or a range of two numbers separated "
                + "with a '-', with or without protocol specified (e.g. '<portNum>/tcp' or "
                + "'<portNum>/udp').",
            ex.getMessage());
      }
    }

    try {
      ExposedPortsParser.parse(Collections.singletonList("4002-4000"));
      Assert.fail();
    } catch (NumberFormatException ex) {
      Assert.assertEquals(
          "Invalid port range '4002-4000'; smaller number must come first.", ex.getMessage());
    }

    badInputs = Arrays.asList("0", "70000", "0-400", "1-70000");
    for (String input : badInputs) {
      try {
        ExposedPortsParser.parse(Collections.singletonList(input));
        Assert.fail();
      } catch (NumberFormatException ex) {
        Assert.assertEquals(
            "Port number '" + input + "' is out of usual range (1-65535).", ex.getMessage());
      }
    }
  }
}
