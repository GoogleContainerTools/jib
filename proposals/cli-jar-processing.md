# Proposal : Jib CLI Jar Processing 

Relevant Issue: [#2796](https://github.com/GoogleContainerTools/jib/issues/2796)

# Motivation 
Allow users to containerize arbitrary jar files without having to integrate Jib into java build tools (maven and gradle).

# Proposal Changes

##  Standard Jar 
A standard jar can be containerized in two modes, exploded or packaged. 

### Exploded Mode
Achieved by calling `jib jar ${JAR_NAME}.jar --to ${TARGET_REGISTRY}`
The default mode for the jar cli, used to explode a jar into three layers:  
- Dependencies Layer: Contains dependencies derived from `Class-Path` in jar manifest.
- Resources Layer: Contains resources parsed from jar file. Note that it will also include `MANIFEST.MF`.
- Classes Layer: Contains classes parsed from jar file. 

**Entrypoint** : `java -cp /app/dependencies/ /app/explodedJar/ ${MAIN_CLASS}`

### Packaged Mode
Achieved by calling `jib jar ${JAR_NAME}.jar --to ${TARGET_REGISTRY} --mode packaged`.
This will result in two layers on the container:
- Dependencies:  Contains the dependencies derived from `Class-Path` in jar manifest.
- Jar Layer: Contains the original jar.

**Entrypoint** : `java -jar ${JAR_NAME}.jar`

## Spring Boot Fat Jar
A Spring Boot Fat Jar can be containerized in two modes, exploded or packaged. 

### Exploded Mode
Achieved by calling `jib jar ${JAR_NAME}.jar --to ${TARGET_REGISTRY}`
The default mode for the jar cli, used to explode a jar into three layers:  
- Dependencies Layer: Contains dependencies derived from the `BOOT-INF/libs` directory in the jar.
- Resources Layer: Contains resources parsed from the jar. Note that it will also include `MANIFEST.MF`.
- Classes Layer: Contains classes parsed from the jar.

**Entrypoint** : `java -cp . org.springframework.boot.loader.JarLauncher`

### Packaged Mode
Achieved by calling `jib jar ${JAR_NAME}.jar --to ${TARGET_REGISTRY} --mode packaged`
This will result in two layers on the container: 
- Dependencies Layer: Contains dependencies derived from the `BOOT-INF/libs` directory in the jar.
- Jar Layer: Contains the original jar.

**Entrypoint**: `java -jar ${JAR_NAME}.jar`