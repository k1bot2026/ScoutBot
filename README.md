# ScoutBot — COX Layout Scout for DreamBot

Automates Chambers of Xeric raid scouting in OSRS. Reloads the raid entrance repeatedly, detects the generated layout, and stops when it matches one of your desired rotations.

## How It Works

1. Right-clicks the raid entrance "Steps" and selects **Reload** to generate a new layout
2. Skips the entry dialog automatically
3. Reads instance template chunks to detect the room layout
4. Compares the layout against your selected presets
5. If it matches — beeps and stops. If not — reloads and tries again

## Features

- **8 preset rotations** — 4 VTV and 4 VSV layouts ready to go
- **Custom layouts** — add your own sequences (4-16 chars using S/C/P/F/M/V/T)
- **Live GUI** — shows current state, attempt count, detected layouts, and match results
- **Room detection** — identifies Combat, Puzzle, Scavengers, Farming, and boss rooms from game chunks

### Room Type Key

| Code | Room Type |
|------|-----------|
| S | Scavengers |
| C | Combat |
| P | Puzzle |
| F | Farming |
| M | Mysticism |
| V | Vespula |
| T | Tekton |

### Default Presets

**VTV rotations:** `FSCCSPCPSF`, `SFCCSPCPSF`, `SCPFCCSPSF`, `SCSPFCCSPF`

**VSV rotations:** `SCSPFCCSPF`, `SCPFCCSPSF`, `SFCCSPCPSF`, `FSCCSPCPSF`

## Requirements

- [DreamBot](https://dreambot.org/) client installed
- Java 11+
- OSRS account at the Chambers of Xeric entrance

## Installation

### Quick Install (recommended)

Download the installer for your OS from the [`installer/`](installer/) folder:

**macOS / Linux:**
```bash
curl -O https://raw.githubusercontent.com/k1bot2026/ScoutBot/main/installer/install.sh
chmod +x install.sh
./install.sh
```

**Windows (PowerShell):**
```powershell
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/k1bot2026/ScoutBot/main/installer/install.bat" -OutFile install.bat
.\install.bat
```

The installer checks for Java and Git, clones the repo, builds the JAR, and copies it to your DreamBot Scripts folder.

### Manual Install

1. Clone the repo:
   ```bash
   git clone https://github.com/k1bot2026/ScoutBot.git
   cd ScoutBot
   ```

2. Build:
   ```bash
   ./gradlew :scripts:coxscout:jar
   ```

3. Copy `scripts/coxscout/build/libs/CoxScout.jar` to `~/DreamBot/Scripts/`

4. Open DreamBot and select **COX Scout** from the script list.

## Usage

1. Log in to OSRS and stand at the Chambers of Xeric entrance
2. Start DreamBot and run the **COX Scout** script
3. Select your desired layouts from the GUI (presets or add custom)
4. Click **Start Scouting**
5. The bot scouts until it finds a matching layout, then beeps and stops

## Building from Source

Requires Java 11+ and the DreamBot client installed at `~/DreamBot/`.

```bash
./gradlew :scripts:coxscout:jar    # DreamBot script
./gradlew :plugins:coxscout:jar    # RuneLite plugin (development)
```

## Project Structure

```
scripts/coxscout/    DreamBot script
  CoxScoutScript     Main bot loop and state machine
  ScoutState         State enum (CLICK_STEPS, SKIP_DIALOG, READ_CHAT, MATCH_LAYOUT, FOUND)
  RaidLayoutDetector Reads game chunks to identify room layout
  LayoutManager      Preset/custom layout storage and matching
  CoxScoutGUI        Live status GUI with layout selection

plugins/coxscout/    RuneLite plugin (overlay/development)
installer/           Cross-platform install scripts
```
