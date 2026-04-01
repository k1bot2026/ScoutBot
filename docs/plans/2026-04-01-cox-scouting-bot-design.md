# COX Scouting Bot — Design Document

**Date:** 2026-04-01
**Client:** DreamBot (dreambot.org)
**Language:** Java
**Status:** Approved

---

## 1. Overview

A standalone DreamBot script that automatically scouts Chambers of Xeric (COX) raid layouts by repeatedly reloading the raid entrance until a desired room sequence is found. No mouse movement or anti-ban needed — keyboard-only interaction.

### Process Flow

1. Left-click the Steps object (raid entrance stairs)
2. Hold "1" key to skip dialog
3. Read the room sequence from game chat (e.g., "SCPFCCSPSF")
4. If sequence matches a desired layout → alert and stop
5. If not → repeat from step 1

---

## 2. Architecture

Standalone script under `scripts/coxscout/`. No dependency on the core module.

```
scripts/coxscout/
├── build.gradle
└── src/main/java/com/osrs/scripts/coxscout/
    ├── CoxScoutScript.java      → main script (extends AbstractScript)
    ├── ScoutState.java          → enum: CLICK_STEPS, SKIP_DIALOG, READ_CHAT, MATCH_LAYOUT, FOUND
    ├── LayoutManager.java       → manages desired sequences (presets + custom)
    └── CoxScoutGUI.java         → simple Swing GUI for layout selection
```

---

## 3. State Machine

```
CLICK_STEPS → SKIP_DIALOG → READ_CHAT → MATCH_LAYOUT
     ↑                                       |
     |          no match                      |
     └────────────────────────────────────────┘
                    match → FOUND (alert + stop)
```

- **CLICK_STEPS** — Left-click the Steps object (raid entrance stairs)
- **SKIP_DIALOG** — Hold keyboard "1" to skip through the dialog
- **READ_CHAT** — Poll DreamBot's chat API for the layout string
- **MATCH_LAYOUT** — Compare against user-selected desired sequences
- **FOUND** — Play sound alert, log the layout, stop the loop

---

## 4. GUI

Simple Swing panel shown at startup:

- **Preset layouts** with checkboxes (togglable):
  - "SCPFCCSPSF" — No Vespula/Vasa
  - "SCFPCCSPSF" — Tekton skip
  - Additional known meta layouts
- **Custom input** — text field to add custom sequences
- **Start / Stop** button
- **Status display** — current state, attempt count, last seen layout

---

## 5. Chat Detection

- Use DreamBot's `ChatMessage` API to read game chat messages
- Match against regex pattern `[SCPFMVT]{8,12}` for room sequence letters
- Layout message appears after dialog completion

### Room Letter Key

| Letter | Room |
|--------|------|
| S | Skeletal Mystics / Shamans |
| C | Crabs (Jewelled) |
| P | Prep room |
| F | Floor (Rope / Ice) |
| M | Muttadile |
| V | Vespula / Vasa |
| T | Tekton / Tightrope |

---

## 6. Notifications

- System beep / sound alert on match
- Log matched layout to script console
- GUI updates to show the matched layout and total attempts

---

## 7. Technical Specs

- **Java version:** Temurin 11 (DreamBot requirement)
- **Build:** Gradle, added as submodule in existing `settings.gradle`
- **DreamBot API:** AbstractScript, ChatMessage, Keyboard, GameObjects
- **GUI:** Java Swing
- **Output:** JAR → `~/DreamBot/Scripts/`
