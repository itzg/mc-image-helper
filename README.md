[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/itzg/mc-image-helper?sort=semver)](https://github.com/itzg/mc-image-helper/releases/latest)
[![test](https://github.com/itzg/mc-image-helper/actions/workflows/test.yml/badge.svg)](https://github.com/itzg/mc-image-helper/actions/workflows/test.yml)

This tool does the complicated bits for the [itzg/minecraft-server](https://github.com/itzg/docker-minecraft-server) and [itzg/bungeecord](https://github.com/itzg/docker-bungeecord/) Docker images.

## Usage

> **NOTE** The following documentation may not always be up-to-date. Please be sure to use `-h` or `--help` after any subcommand to view the current usage.

```
Usage: mc-image-helper [-hs] [--debug] [COMMAND]
      --debug    Enable debug output. Can also set environment variable
                   DEBUG_HELPER
  -h, --help     Show this usage and exit
  -s, --silent   Don't output logs even if there's an error
Commands:
  asciify               Converts UTF-8 on stdin to ASCII by escaping Unicode
                          characters
  assert                Provides assertion operators for verifying container
                          setup
  compare-versions      Used for shell scripting, exits with success(0) when
                          comparison is satisfied or 1 when not
  find                  Specialized replacement for GNU's find
  get                   Download a file
  hash                  Outputs an MD5 hash of the standard input
  install-forge         Downloads and installs a requested version of Forge
  interpolate           Interpolates existing files in one or more directories
  java-release          Outputs the Java release number, such as 8, 11, 17
  maven-download        Downloads a maven artifact from a Maven repository
  modrinth              Automates downloading of modrinth resources
  patch                 Patches one or more existing files using JSON path
                          based operations
  sync                  Synchronizes the contents of one directory to another.
  sync-and-interpolate  Synchronizes the contents of one directory to another
                          with conditional variable interpolation.
  yaml-path             Extracts a path from a YAML file using json-path syntax
  vanillatweaks         Downloads Vanilla Tweaks resource packs, data packs, or
                          crafting tweaks given a share code or pack file
```

### find

```
Usage: mc-image-helper find [-hq] [--delete] [--fail-no-matches]
                            [--only-shallowest] [--output-count-only]
                            [--stop-on-first] [--format=<format>]
                            [--max-depth=N] [-t=<type>] [--exclude-name=glob[,
                            glob...]]... [--name=glob[,glob...]]... startDir...
Specialized replacement for GNU's find
      startDir...           One or more starting directories
      --delete              Deletes the matched entries. When searching for
                              directories, each directory and its contents will
                              be recursively deleted.
      --exclude-name=glob[,glob...]
                            One or more glob patterns to exclude by looking at
                              name part of the path. If a pattern matches a
                              directory's name, then its entire subtree is
                              excluded.
      --fail-no-matches
      --format=<format>     Applies a format when printing each matched entry.
                              Supports the following directives
                            %% a literal %
                            %h leading directory of the entry
                            %P path of the entry relative to the starting point
  -h, --help
      --max-depth=N         Unlimited depth if zero or negative
      --name=glob[,glob...] One or more glob patterns to match name part of the
                              path
      --only-shallowest
      --output-count-only
  -q, --quiet
      --stop-on-first
  -t, --type=<type>         Valid values: file, directory
```

### get

```
Usage: mc-image-helper get [-hsz] [--exists] [--log-progress-each]
                           [--output-filename] [--skip-existing]
                           [--accept=<acceptHeader>] [--json-path=<jsonPath>]
                           [-o=FILE|DIR] [--prune-depth=<pruneDepth>]
                           [--uris-file=<urisFile>] [--prune-others=GLOB[,
                           GLOB...]]... [URI[,URI...]...]
Download a file
      [URI[,URI...]...]     The URI of the resource to retrieve. When the
                              output is a directory, more than one URI can be
                              requested.
      --accept=<acceptHeader>
                            Specifies the accept header to use with the request
      --exists              Test if the given URIs are retrievable
  -h, --help                Show this usage and exit
      --json-path=<jsonPath>
                            Extract and output a JsonPath from the response
      --log-progress-each   Output a log as each URI is being retrieved
  -o, --output=FILE|DIR     Specifies the name of a file or directory to write
                              the downloaded content. If a directory is
                              provided, the filename will be derived from the
                              content disposition or the URI's path. If not
                              provided, then content will be output to standard
                              out.
      --output-filename     Output the resulting filename
      --prune-depth=<pruneDepth>
                            When using prune-others, this specifies how deep to
                              search for files to prune
      --prune-others=GLOB[,GLOB...]
                            When set and using an output directory, files that
                              match the given glob patterns will be pruned if
                              not part of the download set. For example *.jar
  -s, --silent              Don't output logs even if there's an error
      --skip-existing       Do not retrieve if the output file already exists
      --uris-file=<urisFile>
                            A file that contains a URL per line
  -z, --skip-up-to-date     Skips re-downloading a file that is up to date
```

### install-curseforge

```
Usage: mc-image-helper install-curseforge [-h] [--force-synchronize]
       [--file-id=<fileId>] [--filename-matcher=STR] [--modpack-page-url=URL]
       [--output-directory=DIR] [--parallel-downloads=<parallelDownloads>]
       [--results-file=FILE] [--slug=<slug>] [--exclude-include-file=FILE |
       [[--exclude-mods=PROJECT_ID|SLUG[,| PROJECT_ID|SLUG...]]...
       [--force-include-mods=PROJECT_ID|SLUG[,| PROJECT_ID|SLUG...]]...]]
       [COMMAND]
      --exclude-include-file=FILE
                            A JSON file that contains global and per modpack
                              exclude/include declarations. See README for
                              schema.
      --exclude-mods=PROJECT_ID|SLUG[,| PROJECT_ID|SLUG...]
                            For mods that need to be excluded from server
                              deployments, such as those that don't label as
                              client
      --file-id=<fileId>
      --filename-matcher=STR
                            Substring to select specific modpack filename
      --force-include-mods=PROJECT_ID|SLUG[,| PROJECT_ID|SLUG...]
                            Some mods incorrectly declare client-only support,
                              but still need to be included in a server deploy
      --force-synchronize
  -h, --help
      --modpack-page-url=URL
                            URL of a modpack page such as https://www.
                              curseforge.
                              com/minecraft/modpacks/all-the-mods-8or a file
                              page https://www.curseforge.
                              com/minecraft/modpacks/all-the-mods-8/files/424839
                              0
      --output-directory=DIR

      --parallel-downloads=<parallelDownloads>
                            Default: 4
      --results-file=FILE   A key=value file suitable for scripted environment
                              variables. Currently includes
                              SERVER: the entry point jar or script
      --slug=<slug>         The short-URL identifier
Commands:
  schemas  Output relevant JSON schemas
```

#### Exclude/Include File Schema

```json
{
  "$schema": "http://json-schema.org/draft-04/schema#",
  "title": "Mods Exclude/Include File Content",
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "globalExcludes": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Mods by slug|id to exclude for all modpacks"
    },
    "globalForceIncludes": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Mods by slug|id to force include for all modpacks"
    },
    "modpacks": {
      "type": "object",
      "additionalProperties": {
        "$ref": "#/definitions/ExcludeIncludes"
      },
      "description": "Specific exclude/includes by modpack slug"
    }
  },
  "definitions": {
    "ExcludeIncludes": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "excludes": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Mods by slug|id to exclude for this modpack"
        },
        "forceIncludes": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Mods by slug|id to force include for this modpack"
        }
      }
    }
  }
}
```

### install-fabric-loader

```
Usage: mc-image-helper install-fabric-loader [-h] [--output-directory=DIR]
       [--results-file=FILE] (--from-local-file=FILE | --from-url=URL |
       [--minecraft-version=VERSION [--installer-version=VERSION]
       [--loader-version=VERSION]])
Provides a few ways to obtain a Fabric loader with simple cleanup of previous
loader instances
      --from-local-file=FILE

      --from-url=URL
  -h, --help
      --installer-version=VERSION
                            By default the latest installer version is used
      --loader-version=VERSION
                            By default the latest launcher version is used
      --minecraft-version=VERSION

      --output-directory=DIR

      --results-file=FILE   A key=value file suitable for scripted environment
                              variables. Currently includes
                              SERVER: the entry point jar or script
```

### install-forge

```
Usage: mc-image-helper install-forge [-hV] [--forge-version=<forgeVersion>]
                                     --minecraft-version=<minecraftVersion>
                                     [--output-directory=DIR]
                                     [--results-file=<resultsFile>]
      --forge-version=<forgeVersion>
                  A specific Forge version or to auto-resolve the version
                    provide 'latest' or 'recommended'. Default value is
                    recommended
  -h, --help      Show this help message and exit.
      --minecraft-version=<minecraftVersion>

      --output-directory=DIR

      --results-file=<resultsFile>
                  A key=value file suitable for scripted environment variables.
                    Currently includes
                    SERVER: the entry point jar or script
```

### install-modrinth-modpack

```
Usage: mc-image-helper install-modrinth-modpack [-hV]
       [--force-modloader-reinstall] [--force-synchronize]
       [--api-base-url=<baseUrl>] [--default-version-type=TYPE]
       [--game-version=<gameVersion>] [--loader=<loader>]
       [--output-directory=DIR] --project=<modpackProject>
       [--results-file=FILE] [--version-id=<versionId>]
       [[--tls-handshake-timeout=DURATION] [--http-response-timeout=DURATION]
       [--connection-pool-max-idle-timeout=DURATION]]
Supports installation of Modrinth modpacks along with the associated mod loader
      --api-base-url=<baseUrl>
                            Default: https://api.modrinth.com/v2
      --connection-pool-max-idle-timeout=DURATION

      --default-version-type=TYPE
                            Valid values: release, beta, alpha
                            Default: release
      --force-modloader-reinstall

      --force-synchronize
      --game-version=<gameVersion>
                            Applicable Minecraft version where default is any
                              version
  -h, --help                Show this help message and exit.
      --http-response-timeout=DURATION
                            The response timeout to apply to HTTP operations.
                              Parsed from ISO-8601 format. Default: PT30S
      --loader=<loader>     Valid values: fabric, forge where default is any
                              loader
      --output-directory=DIR

      --project=<modpackProject>
                            One of
                            - Project ID or slug
                            - Project page URL
                            - Project file URL
      --results-file=FILE   A key=value file suitable for scripted environment
                              variables. Currently includes
                              SERVER: the entry point jar or script
      --tls-handshake-timeout=DURATION
                            Default: PT30S
  -V, --version             Print version information and exit.
      --version-id=<versionId>
                            Version ID (not name) from the file's metadata
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

### maven-download

```
Usage: mc-image-helper maven-download [-h] [--print-filename] [--skip-existing]
                                      [--skip-up-to-date] -a=<artifact>
                                      [--classifier=<classifier>] -g=<group>
                                      [--output-directory=<outputDirectory>]
                                      [--packaging=<packaging>]
                                      [-r=<mavenRepo>] [-v=<version>]
Downloads a maven artifact from a Maven repository
  -a, -m, --module, --artifact=<artifact>

      --classifier=<classifier>

  -g, --group=<group>
  -h, --help
      --output-directory=<outputDirectory>

      --packaging=<packaging>

      --print-filename
  -r, --maven-repo=<mavenRepo>

      --skip-existing
      --skip-up-to-date
  -v, --version=<version>
```

### mcopy

```
Usage: mc-image-helper mcopy [-hz] [--file-is-listing] [--skip-existing]
                             [--glob=GLOB] [--scope=<manifestId>] --to=<dest>
                             SRC[,SRC...]...
Multi-source file copy operation with with managed cleanup. Supports
auto-detected sourcing from file list, directories, and URLs
      SRC[,SRC...]...     Any mix of source file, directory, or URL that can be
                            optionally comma-separated.
      --file-is-listing   Source files or URLs are processed as a line
                            delimited list of sources.
                          For remote listing files, the contents must all be
                            file URLs.
      --glob=GLOB         When a source is a directory, this filename glob will
                            be applied to select files.
  -h, --help
      --scope, --manifest-id=<manifestId>
                          If managed cleanup is required, this is the
                            identifier used for qualifying manifest filename in
                            destination
      --skip-existing
      --to, --output-directory=<dest>

  -z, --skip-up-to-date
```

### patch

Supports the file formats:
- JSON
- JSON5
- Yaml
- TOML, but processed output is not pretty

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

### install-quilt

```
Usage: mc-image-helper install-quilt [-h] [--force-reinstall]
                                     [--loader-version=VERSION]
                                     [--minecraft-version=VERSION]
                                     [--output-directory=DIR]
                                     [--repo-url=<repoUrl>]
                                     [--results-file=FILE] [--installer-url=URL
                                     | --installer-version=VERSION]
                                     [[--tls-handshake-timeout=DURATION]
                                     [--connection-pool-max-idle-timeout=DURATIO
                                     N] [--http-response-timeout=DURATION]]
Installs Quilt mod loader
      --connection-pool-max-idle-timeout=DURATION

      --force-reinstall
  -h, --help
      --http-response-timeout=DURATION
                             The response timeout to apply to HTTP operations.
                               Parsed from ISO-8601 format. Default: PT30S
      --installer-url=URL
      --installer-version=VERSION
                             Default uses latest
      --loader-version=VERSION
                             Default uses latest
      --minecraft-version=VERSION
                             'latest', 'snapshot', or specific version
      --output-directory=DIR
      --repo-url=<repoUrl>   Default: https://maven.quiltmc.
                               org/repository/release
      --results-file=FILE    A key=value file suitable for scripted environment
                               variables. Currently includes
                               SERVER: the entry point jar or script
      --tls-handshake-timeout=DURATION
                             Default: PT30S
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