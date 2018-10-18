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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.cloud.tools.jib.plugins.common.InferredAuthRetrievalException;
import com.google.cloud.tools.jib.plugins.common.RawConfigurations;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

class MavenRawConfigurations implements RawConfigurations {

  private final JibPluginConfiguration jibPluginConfiguration;
  private final MavenSettingsServerCredentials mavenSettingsServerCredentials;

  public MavenRawConfigurations(
      JibPluginConfiguration jibPluginConfiguration, EventDispatcher eventDispatcher) {
    Preconditions.checkNotNull(jibPluginConfiguration.getSession());

    this.jibPluginConfiguration = jibPluginConfiguration;
    this.mavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(
            jibPluginConfiguration.getSession().getSettings(),
            jibPluginConfiguration.getSettingsDecrypter(),
            eventDispatcher);
  }

  @Nullable
  @Override
  public String getFromImage() {
    return jibPluginConfiguration.getBaseImage();
  }

  @Override
  public AuthProperty getFromAuth() {
    return jibPluginConfiguration.getBaseImageAuth();
  }

  @Nullable
  @Override
  public String getFromCredHelper() {
    return jibPluginConfiguration.getBaseImageCredentialHelperName();
  }

  @Nullable
  @Override
  public List<String> getEntrypoint() {
    return jibPluginConfiguration.getEntrypoint();
  }

  @Nullable
  @Override
  public List<String> getProgramArguments() {
    return jibPluginConfiguration.getArgs();
  }

  @Override
  public Map<String, String> getEnvironment() {
    return jibPluginConfiguration.getEnvironment();
  }

  @Override
  public List<String> getPorts() {
    return jibPluginConfiguration.getExposedPorts();
  }

  @Nullable
  @Override
  public String getUser() {
    return jibPluginConfiguration.getUser();
  }

  @Override
  public boolean getUseCurrentTimestamp() {
    return jibPluginConfiguration.getUseCurrentTimestamp();
  }

  @Override
  public List<String> getJvmFlags() {
    return jibPluginConfiguration.getJvmFlags();
  }

  @Nullable
  @Override
  public String getMainClass() {
    return jibPluginConfiguration.getMainClass();
  }

  @Override
  public String getAppRoot() {
    return jibPluginConfiguration.getAppRoot();
  }

  @Nullable
  @Override
  public AuthProperty getInferredAuth(String authTarget) throws InferredAuthRetrievalException {
    return mavenSettingsServerCredentials.retrieve(authTarget).orElse(null);
  }
}
