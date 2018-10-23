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
import com.google.cloud.tools.jib.plugins.common.RawConfiguration;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Maven-specific adapter for providing raw configuration parameter values. */
class MavenRawConfiguration implements RawConfiguration {

  private final JibPluginConfiguration jibPluginConfiguration;
  private final MavenSettingsServerCredentials mavenSettingsServerCredentials;

  /**
   * Creates a raw configuration instances.
   *
   * @param jibPluginConfiguration the Jib plugin configuration
   * @param eventDispatcher Jib event dispatcher
   */
  MavenRawConfiguration(
      JibPluginConfiguration jibPluginConfiguration, EventDispatcher eventDispatcher) {
    this.jibPluginConfiguration = jibPluginConfiguration;
    mavenSettingsServerCredentials =
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

  @Override
  public Iterable<String> getToTags() {
    return jibPluginConfiguration.getTargetImageAdditionalTags();
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

  @Nullable
  @Override
  public String getMainClass() {
    return jibPluginConfiguration.getMainClass();
  }

  @Override
  public List<String> getJvmFlags() {
    return jibPluginConfiguration.getJvmFlags();
  }

  @Override
  public String getAppRoot() {
    return jibPluginConfiguration.getAppRoot();
  }

  @Override
  public Map<String, String> getEnvironment() {
    return jibPluginConfiguration.getEnvironment();
  }

  @Override
  public Map<String, String> getLabels() {
    return jibPluginConfiguration.getLabels();
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
  public boolean getAllowInsecureRegistries() {
    return jibPluginConfiguration.getAllowInsecureRegistries();
  }

  @Override
  public boolean getUseOnlyProjectCache() {
    return jibPluginConfiguration.getUseOnlyProjectCache();
  }

  @Nullable
  @Override
  public AuthProperty getInferredAuth(String authTarget) throws InferredAuthRetrievalException {
    return mavenSettingsServerCredentials.retrieve(authTarget).orElse(null);
  }
}
