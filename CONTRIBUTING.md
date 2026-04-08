# Contributing

## Development Requirements

- Java `17`
- Minecraft Forge `1.20.1-47.4.x`
- Local Epic Fight development jar: `libs/epicfight-forge-20.13.6-1.20.1.jar`

Curios is fetched from a public Maven repository. Epic Fight is still provided locally for this project, so full compilation is only available after placing the jar in `libs/`.

## Recommended Workflow

1. Create a branch from the active `1.20.1` line.
2. Run `./gradlew help` to confirm the wrapper and project metadata load correctly.
3. Add the Epic Fight jar to `libs/`.
4. Run `./gradlew clean build` before opening a pull request.

## Pull Request Guidelines

- Keep changes focused and explain the gameplay or compatibility impact.
- Update docs when setup, compatibility, or controls change.
- Update `src/main/resources/assets/epicfight_curios_compat/lang/en_us.json` when adding or renaming user-facing text.
- Do not commit generated folders such as `.gradle/`, `build/`, `run/`, or local dependency jars.

## Code Style Notes

- Follow the existing package layout and naming style.
- Prefer compatibility-safe changes over broad refactors.
- Keep client-only behavior isolated from common logic where possible.
