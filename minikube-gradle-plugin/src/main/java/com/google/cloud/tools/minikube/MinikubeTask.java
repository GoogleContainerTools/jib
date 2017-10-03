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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.PropertyState;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/** Generic Minikube task. */
public class MinikubeTask extends DefaultTask {

  /** minikube executable : lazily evaluated from extension input */
  private PropertyState<String> minikube;
  /** The minikube command: start, stop, etc. */
  private String command;
  /** Flag passthrough */
  private String[] flags = {};

  public MinikubeTask() {
    minikube = getProject().property(String.class);
  }

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
  public String getCommand() {
    return command;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  @Input
  public String[] getFlags() {
    return flags;
  }

  public void setFlags(String[] flags) {
    this.flags = flags;
  }

  @TaskAction
  public void execMinikube() throws IOException, InterruptedException {
    List<String> minikubeCommand = buildMinikubeCommand();
    new CommandExecutor().setLogger(getLogger()).run(minikubeCommand);
  }

  // @VisibleForTesting
  List<String> buildMinikubeCommand() {
    List<String> execString = new ArrayList<>();
    execString.add(getMinikube());
    execString.add(command);
    execString.addAll(Arrays.asList(flags));

    return execString;
  }
}
