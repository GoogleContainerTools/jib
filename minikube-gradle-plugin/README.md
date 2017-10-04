Minikube Gradle Plugin
======================
This plugin provides tasks and configuration to manage a minikube lifecycle for gradle developers.

Now available on [gradle plugin portal](https://plugins.gradle.org/plugin/com.google.cloud.tools.minikube).

This plugin requires that you have Minikube [installed](https://kubernetes.io/docs/tasks/tools/install-minikube/).

It exposes the following tasks
- `minikubeStart`
- `minikubeStop`
- `minikubeDelete`
- `minikubeDockerBuild` - runs `docker build` in the minikube Docker environment

It exposes the `minikube` configuration extension.

```groovy
minikube {
  minikube = // path to minikube, default is "minikube"
  docker = // path to Docker, default is "docker"
}
```

Task specific flags are configured on the tasks themselves.
 
All `minikube` tasks, except `minikubeDockerBuild`, are of the type `MinikubeTask` and all share the same kind of configuration.
- `flags` (`String[]`) : any minikube flags **this is the only one users should edit for the provided tasks**
- `minikube` (`String`) : path to minikube executable which should be set by using the `minikube` extension
- `command` (`String`) : start/stop/whatever (users probably shouldn't be editing this for default commands)

```groovy
minikubeStart {
  flags = ["--vm-driver=none"]
}
```

`minikubeDockerBuild` task is of the type `DockerBuildTask` and can be configured with:
- `context` (`String`) : PATH | URL | - (See 'Extended description' under the [`docker build` Reference](https://docs.docker.com/engine/reference/commandline/build/))
    - Defaults to `build/libs/`
- `flags` (`String[]`) : any flags to pass to `docker build` (See 'Options' under the [`docker build` Reference](https://docs.docker.com/engine/reference/commandline/build/))
- `minikube` (`String`) : path to minikube executable which should be set by using the `minikube` extension **users should not edit this for this provided task**
- `docker` (`String`) : path to Docker executable which should be set by using the `docker` extension **users should not edit this for this provided task**

```groovy
minikubeDockerBuild {
  context = "build/libs/"
  flags = ["--build-arg ARTIFACT_NAME=my_kubernetes_app"]
}
```

This plugin also allows users to add in any custom `minikube` task.

```groovy
task minikubeCustom(type: com.google.cloud.tools.minikube.MinikubeTask) {
  command = "custom"
  flags = ["--some-flag"]
}
```
