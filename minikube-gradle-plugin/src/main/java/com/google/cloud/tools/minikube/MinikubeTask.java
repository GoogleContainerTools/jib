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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.PropertyState;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/** Generic Minikube task. */
public class MinikubeTask extends DefaultTask {

  /** minikube exectuable : lazily evaluated from extension input */
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
    ExecutorService executor = Executors.newSingleThreadExecutor();

    List<String> execString = buildMinikubeCommand();
    getLogger().debug("Running command : " + String.join(" ", execString));

    ProcessBuilder pb = new ProcessBuilder();
    pb.command(execString);
    pb.redirectErrorStream(true);
    final Process process = pb.start();

    // stream consumer
    executor.execute(
        () -> {
          try (BufferedReader br =
              new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = br.readLine();
            while (line != null) {
              getLogger().lifecycle(line);
              line = br.readLine();
            }
          } catch (IOException e) {
            getLogger().warn("IO Exception reading minikube process output");
          }
        });
    int exitCode = process.waitFor();
    executor.shutdown();
    try {
      executor.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      getLogger().debug("Task Executor interrupted waiting for output consumer thread");
    }

    // stop the build if minikube fails to do something, we may want to make this configurable
    if (exitCode != 0) {
      throw new GradleException("minikube exited with non-zero exit code : " + exitCode);
    }
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
