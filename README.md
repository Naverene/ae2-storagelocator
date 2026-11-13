# AE2 Storage Locator

An add-on for Applied Energistics 2: hover an item or fluid in any AE terminal and the tooltip lists where the
network keeps it (which drive cells, ME chests and storage-bus targets hold it, and how many each). A key press
in the terminal outlines those blocks in the world.

This repository holds every version of the mod, one folder per Minecraft version, laid out like
[More Dyes](https://github.com/Naverene/moredyes):

| Folder | Minecraft | Loader | AE2 | Java |
| --- | --- | --- | --- | --- |
| [1.7.10](1.7.10) | 1.7.10 | Forge (GTNH buildscript) | rv3-beta GTNH | 8 to play, 25 to build (the GTNH buildscript needs it) |
| [1.16.5](1.16.5) | 1.16.5 | Forge | 8.x | 8 |
| [1.18.2](1.18.2) | 1.18.2 | Forge | 11.x | 17 |
| [1.19.2](1.19.2) | 1.19.2 | Forge | 12.x | 17 |
| [1.20.1](1.20.1) | 1.20.1 | Forge | 15.x | 17 |
| [1.20.1-fabric](1.20.1-fabric) | 1.20.1 | Fabric (needs Fabric API) | 15.x | 17 to play, 21 to build |
| [1.21.1](1.21.1) | 1.21.1 | NeoForge | 19.x | 21 |
| [26.1.2](26.1.2) | 26.1.2 | NeoForge | 26.1.x (beta) | 25 |
| [26.3](26.3) | 26.3 | NeoForge | 26.3.x (alpha) | 25 |

More Minecraft versions are on the way, one folder each, for the versions More Dyes targets that have an AE2
release: 1.12.2.

The GregTech parts of the 1.7.10 version (Super/Quantum Chest status, gtstorageclusters, WAILA) are 1.7.10 only.

Each Minecraft folder is its own Gradle project with its own wrapper. Open the folder for the version you want in
your IDE (not the repository root), and run `./gradlew build` inside it. The jar lands in that folder's
`build/libs`. See each folder's README for what that version does and how it is built.

## Builds

GitHub Actions (`.github/workflows/build.yml`) builds every version folder:

- a pull request builds only the versions whose folders it changes (all of them if the workflow changes)
- every push to the default branch builds all versions and updates the rolling "Development build" pre-release
- pushing a version tag such as `v1.2.0` builds all versions as that mod version and publishes one release with
  every jar

`.gitlab-ci.yml` does the same everyday builds on a self-hosted GitLab, like More Dyes.

## Adding a Minecraft version

1. Make a new folder named after the Minecraft version (add `-fabric` when the loader is Fabric and the plain
   name is taken by Forge or NeoForge), with its own Gradle wrapper, `.gitignore` and `README.md`.
2. Add it to the table above, to `VERSIONS` in `.github/workflows/build.yml` and as a job in `.gitlab-ci.yml`.
