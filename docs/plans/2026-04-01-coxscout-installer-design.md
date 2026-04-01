# CoxScout Installer Design

## Overview

A setup package (install.sh + install.bat) that friends/teammates download and run to install the CoxScout DreamBot script from GitHub. It clones the repo, builds the JAR, and places it in the DreamBot Scripts folder.

## Prerequisites

- DreamBot already installed (`~/DreamBot/Scripts/` must exist)
- Java 11+ and Git (installer checks and guides through installation if missing)

## Installer Flow

1. **Check prerequisites** — Java 11+, Git
   - macOS: suggests `brew install openjdk@11` / `brew install git`
   - Windows: suggests `winget install Microsoft.OpenJDK.11` / `winget install Git.Git`
   - Exits with clear instructions if not found
2. **Clone the repo** from configurable `REPO_URL` variable at top of script
3. **Build with Gradle wrapper** — `./gradlew :scripts:coxscout:jar`
4. **Verify DreamBot** — check `~/DreamBot/Scripts/` exists
5. **Copy JAR** — move built `CoxScout.jar` into DreamBot Scripts folder
6. **Cleanup** — remove cloned repo after successful build
7. **Print success** message

## Deliverables

- `installer/install.sh` — macOS/Linux
- `installer/install.bat` — Windows

## Notes

- Repo URL is a placeholder variable until the repo is pushed to GitHub
- Gradle wrapper in the repo handles the build — no global Gradle install needed
