## Ad hoc testing

Beyond the unit tests, ad hoc "integration testing" can be done by running via Gradle passing the intended command-line via `--args`, such as:

```shell
./gradlew run --args="assert fileExists build.gradle"
```

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