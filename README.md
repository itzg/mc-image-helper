[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/itzg/mc-image-helper?sort=semver)](https://github.com/itzg/mc-image-helper/releases/latest)

This tool does the complicated bits for [the itzg/minecraft-server Docker image](https://github.com/itzg/docker-minecraft-server).

## Usage

> **NOTE** The following documentation may not always be up to date. Please be sure to use `-h` or `--help` after any subcommand to view the current usage.

```
Usage: mc-image-helper [-h] [--debug] [COMMAND]
      --debug   Enable debug output
  -h, --help    Show this usage and exit
Commands:
  sync-and-interpolate  Synchronizes the contents of one directory to another
                          with conditional variable interpolation.
  interpolate           Interpolates existing files in one or more directories
  sync                  Synchronizes the contents of one directory to another.
  asciify               Converts UTF-8 on stdin to ASCII by escaping Unicode
                          characters
  patch                 Patches one or more existing files using JSON path
                          based operations
  get                   Download a file
```

### sync-and-interpolate

```
Usage: mc-image-helper sync-and-interpolate [-h] [--skip-newer-in-destination]
       ([--replace-env-prefix=<prefix>] [--replace-env-excludes=FILENAME[,
       FILENAME...]]... [--replace-env-exclude-paths=PATH[,PATH...]]...
       --replace-env-file-suffixes=PATH[,PATH...]
       [--replace-env-file-suffixes=PATH[,PATH...]]...) <src> <dest>
Synchronizes the contents of one directory to another with conditional variable
interpolation.
      <src>    source directory
      <dest>   destination directory
  -h, --help   Show this usage and exit
      --replace-env-exclude-paths=PATH[,PATH...]
               Destination paths that will be excluded from processing
      --replace-env-excludes=FILENAME[,FILENAME...]
               Filenames (without path) that should be excluded from processing.
      --replace-env-file-suffixes=PATH[,PATH...]
               Filename suffixes (without dot) that should be processed. For
                 example: txt,json,yaml
      --replace-env-prefix=<prefix>
               Only placeholder variables with this prefix will be processed.
                 Default: CFG_
      --skip-newer-in-destination
               Skip any files that exist in the destination and have a newer
                 modification time than the source.
```

### interpolate

```
Usage: mc-image-helper interpolate [-h] ([--replace-env-prefix=<prefix>]
                                   [--replace-env-excludes=FILENAME[,
                                   FILENAME...]]...
                                   [--replace-env-exclude-paths=PATH[,
                                   PATH...]]... --replace-env-file-suffixes=PATH
                                   [,PATH...] [--replace-env-file-suffixes=PATH
                                   [,PATH...]]...) [DIRECTORY...]
Interpolates existing files in one or more directories
      [DIRECTORY...]
  -h, --help           Show this usage and exit
      --replace-env-exclude-paths=PATH[,PATH...]
                       Destination paths that will be excluded from processing
      --replace-env-excludes=FILENAME[,FILENAME...]
                       Filenames (without path) that should be excluded from
                         processing.
      --replace-env-file-suffixes=PATH[,PATH...]
                       Filename suffixes (without dot) that should be
                         processed. For example: txt,json,yaml
      --replace-env-prefix=<prefix>
                       Only placeholder variables with this prefix will be
                         processed.
                         Default: CFG_
```

### patch

```
Usage: mc-image-helper patch [-h] [--patch-env-prefix=<envPrefix>] FILE_OR_DIR
      FILE_OR_DIR   Path to a PatchSet json file or directory containing
                      PatchDefinition json files
  -h, --help        Show this usage and exit
      --patch-env-prefix=<envPrefix>
                    Only placeholder variables with this prefix will be
                      processed
                      Default: CFG_
```

[See below](#patch-schemas) for a description of [PatchSet](#patchset) and [PatchDefinition](#patchdefinition) JSON schemas.

### get

```
Usage: mc-image-helper get [-h] [--output-filename] [--json-path=<jsonPath>]
                           [-o=FILE|DIR] <uri>
Download a file
      <uri>
  -h, --help              Show this usage and exit
      --json-path=<jsonPath>
                          Extract and output a JsonPath from the response
  -o, --output=FILE|DIR   Specifies the name of a file or directory to write
                            the downloaded content. If a directory is provided,
                            the filename will be derived from the content
                            disposition or the URI's path. If not provided,
                            then content will be output to standard out.
      --output-filename   Output the resulting filename
```

## Patch Schemas

### PatchSet

- `patches` : array of [PatchDefinition](#patchdefinition)

Example

```json
{
  "patches": [
    {
      "file": "/data/paper.yml",
      "ops": [
        {
          "$set": {
            "path": "$.verbose",
            "value": true
          }
        },
        {
          "$set": {
            "path": "$.settings['velocity-support'].enabled",
            "value": "${CFG_VELOCITY_ENABLED}",
            "value-type": "bool"
          }
        },
        {
          "$put": {
            "path": "$.settings",
            "key": "my-test-setting",
            "value": "testing"
          }
        }
      ]
    }
  ]
}
```

### PatchDefinition

- `file` : Path to the file to patch
- `file-format` : **optional** If non-null, declares a specifically supported format name: json, yaml. Otherwise, the file format is detected by the file's suffix.
- `ops` : array of [PatchOperation](#patchoperation)

Example:

```json
{
  "file": "/data/paper.yml",
  "ops": [
    {
      "$set": {
        "path": "$.verbose",
        "value": true
      }
    },
    {
      "$set": {
        "path": "$.settings['velocity-support'].enabled",
        "value": "${CFG_VELOCITY_ENABLED}",
        "value-type": "bool"
      }
    },
    {
      "$put": {
        "path": "$.settings",
        "key": "my-test-setting",
        "value": "testing"
      }
    }
  ]
}
```

### PatchOperation

Each patch operation object contains a single key that one is one of the following sections and then an object within each. For example `{ "$set" : { ... } }`.

#### `$set`

The `$set` operation can set an existing field to a new value. If a new field needs to be added, use the [$put](#put) operation.

- `$set`
  - `path` : The [JSON path](https://github.com/json-path/JsonPath#readme) to the field to set
  - `value` : The value to set. If the given value is a string, variable placeholders of the form `${...}` will be replaced from the environment variables and the resulting string can be converted by setting value-type.
  - `value-type` : **optional** [see below](#valuetype)

Example:

```json
{
  "$set": {
    "path": "$.verbose",
    "value": true
  }
}
```

#### `$put`

The `$put` operation can add or update a field with the given key within an object.

- `$put`
    - `path` : The [JSON path](https://github.com/json-path/JsonPath#readme) to the object containing key to set
    - `key` : The key to set
    - `value` : The value to set. If the given value is a string, variable placeholders of the form `${...}` will be replaced from the environment variables and the resulting string can be converted by setting value-type.
    - `value-type` : **optional** [see below](#valuetype)

Example:

```json
{
  "$put": {
    "path": "$.settings",
    "key": "my-test-setting",
    "value": "testing"
  }
}
```

### ValueType

One of the following identifiers or can be prefixed with `list of ` to indicate a list of the identified type:
- int : integer/whole value
- float : numerical value with an optional decimal part
- bool : boolean value of `true` or `false`
- auto : where the text value is attempted to be converted into one of the above