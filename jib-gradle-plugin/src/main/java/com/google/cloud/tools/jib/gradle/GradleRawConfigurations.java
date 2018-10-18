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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.cloud.tools.jib.plugins.common.RawConfigurations;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class GradleRawConfigurations implements RawConfigurations {

  private final JibExtension jibExtension;

  public GradleRawConfigurations(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
  }

  @Nullable
  @Override
  public String getFromImage() {
    return jibExtension.getFrom().getImage();
  }

  @Override
  public AuthProperty getFromAuth() {
    return jibExtension.getFrom().getAuth();
  }

  @Nullable
  @Override
  public String getFromCredHelper() {
    return jibExtension.getFrom().getCredHelper();
  }

  @Nullable
  @Override
  public List<String> getEntrypoint() {
    return jibExtension.getContainer().getEntrypoint();
  }

  @Nullable
  @Override
  public List<String> getProgramArguments() {
    return jibExtension.getContainer().getArgs();
  }

  @Override
  public Map<String, String> getEnvironment() {
    return jibExtension.getContainer().getEnvironment();
  }

  @Override
  public List<String> getPorts() {
    return jibExtension.getContainer().getPorts();
  }

  @Nullable
  @Override
  public String getUser() {
    return jibExtension.getContainer().getUser();
  }

  @Override
  public boolean getUseCurrentTimestamp() {
    return jibExtension.getContainer().getUseCurrentTimestamp();
  }

  @Override
  public List<String> getJvmFlags() {
    return jibExtension.getContainer().getJvmFlags();
  }

  @Nullable
  @Override
  public String getMainClass() {
    return jibExtension.getContainer().getMainClass();
  }

  @Nullable
  @Override
  public String getAppRoot() {
    return jibExtension.getContainer().getAppRoot();
  }

  @Nullable
  @Override
  public AuthProperty getInferredAuth(String authTarget) {
    return null;
  }
}
