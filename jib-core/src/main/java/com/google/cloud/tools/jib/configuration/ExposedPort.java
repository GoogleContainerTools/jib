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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.util.List;

/** Holds port number and protocol for an exposed port. */
public class ExposedPort {

  /** Represents the protocol portion of the port. */
  enum Protocol {
    TCP,
    UDP
  }

  private int port;
  private Protocol protocol;

  /**
   * @param configurationString the string to parse, in the format "port[/protocol]". Defaults to
   *     TCP if protocol is not specified.
   * @return an {@link ExposedPort} with values parsed from the string
   */
  public static ExposedPort fromString(String configurationString) {
    List<String> parts = Splitter.on('/').splitToList(configurationString);
    int port = Integer.parseInt(parts.get(0));
    if (parts.size() > 1) {
      return new ExposedPort(port, Enum.valueOf(Protocol.class, parts.get(1).toUpperCase()));
    } else {
      return new ExposedPort(port, Protocol.TCP);
    }
  }

  public int getPort() {
    return port;
  }

  public Protocol getProtocol() {
    return protocol;
  }

  /** @return the {@link ExposedPort} in string form "port/protocol" */
  @Override
  public String toString() {
    return port + "/" + protocol.toString().toLowerCase();
  }

  @VisibleForTesting
  ExposedPort(int port, Protocol protocol) {
    this.port = port;
    this.protocol = protocol;
  }
}
