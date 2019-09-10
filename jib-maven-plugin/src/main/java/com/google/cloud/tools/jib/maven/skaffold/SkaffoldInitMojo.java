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
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Prints out to.image configuration and project name, used for Jib project detection in Skaffold.
 *
 * <p>Expected use: {@code ./mvnw jib:_skaffold-init -q}
 */
@Mojo(
    name = SkaffoldInitMojo.GOAL_NAME,
    requiresDependencyCollection = ResolutionScope.NONE,
    aggregator = true)
public class SkaffoldInitMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "_skaffold-init";

  @Nullable
  @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
  private List<MavenProject> projects;

  @Override
  public void execute() throws MojoExecutionException {
    Preconditions.checkNotNull(projects);

    checkJibVersion();

    for (MavenProject project : projects) {
      // Ignore parent projects
      if (project.getModules().size() > 0) {
        continue;
      }

      // Find "<to><image>"
      SkaffoldInitOutput skaffoldInitOutput = new SkaffoldInitOutput();
      for (Plugin plugin : project.getBuildPlugins()) {
        if (plugin.getArtifactId().equals("jib-maven-plugin")) {
          Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
          if (configuration != null) {
            Xpp3Dom to = configuration.getChild("to");
            if (to != null) {
              Xpp3Dom image = to.getChild("image");
              if (image != null) {
                skaffoldInitOutput.setImage(image.getValue());
                break;
              }
            }
          }
        }
      }

      if (projects.size() > 1) {
        skaffoldInitOutput.setProject(project.getName());
      }

      System.out.println("\nBEGIN JIB JSON");
      try {
        System.out.println(skaffoldInitOutput.getJsonString());
      } catch (IOException ex) {
        throw new MojoExecutionException(ex.getMessage(), ex);
      }
    }
  }
}
