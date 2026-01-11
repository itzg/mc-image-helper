[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/itzg/mc-image-helper?sort=semver)](https://github.com/itzg/mc-image-helper/releases/latest)
[![test](https://github.com/itzg/mc-image-helper/actions/workflows/test.yml/badge.svg)](https://github.com/itzg/mc-image-helper/actions/workflows/test.yml)
![LOC](https://img.shields.io/endpoint?url=https%3A%2F%2Fshields-codetab-code-loc-bridge.vercel.app%2Fapi%2Fcodeloc%3Fgithub%3Ditzg%2Fmc-image-helper%26language%3Djava)

This tool does the complicated bits for the [itzg/minecraft-server](https://github.com/itzg/docker-minecraft-server) and [itzg/docker-mc-proxy](https://github.com/itzg/docker-mc-proxy) Docker images.

## Usage

> **NOTE** The following documentation may not always be up-to-date. Please be sure to use `-h` or `--help` after any subcommand to view the current usage.

```
Usage: mc-image-helper [-hsV] [--debug | --logging=<loggingLevel>] [COMMAND]
      --debug     Enable debug output. Can also set environment variables
                    DEBUG_HELPER or DEBUG
  -h, --help      Show this usage and exit
      --logging=<loggingLevel>
                  Set logging to specific level.
                  Valid values:
  -s, --silent    Don't output logs even if there's an error
  -V, --version
Commands:
  asciify                         Converts UTF-8 on stdin to ASCII by escaping
                                    Unicode characters
  assert                          Provides assertion operators for verifying
                                    container setup
  compare-versions                Used for shell scripting, exits with success
                                    (0) when comparison is satisfied or 1 when
                                    not
  curseforge-files                Download and manage individual mod/plugin
                                    files from CurseForge
  find                            Specialized replacement for GNU's find
  get                             Download a file
  github
  hash                            Outputs an MD5 hash of the standard input
  ini-path                        Extracts a field from an INI file
  install-curseforge              Downloads, installs, and upgrades CurseForge
                                    modpacks
  install-fabric-loader           Provides a few ways to obtain a Fabric loader
                                    with simple cleanup of previous loader
                                    instances
  install-forge                   Downloads and installs a requested version of
                                    Forge
  install-modrinth-modpack        Supports installation of Modrinth modpacks
                                    along with the associated mod loader
  install-neoforge                Downloads and installs a requested version of
                                    NeoForge
  install-paper                   Installs selected PaperMC
  install-purpur                  Downloads latest or selected version of Purpur
  install-quilt                   Installs Quilt mod loader
  interpolate                     Interpolates existing files in one or more
                                    directories
  java-release                    Outputs the Java release number, such as 8,
                                    11, 17
  manage-users
  maven-download                  Downloads a maven artifact from a Maven
                                    repository
  modrinth                        Automates downloading of modrinth resources
  mcopy                           Multi-source file copy operation with with
                                    managed cleanup. Supports auto-detected
                                    sourcing from file list, directories, and
                                    URLs
  network-interfaces              Provides simple operations to list network
                                    interface names and check existence
  patch                           Patches one or more existing files using JSON
                                    path based operations
                                  Supports the file formats:
                                  - JSON
                                  - JSON5
                                  - Yaml
                                  - TOML, but processed output is not pretty
  resolve-minecraft-version       Resolves and validate latest, snapshot, and
                                    specific versions
  set-properties                  Maps environment variables to a properties
                                    file
  show-all-subcommand-usage       Renders all of the subcommand usage as
                                    markdown sections for README
  sync                            Synchronizes the contents of one directory to
                                    another.
  sync-and-interpolate            Synchronizes the contents of one directory to
                                    another with conditional variable
                                    interpolation.
  test-logging-levels
  toml-path                       Extracts a path from a TOML file using
                                    json-path syntax
  vanillatweaks                   Downloads Vanilla Tweaks resource packs, data
                                    packs, or crafting tweaks given a share
                                    code or pack file
  version-from-modrinth-projects  Finds a compatible Minecraft version across
                                    given Modrinth projects
  yaml-path                       Extracts a path from a YAML file using
                                    json-path syntax
```

For [patch](#patch) command [see below](#patch-schemas) for a description of [PatchSet](#patchset) and [PatchDefinition](#patchdefinition) JSON schemas.

For [install-curseforge](#install-curseforge) and [install-modrinth-modpack](#install-modrinth-modpack) commands, refer to [the exclude/include file schema](#excludeinclude-file-schema).

<!-- START of documentation generated using `mc-image-helper show-all-subcommand-usage` -->

### asciify

```
Usage: mc-image-helper asciify
Converts UTF-8 on stdin to ASCII by escaping Unicode characters
```

### assert

```
Usage: mc-image-helper assert [COMMAND]
Provides assertion operators for verifying container setup
Commands:
  fileExists
  fileNotExists
  jsonPathEquals
  propertyEquals
```

### compare-versions

```
Usage: mc-image-helper compare-versions <leftVersion> <comparison>
                                        <rightVersion>
Used for shell scripting, exits with success(0) when comparison is satisfied or
1 when not
      <leftVersion>
      <comparison>
      <rightVersion>
```

### curseforge-files

```
Usage: mc-image-helper curseforge-files [-h] [--disable-api-caching]
                                        [--api-base-url=<apiBaseUrl>]
                                        [--api-key=<apiKey>]
                                        [--default-category=<slugCategory>]
                                        [--game-version=<gameVersion>]
                                        [--mod-loader=<modLoaderType>] [-o=DIR]
                                        [[--api-cache-ttl=OPERATION=DURATION]...
                                         [--api-cache-default-ttl=DURATION]]
                                        [[--connection-pool-pending-acquire-time
                                        out=DURATION]
                                        [--http-response-timeout=DURATION]
                                        [--tls-handshake-timeout=DURATION]
                                        [--use-http2] [--wiretap]
                                        [--connection-pool-max-idle-timeout=DURA
                                        TION]] [REF[,|<nl>REF...]...]
Download and manage individual mod/plugin files from CurseForge
      [REF[,|<nl>REF...]...]
                           Can be <project ID>|<slug>':'<file ID>, <project
                             ID>|<slug>'@'<filename matcher>, <project
                             ID>|<slug>, project page URL, file page URL,
                             '@'<filename with ref per line>%nIf not specified,
                             any previous mod/plugin files are removed.%
                             Embedded comments are allowed
      --api-base-url=<apiBaseUrl>
                           Allows for overriding the CurseForge Eternal API used
                           Can also be passed via CF_API_BASE_URL
      --api-cache-default-ttl=DURATION
                           Set default/fallback TTL in ISO-8601 duration format.
                           Default: P2D
      --api-cache-ttl=OPERATION=DURATION
                           Set individual operation TTLs
      --api-key=<apiKey>   An API key allocated from the Eternal developer
                             console at https://console.curseforge.com/
                           Can also be passed via CF_API_KEY
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --default-category=<slugCategory>
                           When providing slugs, a category is required to
                             qualify those
      --disable-api-caching

      --game-version=<gameVersion>
                           The Minecraft version
                           Can also be passed via VERSION
  -h, --help
      --http-response-timeout=DURATION
                           The response timeout to apply to HTTP operations.
                             Parsed from ISO-8601 format. Default: PT30S
      --mod-loader=<modLoaderType>
                           One of Any, Forge, Cauldron, LiteLoader, Fabric,
                             Quilt, NeoForge
  -o, --output-directory=DIR

      --tls-handshake-timeout=DURATION
                           Default: PT30S
      --use-http2          Whether to use HTTP/2. Default: false
      --wiretap            Whether to enable Reactor Netty wiretap logging.
                             Default: false
```

### find

```
Usage: mc-image-helper find [-hq] [--delete] [--delete-empty-directories]
                            [--fail-no-matches] [--only-shallowest]
                            [--output-count-only] [--stop-on-first]
                            [--format=<format>] [--max-depth=N] [--min-depth=N]
                            [--exclude-name=glob[\s*,\s*glob...]]...
                            [--name=glob[\s*,\s*glob...]]... [-t=<type>[\s*,
                            \s*<type>...]]... startDir...
Specialized replacement for GNU's find
      startDir...           One or more starting directories
      --delete              Deletes the matched entries. When searching for
                              directories, each directory and its contents will
                              be recursively deleted.
      --delete-empty-directories
                            Deletes a traversed directory if it becomes empty
                              after matching files/directories within it were
                              deleted
      --exclude-name=glob[\s*,\s*glob...]
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
      --max-depth=N         Unlimited depth if negative
      --min-depth=N         Minimum match depth where 0 is a starting point
      --name=glob[\s*,\s*glob...]
                            One or more glob patterns to match name part of the
                              path
      --only-shallowest
      --output-count-only
  -q, --quiet
      --stop-on-first
  -t, --type=<type>[\s*,\s*<type>...]
                            Valid values: file, directory
```

### get

```
Usage: mc-image-helper get [-hz] [--exists] [--log-progress-each]
                           [--output-filename] [--skip-existing]
                           [--apikey=<apikeyHeader>] [--json-path=<jsonPath>]
                           [--json-value-when-missing=<jsonValueWhenMissing>]
                           [-o=FILE|DIR] [--prune-depth=<pruneDepth>]
                           [--retry-count=<retryCount>]
                           [--retry-delay=<retryDelay>]
                           [--uris-file=<urisFile>]
                           [--accept=<acceptContentTypes>[\s*,
                           \s*<acceptContentTypes>...]]... [--prune-others=GLOB
                           [\s*,\s*GLOB...]]... [URI[\s*,\s*URI...]...]
Download a file
      [URI[\s*,\s*URI...]...]
                            The URI of the resource to retrieve. When the
                              output is a directory, more than one URI can be
                              requested.
      --accept=<acceptContentTypes>[\s*,\s*<acceptContentTypes>...]
                            Specifies the accepted content type(s) to use with
                              the request
      --apikey=<apikeyHeader>
                            Specifies the accept header to use with the request
      --exists              Test if the given URIs are retrievable
  -h, --help                Show this usage and exit
      --json-path=<jsonPath>
                            Extract and output a JsonPath from the response
      --json-value-when-missing=<jsonValueWhenMissing>
                            Defines the value that is output when the requested
                              JSON path does not exist. An empty value results
                              in a non-zero exit code.
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
      --prune-others=GLOB[\s*,\s*GLOB...]
                            When set and using an output directory, files that
                              match the given glob patterns will be pruned if
                              not part of the download set. For example *.jar
      --retry-count=<retryCount>

      --retry-delay=<retryDelay>
                            in seconds
      --skip-existing       Do not retrieve if the output file already exists
      --uris-file=<urisFile>
                            A file that contains a URL per line
  -z, --skip-up-to-date     Skips re-downloading a file that is up to date
```

### github

```
Usage: mc-image-helper github [--api-base-url=<apiBaseUrl>] [--token=<token>]
                              [COMMAND]
      --api-base-url=<apiBaseUrl>

      --token=<token>   An access token for GitHub to elevate rate limit vs
                          anonymous access
Commands:
  download-latest-asset  From the latest release, downloads the first matching
                           asset, and outputs the downloaded filename
```

### hash

```
Usage: mc-image-helper hash
Outputs an MD5 hash of the standard input
```

### ini-path

```
Usage: mc-image-helper ini-path [--file=FILE] ref
Extracts a field from an INI file
      ref           section/option, section/option[index], /option, /option
                      [index]
      --file=FILE   An INI file to query. If not set, reads stdin
```

### install-curseforge

```
Usage: mc-image-helper install-curseforge [-h] [--disable-api-caching]
       [--force-reinstall-modloader] [--force-synchronize]
       [--overrides-skip-existing] [--api-base-url=<apiBaseUrl>]
       [--api-key=<apiKey>] [--downloads-repo=DIR]
       [--file-download-retries=COUNT]
       [--file-download-retry-min-delay=DURATION] [--file-id=<fileId>]
       [--filename-matcher=STR]
       [--max-concurrent-downloads=<maxConcurrentDownloads>]
       [--missing-mods-filename=<missingModsFilename>]
       [--mod-loader-version=VERSION] [--modpack-manifest=PATH]
       [--modpack-page-url=URL] [--modpack-zip=PATH] [-o=DIR]
       [--results-file=FILE] [--set-level-from=<levelFrom>] [--slug=<slug>]
       [--ignore-missing-files=<ignoreMissingFiles>[,
       |<nl><ignoreMissingFiles>...]]...
       [--overrides-exclusions=<overridesExclusions>[NL or ,
       <overridesExclusions>...]]... [[--exclude-include-file=FILE|URI]
       [--exclude-all-mods] [[--excludes=PROJECT_ID|SLUG[,
       |<nl>PROJECT_ID|SLUG...]]... [--force-includes=PROJECT_ID|SLUG[,
       |<nl>PROJECT_ID|SLUG...]]...]]
       [[--connection-pool-pending-acquire-timeout=DURATION]
       [--http-response-timeout=DURATION] [--tls-handshake-timeout=DURATION]
       [--use-http2] [--wiretap] [--connection-pool-max-idle-timeout=DURATION]]
       [[--api-cache-ttl=OPERATION=DURATION]...
       [--api-cache-default-ttl=DURATION]] [[--forge-promotions-url=URL]
       [--forge-maven-repo-url=URL]] [COMMAND]
Downloads, installs, and upgrades CurseForge modpacks
      --api-base-url=<apiBaseUrl>
                             Allows for overriding the CurseForge Eternal API
                               used
      --api-cache-default-ttl=DURATION
                             Set default/fallback TTL in ISO-8601 duration
                               format.
                             Default: P2D
      --api-cache-ttl=OPERATION=DURATION
                             Set individual operation TTLs
      --api-key=<apiKey>     An API key allocated from the Eternal developer
                               console at https://console.curseforge.com/
                             Can also be passed via CF_API_KEY
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --disable-api-caching
      --downloads-repo=DIR   A local directory that will supply pre-downloaded
                               mod and modpack files that are marked disallowed
                               for automated download. The subdirectories mods,
                               modpacks, and worlds will also be consulted
                               accordingly.
      --exclude-all-mods     Exclude all mods regardless of manifest contents
      --exclude-include-file=FILE|URI
                             A JSON file that contains global and per modpack
                               exclude/include declarations. See README for
                               schema.
      --excludes, --exclude-mods=PROJECT_ID|SLUG[,|<nl>PROJECT_ID|SLUG...]
                             For mods that need to be excluded from server
                               deployments, such as those that don't label as
                               client
      --file-download-retries=COUNT
                             Default is 5
      --file-download-retry-min-delay=DURATION
                             Default is PT5S
      --file-id=<fileId>
      --filename-matcher=STR Substring to select specific modpack filename
      --force-includes, --force-include-mods=PROJECT_ID|SLUG[,
        |<nl>PROJECT_ID|SLUG...]
                             Some mods incorrectly declare client-only support,
                               but still need to be included in a server deploy.
                             This can also be used to selectively override
                               exclusions.
      --force-reinstall-modloader

      --force-synchronize
      --forge-maven-repo-url=URL
                             URL for Forge Maven repository where installer is
                               downloaded.
                             Can also be set via env var FORGE_MAVEN_REPO_URL
                             Default is https://maven.minecraftforge.net
      --forge-promotions-url=URL
                             URL for Forge promotions JSON.
                             Can also be set via env var FORGE_PROMOTIONS_URL
                             Default is https://files.minecraftforge.
                               net/net/minecraftforge/forge/promotions_slim.json
  -h, --help
      --http-response-timeout=DURATION
                             The response timeout to apply to HTTP operations.
                               Parsed from ISO-8601 format. Default: PT30S
      --ignore-missing-files=<ignoreMissingFiles>[,|<nl><ignoreMissingFiles>...]
                             These files will be ignored when evaluating if the
                               modpack is up to date
      --max-concurrent-downloads=<maxConcurrentDownloads>
                             Default is 10
      --missing-mods-filename=<missingModsFilename>

      --mod-loader-version=VERSION
                             Override the mod loader version specified in the
                               modpack
      --modpack-manifest=PATH
                             Similar to --modpack-zip but provide the manifest.
                               json from the modpack.
                             Can be a local file path or a URL to a manifest.
      --modpack-page-url=URL URL of a modpack page such as
                             https://www.curseforge.
                               com/minecraft/modpacks/all-the-mods-8or a file's
                               page
                             https://www.curseforge.
                               com/minecraft/modpacks/all-the-mods-8/files/42483
                               90
      --modpack-zip=PATH     Path to a pre-downloaded modpack client zip file
                               that can be used when modpack author disallows
                               automation.
                             Can also be passed via CF_MODPACK_ZIP
  -o, --output-directory=DIR
      --overrides-exclusions=<overridesExclusions>[NL or ,
        <overridesExclusions>...]
                             Excludes files from the overrides that match these
                               ant-style patterns
                             *  : matches any non-slash characters
                             ** : matches any characters
                             ?  : matches one character
      --overrides-skip-existing
                             When enabled, existing files will not be replaced
                               by overrides content from the modpack
      --results-file=FILE    A key=value file suitable for scripted environment
                               variables. Currently includes
                               SERVER: the entry point jar or script
      --set-level-from=<levelFrom>
                             When WORLD_FILE, a world file included the modpack
                               will be unzipped into a folder under 'saves' and
                               referenced as 'LEVEL' in the results file.
                             When OVERRIDES and the overrides contains a world
                               save directory (contains level.dat), then that
                               directory will be referenced as 'LEVEL' in the
                               results file.
                             In either case, existing world data will be
                               preserved and skipped if it already exists.
                             Valid values: WORLD_FILE, OVERRIDES
      --slug=<slug>          The short-URL identifier
      --tls-handshake-timeout=DURATION
                             Default: PT30S
      --use-http2            Whether to use HTTP/2. Default: false
      --wiretap              Whether to enable Reactor Netty wiretap logging.
                               Default: false
Commands:
  schemas  Output relevant JSON schemas
```

### install-fabric-loader

```
Usage: mc-image-helper install-fabric-loader [-h] [--force-reinstall]
       [--output-directory=DIR] [--results-file=FILE] [--from-local-file=FILE |
       --from-url=URL | [[--minecraft-version=VERSION]
       [--installer-version=VERSION] [--loader-version=VERSION]]]
       [[--connection-pool-pending-acquire-timeout=DURATION]
       [--http-response-timeout=DURATION] [--tls-handshake-timeout=DURATION]
       [--use-http2] [--wiretap] [--connection-pool-max-idle-timeout=DURATION]]
Provides a few ways to obtain a Fabric loader with simple cleanup of previous
loader instances
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --force-reinstall     Force reinstall of the loader even if it already
                              exists
      --from-local-file=FILE

      --from-url=URL
  -h, --help
      --http-response-timeout=DURATION
                            The response timeout to apply to HTTP operations.
                              Parsed from ISO-8601 format. Default: PT30S
      --installer-version=VERSION
                            By default the latest installer version is used
      --loader-version=VERSION
                            By default the latest launcher version is used
      --minecraft-version=VERSION

      --output-directory=DIR

      --results-file=FILE   A key=value file suitable for scripted environment
                              variables. Currently includes
                              SERVER: the entry point jar or script
      --tls-handshake-timeout=DURATION
                            Default: PT30S
      --use-http2           Whether to use HTTP/2. Default: false
      --wiretap             Whether to enable Reactor Netty wiretap logging.
                              Default: false
```

### install-forge

```
Usage: mc-image-helper install-forge [-h] [--force-reinstall]
                                     [--minecraft-version=VERSION]
                                     [--output-directory=DIR]
                                     [--results-file=FILE]
                                     [--forge-installer=FILE |
                                     [--forge-version=<version>]]
                                     [[--connection-pool-pending-acquire-timeout
                                     =DURATION]
                                     [--http-response-timeout=DURATION]
                                     [--tls-handshake-timeout=DURATION]
                                     [--use-http2] [--wiretap]
                                     [--connection-pool-max-idle-timeout=DURATIO
                                     N]] [[--forge-promotions-url=URL]
                                     [--forge-maven-repo-url=URL]]
Downloads and installs a requested version of Forge
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION
[picocli WARN] Could not format 'Can be <project ID>|<slug>':'<file ID>, <project ID>|<slug>'@'<filename matcher>, <project ID>|<slug>, project page URL, file page URL, '@'<filename with ref per line>%nIf not specified, any previous mod/plugin files are removed.%Embedded comments are allowed' (Underlying error: Format specifier '%E'). Using raw String: '%n' format strings have not been replaced with newlines. Please ensure to escape '%' characters with another '%'.

      --force-reinstall
      --forge-installer=FILE
                            Use a local forge installer
      --forge-maven-repo-url=URL
                            URL for Forge Maven repository where installer is
                              downloaded.
                            Can also be set via env var FORGE_MAVEN_REPO_URL
                            Default is https://maven.minecraftforge.net
      --forge-promotions-url=URL
                            URL for Forge promotions JSON.
                            Can also be set via env var FORGE_PROMOTIONS_URL
                            Default is https://files.minecraftforge.
                              net/net/minecraftforge/forge/promotions_slim.json
      --forge-version=<version>
                            A specific Forge version or to auto-resolve the
                              version provide 'latest' or 'recommended'.
                              Default value is recommended
  -h, --help
      --http-response-timeout=DURATION
                            The response timeout to apply to HTTP operations.
                              Parsed from ISO-8601 format. Default: PT30S
      --minecraft-version=VERSION
                            'latest', which is the default, or a specific
                              version
      --output-directory=DIR

      --results-file=FILE   A key=value file suitable for scripted environment
                              variables. Currently includes
                              SERVER: the entry point jar or script
      --tls-handshake-timeout=DURATION
                            Default: PT30S
      --use-http2           Whether to use HTTP/2. Default: false
      --wiretap             Whether to enable Reactor Netty wiretap logging.
                              Default: false
```

### install-modrinth-modpack

```
Usage: mc-image-helper install-modrinth-modpack [--force-modloader-reinstall]
       [--force-synchronize] [--api-base-url=<baseUrl>]
       [--default-exclude-includes=FILE|URI] [--default-version-type=TYPE]
       [--game-version=<gameVersion>] [--loader=<loader>]
       [--max-concurrent-downloads=<maxConcurrentDownloads>]
       [--output-directory=DIR] --project=<modpackProject>
       [--results-file=FILE] [--version=<version>]
       [--exclude-files=<excludeFiles>[,|<nl><excludeFiles>...]]...
       [--force-include-files=<forceIncludeFiles>[,
       |<nl><forceIncludeFiles>...]]...
       [--ignore-missing-files=<ignoreMissingFiles>[,
       |<nl><ignoreMissingFiles>...]]...
       [--overrides-exclusions=<overridesExclusions>[NL or ,
       <overridesExclusions>...]]...
       [[--connection-pool-pending-acquire-timeout=DURATION]
       [--http-response-timeout=DURATION] [--tls-handshake-timeout=DURATION]
       [--use-http2] [--wiretap] [--connection-pool-max-idle-timeout=DURATION]]
       [[--forge-promotions-url=URL] [--forge-maven-repo-url=URL]]
Supports installation of Modrinth modpacks along with the associated mod loader
      --api-base-url=<baseUrl>
                            Default: https://api.modrinth.com
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --default-exclude-includes=FILE|URI
                            A JSON file that contains global and per modpack
                              exclude/include declarations. See README for
                              schema.
      --default-version-type=TYPE
                            Valid values: release, beta, alpha
                            Default: release
      --exclude-files=<excludeFiles>[,|<nl><excludeFiles>...]
                            Files to exclude, such as improperly declared
                              client mods. It will match any part of the file's
                              name/path.
                            Embedded comments are allowed.
      --force-include-files=<forceIncludeFiles>[,|<nl><forceIncludeFiles>...]
                            Files to force include that were marked as
                              non-server mods. It will match any part of the
                              file's name/path.
                            Embedded comments are allowed.
      --force-modloader-reinstall

      --force-synchronize
      --forge-maven-repo-url=URL
                            URL for Forge Maven repository where installer is
                              downloaded.
                            Can also be set via env var FORGE_MAVEN_REPO_URL
                            Default is https://maven.minecraftforge.net
      --forge-promotions-url=URL
                            URL for Forge promotions JSON.
                            Can also be set via env var FORGE_PROMOTIONS_URL
                            Default is https://files.minecraftforge.
                              net/net/minecraftforge/forge/promotions_slim.json
      --game-version=<gameVersion>
                            Applicable Minecraft version
                            Default: (any)
      --http-response-timeout=DURATION
                            The response timeout to apply to HTTP operations.
                              Parsed from ISO-8601 format. Default: PT30S
      --ignore-missing-files=<ignoreMissingFiles>[,|<nl><ignoreMissingFiles>...]
                            These files will be ignored when evaluating if the
                              modpack is up to date
      --loader=<loader>     Valid values: fabric, forge, quilt, neoforge
                            Default: (any)
      --max-concurrent-downloads=<maxConcurrentDownloads>
                            Can also set env var
                              MODRINTH_MAX_CONCURRENT_DOWNLOADS
                            Default is 10
      --output-directory=DIR

      --overrides-exclusions=<overridesExclusions>[NL or ,
        <overridesExclusions>...]
                            Excludes files from the overrides that match these
                              ant-style patterns
                            *  : matches any non-slash characters
                            ** : matches any characters
                            ?  : matches one character
                            Embedded comments are allowed.
      --project=<modpackProject>
                            One of
                            - Project ID or slug
                            - Project page URL
                            - Project file URL
                            - Custom URL of a hosted modpack file
                            - Local path to a modpack file
      --results-file=FILE   A key=value file suitable for scripted environment
                              variables. Currently includes
                              SERVER: the entry point jar or script
      --tls-handshake-timeout=DURATION
                            Default: PT30S
      --use-http2           Whether to use HTTP/2. Default: false
      --version, --version-id=<version>
                            Version ID, name, or number from the file's metadata
                            Default chooses newest file based on game version,
                              loader, and/or default version type
      --wiretap             Whether to enable Reactor Netty wiretap logging.
                              Default: false
```

### install-neoforge

```
Usage: mc-image-helper install-neoforge [-h] [--force-reinstall]
                                        [--minecraft-version=VERSION]
                                        [--neoforge-version=<version>]
                                        [--output-directory=DIR]
                                        [--results-file=FILE]
                                        [[--connection-pool-pending-acquire-time
                                        out=DURATION]
                                        [--http-response-timeout=DURATION]
                                        [--tls-handshake-timeout=DURATION]
                                        [--use-http2] [--wiretap]
                                        [--connection-pool-max-idle-timeout=DURA
                                        TION]]
Downloads and installs a requested version of NeoForge
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --force-reinstall
  -h, --help
      --http-response-timeout=DURATION
                            The response timeout to apply to HTTP operations.
                              Parsed from ISO-8601 format. Default: PT30S
      --minecraft-version=VERSION
                            'latest', which is the default, or a specific
                              version to narrow NeoForge version selection
      --neoforge-version=<version>
                            A specific NeoForge version, 'latest', or 'beta'.
                              Default value is latest
      --output-directory=DIR

      --results-file=FILE   A key=value file suitable for scripted environment
                              variables. Currently includes
                              SERVER: the entry point jar or script
      --tls-handshake-timeout=DURATION
                            Default: PT30S
      --use-http2           Whether to use HTTP/2. Default: false
      --wiretap             Whether to enable Reactor Netty wiretap logging.
                              Default: false
```

### install-paper

```
Usage: mc-image-helper install-paper [--check-updates] [--base-url=<baseUrl>]
                                     [-o=<outputDirectory>]
                                     [--results-file=FILE] [--url=<downloadUrl>
                                     | [[--project=<project>] [--build=<build>]
                                     [--channel=<channel>]
                                     [--version=<version>]]]
                                     [--connection-pool-pending-acquire-timeout=
                                     DURATION |
                                     [--http-response-timeout=DURATION] |
                                     [--tls-handshake-timeout=DURATION] |
                                     [--use-http2] | [--wiretap] |
                                     [--connection-pool-max-idle-timeout=DURATIO
                                     N]]
Installs selected PaperMC
      --base-url=<baseUrl>
      --build=<build>
      --channel=<channel>    This is ignored for now
      --check-updates        Check for updates and exit with status code 0 when
                               available
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --http-response-timeout=DURATION
                             The response timeout to apply to HTTP operations.
                               Parsed from ISO-8601 format. Default: PT30S
  -o, --output-directory=<outputDirectory>

      --project=<project>
      --results-file=FILE    A key=value file suitable for scripted environment
                               variables. Currently includes
                               SERVER: the entry point jar or script
      --tls-handshake-timeout=DURATION
                             Default: PT30S
      --url=<downloadUrl>    Use a custom URL location
      --use-http2            Whether to use HTTP/2. Default: false
      --version=<version>    May be 'latest' or specific version
      --wiretap              Whether to enable Reactor Netty wiretap logging.
                               Default: false
```

### install-purpur

```
Usage: mc-image-helper install-purpur [--base-url=<baseUrl>]
                                      [-o=<outputDirectory>]
                                      [--results-file=FILE]
                                      [--url=<downloadUrl> | [[--build=<build>]
                                      [--version=<version>]]]
                                      [--connection-pool-pending-acquire-timeout
                                      =DURATION |
                                      [--http-response-timeout=DURATION] |
                                      [--tls-handshake-timeout=DURATION] |
                                      [--use-http2] | [--wiretap] |
                                      [--connection-pool-max-idle-timeout=DURATI
                                      ON]]
Downloads latest or selected version of Purpur
      --base-url=<baseUrl>
      --build=<build>
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --http-response-timeout=DURATION
                             The response timeout to apply to HTTP operations.
                               Parsed from ISO-8601 format. Default: PT30S
  -o, --output-directory=<outputDirectory>

      --results-file=FILE    A key=value file suitable for scripted environment
                               variables. Currently includes
                               SERVER: the entry point jar or script
      --tls-handshake-timeout=DURATION
                             Default: PT30S
      --url=<downloadUrl>    Use a custom URL location
      --use-http2            Whether to use HTTP/2. Default: false
      --version=<version>    May be 'latest' or specific version
      --wiretap              Whether to enable Reactor Netty wiretap logging.
                               Default: false
```

### install-quilt

```
Usage: mc-image-helper install-quilt [-h] [--force-reinstall]
                                     [--loader-version=VERSION]
                                     [--minecraft-version=VERSION]
                                     [--output-directory=DIR]
                                     [--repo-url=<repoUrl>]
                                     [--results-file=FILE] [--installer-url=URL
                                     | --installer-version=VERSION]
                                     [[--connection-pool-pending-acquire-timeout
                                     =DURATION]
                                     [--http-response-timeout=DURATION]
                                     [--tls-handshake-timeout=DURATION]
                                     [--use-http2] [--wiretap]
                                     [--connection-pool-max-idle-timeout=DURATIO
                                     N]]
Installs Quilt mod loader
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

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
      --use-http2            Whether to use HTTP/2. Default: false
      --wiretap              Whether to enable Reactor Netty wiretap logging.
                               Default: false
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

### java-release

```
Usage: mc-image-helper java-release
Outputs the Java release number, such as 8, 11, 17
```

### manage-users

```
Usage: mc-image-helper manage-users [-fh] [--existing=<existingFileBehavior>]
                                    [--mojang-api-base-url=<mojangApiBaseUrl>]
                                    [--output-directory=<outputDirectory>]
                                    [--playerdb-api-base-url=<playerdbApiBaseUrl
                                    >] -t=<type>
                                    [--user-api-provider=<userApiProvider>]
                                    [--version=<version>]
                                    [[--connection-pool-pending-acquire-timeout=
                                    DURATION]
                                    [--http-response-timeout=DURATION]
                                    [--tls-handshake-timeout=DURATION]
                                    [--use-http2] [--wiretap]
                                    [--connection-pool-max-idle-timeout=DURATION
                                    ]] [INPUT[,INPUT...]...]
      [INPUT[,INPUT...]...] One or more Mojang usernames, UUID, or ID (UUID
                              without dashes); however, when offline, only
                              UUID/IDs can be provided.
                            When input is a file, only one local file path or
                              URL can be provided
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --existing=<existingFileBehavior>
                            Select the behavior when the resulting file already
                              exists
                            Allowed: SYNCHRONIZE, MERGE, SKIP
  -f, --input-is-file
  -h, --help
      --http-response-timeout=DURATION
                            The response timeout to apply to HTTP operations.
                              Parsed from ISO-8601 format. Default: PT30S
      --mojang-api-base-url=<mojangApiBaseUrl>

      --output-directory=<outputDirectory>

      --playerdb-api-base-url=<playerdbApiBaseUrl>

  -t, --type=<type>         Allowed: JAVA_WHITELIST, JAVA_OPS
      --tls-handshake-timeout=DURATION
                            Default: PT30S
      --use-http2           Whether to use HTTP/2. Default: false
      --user-api-provider=<userApiProvider>
                            Allowed: mojang, playerdb
      --version=<version>   Minecraft game version. If not provided, assumes
                              JSON format
      --wiretap             Whether to enable Reactor Netty wiretap logging.
                              Default: false
```

### maven-download

```
Usage: mc-image-helper maven-download [-h] [--print-filename] [--skip-existing]
                                      [--skip-up-to-date] -a=<artifact>
                                      [--classifier=<classifier>] -g=<group>
                                      [--output-directory=<outputDirectory>]
                                      [--packaging=<packaging>]
                                      [-r=<mavenRepo>] [-v=<version>]
                                      [[--connection-pool-pending-acquire-timeou
                                      t=DURATION]
                                      [--http-response-timeout=DURATION]
                                      [--tls-handshake-timeout=DURATION]
                                      [--use-http2] [--wiretap]
                                      [--connection-pool-max-idle-timeout=DURATI
                                      ON]]
Downloads a maven artifact from a Maven repository
  -a, -m, --module, --artifact=<artifact>

      --classifier=<classifier>

      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

  -g, --group=<group>
  -h, --help
      --http-response-timeout=DURATION
                            The response timeout to apply to HTTP operations.
                              Parsed from ISO-8601 format. Default: PT30S
      --output-directory=<outputDirectory>

      --packaging=<packaging>

      --print-filename
  -r, --maven-repo=<mavenRepo>

      --skip-existing
      --skip-up-to-date
      --tls-handshake-timeout=DURATION
                            Default: PT30S
      --use-http2           Whether to use HTTP/2. Default: false
  -v, --version=<version>   A specific version, 'release', or 'latest'
                            Default: release
      --wiretap             Whether to enable Reactor Netty wiretap logging.
                              Default: false
```

### mcopy

```
Usage: mc-image-helper mcopy [-hz] [--file-is-listing]
                             [--ignore-missing-sources] [--quiet-when-skipped]
                             [--skip-existing] [--glob=GLOB]
                             [--scope=<manifestId>] --to=<dest>
                             [[--connection-pool-pending-acquire-timeout=DURATIO
                             N] [--http-response-timeout=DURATION]
                             [--tls-handshake-timeout=DURATION] [--use-http2]
                             [--wiretap]
                             [--connection-pool-max-idle-timeout=DURATION]] [SRC
                             [,|<nl>SRC...]...]
Multi-source file copy operation with with managed cleanup. Supports
auto-detected sourcing from file list, directories, and URLs
      [SRC[,|<nl>SRC...]...] Any mix of source file, directory, or URLs
                               delimited by commas or newlines
                             Per-file destinations can be assigned by
                               destination<source
                             Embedded comments are allowed.
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --file-is-listing      Source files or URLs are processed as a line
                               delimited list of sources.
                             For remote listing files, the contents must all be
                               file URLs.
      --glob=GLOB            When a source is a directory, this filename glob
                               will be applied to select files.
  -h, --help
      --http-response-timeout=DURATION
                             The response timeout to apply to HTTP operations.
                               Parsed from ISO-8601 format. Default: PT30S
      --ignore-missing-sources
                             Don't log or fail exit code when any or all
                               sources are missing
      --quiet-when-skipped   Don't log when file exists or is up to date
      --scope, --manifest-id=<manifestId>
                             If managed cleanup is required, this is the
                               identifier used for qualifying manifest filename
                               in destination
      --skip-existing
      --tls-handshake-timeout=DURATION
                             Default: PT30S
      --to, --output-directory=<dest>

      --use-http2            Whether to use HTTP/2. Default: false
      --wiretap              Whether to enable Reactor Netty wiretap logging.
                               Default: false
  -z, --skip-up-to-date
```

### modrinth

```
Usage: mc-image-helper modrinth [--skip-existing] [--skip-up-to-date]
                                [--allowed-version-type=<defaultVersionType>]
                                [--api-base-url=<baseUrl>]
                                [--download-dependencies=<downloadDependencies>]
                                 --game-version=<gameVersion> --loader=<loader>
                                [--output-directory=DIR]
                                [--world-directory=<worldDirectory>]
                                [--projects=[loader:]id|slug[:version][,|<nl>
                                [loader:]id|slug[:version]...]]...
                                [[--connection-pool-pending-acquire-timeout=DURA
                                TION] [--http-response-timeout=DURATION]
                                [--tls-handshake-timeout=DURATION]
                                [--use-http2] [--wiretap]
                                [--connection-pool-max-idle-timeout=DURATION]]
Automates downloading of modrinth resources
      --allowed-version-type=<defaultVersionType>
                          Valid values: release, beta, alpha
      --api-base-url=<baseUrl>
                          Default: https://api.modrinth.com
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --download-dependencies=<downloadDependencies>
                          Default is NONE
                          Valid values: NONE, REQUIRED, OPTIONAL
      --game-version=<gameVersion>
                          Applicable Minecraft version
      --http-response-timeout=DURATION
                          The response timeout to apply to HTTP operations.
                            Parsed from ISO-8601 format. Default: PT30S
      --loader=<loader>   Valid values: fabric, quilt, forge, neoforge, bukkit,
                            spigot, paper, folia, pufferfish, leaf, purpur,
                            bungeecord, velocity, datapack
      --output-directory=DIR

      --projects=[loader:]id|slug[:version][,|<nl>[loader:]id|slug[:version]...]
                          Project ID or Slug. Can be <project ID>|<slug>,
                            <loader>:<project ID>|<slug>, <loader>:<project
                            ID>|<slug>:<version ID|version number|release
                            type>, '@'<filename with ref per line (supports #
                            comments)>
                          Examples: fabric-api, fabric:fabric-api, fabric:
                            fabric-api:0.76.1+1.19.2, datapack:terralith,
                            @/path/to/modrinth-mods.txt
                          Valid release types: release, beta, alpha
                          Valid loaders: fabric, forge, paper, datapack, etc.
                          Embedded comments are allowed.
      --skip-existing
      --skip-up-to-date
      --tls-handshake-timeout=DURATION
                          Default: PT30S
      --use-http2         Whether to use HTTP/2. Default: false
      --wiretap           Whether to enable Reactor Netty wiretap logging.
                            Default: false
      --world-directory=<worldDirectory>
                          Used for datapacks, a path relative to the output
                            directory or an absolute path
                          Default: world
```

### network-interfaces

```
Usage: mc-image-helper network-interfaces [--include-loopback]
       [--check=<ifNameToCheck>]
Provides simple operations to list network interface names and check existence
      --check=<ifNameToCheck>

      --include-loopback
```

### patch

```
Usage: mc-image-helper patch [-h] [--json-allow-comments]
                             [--patch-env-prefix=<envPrefix>] FILE_OR_DIR
Patches one or more existing files using JSON path based operations
Supports the file formats:
- JSON
- JSON5
- Yaml
- TOML, but processed output is not pretty
      FILE_OR_DIR   Path to a PatchSet json file or directory containing
                      PatchDefinition json files
  -h, --help        Show this usage and exit
      --json-allow-comments
                    Whether to allow comments in JSON files. Env:
                      PATCH_JSON_ALLOW_COMMENTS
                      Default: true
      --patch-env-prefix=<envPrefix>
                    Only placeholder variables with this prefix will be
                      processed
                      Default: CFG_
```

### resolve-minecraft-version

```
Usage: mc-image-helper resolve-minecraft-version
       [[--connection-pool-pending-acquire-timeout=DURATION]
       [--http-response-timeout=DURATION] [--tls-handshake-timeout=DURATION]
       [--use-http2] [--wiretap] [--connection-pool-max-idle-timeout=DURATION]]
       <inputVersion>
Resolves and validate latest, snapshot, and specific versions
      <inputVersion>
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --http-response-timeout=DURATION
                       The response timeout to apply to HTTP operations. Parsed
                         from ISO-8601 format. Default: PT30S
      --tls-handshake-timeout=DURATION
                       Default: PT30S
      --use-http2      Whether to use HTTP/2. Default: false
      --wiretap        Whether to enable Reactor Netty wiretap logging.
                         Default: false
```

### set-properties

```
Usage: mc-image-helper set-properties [--escape-unicode]
                                      [--definitions=<propertyDefinitionsFile>]
                                      [-p=<String=String>]... <propertiesFile>
Maps environment variables to a properties file
      <propertiesFile>
      --definitions=<propertyDefinitionsFile>
                         JSON file of property names to PropertyDefinition
                           mappings
      --escape-unicode
  -p, --custom-property, --custom-properties=<String=String>
                         Key=value pairs of custom properties to set
```

### show-all-subcommand-usage

```
Usage: mc-image-helper show-all-subcommand-usage
Renders all of the subcommand usage as markdown sections for README
```

### sync

```
Usage: mc-image-helper sync [-h] [--skip-newer-in-destination] <srcDest>[,
                            |<nl><srcDest>...] <srcDest>[,|<nl><srcDest>...]...
Synchronizes the contents of one directory to another.
      <srcDest>[,|<nl><srcDest>...] <srcDest>[,|<nl><srcDest>...]...
               src... dest directories
  -h, --help   Show this usage and exit
      --skip-newer-in-destination
               Skip any files that exist in the destination and have a newer
                 modification time than the source.
```

### sync-and-interpolate

```
Usage: mc-image-helper sync-and-interpolate [-h] [--skip-newer-in-destination]
       ([--replace-env-prefix=<prefix>] [--replace-env-excludes=FILENAME[,
       FILENAME...]]... [--replace-env-exclude-paths=PATH[,PATH...]]...
       --replace-env-file-suffixes=PATH[,PATH...]
       [--replace-env-file-suffixes=PATH[,PATH...]]...) <srcDest>[,
       |<nl><srcDest>...] <srcDest>[,|<nl><srcDest>...]...
Synchronizes the contents of one directory to another with conditional variable
interpolation.
      <srcDest>[,|<nl><srcDest>...] <srcDest>[,|<nl><srcDest>...]...
               src... dest directories
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

### test-logging-levels

```
Usage: mc-image-helper test-logging-levels
```

### toml-path

```
Usage: mc-image-helper toml-path [--file=FILE] query
Extracts a path from a TOML file using json-path syntax
      query         JSON path expression where root element $ can be omitted
      --file=FILE   A TOML file to query. If not set, reads stdin
```

### vanillatweaks

```
Usage: mc-image-helper vanillatweaks [--force-synchronize]
                                     [--base-url=<baseUrl>]
                                     [--output-directory=DIR]
                                     [--world-subdir=<worldSubdir>]
                                     [--pack-files=FILE[,|<nl>FILE...]]...
                                     [--share-codes=CODE[,|<nl>CODE...]]...
                                     [[--connection-pool-pending-acquire-timeout
                                     =DURATION]
                                     [--http-response-timeout=DURATION]
                                     [--tls-handshake-timeout=DURATION]
                                     [--use-http2] [--wiretap]
                                     [--connection-pool-max-idle-timeout=DURATIO
                                     N]]
Downloads Vanilla Tweaks resource packs, data packs, or crafting tweaks given a
share code or pack file
      --base-url=<baseUrl>
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --force-synchronize
      --http-response-timeout=DURATION
                             The response timeout to apply to HTTP operations.
                               Parsed from ISO-8601 format. Default: PT30S
      --output-directory=DIR
      --pack-files=FILE[,|<nl>FILE...]

      --share-codes=CODE[,|<nl>CODE...]

      --tls-handshake-timeout=DURATION
                             Default: PT30S
      --use-http2            Whether to use HTTP/2. Default: false
      --wiretap              Whether to enable Reactor Netty wiretap logging.
                               Default: false
      --world-subdir=<worldSubdir>

```

### version-from-modrinth-projects

```
Usage: mc-image-helper version-from-modrinth-projects
       [--api-base-url=<baseUrl>] [--projects=[loader:]id|slug[:version][,|<nl>
       [loader:]id|slug[:version]...]...]...
       [[--connection-pool-pending-acquire-timeout=DURATION]
       [--http-response-timeout=DURATION] [--tls-handshake-timeout=DURATION]
       [--use-http2] [--wiretap] [--connection-pool-max-idle-timeout=DURATION]]
Finds a compatible Minecraft version across given Modrinth projects
      --api-base-url=<baseUrl>
                    Default: https://api.modrinth.com
      --connection-pool-max-idle-timeout=DURATION

      --connection-pool-pending-acquire-timeout=DURATION

      --http-response-timeout=DURATION
                    The response timeout to apply to HTTP operations. Parsed
                      from ISO-8601 format. Default: PT30S
      --projects=[loader:]id|slug[:version][,|<nl>[loader:]id|slug[:
        version]...]...
                    Project ID or Slug. Can be <project ID>|<slug>, <loader>:
                      <project ID>|<slug>, <loader>:<project ID>|<slug>:
                      <version ID|version number|release type>, '@'<filename
                      with ref per line (supports # comments)>
                    Examples: fabric-api, fabric:fabric-api, fabric:fabric-api:
                      0.76.1+1.19.2, datapack:terralith,
                      @/path/to/modrinth-mods.txt
                    Valid release types: release, beta, alpha
                    Valid loaders: fabric, forge, paper, datapack, etc.
      --tls-handshake-timeout=DURATION
                    Default: PT30S
      --use-http2   Whether to use HTTP/2. Default: false
      --wiretap     Whether to enable Reactor Netty wiretap logging. Default:
                      false
```

### yaml-path

```
Usage: mc-image-helper yaml-path [--file=<yamlFile>] <yamlPath>
Extracts a path from a YAML file using json-path syntax
      <yamlPath>          A YAML/JSON path in to query. Leading root anchor, $,
                            will be added if not present
      --file=<yamlFile>   A YAML file to query
```

<!-- END of documentation generated using `mc-image-helper show-all-subcommand-usage` -->


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

#### `$add`

The `$add` operation allows for adding a value to an array.

- `$add`
    - `path` : The [JSON path](https://github.com/json-path/JsonPath#readme) to the array
    - `value` : The value to add. If the given value is a string, variable placeholders of the form `${...}` will be replaced from the environment variables and the resulting string can be converted by setting value-type.
    - `value-type` : **optional** [see below](#valuetype)

### ValueType

One of the following identifiers or can be prefixed with `list of ` to indicate a list of the identified type:
- int : integer/whole value
- float : numerical value with an optional decimal part
- bool : boolean value of `true` or `false`
- auto : where the text value is attempted to be converted into one of the above

## Exclude/Include File Schema

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

