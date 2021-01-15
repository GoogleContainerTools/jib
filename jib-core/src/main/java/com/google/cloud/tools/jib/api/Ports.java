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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.common.base.Strings;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility for parsing Docker/OCI ports from text representations. */
public class Ports {

  /**
   * Pattern used for parsing information out of exposed port configurations.
   *
   * <p>Example matches: 100, 200-210, 1000/tcp, 2000/udp, 500-600/tcp
   */
  private static final Pattern portPattern = Pattern.compile("(\\d+)(?:-(\\d+))?(?:/(tcp|udp))?");

  /**
   * Converts/validates a list of strings representing port ranges to an expanded list of {@link
   * Port}s.
   *
   * <p>For example: ["1000", "2000-2002"] will expand to a list of {@link Port}s with the port
   * numbers [1000, 2000, 2001, 2002]
   *
   * @param ports the list of port numbers/ranges, with an optional protocol separated by a '/'
   *     (defaults to TCP if missing).
   * @return the ports as a list of {@link Port}
   * @throws NumberFormatException if any of the ports are in an invalid format or out of range
   */
  public static Set<Port> parse(List<String> ports) throws NumberFormatException {
    Set<Port> result = new HashSet<>();

    for (String port : ports) {
      Matcher matcher = portPattern.matcher(port);

      if (!matcher.matches()) {
        throw new NumberFormatException(
            "Invalid port configuration: '"
                + port
                + "'. Make sure the port is a single number or a range of two numbers separated "
                + "with a '-', with or without protocol specified (e.g. '<portNum>/tcp' or "
                + "'<portNum>/udp').");
      }

      // Parse protocol
      int min = Integer.parseInt(matcher.group(1));
      int max = min;
      if (!Strings.isNullOrEmpty(matcher.group(2))) {
        max = Integer.parseInt(matcher.group(2));
      }
      String protocol = matcher.group(3);

      // Error if configured as 'max-min' instead of 'min-max'
      if (min > max) {
        throw new NumberFormatException(
            "Invalid port range '" + port + "'; smaller number must come first.");
      }

      // Warn for possibly invalid port numbers
      if (min < 1 || max > 65535) {
        throw new NumberFormatException(
            "Port number '" + port + "' is out of usual range (1-65535).");
      }

      for (int portNumber = min; portNumber <= max; portNumber++) {
        result.add(Port.parseProtocol(portNumber, protocol));
      }
    }

    return result;
  }

  private Ports() {}
}
