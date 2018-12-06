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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;

/** Test helper to expose package-private members of {@link JibContainerBuilder}. */
public class JibContainerBuilderTestHelper {

  public static BuildConfiguration toBuildConfiguration(
      JibContainerBuilder jibContainerBuilder, Containerizer containerizer)
      throws IOException, CacheDirectoryCreationException {
    return jibContainerBuilder.toBuildConfiguration(
        containerizer,
        containerizer.getExecutorService().orElseGet(MoreExecutors::newDirectExecutorService));
  }

  private JibContainerBuilderTestHelper() {}
}
