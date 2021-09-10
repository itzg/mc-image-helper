This tool does the complicated bits for [the itzg/minecraft-server Docker image](https://github.com/itzg/docker-minecraft-server).

## Usage

```
Usage: mc-image-helper copy-and-interpolate [-hV] [--skip-newer-in-destination]
       ([--replace-env-prefix=<prefix>] [--replace-env-excludes=<excludes>]...
       [--replace-env-exclude-path=<excludePaths>]...
       --replace-env-file-suffixes=<suffixes>[,<suffixes>...]
       [--replace-env-file-suffixes=<suffixes>[,<suffixes>...]]...) <src> <dest>
Copies the contents of one directory to another with conditional variable
interpolation.
      <src>       source directory
      <dest>      destination directory
  -h, --help      Show this help message and exit.
      --replace-env-exclude-path=<excludePaths>
                  Destination paths that will be excluded from processing
      --replace-env-excludes=<excludes>
                  Filenames (without path) that should be excluded from
                    processing.
      --replace-env-file-suffixes=<suffixes>[,<suffixes>...]
                  Filename suffixes (without dot) that should be processed. For
                    example: txt,json,yaml
      --replace-env-prefix=<prefix>
                  Only placeholder variables with this prefix will be processed.
      --skip-newer-in-destination
                  Skip any files that exist in the destination and have a newer
                    modification time than the source.
```