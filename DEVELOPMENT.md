## Creating a new release

The Github Actions workflow `publish-release` will take of performing the release build, but the tag needs to be created with the release tasks shown below.

### Patch release

```shell
./gradlew release -PpushReleaseTag=true
```

### Minor release

```shell
./gradlew releaseMinorVersion -PpushReleaseTag=true
```