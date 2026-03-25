# OSRS MoneyMaker Bot — Design Document

**Datum:** 2026-03-25
**Client:** DreamBot (dreambot.org)
**Taal:** Java
**Status:** Goedgekeurd

---

## 1. Architectuur

### Multi-Module Gradle Project (Embedded Core)

```
OSRS/
├── build.gradle                    → root build, DreamBot client.jar dependency
├── settings.gradle                 → multi-module config
├── core/
│   ├── build.gradle
│   └── src/main/java/com/osrs/core/
│       ├── antiban/                → anti-detection systeem
│       ├── ge/                     → Grand Exchange management
│       ├── gui/                    → gedeeld GUI framework
│       ├── movement/               → walking + flee systeem
│       ├── session/                → sessie manager, breaks, dagschema
│       ├── strategy/               → strategy selector (rule-based + weighted scoring)
│       ├── skills/                 → skill checking utilities
│       └── utils/                  → timers, random, logging
├── scripts/
│   └── moneymaker/
│       ├── build.gradle
│       └── src/main/java/com/osrs/scripts/moneymaker/
│           ├── MoneyMakerScript.java      → hoofdscript (extends AbstractScript)
│           ├── methods/                    → money-making methodes
│           │   ├── skilling/              → mining, wc, fishing, etc.
│           │   ├── combat/                → chaos druids, cows, etc.
│           │   ├── processing/            → smelting, cooking, tanning
│           │   ├── flipping/              → GE flipping
│           │   └── looting/               → world hopping loot pickup
│           ├── model/                      → data models (Method, Requirement, etc.)
│           └── gui/                        → MoneyMaker-specifieke GUI
```

- Een `gradle build` produceert alle script JARs
- Elke JAR bevat core classes + script-specifieke code
- Output gaat direct naar DreamBot's Scripts folder
- DreamBot ziet gewoon losse scripts

---

## 2. Anti-Ban / Human-Like Systeem (5 Lagen)

### Laag 1: Mouse Behavior
- Custom MouseAlgorithm met varierende Bezier curves (snelheid, overshoot, correcties)
- Misclicks: 1-3% kans naast doel klikken, dan corrigeren
- Variabele kliksnelheid: sneller bij repetitie, langzamer bij nieuwe situaties
- Mouse drift: lichte beweging tijdens idle

### Laag 2: Timing & Ritme
- Fatigue systeem: reactietijden worden langer naarmate sessie vordert
- Gaussian randomisatie: delays volgen normale verdeling (bell curve)
- Micro-pauzes: 0.5-3 sec tussen acties
- Action bunching: soms snel meerdere acties, dan langere pauze

### Laag 3: Idle Gedrag
- Camera draaien (willekeurig)
- Tab switching (skills/quest/combat)
- Random objecten/NPCs examineren
- Mouse off-screen (tabben naar "ander venster")
- Inventory hoveren zonder reden

### Laag 4: Sessie Management
- Dagschema per dag uniek gegenereerd:
  - Sessieduur: 30-120 min (normaal verdeeld rond 60 min)
  - Break duur: 5-30 min
  - Totale speeltijd per dag: 4-10 uur (random)
  - Variatie per dag
- Soft stops: rondt huidige taak af
- Login/logout variatie

### Laag 5: Patroon Doorbreking
- Methode rotatie (niet urenlang hetzelfde)
- Route variatie naar dezelfde bestemming
- World hopping (periodiek + bij drukte)
- Reactie op chat berichten (kort pauzeren)

Alles configureerbaar via `AntiBanProfile` die per sessie licht varieert.

---

## 3. Strategy Selector (Rule-Based + Weighted Scoring)

### Stap 1: Rule-Based Filter

Harde requirements per methode:
- Minimum skill levels
- Membership status (F2P / P2P)
- Benodigde items
- Benodigde quests
- Minimum combat level
- Startkapitaal

### Stap 2: Weighted Scoring

| Factor | Gewicht |
|--------|---------|
| GP/uur (geschat) | 30% |
| Skill progression | 20% |
| Tijd sinds laatst gedaan | 20% |
| Risico niveau | 15% |
| Resource beschikbaarheid | 15% |

Scores worden omgezet naar kansen → probabilistische keuze. Niet altijd de "beste" methode, soms sub-optimaal voor menselijke variatie.

### Method Registry
Nieuwe methodes implementeren `MoneyMethod` interface met:
- `getRequirements()` — harde vereisten
- `getEstimatedGpPerHour()` — geschatte opbrengst
- `getRiskLevel()` — risico classificatie
- `execute()` — uitvoerlogica

