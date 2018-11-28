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
import com.google.cloud.tools.jib.image.ImageFormat;
import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.cloud.tools.jib.plugins.common.InferredAuthRetrievalException;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

  @Override
  public Optional<String> getFromImage() {
    return Optional.ofNullable(jibPluginConfiguration.getBaseImage());
  }

  @Override
  public AuthProperty getFromAuth() {
    return jibPluginConfiguration.getBaseImageAuth();
  }

  @Override
  public Optional<String> getFromCredHelper() {
    return Optional.ofNullable(jibPluginConfiguration.getBaseImageCredentialHelperName());
  }

  @Override
  public Optional<String> getToImage() {
    return Optional.ofNullable(jibPluginConfiguration.getTargetImage());
  }

  @Override
  public AuthProperty getToAuth() {
    return jibPluginConfiguration.getTargetImageAuth();
  }

  @Override
  public String getAuthDescriptor(String source) {
    return "<" + source + "><auth>";
  }

  @Override
  public String getUsernameAuthDescriptor(String source) {
    return getAuthDescriptor(source) + "<username>";
  }

  @Override
  public String getPasswordAuthDescriptor(String source) {
    return getAuthDescriptor(source) + "<password>";
  }

  @Override
  public Optional<String> getToCredHelper() {
    return Optional.ofNullable(jibPluginConfiguration.getTargetImageCredentialHelperName());
  }

  @Override
  public Iterable<String> getToTags() {
    return jibPluginConfiguration.getTargetImageAdditionalTags();
  }

  @Override
  public Optional<List<String>> getEntrypoint() {
    return Optional.ofNullable(jibPluginConfiguration.getEntrypoint());
  }

  @Override
  public Optional<List<String>> getProgramArguments() {
    return Optional.ofNullable(jibPluginConfiguration.getArgs());
  }

  @Override
  public Optional<String> getMainClass() {
    return Optional.ofNullable(jibPluginConfiguration.getMainClass());
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
  public List<String> getVolumes() {
    return jibPluginConfiguration.getVolumes();
  }

  @Override
  public List<String> getPorts() {
    return jibPluginConfiguration.getExposedPorts();
  }

  @Override
  public Optional<String> getUser() {
    return Optional.ofNullable(jibPluginConfiguration.getUser());
  }

  @Override
  public Optional<String> getWorkingDirectory() {
    return Optional.ofNullable(jibPluginConfiguration.getWorkingDirectory());
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

  @Override
  public Optional<AuthProperty> getInferredAuth(String authTarget)
      throws InferredAuthRetrievalException {
    return mavenSettingsServerCredentials.retrieve(authTarget);
  }

  @Override
  public String getInferredAuthDescriptor() {
    return MavenSettingsServerCredentials.CREDENTIAL_SOURCE;
  }

  @Override
  public ImageFormat getImageFormat() {
    return ImageFormat.valueOf(jibPluginConfiguration.getFormat());
  }
}
