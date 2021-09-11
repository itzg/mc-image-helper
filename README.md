This tool does the complicated bits for [the itzg/minecraft-server Docker image](https://github.com/itzg/docker-minecraft-server).

## Usage

```
Usage: mc-image-helper [-hV] [--debug] [COMMAND]
      --debug     Enable debug output
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  sync-and-interpolate  Synchronizes the contents of one directory to another
                          with conditional variable interpolation.
  sync                  Synchronizes the contents of one directory to another.
  asciify               Converts UTF-8 on stdin to ASCII by escaping Unicode
                          characters
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