/*
 * Copyright 2020 Google LLC.
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

import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/** Represents an image platform (for example, "amd64/linux"). */
@Immutable
public class Platform {
  private final String architecture;
  private final String os;

  public Platform(String architecture, String os) {
    this.architecture = architecture;
    this.os = os;
  }

  public String getArchitecture() {
    return architecture;
  }

  public String getOs() {
    return os;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Platform)) {
      return false;
    }
    Platform otherPlatform = (Platform) other;
    return architecture.equals(otherPlatform.getArchitecture()) && os.equals(otherPlatform.getOs());
  }

  @Override
  public int hashCode() {
    return Objects.hash(architecture, os);
  }
}
