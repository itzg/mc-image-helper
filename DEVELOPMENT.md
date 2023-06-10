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

## Creating a pre-release

When needing to test a pre-release, such as a PR's changes, a pre-release can be run locally.

Ensure `$HOME/.jreleaser/config.properties` contains `JRELEASER_GITHUB_TOKEN` set with a token with repo access.

Invoke the gradle task `jreleaseRelease`.

The release and artifacts are located at <https://github.com/itzg/mc-image-helper/releases/tag/early-access>