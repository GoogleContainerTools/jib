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

package com.google.cloud.tools.jib.maven.skaffold;

import com.google.cloud.tools.jib.maven.MavenProjectProperties;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Print out all jib goals tied to the package phase. Useful in multimodule situations to determine
 * if the correct jib goals are configured when running skaffold. For use only within skaffold.
 *
 * <p>It is intended to be used from the root project and only in multimodule situations:
 *
 * <p>./mvnw jib:_skaffold-package-goals -q -pl module [-Pprofile]
 */
@Mojo(
    name = PackageGoalsMojo.GOAL_NAME,
    requiresDirectInvocation = true,
    requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class PackageGoalsMojo extends SkaffoldBindingMojo {

  @VisibleForTesting static final String GOAL_NAME = "_skaffold-package-goals";

  @Nullable @Component private LifecycleExecutor lifecycleExecutor;

  @Nullable
  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    Preconditions.checkNotNull(lifecycleExecutor);
    Preconditions.checkNotNull(session);
    checkJibVersion();

    MavenExecutionPlan mavenExecutionPlan;
    try {
      mavenExecutionPlan = lifecycleExecutor.calculateExecutionPlan(session, "package");
    } catch (Exception ex) {
      throw new MojoExecutionException("failed to calculate execution plan", ex);
    }

    mavenExecutionPlan.getMojoExecutions().stream()
        .filter(mojoExecution -> "package".equals(mojoExecution.getLifecyclePhase()))
        .filter(
            mojoExecution ->
                MavenProjectProperties.PLUGIN_NAME.equals(
                    mojoExecution.getPlugin().getArtifactId()))
        .map(MojoExecution::getGoal)
        .forEach(System.out::println);
  }
}
