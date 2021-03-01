# Proposal : Jib CLI Jar Processing 

Relevant Issue: [#2796](https://github.com/GoogleContainerTools/jib/issues/2796)

# Motivation 
Allow users to containerize arbitrary jar files without having to integrate Jib into java build tools (maven and gradle) or use a `.yaml` jib cli buildfile.

# Proposal Changes

## Standard Jar 
A standard jar can be containerized in two modes, exploded or packaged. 

### Exploded Mode
Achieved by calling `jib jar ${JAR_NAME}.jar --target ${TARGET_REGISTRY}`
The default mode for containerizing a jar. It will explode a jar into the following layers:  
- Dependencies Layer: Contains dependencies whose versions do not contain `SNAPSHOT`. Note that this layer will not be created if `Class-Path` is not present in the manifest.
- Snapshot-Dependencies Layer: Contains dependencies whose versions contain `SNAPSHOT`. Note that this layer will not be created if `Class-Path` is not present in the manifest.
- Resources Layer: Contains resources parsed from jar file. Note that it will also include `MANIFEST.MF`.
- Classes Layer: Contains classes parsed from jar file. 

**Entrypoint** : `java -cp /app/dependencies/:/app/explodedJar/ ${MAIN_CLASS}`

### Packaged Mode
Achieved by calling `jib jar ${JAR_NAME}.jar --target ${TARGET_REGISTRY} --mode packaged`.
It will result in the following layers on the container:
- Dependencies Layer: Contains the dependencies derived from `Class-Path` in jar manifest. Note that this layer will not be created if `Class-Path` is not present in the manifest.
- Jar Layer: Contains the original jar.

**Entrypoint** : `java -jar ${JAR_NAME}.jar`

## Spring-Boot Fat Jar
A Spring-Boot Fat Jar can be containerized in two modes, exploded or packaged. 

### Exploded Mode
Achieved by calling `jib jar ${JAR_NAME}.jar --target ${TARGET_REGISTRY}`
The default mode for containerizing a jar. It will explode a jar according to what is specified in the `layers.idx` file of the jar, if present, or according to following format:
- Dependencies Layer: For a dependency whose version does not contain `SNAPSHOT`.
- Spring-Boot-Loader Layer: Contains jar loader classes.
- Snapshot-Dependencies Layer: For a dependency whose version contains `SNAPSHOT`.
- Resources Layer: Contains resources parsed from `BOOT-INF/classes/` in the jar and `META-INF/`.
- Classes Layer: Contains classes parsed from `BOOT-INF/classes/` in the jar.

**Entrypoint** : `java -cp /app org.springframework.boot.loader.JarLauncher`

### Packaged Mode
Achieved by calling `jib jar ${JAR_NAME}.jar --target ${TARGET_REGISTRY} --mode packaged`
It will containerize the jar as is. However, **note** that we highly recommend against using packaged mode for containerizing spring-boot fat jars. 

**Entrypoint**: `java -jar ${JAR_NAME}.jar`

### Optional Parameters
The `jar` command also provides the option to configure the parameters listed below.  

```
    --from                    The base image to use
    --jvm-flags               JVM arguments, example: --jvm-flags=-Dmy.property=value,-Xshare:off.
    --expose                  Ports to expose on container, example: --expose=5000,7/udp.
    --volumes                 Directories on container to hold extra volumes,  example: --volumes=/var/log,/var/log2.
    --environment-variables   Environment variables to write into container, example: --environment-variables env1=env_value1,env2=env_value2.
    --labels                  Labels to write into container metadata, example: --labels=label1=value1,label2=value2.
-u, --user                    The user to run the container as, example: --user=myuser:mygroup.
    --image-format            Format of container, example --image-format=OCI. Overrides the default (Docker).
    --program-args            Program arguments for container entrypoint.
    --entrypoint              Entrypoint for container. Overrides the default entrypoint, example: --entrypoint='custom entrypoint'.
    --creation-time           The creation time of the container in milliseconds since epoch or iso8601 format. Overrides the default (1970-01-01T00:00:00Z).
```
