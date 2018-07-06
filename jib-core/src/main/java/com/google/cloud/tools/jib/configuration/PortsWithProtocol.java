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

import com.google.common.base.Preconditions;

/** Holds port number and protocol for an exposed port. */
public class PortsWithProtocol {

  /** Represents the protocol portion of the port. */
  public enum Protocol {
    TCP("tcp"),
    UDP("udp");

    private final String stringRepresentation;

    Protocol(String stringRepresentation) {
      this.stringRepresentation = stringRepresentation;
    }

    @Override
    public String toString() {
      return stringRepresentation;
    }
  }

  /**
   * Creates a new {@link PortsWithProtocol} with the specified range and protocol.
   *
   * @param minPort the minimum port number
   * @param maxPort the maximum port number
   * @param protocol the protocol
   * @return the {@link PortsWithProtocol}
   */
  public static PortsWithProtocol forRange(int minPort, int maxPort, Protocol protocol) {
    Preconditions.checkArgument(
        minPort <= maxPort, "minPort must be less than or equal to maxPort in port range");
    return new PortsWithProtocol(minPort, maxPort, protocol);
  }

  /**
   * Creates a new {@link PortsWithProtocol} with the port number and protocol.
   *
   * @param port the port number
   * @param protocol the protocol
   * @return the {@link PortsWithProtocol}
   */
  public static PortsWithProtocol forSingle(int port, Protocol protocol) {
    return new PortsWithProtocol(port, port, protocol);
  }

  private final int minPort;
  private final int maxPort;
  private final Protocol protocol;

  public int getMinPort() {
    return minPort;
  }

  public int getMaxPort() {
    return maxPort;
  }

  public Protocol getProtocol() {
    return protocol;
  }

  private PortsWithProtocol(int minPort, int maxPort, Protocol protocol) {
    this.minPort = minPort;
    this.maxPort = maxPort;
    this.protocol = protocol;
  }
}
