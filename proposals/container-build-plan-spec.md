# Container Build Plan Specification

## Example

```
{
  "baseImage": "gcr.io/distroless/java@sha256:b715126ebd36e5d5c2fd730f46a5b3c3b760e82dc18dffff7f5498d0151137c9",
  "format": "Docker",
  "creationTime": "2011-12-03T22:42:05Z",
  "architecture": "amd64",
  "os": "linux",

  "containerConfig": {
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
        }
      ]
    },
    {
      "archive": {
        "mediaType": "...",
        "path": "/home/jane/misc/cacerts.tar",
      }
    },
    {
      "files": [
        {
          "src": "/home/workspace/scripts/run.sh",
          "dest": "/app/run.sh",
          "permissions": "777",
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

* `architecture`: string

   - `null` or omitted: inherits from the base image. If `baseImage` is not given or the `"scratch"` value, `"amd64"` by default.
   - Otherwise (including an empty string `""`), sets the given value.

* `os`: string

   - `null` or omitted: inherits from the base image. If `baseImage` is not given or the `"scratch"` value, `"linux"` by default.
   - Otherwise (including an empty string `""`), sets the given value.

* `format`: string

   Image output format. Either `"Docker"` or `"OCI"`.

   - `null` or omitted: `"Docker"` by default

* `containerConfig`: [Container Configuration object](#container-configuration-object)

* `layers`: array of [Layer Configuration objects](#layer-configuration-object)

   Adds layers on top of those from the base image; it is not possible to remove layers from the base image.

### Container Configuration Object

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
   - Otherwise (including an empty string `""`, `":"`, `<user>:`, and `":<group>"`), sets the given user and group; it is not possible to only inherit either the user or the group.

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

`entries` and `archive` cannot be defined together.

* `entries`: array of Layer Entry objects

   Collection of files as a layer.

* `archive`

   To be implemented. Allows specifying a (local or remote) archive file representing a layer.
   
### Layer Entry Object

* `src`: single local file
* `dest`: path in the container
* `permissions`: if `null` or omitted, `"644"` by default
* `modificationTime`: if `null` or omitted, the epoch + 1 second by default