---

## 4. Money-Making Methodes (35+)

### Skilling

| Methode | Skill Req | F2P/P2P | ~GP/uur |
|---------|-----------|---------|---------|
| Mine clay | Mining 1 | F2P | 30k |
| Mine iron ore | Mining 15 | F2P | 150k |
| Mine silver ore | Mining 20 | F2P | 75k |
| Mine gold ore | Mining 40 | F2P | 100k |
| Mine coal | Mining 30 | F2P | 80k |
| Cut oak logs | WC 15 | F2P | 40k |
| Cut willow logs | WC 30 | F2P | 30k |
| Cut yew logs | WC 60 | F2P | 120k |
| Fish trout/salmon | Fishing 20 | F2P | 40k |
| Fish lobsters | Fishing 40 | F2P | 80k |
| Fish swordfish | Fishing 50 | F2P | 100k |
| Mine adamantite | Mining 70 | P2P | 200k |
| Mine runite | Mining 85 | P2P | 500k+ |
| Cut magic logs | WC 75 | P2P | 150k |
| Fish monkfish | Fishing 62 | P2P | 180k |
| Fish dark crabs | Fishing 85 | P2P | 300k |

### Combat

| Methode | Combat Req | F2P/P2P | ~GP/uur |
|---------|-----------|---------|---------|
| Kill cows (cowhides) | 1 | F2P | 30k |
| Kill chickens (feathers) | 1 | F2P | 20k |
| Kill Ogresses | 20+ | F2P | 80-150k |
| Kill moss giants | 30+ | F2P | 60k |
| Kill hill giants | 25+ | F2P | 50k |
| Kill green dragons | 60+ | P2P | 400k |
| Kill blue dragons | 65+ | P2P | 350k |
| Kill chaos druids | 20+ | P2P | 200k |
| Kill gargoyles | 75 Slayer | P2P | 500k+ |
| Kill Vorkath | 90+ | P2P | 2M+ |

### Processing

| Methode | Skill Req | F2P/P2P | ~GP/uur |
|---------|-----------|---------|---------|
| Tan cowhides | Geen | F2P | 100k |
| Smelt bars | Smithing 15+ | F2P | 80-200k |
| Cook food | Cooking 1+ | F2P | 50-150k |
| Make pie shells | Geen | F2P | 200k |
| Craft gold amulets | Crafting 8 | F2P | 120k |
| Make cannonballs | Smithing 35 | P2P | 150k |
| Herb cleaning | Herblore 1+ | P2P | 200k+ |
| Make unfinished potions | Herblore 1+ | P2P | 300k+ |

### Flipping (GE)

| Methode | Req | ~GP/uur |
|---------|-----|---------|
| High-volume flipping | 50k+ kapitaal | 50-500k+ |
| Niche flipping | 200k+ kapitaal | 100k-1M |
| Alch flipping | Magic 55 (P2P) | 200k+ |

### Looting

| Methode | Req | F2P/P2P | ~GP/uur |
|---------|-----|---------|---------|
| PvP world looting | Geen | F2P | 100-300k |
| Wilderness looting | Geen | F2P/P2P | 50-200k |

---

## 5. GUI Design

### Startup Configuratie

**Tab 1 — Algemeen:**
- Modus: Automatisch / Focus categorie / Specifieke methode
- Sessieduur: Auto / 1u / 2u / 4u / Custom
- Breaks: automatisch aan/uit
- Dagschema: realistisch patroon aan/uit
- Methode uitsluiting per categorie
- Wilderness toestaan toggle
- Anti-ban intensiteit slider (0-100%)

**Tab 2 — Configuratie:**
- Money Making: min GP/uur drempel, switch interval, max methodes/sessie, prioriteit (GP/XP/balanced)
- Combat: min HP% voor eten, flee HP%, loot min waarde, auto-equip, bury bones
- Grand Exchange: auto-sell, auto-buy, flipping toggle, max investering, sell strategy
- Skilling: power-mine toggle, preferred locaties
- Wilderness: max risk waarde, flee bij speler, max wildy level, hop na PK

**Tab 3 — Geavanceerd:**
- Anti-Ban Tuning: misclick kans, camera frequentie, mouse off-screen kans, idle kans, chat reactie, fatigue snelheid
- Sessie Tuning: min/max sessie duur, min/max break duur, max speeltijd/dag, dagelijkse variatie
- Veiligheid: JMod detectie stop, screenshot bij ban, world hop bij drukte, min/max wereld spelers
- Profiel: opslaan/laden/reset als JSON

