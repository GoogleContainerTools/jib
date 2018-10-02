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

/** Names of system properties used to set configuration via commandline. */
class PropertyNames {
  static final String fromImage = "jib.from.image";
  static final String fromCredHelper = "jib.from.credHelper";
  static final String fromAuthUsername = "jib.from.auth.username";
  static final String fromAuthPassword = "jib.from.auth.password";
  static final String toImage = "jib.to.image";
  static final String toTags = "jib.to.tags";
  static final String toCredHelper = "jib.to.credHelper";
  static final String toAuthUsername = "jib.to.auth.username";
  static final String toAuthPassword = "jib.to.auth.password";
  static final String containerAppRoot = "jib.container.appRoot";
  static final String containerArgs = "jib.container.args";
  static final String containerEntrypoint = "jib.container.entrypoint";
  static final String containerEnvironment = "jib.container.environment";
  static final String containerFormat = "jib.container.format";
  static final String containerJvmFlags = "jib.container.jvmFlags";
  static final String containerLabels = "jib.container.labels";
  static final String containerMainClass = "jib.container.mainClass";
  static final String containerPorts = "jib.container.ports";
  static final String containerUseCurrentTimestamp = "jib.container.useCurrentTimestamp";
  static final String useOnlyProjectCache = "jib.useOnlyProjectCache";
  static final String allowInsecureRegistries = "jib.allowInsecureRegistries";
  static final String extraDirectory = "jib.extraDirectory";
}
