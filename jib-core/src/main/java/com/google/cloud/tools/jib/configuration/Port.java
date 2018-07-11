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

import java.util.Objects;

/** Holds port number and protocol for an exposed port. */
public class Port {

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

  private final int port;
  private final Protocol protocol;

  public Port(int port, Protocol protocol) {
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
    if (other == null || !(other instanceof Port)) {
      return false;
    }
    Port otherPort = (Port) other;
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
