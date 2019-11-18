# Style guide

This style guide defines specific coding standards and advice for this Java codebase. The rules here are extensions to the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

Please see the [contributing guide](CONTRIBUTING.md) for general guidance for contributing to this project.

### Automatic formatting

Automatic formatting should be performed with `./gradlew goJF` or `./mvnw fmt:format`. Formatting all projects can be done with `./build.sh format`.

### Class member order

*Extends [3.4.2](https://google.github.io/styleguide/javaguide.html#s3.4.2-ordering-class-contents)*

Class members should be in the following order, in decreasing priority:

1. Static before non-static
1. Nested classes/interfaces before fields before constructors before methods
1. Public before private
1. Final before non-final

### Public APIs

User-facing methods (such as those in Jib Core) should not have types in their signature that are not standard JDK classes. For example, a parameter should take type `List` rather than Guava's `ImmutableList`.

Jib Core's formal API should not expose internal Jib types. In other words, public classes in the `com.google.cloud.tools.jib.api` package should not contain any public methods that have internal types (Jib classes outside of the `api` package) in the method signature. This includes return types, parameters, thrown types, and javadoc links on public methods.

### Package hierarchy

Packages should depend on each other without cycles.

The following is a list of current `jib-core` packages (under `com.google.cloud.tools.jib`) and their immediate dependencies. These can be amended as code changes, but there should not be cyclical dependencies.

- `api`
- `async`
- `blob` - `filesystem`, `hash`, `image` (cycle - should fix)
- `builder` - `async`, `blob`, `builder`, `cache` `configuration`, `docker`, `event`, `filesystem`, `global`, `http`, `image`, `json`, `registry`
- `cache` - `blob`, `filesystem`, `hash`, `image`, `json`
- `configuration` - `cache`, `filesystem`, `event`, `image`, `registry`
- `docker` - `blob`, `cache`, `image`, `json`, `tar`
- `event`
- `filesystem`
- `frontend` - `configuration`, `event`, `filesystem`, `image`, `registry`
- `global`
- `hash` - `blob`, `image`
- `http` - `blob`
- `image` - `blob`, `configuration` (cycle - should fix - `ImageToJsonTranslator`), `filesystem`, `json`, `tar`
- `json` - `blob`
- `registry` - `blob`, `builder` (cycle - should fix - `RegistryClient`), `configuration` (cycle - should fix - `DockerConfigCredentialRetriever`), `event`, `global`, `http`, `image`, `json`
- `tar` - `blob`
