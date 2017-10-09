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

import org.gradle.api.Project;
import org.gradle.api.provider.PropertyState;

/** Minikube configuration extension. */
public class MinikubeExtension {
  private final PropertyState<String> minikube;

  public MinikubeExtension(Project project) {
    minikube = project.property(String.class);
    setMinikube("minikube");
  }

  public String getMinikube() {
    return minikube.get();
  }

  public void setMinikube(String minikube) {
    this.minikube.set(minikube);
  }

  public PropertyState<String> getMinikubeProvider() {
    return minikube;
  }
}
