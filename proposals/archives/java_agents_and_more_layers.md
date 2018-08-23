# Proposal: Give users ability to add Java agents, arbitrary files, and separate dependencies

Implemented in: **v0.9.5**

## Motivation

There are 3 feature requests that may be able to be solved together. These are:

- Give users ability to add Java agents ([#378](/../../issues/378))
- Give users ability to copy arbitrary files to the image ([#213](/../../issues/213))
- Separate frequently changing from non-changing dependencies for more incrementality ([#403](/../../issues/403))

## Synopsis of the issues

### Give users ability to add Java agents ([#378](/../../issues/378))

Currently, Jib only adds application files to the container image. These include dependency JARs, resource files, and classes. However, Java developers often run their applications with Java Agents that execute as part of the JVM invocation.

For example, in [this Dockerfile example](https://github.com/saturnism/spring-petclinic-gcp/blob/master/docker/Dockerfile), the Cloud Debugger and Cloud Profiler agents are added to the container image. The agent archives are first downloaded, and then extracted to their own directory on the image. The archives contains the `.so` file to pass to the java invocation in the form of:

```shell
java -agentpath:/path/to/agent/files/someagent.so ...
```

### Give users ability to copy arbitrary files to the image ([#213](/../../issues/213))

Users may wish to add other files for use in the image. Currently, the way to do this would be to place the files within the application resources. However, this conflates the classpath of the application, the file can only be reached under the `/app/resources` path, and changes to the file would mean repushing all the resources. Therefore, users should have some way of adding files to a new custom layer.

### Separate frequently changing from non-changing dependencies for more incrementality ([#403](/../../issues/403))

The dependencies layer tends to be the largest layer in the image, since it contains all the dependency JARs. However, not all dependencies are the same. Some may change more frequently than others (especially `-SNAPSHOT` versions). Therefore, users should have some way to group different dependencies into different layers. For example, separating released artifacts from snapshot artifacts, and grouping dependencies from a BOM into a layer, or separating more frequently changed ones from less frequently changed ones to reduce the amortized push time.

## Proposal

The proposal is to define a new convention for adding more files to the Jib container.

The user can add arbitrary files to the image by placing them in a `src/main/jib` directory. This will copy all files within `src/main/jib` to the image's root directory `/`, maintaining the same structure. For example, if the user has a text file at `src/main/jib/dir/hello.txt`, then the built image have `/dir/hello.txt`.

The directory should also be able to be configured to override the `src/main/jib` default.

## Alternative Proposals

### Rejected Proposal 1

**The alternative proposal was rejected** because we deemed that it required too much extra configuration on the part of the user.

The proposal is to keep the configuration for adding additional files and Java agents separate from the configuration for separating the application layers into thinner layers. The point of this is to keep the configuration simple and not allow arbitrary user-controlled layering.

The configuration for adding additional files (including Java agents) would look something like:

*(The exact configuration is to be decided before this PR is merged. Alternative naming for this parameter include: `additionalFiles`, `extraFiles`, and `copyFiles`.)*

*Maven* 

```xml
<addFiles> 
  <addFile>
    <from>path/to/file</from>
    <to>/path/on/image</to>
  </addFile>
  <addFile>
    <from>another/file</from>
    <to>/another/path/on/image</to>
  </addFile>
</addFiles>
```

*Gradle*

```groovy
addFile 'path/to/file', '/path/on/image'
addFile 'another/file', '/another/path/on/image'
```

*The semantics of `from` and `to` will mostly be similar to [Dockerfile `COPY`](https://docs.docker.com/engine/reference/builder/#copy). However, glob matching won't be supported.* 

For separating application layers into thinner layers, the solution will only separate dependencies for simplicity. Jib will *automatically* separate `-SNAPSHOT` dependencies and dependencies with the same group as the project into a separate layer.

In the future, we may consider allowing the user to configure this in the form of something like what was originally suggested in [#403](/../../issues/403):

```xml
<volatileDependencies>com.yourcompany.*, *-SNAPSHOT</volatileDependencies>
```

This would match dependencies that are in the `com.yourcompany` package and `SNAPSHOT` dependencies and place these in a new volatile-dependencies layer.

### Rejected Proposal 2

**The alternative proposal was rejected** because we deemed that layering should be an implementation detail that should not be exposed to the user.

Currently (`v0.9.1`), Jib's configuration looks like:

```xml
<configuration>
  <from>...</from>
  <to>...</to>
  <container>...</container>
</configuration>

```

- `from` defines the base image and credentials
- `to` defines the target image and credentials
- `container` defines container configuration like JVM flags, program arguments, and exposed ports

The alternative proposal is to add another top-level configuration object called `layers` to add additional layers with custom files. The configuration would look like:

```xml
<layers>
  <layer>
    <files>
      <file>
        <from>path/to/file</from>
        <to>/path/on/image</to>
      </file>
    </files>
    <matchDependencies>org.springframework:*, org.hibernate:*...</matchDependencies>
    <matchResources>static/, *.jpg</matchResources>
    <matchClasses>my.package.that.does.not.change.much.*</matchClasses>
  </layer>
</layers>
```

The user can choose whatever subset of `layer` parameters to define.

Parameter | Description
--- | ---
files | In the form `<src>:<dest>`, where the file `<src>` is added to the image at path `<dest>`.
matchDependencies | Matches dependencies with the given patterns and adds those dependency artifacts into this layer rather than the original dependencies layer.
matchResources | Like `matchDependencies`, but for resource files.
matchClasses | ... but for classes files.

For implementation, `files` should be implemented first to support Java agent usage and `match*` could be added in later updates.

And similarly for Gradle:

```groovy
layer {
  file 'path/to/file', '/path/on/image'
  matchDependencies = 'org.springframework:*, org.hibernate:*...'
  matchResources = 'static/, *.jpg'
  matchClasses = 'my.package.that.does.not.change.much.*'
}
```

## Other options considered

### Give users ability to add Java agents ([#378](/../../issues/378))

#### Option 1 - Specific configuration for each agent

Provide agent-specific configuration options similar to [CloudFoundry BuildPacks](https://github.com/cloudfoundry/java-buildpack). This would mean that **each agent would have a different set of configuration parameters** specifically for that agent. Each agent-specific implementation would automatically download and add the agent files to the container image and automatically configure the JVM flags. 

**Benefit:** User would be given the exact and minimal controls over specific agents.
**Downside:** We would have to implement and maintain custom implementations for each agent we wish to support.

#### Option 2 - `agents` configuration

Provide configuration options to add agents by specifying the archive download location or local files. For example, the configuration could look like:

```xml
<agents>
  <agent>
    <location>${project.basedir}/myagent-archive.tar.gz</location>
    <pathOnImage>/opt/myagent</pathOnImage>
  </agent>
</agents>
<container>
  <jvmFlags>
    <jvmFlag>-agentpath:/opt/myagent/myagent.so=-some_option=yes</jvmFlag>
  </jvmFlags>
</container>
```

#### Option 3 - copy to image

Solve this as part of *### Give users ability to copy arbitrary files to the image ([#213](/../../issues/213))*.

### Give users ability to copy arbitrary files to the image ([#213](/../../issues/213))

#### Option 1 - Single custom layer

The user defines files to copy, along with their destinations on the image. For example:

```xml
<copies>
  <copy>
    <source>path/to/file</source>
    <pathOnImage>/path/on/image</pathOnImage>
  </copy>
</copies>
```

All files defined would be placed in a single layer.

**Benefit:** Prevents possible large number of layers.
**Downside:** All files are in same layer.

#### Option 2 - Multiple custom layers

The user can define their own custom layers, and define what files to copy into each layer. For example:

```xml
<additionalLayers>
  <additionalLayer>
    <copies>
      <copy>
        <source>path/to/file</source>
        <pathOnImage>/path/on/image</pathOnImage>
      </copy>
    </copies>
  </additionalLayer>
</additionalLayers>
```

**Benefit:** Can be used to solve all three issues (with more options for other things to add to such custom layers)
**Downside:** Quite verbose

#### Option 3 - Multiple automatic layers

The user defines files to copy like in *Option 1*, but a separate layer is generated for each copy.

#### Option 4 - Multiple custom layers by layer ID

Like in *Option 1*, but the user can set a layer ID for each copy. Copies with the same layer ID are placed in the same layer. For example:

```xml
<copies>
  <copy>
    <source>path/to/file</source>
    <pathOnImage>/path/on/image</pathOnImage>
    <layerId>somelayername</layerId>
  </copy>
  <copy>
    <source>path/to/another/file</source>
    <pathOnImage>/another/path/on/image</pathOnImage>
    <layerId>somelayername</layerId> ← this goes in the same layer as the first
  </copy>
  <copy>
    <source>path/to/yet/another/file</source>
    <pathOnImage>/path/on/image</pathOnImage>
    <layerId>someotherlayername</layerId>
  </copy>
</copies>
```

**Benefit:** Keeps the configuration concise

### Separate frequently changing from non-changing dependencies for more incrementality ([#403](/../../issues/403))

#### Option 1 - Top-level configuration

Configuration options `silentDependencies` and `mutableDependencies`, as recommended in [#403](/../../issues/403). Example:

```xml
<silentDependencies>org.springframework:*, org.hibernate:*, *:commons-*, org.webjars:*</silentDependencies>
<mutableDependencies>com.yourcompany:*,*:*:*-SNAPSHOT</mutableDependencies>
```

**Benefit:** Simple and understandable
**Downside:** Does not solve any of the other issues

#### Option 2 - for classes, resources, and dependencies

The user would be able to define patterns to match against both dependencies and any other files that go in the application layers. These matched files are taken from the `classes`, `resources`, and `dependencies` layers and placed into 3 new layers - `silentClasses`, `silentResources`, and `silentDependencies`. The configuration would look like:

```xml
<silentDependencies>org.springframework:*, org.hibernate:*...</silentDependencies>
<silentFiles>static/, *.jpg</silentFiles>
<silentPackages>my.package.that.does.not.change.much</silentPackages>
```

**Benefit:** Supports splitting of all application layers into thinner layers.
**Downside:** Does not solve any of the other issues

#### Option 3 - in custom layers

Similar to the above *[Option 2 - Multiple custom layers](#option-2---multiple-custom-layers)*:

```xml
<additionalLayers>
  <additionalLayer>
    <copies>...</copies>
    <matchDependencies>org.springframework:*, org.hibernate:*...</matchDependencies>
    <matchFiles>static/, *.jpg</matchFiles>
    <matchPackages>my.package.that.does.not.change.much</matchPackages>
  </additionalLayer>
</additionalLayers>
```

In the above example, each configuration option for `additionalLayer` is optional.

**Benefits:** Solves all 3 issues
**Downside:** Might be confusing
