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

import com.google.cloud.tools.jib.builder.BuildLogger;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link ExposedPortsParser}. */
@RunWith(MockitoJUnitRunner.class)
public class ExposedPortsParserTest {

  @Mock private BuildLogger mockLogger;

  @Test
  public void testExpandPortList() {
    List<String> goodInputs =
        Arrays.asList("1000", "2000-2003", "3000-3000", "4000/tcp", "5000/udp", "6000-6002/tcp");
    ImmutableList<String> expected =
        new ImmutableList.Builder<String>()
            .add(
                "1000",
                "2000",
                "2001",
                "2002",
                "2003",
                "3000",
                "4000/tcp",
                "5000/udp",
                "6000/tcp",
                "6001/tcp",
                "6002/tcp")
            .build();
    ImmutableList<String> result = ExposedPortsParser.parse(goodInputs, mockLogger);
    Assert.assertEquals(expected, result);

    List<String> badInputs = Arrays.asList("abc", "/udp", "1000/abc", "a100/tcp", "20/udpabc");
    for (String input : badInputs) {
      try {
        ExposedPortsParser.parse(Collections.singletonList(input), mockLogger);
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
      ExposedPortsParser.parse(Collections.singletonList("4002-4000"), mockLogger);
      Assert.fail();
    } catch (NumberFormatException ex) {
      Assert.assertEquals(
          "Invalid port range '4002-4000'; smaller number must come first.", ex.getMessage());
    }

    ExposedPortsParser.parse(Collections.singletonList("0"), mockLogger);
    Mockito.verify(mockLogger).warn("Port number '0' is out of usual range (1-65535).");
    ExposedPortsParser.parse(Collections.singletonList("70000"), mockLogger);
    Mockito.verify(mockLogger).warn("Port number '70000' is out of usual range (1-65535).");
    ExposedPortsParser.parse(Collections.singletonList("0-400"), mockLogger);
    Mockito.verify(mockLogger).warn("Port number '0-400' is out of usual range (1-65535).");
    ExposedPortsParser.parse(Collections.singletonList("1-70000"), mockLogger);
    Mockito.verify(mockLogger).warn("Port number '1-70000' is out of usual range (1-65535).");
  }
}
