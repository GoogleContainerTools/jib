/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.maven.skaffold;

import com.google.cloud.tools.jib.maven.MojoCommon;
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Parameter;

/** Base class for Skaffold-related goals. */
abstract class SkaffoldBindingMojo extends AbstractMojo {
  @Nullable
  @Parameter(defaultValue = "${plugin}", readonly = true)
  protected PluginDescriptor descriptor;

  protected void checkJibVersion() throws MojoExecutionException {
    Preconditions.checkNotNull(descriptor);
    MojoCommon.checkJibVersion(descriptor);
  }
}
