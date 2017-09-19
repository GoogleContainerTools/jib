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
