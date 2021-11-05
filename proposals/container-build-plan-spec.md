# Container Build Plan Specification

Specification for building a container image.

Although looking similar, the structure and semantics of similarly named properties are different from the Docker/[OCI Image Configuration](https://github.com/opencontainers/image-spec/blob/master/config.md).

## Example

```
{
  "baseImage": "eclipse-temurin:11-jre@sha256:a3036d6a01859e3fe8cbad4887ccb2e4afd5f5ce8f085ca6bc0302fcbb8601f7",
  "architectureHint": "amd64",
  "osHint": "linux",
  "format": "Docker",
  "created": "2011-12-03T22:42:05Z",

  "config": {
    "env": {
      "KEY": "value",
      "PATH": "/usr/sbin:/usr/bin:/sbin:/bin",
      "HOME": "/home/guest"
    },
    "labels": {
      "com.example.department.some-label-key": "avocado explosion",
      "com.example.system.xml": "<message>delivered</message>"
    },
    "volumes": ["/mnt/shared", "/tmp"]
    "exposedPorts": ["8080", "53/udp", "80/tcp"]
    "user": ":12345",
    "workingDir": "/
    "entrypoint": ["/bin/bash", "-c"],
    "cmd": [
      "-x",
      "set -o errexit ; echo \"\$0=$0 \$1=$1\" ; echo \"\$PATH=${PATH}\" ; exit 0",
      "my-shell-name",
      "first shell arg"
    ],
  },

  "layers": [
    {
      "type": "fileEntries"
      "entries": [
        {
          "src": "/home/jane/workspace/bin/Main.class",
          "dest": "/app/classes/Main.class",
          "modificationTime": "2019-07-15T10:15:30+09:00",
          "permissions": "600",
        },
        {
          "src": "/home/jane/libs/util-1.0.jar",
          "dest": "/app/jars/util.jar",
          "modificationTime": "2011-12-03T22:42:05Z",
          "permissions": "644",
        }
      ]
    },
    {
      "type": "layerArchive"
      "mediaType": "...",
      "path": "/home/jane/misc/cacerts.tar",
    },
    {
      "type": "fileEntries"
      "entries": [
        {
          "src": "/home/workspace/scripts/run.sh",
          "dest": "/app/run.sh",
          "modificationTime": "2011-12-03T22:42:05Z",
          "permissions": "777",
          "ownership": ""
        },
      ]
    },
    ...
  ]
}
```

## Build Plan Object

* `baseImage`: string

   - `null` or omitted: no base image (from "scratch")

* `architectureHint`: string

   - If the base image reference is an "image list" (Docker manifest list or an OCI image index), must be set so that an image builder can select the image matching the given architecture.
   - If the base image reference is not an "image list", this value is ignored and the architecture of the built image follows that of the base image.

   The default is "amd64" when omitted.

* `osHint`: string

   - If the base image reference is an "image list" (Docker manifest list or an OCI image index), must be set so that an image builder can select the image matching the given OS.
   - If the base image reference is not an "image list", this value is ignored and the OS of the built image follows that of the base image.

   The default is "linux" when omitted.

* `format`: string

   Image output format. Either `"Docker"` or `"OCI"`.

   - `null` or omitted: `"Docker"` by default

* `created`: string

   ISO 8601-like date-time format.

   - `null` or omitted: the epoch (`"1970-01-01T00:00:00Z"`) by default

* `config`: [Execution Parameters object](#execution-parameters-object)

   Can be `null` or omitted.

* `layers`: array of [Layer Configuration objects](#layer-configuration-object)

   Adds layers on top of those from the base image; it is not possible to remove layers from the base image.

A builder implementation must inherit the [`history` entries](https://github.com/opencontainers/image-spec/blob/master/config.md) of base image layers.

### Execution Parameters Object

* `env`: map of (string, string)

   Adds environment variables on top of those from the base image (hence overridable for same variable names); it is not possible to unset variables from the base image.

* `labels`: map of (string, string)

   Adds labels on top of those from the base image (hence overridable for same label keys); it is not possible to unset labels from the base image.

* `volumes`: array of strings

   Adds volumes on top of those from the base image (no duplicate entries possible); it is not possible to unset volumes from the base image.

* `exposedPorts`: strings

   Adds ports on top of those from the base image; it is not possible to unset ports from the base image.

* `user`: string

   - `null` or omitted: inherits from the base image.
   - Otherwise (including an empty string `""`, `":"`, `"<user>:"`, and `":<group>"`), sets the given user and group; it is not possible to only inherit either the user or the group.

* `workingDir`: string

   - `null` or omitted: inherits from the base image.
   - Otherwise (including an empty string `""`), sets the given directory.

* `entrypoint`: array of strings

   - `null` or omitted: inherits from the base image.
   - Otherwise (including an empty list `[]` and `[""]`), sets the given entrypoint. (Note, if `cmd` is not given, also sets `cmd` to `null`.)
 
   Note `[]` is different from the `Dockerfile` build behavior. `Dockerfile` build sets the entrypoint to `null` if given `ENTRYPOINT []`.

* `cmd`: array of strings

   | `entrypoint`      | `cmd`             | `cmd` set in container |
   |-------------------|-------------------|------------------------|
   |                   | defined           | given value            |
   | defined           | `null` or omitted | `null`                 |
   | `null` or omitted | `null` or omitted | inherited              |

   Note `[]` for `entrypoint` and `cmd` is considered "defined".

### Layer Configuration Object

* `type`: string

   - `fileEntries` (collection of files as a layer), `layerArchive` (an archive file as a laye, to be implemented), etc.

#### `fileEntries` Sub-Type of Layer Configuration Object

* `entries`: array of [Layer Entry objects](#layer-entry-object)

### Layer Entry Object

* `src`: single local file, required
* `dest`: path in the container, required
* `permissions`: POSIX permissions, required
* `modificationTime`: if `null` or omitted, the epoch + 1 second by default
* `ownership`:
   - If `null`, omitted, or an empty string `""`, then effectively equivalent to `"0:0"` (`"root:root"`).
   - Otherwise, in the form of `"<user>:<group>"` where `<user>` and `<group>` are optional. When `<user>` or `<group>` is omitted, it is equivalent to `0` (`root`).
