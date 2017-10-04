/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.minikube;

import com.google.cloud.tools.minikube.util.CommandExecutor;
import com.google.cloud.tools.minikube.util.MinikubeDockerEnvParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.PropertyState;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/** Task to build Docker images. */
public class DockerBuildTask extends DefaultTask {

  /** minikube executable : lazily evaluated from extension input */
  private PropertyState<String> minikube;
  /** docker executable : lazily evaluated from extension input */
  private PropertyState<String> docker;
  /** The set of files to build (PATH | URL | -) */
  private String context;
  /** Flags passthrough */
  private String[] flags = {};

  public DockerBuildTask() {
    minikube = getProject().property(String.class);
    docker = getProject().property(String.class);
    context = getProject().getBuildDir().toPath().resolve("libs").toString();
  }

  // @VisibleForTesting
  class CommandExecutorFactory {
    CommandExecutor createCommandExecutor() {
      return new CommandExecutor().setLogger(getLogger());
    }
  }

  // @VisibleForTesting
  DockerBuildTask setCommandExecutorFactory(CommandExecutorFactory commandExecutorFactory) {
    this.commandExecutorFactory = commandExecutorFactory;
    return this;
  }

  private CommandExecutorFactory commandExecutorFactory = new CommandExecutorFactory();

  @Input
  public String getMinikube() {
    return minikube.get();
  }

  public void setMinikube(String minikube) {
    this.minikube.set(minikube);
  }

  public void setMinikube(PropertyState<String> minikube) {
    this.minikube = minikube;
  }

  @Input
  public String getDocker() {
    return docker.get();
  }

  public void setDocker(String docker) {
    this.docker.set(docker);
  }

  public void setDocker(PropertyState<String> docker) {
    this.docker = docker;
  }

  @Input
  public String getContext() {
    return context;
  }

  public void setContext(String context) {
    this.context = context;
  }

  @Input
  public String[] getFlags() {
    return flags;
  }

  public void setFlags(String[] flags) {
    this.flags = flags;
  }

  @TaskAction
  public void execDockerBuild() throws IOException, InterruptedException {
    // Gets the minikube docker environment variables by running the command 'minikube docker-env'.
    List<String> minikubeDockerEnvCommand =
        Arrays.asList(minikube.get(), "docker-env --shell none");
    List<String> dockerEnv =
        commandExecutorFactory.createCommandExecutor().run(minikubeDockerEnvCommand);

    Map<String, String> environment;
    environment = MinikubeDockerEnvParser.parse(dockerEnv);

    // Runs the docker build command with the minikube docker environment.
    List<String> dockerBuildCommand = buildDockerBuildCommand();
    commandExecutorFactory
        .createCommandExecutor()
        .setEnvironment(environment)
        .run(dockerBuildCommand);
  }

  // @VisibleForTesting
  List<String> buildDockerBuildCommand() {
    List<String> execString = new ArrayList<>();
    execString.add(docker.get());
    execString.add("build");
    execString.addAll(Arrays.asList(flags));
    execString.add(context);

    return execString;
  }
}