### Live Dashboard (tijdens runtime)
- Status: huidige methode, locatie, inventory status, runtime, sessie info
- Winst: GP verdiend, GP/uur, items in bank, GE pending offers
- Skills: level changes met XP bars
- Methode historie: tijdlijn met GP per methode
- Anti-Ban stats: fatigue level, volgende break, camera/tab/misclick/idle counters
- Controls: Pause, Switch Method, Stop

---

## 6. Flee & Safety Systeem

### Threat Detection (3 prioriteiten)

**P1 — Instant:**
- JMod detectie → log uit, stop, screenshot
- HP onder threshold → teleport/run naar safe zone
- Teleblock → run naar wildy grens

**P2 — Snel:**
- PKer spotted → flee of hop
- Poison zonder antidote → retreat
- Geen food bij combat → bank run
- Skulled → vluchten

**P3 — Tactisch:**
- Te veel spelers → world hop
- Andere bot op spot → verplaats/hop
- Dood → reclaim, switch methode
- GE crash → stop verkoop, herbereken

### Flee Mechanisme
1. Teleport (tab/ring/amulet) als beschikbaar
2. Run naar safe zone met eat-cycling
3. Protection prayers activeren
4. Nood-logout als combat timer het toelaat

### Death Recovery
1. Respawn → check reclaimable items
2. Bereken: reclaimen vs vervangen
3. Herbereken beschikbare methodes
4. Fallback naar processing/skilling
5. Verdien voor nieuwe gear → hervat

---

## 7. Grand Exchange Systeem

### Verkoop Manager
- Triggers: inventory vol, bank threshold (100+), sessie einde
- Pricing: instant (-5%), market, patient (+5%) — configureerbaar
- Menselijk gedrag: random hoeveelheden, soms annuleren, niet constant checken

### Inkoop Manager
- Auto-buy: food, tools, processing materialen, teleports
- Budget: 10% cash reserve, max spend configureerbaar
- Bulk kopen bij lage prijzen

### Flipping Engine
- Margin check: koop 1, verkoop 1, bereken marge
- Filter: min 3% marge, min 1k volume/uur
- Max 25% investering per item, diversificeer 2-4 items
- Cancel bij >5% prijsverandering
- Roteer items dagelijks, houd prijs-historie bij

### GE Anti-Ban Gedrag
- Niet recht naar clerk lopen
- Soms verkeerde offer plaatsen
- Random browsen door interface
- Verschillende booths gebruiken
- Soms handmatig prijs typen

---

## 8. Skill Progression & Account Awareness

### Level Tracking
- Bij startup: lees alle skills, membership, quests, bank contents
- Continue monitoring: XP gains, level-ups, unlock notificaties
- Herbereken eligible methodes bij elke level-up

### Progression Paden

**Fase 1 — Starter (totaal level < 100):**
Chickens, clay mining, cowhide tanning → Mining 15, Combat 20, 100k cash

**Fase 2 — Vroeg (100-300):**
Iron ore, hill giants, Ogresses, start flipping → Mining 40, Combat 40, WC 30

**Fase 3 — Midden (300-600):**
Gold ore, yew logs, lobsters, actief flipping → 60+ key skills

**Fase 4 — Gevorderd (600+):**
P2P: chaos druids, dragons, cannonballs, herb cleaning, slayer → endgame PvM

### Gear Upgrades (automatisch bij milestones)
- Combat: bronze → iron → steel → mithril → adamant → rune → dragon → barrows
- Tools: altijd beste pickaxe/axe/rod per skill level
- Kopen via GE, nooit overpricen

### Smart Investment
- Berekent ROI voor skill training (bijv. 50k voor Smithing 35 → cannonballs unlock)
- Investeer als ROI < 2 uur terugverdiend
- Geldt ook voor quest unlocks, betere tools, combat supplies

---

## 9. Toekomstige Scripts (hergebruiken core)

| Script | Beschrijving | Status |
|--------|-------------|--------|
| MoneyMaker | Multi-methode gold verdienen | Eerste build |
| SkillTrainer | Gerichte skill training | Toekomstig |
| Quester | Quest automation | Toekomstig |
| AccountBuilder | Combinatie van alles | Toekomstig |

---

## 10. Technische Specificaties

- **Java versie:** Temurin 11 (DreamBot vereiste)
- **Build tool:** Gradle (multi-module)
- **DreamBot API:** AbstractScript + custom node pattern
- **GUI:** Java Swing
- **Config opslag:** JSON profielen
- **Output:** JAR per script → `~/DreamBot/Scripts/`
