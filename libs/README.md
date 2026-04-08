# Local Epic Fight Dependency

This project resolves `Curios` from its public Maven, but `Epic Fight` is still expected as a local development jar.

Place the following file in this directory before running `./gradlew build` or `./gradlew runClient`:

- `epicfight-forge-20.13.6-1.20.1.jar`

The jar is ignored by Git and must not be committed.
