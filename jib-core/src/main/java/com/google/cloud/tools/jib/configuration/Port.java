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

package com.google.cloud.tools.jib.configuration;

import java.util.Objects;

/** Represents a port number with a protocol (TCP or UDP). */
public class Port {

  /** Represents the protocol for the port. */
  private enum Protocol {
    TCP("tcp"),
    UDP("udp");

    private final String stringRepresentation;

    Protocol(String stringRepresentation) {
      this.stringRepresentation = stringRepresentation;
    }
  }

  /**
   * Create a new {@link Port} with TCP protocol.
   *
   * @param port the port number
   * @return the new {@link Port}
   */
  public static Port tcp(int port) {
    return new Port(port, Protocol.TCP);
  }

  /**
   * Create a new {@link Port} with UDP protocol.
   *
   * @param port the port number
   * @return the new {@link Port}
   */
  public static Port udp(int port) {
    return new Port(port, Protocol.UDP);
  }

  /**
   * Gets a {@link Port} with protocol parsed from the string form {@code protocolString}. Unknown
   * protocols will default to TCP.
   *
   * @param port the port number
   * @param protocolString the case insensitive string (e.g. "tcp", "udp")
   * @return the {@link Port}
   */
  public static Port parseProtocol(int port, String protocolString) {
    Protocol protocol =
        Protocol.UDP.toString().equalsIgnoreCase(protocolString) ? Protocol.UDP : Protocol.TCP;
    return new Port(port, protocol);
  }

  private final int port;
  private final String protocol;

  private Port(int port, Protocol protocol) {
    this.port = port;
    this.protocol = protocol.stringRepresentation;
  }

  /**
   * Gets the port number.
   *
   * @return the port number
   */
  public int getPort() {
    return port;
  }

  /**
   * Gets the protocol.
   *
   * @return the protocol
   */
  public String getProtocol() {
    return protocol;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Port)) {
      return false;
    }
    Port otherPort = (Port) other;
    return port == otherPort.port && protocol.equals(otherPort.protocol);
  }

  @Override
  public int hashCode() {
    return Objects.hash(port, protocol);
  }

  /**
   * Stringifies the port with protocol, in the form {@code <port>/<protocol>}. For example: {@code
   * 1337/TCP}.
   *
   * @return the string form of the port with protocol
   */
  @Override
  public String toString() {
    return port + "/" + protocol;
  }
}
