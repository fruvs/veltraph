# Veltraph

## A hypixel skyblock macro.

**Status: active development.** Features have different levels of testing, and some workflows remain incomplete. Development builds may contain bugs, particularly around full farming courses, travel and returns, and interrupted workflows. Many features have not been live tested in game.
### continue at your own risk, using this mod may result in a ban.

## Features

- **Farming:** crop and layout profiles, row traversal, harvesting, rewarps, and maintenance workflows.
- **Fishing:** casting and reeling, configurable creature handling, equipment selection, and support routines.
- **Foraging and combat:** tree workflows, configurable combat profiles, and specialized encounter controllers.
- **Routes and building:** saved local routes, walking, permitted flight, etherwarp checkpoints, and layout execution.
- **Garden tools:** visitor, pest, plot, and maintenance workflows.
- **Utilities:** inventory and equipment management, selling, brewing, crafting, Harp, and Experiments helpers.
- **Interface:** automation controls, persistent settings, HUD information, local diagnostics, and stop controls.

These describe implemented areas of the codebase. They do not imply that every option or complete gameplay cycle is finished or verified.

## Requirements

| Component | Target |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | A version compatible with Minecraft 26.3 |

The development build uses Fabric API `0.161.0+26.3`.

## Installation

1. Set up a Fabric instance for Minecraft 26.3 using Java 25.
2. Install Fabric API in the instance's `mods` folder.
3. Add the compiled Veltraph JAR from this repository's GitHub Releases.
4. Launch Minecraft and configure Veltraph before starting an automation.

Use the main `veltraph-<version>.jar` file. The `-sources.jar` is for development.

## Getting started

- `/vmacro` or `/veltraphmacro` opens Automations.
- `/veltraph` opens Settings.
- Configure Open Menu and Stop All in Minecraft's Controls menu.
- `/vdebug macro` shows automation state.
- `/vdebug location` shows detected location information.
- `/vdebug log true mode verbose` enables detailed local diagnostics.

Set the required tools, slots, profiles, and location settings before starting a workflow. Start with a short, supervised run and use Stop All if the behavior differs from what you expect.

## Building from source

The repository includes the Gradle wrapper. With JDK 25 installed, run:

```sh
./gradlew test build
```

On Windows:

```powershell
.\gradlew.bat test build
```

Compiled JARs are written to `build/libs/`.

The standalone repository includes unit tests. Captured-world integration fixtures and local diagnostic data are kept outside this repository. A successful build does not establish that every gameplay workflow works correctly on a server.

## Reporting issues

Include the build version, Minecraft and Fabric versions, selected workflow and settings, steps to reproduce, and what you expected to happen. Attach only the relevant diagnostic excerpt, with personal information removed.

## License

MIT. See [LICENSE](LICENSE).
