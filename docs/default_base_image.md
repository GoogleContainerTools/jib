# Default Base Images in Jib

## Jib Build Plugins 3.0

Starting from version 3.0, the default base image is the official [`adoptopenjdk`](https://hub.docker.com/_/adoptopenjdk) image on Docker Hub. AdoptOpenJDK (which is [being renamed to Adoptium](https://blog.adoptopenjdk.net/2020/06/adoptopenjdk-to-join-the-eclipse-foundation/)) is a popular OpenJDK build also used by [Google Cloud Buildpacks](https://github.com/GoogleCloudPlatform/buildpacks) (for Java) by default.

For WAR projects, the default is the official [`jetty`](https://hub.docker.com/_/jetty) image on Docker Hub.

Note that Jib's default choice for AdoptOpenJDK and Jetty does not imply any endorsement to them. In fact, for strong reproducibility (which also results in better performance and efficiency), we always recommend configuring [`jib.from.image`](https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#from-closure) (Gradle) or [`<from><image>`](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#from-object) (Maven) to pin to a specific base image using a digest (or at least a tag). And while doing so, you should do your due diligence to figure out which base image will work best for you.

### Docker Hub Download Rate Limit

Docker Hub enforces download rate limit. Because Jib pulls base images from Docker Hub, you can occasionally lead to the following error:
```
    > com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException: 429 Too Many Requests
      GET https://registry-1.docker.io/v2/library/adoptopenjdk/manifests/sha256:9db4a57b38b6d928761ec9c5a250677d306095df0f6a6bdd8936628e033b9f1a
      {
        "errors": [
          {
            "code": "TOOMANYREQUESTS",
            "message": "You have reached your pull rate limit. You may increase the limit by authenticating and upgrading: https://www.docker.com/increase-rate-limit"
          }
        ]
      }
```
Note that, even after Jib fully cached a base image, Jib still connects to Docker Hub to check the image every time it runs (unless you pin to a specific base image using a SHA digest). This is to check if the cached base image is up-to-date.

Some options:
* Configure a registry mirror.
* Prevent Jib from accessing Docker Hub (after Jib cached a base image locally).
   - Pin to a specific base image using a SHA digest (for example, `jib.from.image='adoptopenjdk:11-jre@sha256:...'`).
   - Do offline building.
   - Read a base from a local Docker deamon.
   - Set up a local registry, store a base image, and read it from the local registry.
* Retry with increasing backoffs.

See this [FAQ](https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#i-am-hitting-docker-hub-rate-limits-how-can-i-configure-registry-mirrors) for more details about the options.

### Migration from Pre-3.0

Prior to 3.0, Jib Maven and Gradle plugins built images based on Distroless Java when the user didn't make an explicit choice. The Jib team carefully assessed all the surrounding factors and ultimately decided to switch from Distroless for the best interest of the Jib users. That is, to meet one of the core missions of Jib to build a secure and optimized image.

If you are already setting a specific base image, there will be absolutely no difference when you upgrade to 3.0. It will continue to use the base image you specified. Even if you are not specifying a base image, most likely upgrading to 3.0 will keep working fine, because it's just about getting a JRE from a different image. (But if you are not setting a base image, as we explained in the previous section, we highly recommend configuring it.)

For some reason if you have to keep the exact same behavior when using 3.0, you can always specify the Distroless Java as a base image.

* non-WAR projects
   ```groovy
   jib {
     from.image = 'gcr.io/distroless/java:11' // or ":8"
     ...
   }
   ```
   ```xml
     <configuration>
       <from><image>gcr.io/distroless/java:11</image></from> <!-- or ":8" -->
     </configuration>
   ```
   However, even when you decide to keep using Distroless, at least we strongly recommend `gcr.io/distroless/java-debian10:11`, because, as of Apr 2021, `gcr.io/distroless/java:{8,11}` is based on Debian 9 that reached end-of-life. (Note `gcr.io/distroless/java-debian10` doesn't have `:8`.)

* WAR projects
   ```gradle
   jib {
     from.image = 'gcr.io/distroless/java/jetty:java11' // or ":java8"
     container {
       entrypoint = 'INHERIT'
       appRoot = '/jetty/webapps/ROOT'
     }
     ...
   }
   ```
   ```xml
     <configuration>
       <from><image>gcr.io/distroless/java/jetty:java11</image></from> <!-- or ":java8" -->
       <container>
         <entrypoint>INHERIT</entrypoint>
         <appRoot>/jetty/webapps/ROOT</appRoot>
       </container>
     </configuration>
   ```

## Jib CLI

The JAR mode of the Jib CLI has always used AdoptOpenJDK and the WAR mode uses `jetty`.
