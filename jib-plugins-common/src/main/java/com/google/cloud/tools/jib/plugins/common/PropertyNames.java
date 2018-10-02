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

package com.google.cloud.tools.jib.plugins.common;

/** Names of system properties used to set configuration via commandline. */
public class PropertyNames {
  public static final String fromImage = "jib.from.image";
  public static final String fromCredHelper = "jib.from.credHelper";
  public static final String fromAuthUsername = "jib.from.auth.username";
  public static final String fromAuthPassword = "jib.from.auth.password";
  public static final String toImage = "jib.to.image";
  public static final String toImageAlternate = "image";
  public static final String toTags = "jib.to.tags";
  public static final String toCredHelper = "jib.to.credHelper";
  public static final String toAuthUsername = "jib.to.auth.username";
  public static final String toAuthPassword = "jib.to.auth.password";
  public static final String containerAppRoot = "jib.container.appRoot";
  public static final String containerArgs = "jib.container.args";
  public static final String containerEntrypoint = "jib.container.entrypoint";
  public static final String containerEnvironment = "jib.container.environment";
  public static final String containerFormat = "jib.container.format";
  public static final String containerJvmFlags = "jib.container.jvmFlags";
  public static final String containerLabels = "jib.container.labels";
  public static final String containerMainClass = "jib.container.mainClass";
  public static final String containerPorts = "jib.container.ports";
  public static final String containerUseCurrentTimestamp = "jib.container.useCurrentTimestamp";
  public static final String useOnlyProjectCache = "jib.useOnlyProjectCache";
  public static final String allowInsecureRegistries = "jib.allowInsecureRegistries";
  public static final String extraDirectory = "jib.extraDirectory";
  public static final String skip = "jib.skip";
}
