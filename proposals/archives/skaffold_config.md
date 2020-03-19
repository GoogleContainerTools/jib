# Proposal: Control over skaffold tasks/goals

Relevant issue: https://github.com/GoogleContainerTools/skaffold/issues/3457

## Motivation

Jib makes assumptions about what files to watch based on what it knows. Users
may have other ideas about intermediate build processes that occur before jib is
ready to process anything. Allowing users to configure the skaffold tasks to
correctly reflect what their build is doing could help our users use skaffold
more effectively.

## Current Configuration

None: Jib does not have any way to configure `_skaffold` tasks

## Proposed Configuration
The proposal is to allow users to configure what jib shares with skaffold
1. Allowing inclusion/exclusion on top of the jib defaults for files to watch
2. Allow exclusion of files that will be sync'd

The final jib output will not deviate from what skaffold expects, but just
allows for tighter control of what is sent to skaffold from jib.

#### Gradle (`build.gradle`)
```groovy
jib {
  ...
  skaffold {
    watch {
      buildIncludes = 'script.gradle'
      includes = project.files('my/custom/inputs')
      excludes = ['some/file/i/dont/want/watched']
    }
    sync {
      exclude = 'a/file'
    }
  }
}
```

#### Maven (`pom.xml`)
```xml
<configuration>
  <skaffold>
    <watch>
      <buildIncludes>
        <buildInclude>some/pomfile.xml</buildInclude>
      </buildIncludes>
      <includes>
        <include>some/file</include>
        <include>another/file</include>
      </includes>
      <excludes>
        <exclude>not/me</exclude>
        <exclude>/absolute/path/to/not/me</exclude>
      <excludes>
    </watch>
    <sync>
      <excludes>
        <exclude>some/file</exclude>
      <excludes>
    </sync>
  </skaffold>
</configuration>
```

## Changes to Skaffold

None
