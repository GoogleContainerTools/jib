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
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;

/** Holds port number and protocol for an exposed port. */
public class PortWithProtocol {

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
   * Creates a list of {@link PortWithProtocol}s over the specified range with the given protocol.
   *
   * @param minPort the minimum port number
   * @param maxPort the maximum port number
   * @param protocol the protocol
   * @return the list of {@link PortWithProtocol}
   */
  public static List<PortWithProtocol> expandRange(int minPort, int maxPort, Protocol protocol) {
    Preconditions.checkArgument(
        minPort <= maxPort, "minPort must be less than or equal to maxPort in port range");
    ImmutableList.Builder<PortWithProtocol> result = new ImmutableList.Builder<>();
    for (int port = minPort; port <= maxPort; port++) {
      result.add(new PortWithProtocol(port, protocol));
    }
    return result.build();
  }

  private final int port;
  private final Protocol protocol;

  public PortWithProtocol(int port, Protocol protocol) {
    this.port = port;
    this.protocol = protocol;
  }

  public int getPort() {
    return port;
  }

  public Protocol getProtocol() {
    return protocol;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (other == null || !(other instanceof PortWithProtocol)) {
      return false;
    }
    PortWithProtocol otherPort = (PortWithProtocol) other;
    return port == otherPort.port && protocol == otherPort.protocol;
  }

  @Override
  public int hashCode() {
    return Objects.hash(port, protocol);
  }

  @Override
  public String toString() {
    return port + "/" + protocol;
  }
}
