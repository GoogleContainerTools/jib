/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.maven.skaffold;

import com.google.cloud.tools.jib.maven.JibPluginConfiguration;
import com.google.cloud.tools.jib.plugins.common.SkaffoldInitOutput;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Prints out to.image configuration and project name, used for Jib project detection in Skaffold.
 *
 * <p>Expected use: {@code ./mvnw jib:_skaffold-init -q}
 */
@Mojo(name = SkaffoldInitMojo.GOAL_NAME, requiresDependencyCollection = ResolutionScope.NONE)
public class SkaffoldInitMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "_skaffold-init";

  @Override
  public void execute() throws MojoExecutionException {
    SkaffoldInitOutput skaffoldInitOutput = new SkaffoldInitOutput();
    skaffoldInitOutput.setImage(getTargetImage());
    if (getProject().getParent() != null) {
      skaffoldInitOutput.setProject(getProject().getName());
    }

    System.out.println("\nBEGIN JIB JSON");
    try {
      System.out.println(skaffoldInitOutput.getJsonString());
    } catch (IOException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);
    }
  }
}
