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

import com.google.cloud.tools.jib.image.ImageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds raw plugin configuration parameter values. Acts as a common adapter for heterogeneous
 * plugin configuration models.
 */
public interface RawConfiguration {

  Optional<String> getFromImage();

  Optional<String> getToImage();

  AuthProperty getFromAuth();

  AuthProperty getToAuth();

  Optional<String> getFromCredHelper();

  Optional<String> getToCredHelper();

  Iterable<String> getToTags();

  Optional<List<String>> getEntrypoint();

  Optional<List<String>> getProgramArguments();

  Optional<String> getMainClass();

  List<String> getJvmFlags();

  String getAppRoot();

  Map<String, String> getEnvironment();

  Map<String, String> getLabels();

  List<String> getVolumes();

  List<String> getPorts();

  Optional<String> getUser();

  Optional<String> getWorkingDirectory();

  boolean getUseCurrentTimestamp();

  boolean getAllowInsecureRegistries();

  ImageFormat getImageFormat();

  Optional<String> getPackagingOverride();
}
