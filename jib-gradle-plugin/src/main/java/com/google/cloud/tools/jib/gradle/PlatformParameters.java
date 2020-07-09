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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.plugins.common.RawConfiguration.PlatformConfiguration;
import java.util.Optional;
import javax.annotation.Nullable;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/** Configuration of a platform. */
public class PlatformParameters implements PlatformConfiguration {
  @Nullable private String os;
  @Nullable private String architecture;

  @Input
  @Nullable
  public String getOs() {
    return os;
  }

  @Internal
  @Override
  public Optional<String> getOsName() {
    return Optional.ofNullable(os);
  }

  public void setOs(String os) {
    this.os = os;
  }

  @Input
  @Nullable
  public String getArchitecture() {
    return architecture;
  }

  @Internal
  @Override
  public Optional<String> getArchitectureName() {
    return Optional.ofNullable(architecture);
  }

  public void setArchitecture(String architecture) {
    this.architecture = architecture;
  }
}
