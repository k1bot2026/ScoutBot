# OSRS MoneyMaker Bot — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a modular OSRS bot framework with an intelligent money-making script for DreamBot, featuring anti-detection, GE management, account progression, and a live GUI dashboard.

**Architecture:** Gradle multi-module project with embedded core library. Core provides anti-ban, GE, session management, strategy selection, safety, and GUI utilities. MoneyMaker script extends AbstractScript using a custom Node pattern, selecting from 35+ money-making methods via rule-based filtering + weighted scoring.

**Tech Stack:** Java 11 (Temurin), Gradle, DreamBot API (client.jar), Java Swing (GUI)

---

## Phase 1: Project Setup & Build System

### Task 1.1: Initialize Gradle Multi-Module Project

**Files:**
- Create: `build.gradle`
- Create: `settings.gradle`
- Create: `core/build.gradle`
- Create: `scripts/moneymaker/build.gradle`

**Step 1: Create root build.gradle**

```groovy
plugins {
    id 'java'
}

allprojects {
    group = 'com.osrs'
    version = '1.0.0'

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply plugin: 'java'

    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11

    dependencies {
        compileOnly files("${System.getProperty('user.home')}/DreamBot/BotData/client.jar")
    }
}
```

**Step 2: Create settings.gradle**

```groovy
rootProject.name = 'osrs-bots'

include 'core'
include 'scripts:moneymaker'
```

**Step 3: Create core/build.gradle**

```groovy
// Core module - shared library, no script manifest needed
```

**Step 4: Create scripts/moneymaker/build.gradle**

```groovy
dependencies {
    implementation project(':core')
}

jar {
    // Include core classes in the output JAR
    from {
        project(':core').sourceSets.main.output
    }

    // Output to DreamBot Scripts folder
    destinationDirectory = file("${System.getProperty('user.home')}/DreamBot/Scripts")
    archiveBaseName = 'OSRSMoneyMaker'
}
```

**Step 5: Create directory structure**

```bash
mkdir -p core/src/main/java/com/osrs/core/{antiban,ge,gui,movement,session,strategy,skills,utils}
mkdir -p core/src/test/java/com/osrs/core/{antiban,strategy,session,utils}
mkdir -p scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/{methods/{skilling,combat,processing,flipping,looting},model,gui,nodes}
```

**Step 6: Verify build compiles**

```bash
./gradlew build
```

**Step 7: Commit**

```bash
git add -A
git commit -m "feat: initialize Gradle multi-module project structure"
```

---

## Phase 2: Core Utilities & Base Classes

### Task 2.1: Random Utilities (Gaussian Distribution)

**Files:**
- Create: `core/src/main/java/com/osrs/core/utils/RandomUtil.java`
- Create: `core/src/test/java/com/osrs/core/utils/RandomUtilTest.java`

**Step 1: Write tests**

```java
package com.osrs.core.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

class RandomUtilTest {

    @RepeatedTest(100)
    void gaussianBetween_shouldReturnValueInRange() {
        int result = RandomUtil.gaussianBetween(100, 500);
        assertTrue(result >= 100 && result <= 500,
            "Result " + result + " not in range [100, 500]");
    }

    @RepeatedTest(100)
    void gaussianBetween_shouldClusterAroundMean() {
        // Run many samples and check distribution clusters around center
        int sum = 0;
        int samples = 1000;
        for (int i = 0; i < samples; i++) {
            sum += RandomUtil.gaussianBetween(100, 500);
        }
        double avg = sum / (double) samples;
        // Mean should be roughly 300 (center of 100-500)
        assertTrue(avg > 250 && avg < 350,
            "Average " + avg + " not near expected mean 300");
    }

    @Test
    void chance_shouldReturnTrueOrFalse() {
        // 100% chance should always be true
        assertTrue(RandomUtil.chance(100.0));
        // 0% chance should always be false
        assertFalse(RandomUtil.chance(0.0));
    }

    @RepeatedTest(100)
    void randomBetween_shouldReturnValueInRange() {
        int result = RandomUtil.randomBetween(10, 20);
        assertTrue(result >= 10 && result <= 20);
    }

    @Test
    void pickWeighted_shouldSelectFromWeightedOptions() {
        // Item with weight 100 should almost always win over weight 1
        String[] items = {"heavy", "light"};
        double[] weights = {100.0, 0.001};
        int heavyCount = 0;
        for (int i = 0; i < 100; i++) {
            if ("heavy".equals(RandomUtil.pickWeighted(items, weights))) {
                heavyCount++;
            }
        }
        assertTrue(heavyCount > 90, "Heavy should win almost always");
    }
}
```

**Step 2: Run tests — expect FAIL**

```bash
./gradlew core:test
```

**Step 3: Implement RandomUtil**

```java
package com.osrs.core.utils;

import java.util.Random;

public final class RandomUtil {

    private static final Random RANDOM = new Random();

    private RandomUtil() {}

    /**
     * Returns a random int between min and max (inclusive) using Gaussian distribution.
     * Values cluster around the midpoint, mimicking human reaction times.
     */
    public static int gaussianBetween(int min, int max) {
        double mean = (min + max) / 2.0;
        double stdDev = (max - min) / 6.0; // 99.7% within range
        double value;
        do {
            value = RANDOM.nextGaussian() * stdDev + mean;
        } while (value < min || value > max);
        return (int) Math.round(value);
    }

    /**
     * Returns a uniform random int between min and max (inclusive).
     */
    public static int randomBetween(int min, int max) {
        return min + RANDOM.nextInt(max - min + 1);
    }

    /**
     * Returns a random long between min and max (inclusive).
     */
    public static long randomBetween(long min, long max) {
        return min + (long) (RANDOM.nextDouble() * (max - min + 1));
    }

    /**
     * Returns true with the given percentage chance (0.0 - 100.0).
     */
    public static boolean chance(double percent) {
        return RANDOM.nextDouble() * 100.0 < percent;
    }

    /**
     * Picks an item from the array based on weighted probabilities.
     */
    public static <T> T pickWeighted(T[] items, double[] weights) {
        double totalWeight = 0;
        for (double w : weights) totalWeight += w;

        double roll = RANDOM.nextDouble() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < items.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return items[i];
        }
        return items[items.length - 1];
    }

    /**
     * Returns a random double between min and max.
     */
    public static double randomDouble(double min, double max) {
        return min + RANDOM.nextDouble() * (max - min);
    }
}
```

**Step 4: Run tests — expect PASS**

```bash
./gradlew core:test
```

**Step 5: Commit**

```bash
git add core/src/main/java/com/osrs/core/utils/RandomUtil.java core/src/test/java/com/osrs/core/utils/RandomUtilTest.java
git commit -m "feat: add RandomUtil with Gaussian distribution and weighted picking"
```

---

### Task 2.2: Timer Utility

**Files:**
- Create: `core/src/main/java/com/osrs/core/utils/TimeUtil.java`
- Create: `core/src/test/java/com/osrs/core/utils/TimeUtilTest.java`

**Step 1: Write tests**

```java
package com.osrs.core.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeUtilTest {

    @Test
    void formatRuntime_shouldFormatCorrectly() {
        assertEquals("00:00:00", TimeUtil.formatRuntime(0));
        assertEquals("01:30:45", TimeUtil.formatRuntime(5445000));
        assertEquals("00:01:00", TimeUtil.formatRuntime(60000));
    }

    @Test
    void formatGp_shouldAddSuffixes() {
        assertEquals("500", TimeUtil.formatGp(500));
        assertEquals("1.5K", TimeUtil.formatGp(1500));
        assertEquals("2.3M", TimeUtil.formatGp(2300000));
    }
}
```

**Step 2: Implement TimeUtil**

```java
package com.osrs.core.utils;

public final class TimeUtil {

    private TimeUtil() {}

    public static String formatRuntime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String formatGp(long amount) {
        if (amount >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000.0);
        } else if (amount >= 1_000) {
            return String.format("%.1fK", amount / 1_000.0);
        }
        return String.valueOf(amount);
    }

    public static long gpPerHour(long gpEarned, long runtimeMillis) {
        if (runtimeMillis <= 0) return 0;
        return (long) (gpEarned * 3_600_000.0 / runtimeMillis);
    }
}
```

**Step 3: Run tests — expect PASS**

```bash
./gradlew core:test
```

**Step 4: Commit**

```bash
git add core/src/main/java/com/osrs/core/utils/TimeUtil.java core/src/test/java/com/osrs/core/utils/TimeUtilTest.java
git commit -m "feat: add TimeUtil for runtime formatting and GP display"
```

---

### Task 2.3: Configuration System (JSON Profiles)

**Files:**
- Create: `core/src/main/java/com/osrs/core/utils/Config.java`
- Create: `core/src/test/java/com/osrs/core/utils/ConfigTest.java`

**Step 1: Write tests**

```java
package com.osrs.core.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @TempDir
    File tempDir;

    @Test
    void defaults_shouldHaveReasonableValues() {
        Config config = new Config();
        assertEquals(50000, config.getMinGpPerHour());
        assertEquals(40, config.getFleeHpPercent());
        assertTrue(config.isAutoBreaksEnabled());
        assertTrue(config.isFlippingEnabled());
    }

    @Test
    void saveAndLoad_shouldPreserveValues() {
        Config config = new Config();
        config.setMinGpPerHour(100000);
        config.setFleeHpPercent(25);
        config.setFlippingEnabled(false);

        File file = new File(tempDir, "test-profile.json");
        config.save(file);

        Config loaded = Config.load(file);
        assertEquals(100000, loaded.getMinGpPerHour());
        assertEquals(25, loaded.getFleeHpPercent());
        assertFalse(loaded.isFlippingEnabled());
    }

    @Test
    void load_nonExistentFile_shouldReturnDefaults() {
        File file = new File(tempDir, "nonexistent.json");
        Config loaded = Config.load(file);
        assertEquals(50000, loaded.getMinGpPerHour());
    }
}
```

**Step 2: Implement Config**

```java
package com.osrs.core.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.Files;

public class Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Money Making
    private int minGpPerHour = 50_000;
    private int methodSwitchMinMinutes = 30;
    private int methodSwitchMaxMinutes = 60;
    private int maxMethodsPerSession = 4;
    private String priority = "balanced"; // "gp", "xp", "balanced"

    // Combat
    private int eatHpPercent = 40;
    private int fleeHpPercent = 20; // changed from 40 to 20 to match design doc (flee at 20%)
    private int lootMinValue = 500;
    private boolean autoEquipGear = true;
    private boolean buryBones = true;

    // Grand Exchange
    private boolean autoSell = true;
    private boolean autoBuy = true;
    private boolean flippingEnabled = true;
    private int maxFlipInvestmentPercent = 50;
    private int maxFlipSlots = 2;
    private String sellStrategy = "market"; // "instant", "market", "patient"

    // Wilderness
    private boolean wildernessAllowed = true;
    private int maxRiskValue = 50_000;
    private boolean fleeOnPlayerSpotted = true;
    private int maxWildernessLevel = 20;
    private boolean hopAfterPk = true;

    // Anti-Ban
    private int antiBanIntensity = 70;
    private double misclickChance = 2.0;
    private String cameraFrequency = "medium"; // "low", "medium", "high"
    private double mouseOffScreenChance = 5.0;
    private double idleChance = 8.0;
    private boolean chatReaction = true;
    private String fatigueSpeed = "normal"; // "normal", "fast", "off"

    // Session
    private int minSessionMinutes = 30;
    private int maxSessionMinutes = 120;
    private int minBreakMinutes = 5;
    private int maxBreakMinutes = 30;
    private int maxDailyHours = 8;
    private boolean dailyVariation = true;

    // Safety
    private boolean stopOnJmod = true;
    private boolean screenshotOnBan = true;
    private boolean hopOnCrowded = true;
    private int minWorldPlayers = 300;
    private int maxWorldPlayers = 800;

    // Categories
    private boolean skillingEnabled = true;
    private boolean combatEnabled = true;
    private boolean processingEnabled = true;
    private boolean lootingEnabled = true;

    // Getters and setters for all fields
    public int getMinGpPerHour() { return minGpPerHour; }
    public void setMinGpPerHour(int v) { this.minGpPerHour = v; }
    public int getMethodSwitchMinMinutes() { return methodSwitchMinMinutes; }
    public void setMethodSwitchMinMinutes(int v) { this.methodSwitchMinMinutes = v; }
    public int getMethodSwitchMaxMinutes() { return methodSwitchMaxMinutes; }
    public void setMethodSwitchMaxMinutes(int v) { this.methodSwitchMaxMinutes = v; }
    public int getMaxMethodsPerSession() { return maxMethodsPerSession; }
    public void setMaxMethodsPerSession(int v) { this.maxMethodsPerSession = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }

    public int getEatHpPercent() { return eatHpPercent; }
    public void setEatHpPercent(int v) { this.eatHpPercent = v; }
    public int getFleeHpPercent() { return fleeHpPercent; }
    public void setFleeHpPercent(int v) { this.fleeHpPercent = v; }
    public int getLootMinValue() { return lootMinValue; }
    public void setLootMinValue(int v) { this.lootMinValue = v; }
    public boolean isAutoEquipGear() { return autoEquipGear; }
    public void setAutoEquipGear(boolean v) { this.autoEquipGear = v; }
    public boolean isBuryBones() { return buryBones; }
    public void setBuryBones(boolean v) { this.buryBones = v; }

    public boolean isAutoSell() { return autoSell; }
    public void setAutoSell(boolean v) { this.autoSell = v; }
    public boolean isAutoBuy() { return autoBuy; }
    public void setAutoBuy(boolean v) { this.autoBuy = v; }
    public boolean isFlippingEnabled() { return flippingEnabled; }
    public void setFlippingEnabled(boolean v) { this.flippingEnabled = v; }
    public int getMaxFlipInvestmentPercent() { return maxFlipInvestmentPercent; }
    public void setMaxFlipInvestmentPercent(int v) { this.maxFlipInvestmentPercent = v; }
    public int getMaxFlipSlots() { return maxFlipSlots; }
    public void setMaxFlipSlots(int v) { this.maxFlipSlots = v; }
    public String getSellStrategy() { return sellStrategy; }
    public void setSellStrategy(String v) { this.sellStrategy = v; }

    public boolean isWildernessAllowed() { return wildernessAllowed; }
    public void setWildernessAllowed(boolean v) { this.wildernessAllowed = v; }
    public int getMaxRiskValue() { return maxRiskValue; }
    public void setMaxRiskValue(int v) { this.maxRiskValue = v; }
    public boolean isFleeOnPlayerSpotted() { return fleeOnPlayerSpotted; }
    public void setFleeOnPlayerSpotted(boolean v) { this.fleeOnPlayerSpotted = v; }
    public int getMaxWildernessLevel() { return maxWildernessLevel; }
    public void setMaxWildernessLevel(int v) { this.maxWildernessLevel = v; }
    public boolean isHopAfterPk() { return hopAfterPk; }
    public void setHopAfterPk(boolean v) { this.hopAfterPk = v; }

    public int getAntiBanIntensity() { return antiBanIntensity; }
    public void setAntiBanIntensity(int v) { this.antiBanIntensity = v; }
    public double getMisclickChance() { return misclickChance; }
    public void setMisclickChance(double v) { this.misclickChance = v; }
    public String getCameraFrequency() { return cameraFrequency; }
    public void setCameraFrequency(String v) { this.cameraFrequency = v; }
    public double getMouseOffScreenChance() { return mouseOffScreenChance; }
    public void setMouseOffScreenChance(double v) { this.mouseOffScreenChance = v; }
    public double getIdleChance() { return idleChance; }
    public void setIdleChance(double v) { this.idleChance = v; }
    public boolean isChatReaction() { return chatReaction; }
    public void setChatReaction(boolean v) { this.chatReaction = v; }
    public String getFatigueSpeed() { return fatigueSpeed; }
    public void setFatigueSpeed(String v) { this.fatigueSpeed = v; }

    public int getMinSessionMinutes() { return minSessionMinutes; }
    public void setMinSessionMinutes(int v) { this.minSessionMinutes = v; }
    public int getMaxSessionMinutes() { return maxSessionMinutes; }
    public void setMaxSessionMinutes(int v) { this.maxSessionMinutes = v; }
    public int getMinBreakMinutes() { return minBreakMinutes; }
    public void setMinBreakMinutes(int v) { this.minBreakMinutes = v; }
    public int getMaxBreakMinutes() { return maxBreakMinutes; }
    public void setMaxBreakMinutes(int v) { this.maxBreakMinutes = v; }
    public int getMaxDailyHours() { return maxDailyHours; }
    public void setMaxDailyHours(int v) { this.maxDailyHours = v; }
    public boolean isDailyVariation() { return dailyVariation; }
    public void setDailyVariation(boolean v) { this.dailyVariation = v; }

    public boolean isStopOnJmod() { return stopOnJmod; }
    public void setStopOnJmod(boolean v) { this.stopOnJmod = v; }
    public boolean isScreenshotOnBan() { return screenshotOnBan; }
    public void setScreenshotOnBan(boolean v) { this.screenshotOnBan = v; }
    public boolean isHopOnCrowded() { return hopOnCrowded; }
    public void setHopOnCrowded(boolean v) { this.hopOnCrowded = v; }
    public int getMinWorldPlayers() { return minWorldPlayers; }
    public void setMinWorldPlayers(int v) { this.minWorldPlayers = v; }
    public int getMaxWorldPlayers() { return maxWorldPlayers; }
    public void setMaxWorldPlayers(int v) { this.maxWorldPlayers = v; }

    public boolean isSkillingEnabled() { return skillingEnabled; }
    public void setSkillingEnabled(boolean v) { this.skillingEnabled = v; }
    public boolean isCombatEnabled() { return combatEnabled; }
    public void setCombatEnabled(boolean v) { this.combatEnabled = v; }
    public boolean isProcessingEnabled() { return processingEnabled; }
    public void setProcessingEnabled(boolean v) { this.processingEnabled = v; }
    public boolean isLootingEnabled() { return lootingEnabled; }
    public void setLootingEnabled(boolean v) { this.lootingEnabled = v; }

    public boolean isAutoBreaksEnabled() { return minBreakMinutes > 0; }

    public void save(File file) {
        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Config load(File file) {
        if (!file.exists()) return new Config();
        try (Reader reader = new FileReader(file)) {
            Config config = GSON.fromJson(reader, Config.class);
            return config != null ? config : new Config();
        } catch (IOException e) {
            e.printStackTrace();
            return new Config();
        }
    }
}
```

**Step 3: Add Gson dependency to core/build.gradle**

```groovy
dependencies {
    implementation 'com.google.gson:gson:2.10.1'
}
```

**Step 4: Update scripts/moneymaker/build.gradle to include Gson in JAR**

```groovy
dependencies {
    implementation project(':core')
}

jar {
    from {
        project(':core').sourceSets.main.output
    }
    from {
        project(':core').configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) }
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory = file("${System.getProperty('user.home')}/DreamBot/Scripts")
    archiveBaseName = 'OSRSMoneyMaker'
}
```

**Step 5: Run tests — expect PASS**

```bash
./gradlew core:test
```

**Step 6: Commit**

```bash
git add core/ scripts/moneymaker/build.gradle
git commit -m "feat: add Config system with JSON save/load and all settings"
```

---

### Task 2.4: Skill Requirement Model

**Files:**
- Create: `core/src/main/java/com/osrs/core/skills/SkillRequirement.java`
- Create: `core/src/main/java/com/osrs/core/skills/AccountState.java`

**Step 1: Implement SkillRequirement**

```java
package com.osrs.core.skills;

public class SkillRequirement {
    private final String skill; // matches DreamBot Skill enum name
    private final int level;

    public SkillRequirement(String skill, int level) {
        this.skill = skill;
        this.level = level;
    }

    public String getSkill() { return skill; }
    public int getLevel() { return level; }
}
```

**Step 2: Implement AccountState**

```java
package com.osrs.core.skills;

import java.util.HashMap;
import java.util.Map;

/**
 * Snapshot of the current account state.
 * Populated from DreamBot API at runtime.
 */
public class AccountState {
    private final Map<String, Integer> skillLevels = new HashMap<>();
    private int combatLevel;
    private long cashStack;
    private boolean isMember;
    private int totalLevel;

    public void setSkillLevel(String skill, int level) {
        skillLevels.put(skill, level);
    }

    public int getSkillLevel(String skill) {
        return skillLevels.getOrDefault(skill, 1);
    }

    public int getCombatLevel() { return combatLevel; }
    public void setCombatLevel(int v) { this.combatLevel = v; }

    public long getCashStack() { return cashStack; }
    public void setCashStack(long v) { this.cashStack = v; }

    public boolean isMember() { return isMember; }
    public void setMember(boolean v) { this.isMember = v; }

    public int getTotalLevel() { return totalLevel; }
    public void setTotalLevel(int v) { this.totalLevel = v; }

    public boolean meetsRequirement(SkillRequirement req) {
        return getSkillLevel(req.getSkill()) >= req.getLevel();
    }
}
```

**Step 3: Commit**

```bash
git add core/src/main/java/com/osrs/core/skills/
git commit -m "feat: add SkillRequirement and AccountState models"
```

---

## Phase 3: Anti-Ban System

### Task 3.1: AntiBanProfile & Fatigue Model

**Files:**
- Create: `core/src/main/java/com/osrs/core/antiban/AntiBanProfile.java`
- Create: `core/src/main/java/com/osrs/core/antiban/FatigueModel.java`
- Create: `core/src/test/java/com/osrs/core/antiban/FatigueModelTest.java`

**Step 1: Write FatigueModel tests**

```java
package com.osrs.core.antiban;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FatigueModelTest {

    @Test
    void fatigue_shouldStartAtZero() {
        FatigueModel model = new FatigueModel("normal");
        assertEquals(0.0, model.getFatigueLevel(), 0.01);
    }

    @Test
    void fatigue_shouldIncreaseOverTime() {
        FatigueModel model = new FatigueModel("normal");
        // Simulate 60 minutes of play
        model.update(60 * 60 * 1000L);
        assertTrue(model.getFatigueLevel() > 0.0);
        assertTrue(model.getFatigueLevel() <= 1.0);
    }

    @Test
    void fatigue_shouldResetOnBreak() {
        FatigueModel model = new FatigueModel("normal");
        model.update(60 * 60 * 1000L);
        double before = model.getFatigueLevel();
        model.reset();
        assertTrue(model.getFatigueLevel() < before);
    }

    @Test
    void getDelayMultiplier_shouldIncreaseWithFatigue() {
        FatigueModel model = new FatigueModel("normal");
        double freshMultiplier = model.getDelayMultiplier();
        model.update(90 * 60 * 1000L);
        double tiredMultiplier = model.getDelayMultiplier();
        assertTrue(tiredMultiplier > freshMultiplier);
    }

    @Test
    void fatigue_offMode_shouldNotIncrease() {
        FatigueModel model = new FatigueModel("off");
        model.update(120 * 60 * 1000L);
        assertEquals(0.0, model.getFatigueLevel(), 0.01);
    }
}
```

**Step 2: Implement FatigueModel**

```java
package com.osrs.core.antiban;

/**
 * Simulates human fatigue. Reaction times gradually increase
 * as the session progresses. Resets partially on breaks.
 */
public class FatigueModel {

    private final String speed; // "normal", "fast", "off"
    private double fatigueLevel = 0.0; // 0.0 = fresh, 1.0 = exhausted

    public FatigueModel(String speed) {
        this.speed = speed;
    }

    /**
     * Update fatigue based on elapsed session time in milliseconds.
     */
    public void update(long sessionTimeMs) {
        if ("off".equals(speed)) return;

        double hours = sessionTimeMs / 3_600_000.0;
        double rate = "fast".equals(speed) ? 0.5 : 0.3;
        // Fatigue follows a logarithmic curve (diminishing returns)
        fatigueLevel = Math.min(1.0, Math.log1p(hours * rate) / Math.log1p(3.0 * rate));
    }

    /**
     * Partial reset after a break. Doesn't fully reset (like a real human).
     */
    public void reset() {
        fatigueLevel = Math.max(0.0, fatigueLevel * 0.3); // Retains 30% fatigue
    }

    public double getFatigueLevel() {
        return fatigueLevel;
    }

    /**
     * Returns delay multiplier based on current fatigue.
     * 1.0 = no delay increase, up to 1.8 when exhausted.
     */
    public double getDelayMultiplier() {
        return 1.0 + (fatigueLevel * 0.8);
    }
}
```

**Step 3: Implement AntiBanProfile**

```java
package com.osrs.core.antiban;

import com.osrs.core.utils.Config;
import com.osrs.core.utils.RandomUtil;

/**
 * Per-session anti-ban configuration. Slightly varies each session
 * to avoid detectable patterns across sessions.
 */
public class AntiBanProfile {

    private final double misclickChance;
    private final double mouseOffScreenChance;
    private final double idleChance;
    private final double cameraRotateChance;
    private final double tabSwitchChance;
    private final double examineChance;
    private final boolean chatReaction;
    private final FatigueModel fatigueModel;

    /**
     * Generate a profile from config with per-session variation.
     */
    public AntiBanProfile(Config config) {
        double intensity = config.getAntiBanIntensity() / 100.0;

        // Add +/- 20% variation to each setting per session
        this.misclickChance = vary(config.getMisclickChance() * intensity);
        this.mouseOffScreenChance = vary(config.getMouseOffScreenChance() * intensity);
        this.idleChance = vary(config.getIdleChance() * intensity);
        this.cameraRotateChance = vary(5.0 * intensity);
        this.tabSwitchChance = vary(3.0 * intensity);
        this.examineChance = vary(2.0 * intensity);
        this.chatReaction = config.isChatReaction();
        this.fatigueModel = new FatigueModel(config.getFatigueSpeed());
    }

    private double vary(double base) {
        double variation = RandomUtil.randomDouble(0.8, 1.2);
        return Math.max(0, base * variation);
    }

    public double getMisclickChance() { return misclickChance; }
    public double getMouseOffScreenChance() { return mouseOffScreenChance; }
    public double getIdleChance() { return idleChance; }
    public double getCameraRotateChance() { return cameraRotateChance; }
    public double getTabSwitchChance() { return tabSwitchChance; }
    public double getExamineChance() { return examineChance; }
    public boolean isChatReaction() { return chatReaction; }
    public FatigueModel getFatigueModel() { return fatigueModel; }
}
```

**Step 4: Run tests — expect PASS**

```bash
./gradlew core:test
```

**Step 5: Commit**

```bash
git add core/src/main/java/com/osrs/core/antiban/ core/src/test/java/com/osrs/core/antiban/
git commit -m "feat: add AntiBanProfile with FatigueModel and per-session variation"
```

---

### Task 3.2: AntiBanExecutor (DreamBot Integration)

**Files:**
- Create: `core/src/main/java/com/osrs/core/antiban/AntiBanExecutor.java`

**Step 1: Implement AntiBanExecutor**

This class uses DreamBot API methods and cannot be unit tested without the client.

```java
package com.osrs.core.antiban;

import com.osrs.core.utils.RandomUtil;
import org.dreambot.api.input.Mouse;
import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.input.Camera;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;

/**
 * Executes anti-ban actions based on the current AntiBanProfile.
 * Call performIdleAction() between main loop iterations.
 */
public class AntiBanExecutor {

    private final AntiBanProfile profile;
    private long lastActionTime = System.currentTimeMillis();
    private int cameraMovesCount = 0;
    private int tabSwitchesCount = 0;
    private int misclickCount = 0;
    private int idleMomentsCount = 0;

    public AntiBanExecutor(AntiBanProfile profile) {
        this.profile = profile;
    }

    /**
     * Called each loop iteration. Randomly performs anti-ban actions
     * based on profile probabilities.
     */
    public void performIdleAction() {
        if (RandomUtil.chance(profile.getIdleChance())) {
            doIdle();
        } else if (RandomUtil.chance(profile.getCameraRotateChance())) {
            doRandomCameraRotation();
        } else if (RandomUtil.chance(profile.getTabSwitchChance())) {
            doRandomTabSwitch();
        } else if (RandomUtil.chance(profile.getExamineChance())) {
            doExamineRandom();
        } else if (RandomUtil.chance(profile.getMouseOffScreenChance())) {
            doMouseOffScreen();
        }
    }

    /**
     * Apply fatigue-adjusted delay to a base sleep time.
     */
    public int adjustDelay(int baseDelay) {
        double multiplier = profile.getFatigueModel().getDelayMultiplier();
        return (int) (baseDelay * multiplier);
    }

    /**
     * Should be called before clicking to potentially cause a misclick.
     * Returns true if a misclick was performed (caller should then correct).
     */
    public boolean shouldMisclick() {
        if (RandomUtil.chance(profile.getMisclickChance())) {
            misclickCount++;
            return true;
        }
        return false;
    }

    private void doIdle() {
        idleMomentsCount++;
        AbstractScript.sleep(RandomUtil.gaussianBetween(500, 3000));
    }

    private void doRandomCameraRotation() {
        cameraMovesCount++;
        int yaw = Calculations.random(0, 360);
        int pitch = Calculations.random(200, 383);
        Camera.rotateTo(yaw, pitch);
    }

    private void doRandomTabSwitch() {
        tabSwitchesCount++;
        Tab[] tabs = {Tab.SKILLS, Tab.QUEST, Tab.COMBAT, Tab.EQUIPMENT, Tab.PRAYER};
        Tab randomTab = tabs[Calculations.random(0, tabs.length - 1)];
        Tabs.open(randomTab);
        AbstractScript.sleep(RandomUtil.gaussianBetween(800, 2500));

        // Sometimes hover a skill
        if (randomTab == Tab.SKILLS && RandomUtil.chance(50)) {
            Skills.hoverSkill(Skills.getRealLevel(org.dreambot.api.methods.skills.Skill.values()[
                Calculations.random(0, org.dreambot.api.methods.skills.Skill.values().length - 1)
            ]) > 0 ? org.dreambot.api.methods.skills.Skill.values()[
                Calculations.random(0, org.dreambot.api.methods.skills.Skill.values().length - 1)
            ] : org.dreambot.api.methods.skills.Skill.HITPOINTS);
            AbstractScript.sleep(RandomUtil.gaussianBetween(500, 1500));
        }

        Tabs.open(Tab.INVENTORY);
    }

    private void doExamineRandom() {
        // Try to examine a nearby NPC or object
        NPC npc = NPCs.closest(n -> n != null && n.distance() < 10);
        if (npc != null && RandomUtil.chance(50)) {
            npc.interact("Examine");
            AbstractScript.sleep(RandomUtil.gaussianBetween(400, 1200));
            return;
        }

        GameObject obj = GameObjects.closest(o -> o != null && o.distance() < 10);
        if (obj != null) {
            obj.interact("Examine");
            AbstractScript.sleep(RandomUtil.gaussianBetween(400, 1200));
        }
    }

    private void doMouseOffScreen() {
        Mouse.moveOutsideScreen();
        AbstractScript.sleep(RandomUtil.gaussianBetween(2000, 8000));
    }

    // Stats for GUI
    public int getCameraMovesCount() { return cameraMovesCount; }
    public int getTabSwitchesCount() { return tabSwitchesCount; }
    public int getMisclickCount() { return misclickCount; }
    public int getIdleMomentsCount() { return idleMomentsCount; }
}
```

**Step 2: Commit**

```bash
git add core/src/main/java/com/osrs/core/antiban/AntiBanExecutor.java
git commit -m "feat: add AntiBanExecutor with idle actions, misclicks, camera, and fatigue"
```

---

## Phase 4: Session Management

### Task 4.1: DailySchedule & SessionManager

**Files:**
- Create: `core/src/main/java/com/osrs/core/session/DailySchedule.java`
- Create: `core/src/main/java/com/osrs/core/session/SessionManager.java`
- Create: `core/src/test/java/com/osrs/core/session/DailyScheduleTest.java`

**Step 1: Write DailySchedule tests**

```java
package com.osrs.core.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

class DailyScheduleTest {

    @Test
    void generate_shouldCreateValidSchedule() {
        DailySchedule schedule = DailySchedule.generate(8, true);
        assertFalse(schedule.getSessions().isEmpty());
        assertTrue(schedule.getTotalPlaytimeMinutes() > 0);
        assertTrue(schedule.getTotalPlaytimeMinutes() <= 8 * 60 + 30); // Some buffer
    }

    @RepeatedTest(20)
    void generate_withVariation_shouldProduceDifferentSchedules() {
        DailySchedule s1 = DailySchedule.generate(8, true);
        DailySchedule s2 = DailySchedule.generate(8, true);
        // Not guaranteed to differ each time, but over 20 runs they should vary
        // This test mainly ensures no crashes
        assertNotNull(s1.getSessions());
        assertNotNull(s2.getSessions());
    }

    @Test
    void sessionDurations_shouldBeInRange() {
        DailySchedule schedule = DailySchedule.generate(8, true);
        for (DailySchedule.Session session : schedule.getSessions()) {
            assertTrue(session.getPlayMinutes() >= 20, "Session too short: " + session.getPlayMinutes());
            assertTrue(session.getPlayMinutes() <= 150, "Session too long: " + session.getPlayMinutes());
            assertTrue(session.getBreakMinutes() >= 3, "Break too short: " + session.getBreakMinutes());
            assertTrue(session.getBreakMinutes() <= 40, "Break too long: " + session.getBreakMinutes());
        }
    }
}
```

**Step 2: Implement DailySchedule**

```java
package com.osrs.core.session;

import com.osrs.core.utils.RandomUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a realistic daily play schedule with sessions and breaks.
 */
public class DailySchedule {

    private final List<Session> sessions;

    private DailySchedule(List<Session> sessions) {
        this.sessions = sessions;
    }

    public static DailySchedule generate(int maxHours, boolean variation) {
        int targetMinutes = maxHours * 60;
        if (variation) {
            // Vary total playtime by +/- 25%
            targetMinutes = RandomUtil.gaussianBetween(
                (int) (targetMinutes * 0.75),
                (int) (targetMinutes * 1.25)
            );
        }

        List<Session> sessions = new ArrayList<>();
        int totalPlayed = 0;

        while (totalPlayed < targetMinutes) {
            int remaining = targetMinutes - totalPlayed;
            if (remaining < 20) break; // Don't create tiny sessions

            // Session length: Gaussian around 60 min, range 30-120
            int playMinutes = RandomUtil.gaussianBetween(
                Math.max(20, Math.min(30, remaining)),
                Math.min(120, remaining)
            );

            // Break length: Gaussian around 15 min, range 5-30
            int breakMinutes = RandomUtil.gaussianBetween(5, 30);

            // Last session gets no break
            if (totalPlayed + playMinutes >= targetMinutes) {
                breakMinutes = 0;
            }

            sessions.add(new Session(playMinutes, breakMinutes));
            totalPlayed += playMinutes;
        }

        return new DailySchedule(sessions);
    }

    public List<Session> getSessions() { return sessions; }

    public int getTotalPlaytimeMinutes() {
        return sessions.stream().mapToInt(Session::getPlayMinutes).sum();
    }

    public static class Session {
        private final int playMinutes;
        private final int breakMinutes;

        public Session(int playMinutes, int breakMinutes) {
            this.playMinutes = playMinutes;
            this.breakMinutes = breakMinutes;
        }

        public int getPlayMinutes() { return playMinutes; }
        public int getBreakMinutes() { return breakMinutes; }
    }
}
```

**Step 3: Implement SessionManager**

```java
package com.osrs.core.session;

import com.osrs.core.utils.Config;
import com.osrs.core.utils.RandomUtil;

/**
 * Manages the current play session within a DailySchedule.
 * Tracks when to break, when to stop, and session transitions.
 */
public class SessionManager {

    private final Config config;
    private DailySchedule schedule;
    private int currentSessionIndex = 0;
    private long sessionStartTime;
    private long breakStartTime;
    private boolean onBreak = false;
    private boolean dayComplete = false;

    public SessionManager(Config config) {
        this.config = config;
        this.schedule = DailySchedule.generate(
            config.getMaxDailyHours(),
            config.isDailyVariation()
        );
        this.sessionStartTime = System.currentTimeMillis();
    }

    /**
     * Check if it's time to take a break or stop.
     * Call this each loop iteration.
     */
    public SessionAction check() {
        if (dayComplete) return SessionAction.STOP;
        if (onBreak) return checkBreakEnd();
        return checkSessionEnd();
    }

    private SessionAction checkSessionEnd() {
        if (currentSessionIndex >= schedule.getSessions().size()) {
            dayComplete = true;
            return SessionAction.STOP;
        }

        DailySchedule.Session current = schedule.getSessions().get(currentSessionIndex);
        long elapsed = System.currentTimeMillis() - sessionStartTime;
        long sessionDuration = current.getPlayMinutes() * 60_000L;

        // Add some randomness to exact break time (+/- 5 min)
        long jitter = RandomUtil.gaussianBetween(-5, 5) * 60_000L;

        if (elapsed >= sessionDuration + jitter) {
            if (current.getBreakMinutes() > 0) {
                onBreak = true;
                breakStartTime = System.currentTimeMillis();
                return SessionAction.START_BREAK;
            } else {
                dayComplete = true;
                return SessionAction.STOP;
            }
        }

        return SessionAction.CONTINUE;
    }

    private SessionAction checkBreakEnd() {
        DailySchedule.Session current = schedule.getSessions().get(currentSessionIndex);
        long breakElapsed = System.currentTimeMillis() - breakStartTime;
        long breakDuration = current.getBreakMinutes() * 60_000L;

        if (breakElapsed >= breakDuration) {
            onBreak = false;
            currentSessionIndex++;
            sessionStartTime = System.currentTimeMillis();

            if (currentSessionIndex >= schedule.getSessions().size()) {
                dayComplete = true;
                return SessionAction.STOP;
            }
            return SessionAction.END_BREAK;
        }

        return SessionAction.ON_BREAK;
    }

    public boolean isOnBreak() { return onBreak; }
    public boolean isDayComplete() { return dayComplete; }
    public int getCurrentSessionIndex() { return currentSessionIndex; }
    public int getTotalSessions() { return schedule.getSessions().size(); }

    public long getMillisUntilBreak() {
        if (currentSessionIndex >= schedule.getSessions().size()) return 0;
        DailySchedule.Session current = schedule.getSessions().get(currentSessionIndex);
        long elapsed = System.currentTimeMillis() - sessionStartTime;
        return Math.max(0, current.getPlayMinutes() * 60_000L - elapsed);
    }

    public enum SessionAction {
        CONTINUE,      // Keep playing
        START_BREAK,   // Time for a break — log out
        ON_BREAK,      // Still on break
        END_BREAK,     // Break over — log back in
        STOP           // Day is done
    }
}
```

**Step 4: Run tests — expect PASS**

```bash
./gradlew core:test
```

**Step 5: Commit**

```bash
git add core/src/main/java/com/osrs/core/session/ core/src/test/java/com/osrs/core/session/
git commit -m "feat: add DailySchedule and SessionManager with realistic play patterns"
```

---

## Phase 5: Strategy Selector

### Task 5.1: MoneyMethod Interface & Requirements Model

**Files:**
- Create: `scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/model/MoneyMethod.java`
- Create: `scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/model/MethodRequirements.java`
- Create: `scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/model/MethodCategory.java`
- Create: `scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/model/RiskLevel.java`

**Step 1: Create enums**

```java
// MethodCategory.java
package com.osrs.scripts.moneymaker.model;

public enum MethodCategory {
    SKILLING, COMBAT, PROCESSING, FLIPPING, LOOTING
}
```

```java
// RiskLevel.java
package com.osrs.scripts.moneymaker.model;

public enum RiskLevel {
    NONE(1.0),
    LOW(0.85),
    MEDIUM(0.65),
    HIGH(0.4),
    EXTREME(0.2);

    private final double safetyMultiplier;

    RiskLevel(double safetyMultiplier) {
        this.safetyMultiplier = safetyMultiplier;
    }

    public double getSafetyMultiplier() { return safetyMultiplier; }
}
```

**Step 2: Create MethodRequirements**

```java
package com.osrs.scripts.moneymaker.model;

import com.osrs.core.skills.AccountState;
import com.osrs.core.skills.SkillRequirement;
import java.util.ArrayList;
import java.util.List;

public class MethodRequirements {

    private final List<SkillRequirement> skillRequirements = new ArrayList<>();
    private boolean membersOnly = false;
    private int minCombatLevel = 0;
    private long minCashStack = 0;
    private List<String> requiredItems = new ArrayList<>();
    private List<String> requiredQuests = new ArrayList<>();

    public MethodRequirements addSkill(String skill, int level) {
        skillRequirements.add(new SkillRequirement(skill, level));
        return this;
    }

    public MethodRequirements setMembersOnly(boolean v) { this.membersOnly = v; return this; }
    public MethodRequirements setMinCombatLevel(int v) { this.minCombatLevel = v; return this; }
    public MethodRequirements setMinCashStack(long v) { this.minCashStack = v; return this; }

    public MethodRequirements addRequiredItem(String item) {
        requiredItems.add(item);
        return this;
    }

    public MethodRequirements addRequiredQuest(String quest) {
        requiredQuests.add(quest);
        return this;
    }

    /**
     * Check if the account meets all requirements.
     */
    public boolean isMet(AccountState state) {
        if (membersOnly && !state.isMember()) return false;
        if (state.getCombatLevel() < minCombatLevel) return false;
        if (state.getCashStack() < minCashStack) return false;
        for (SkillRequirement req : skillRequirements) {
            if (!state.meetsRequirement(req)) return false;
        }
        return true;
    }

    public boolean isMembersOnly() { return membersOnly; }
    public List<SkillRequirement> getSkillRequirements() { return skillRequirements; }
}
```

**Step 3: Create MoneyMethod interface**

```java
package com.osrs.scripts.moneymaker.model;

import com.osrs.core.skills.AccountState;

/**
 * Interface for all money-making methods.
 * Implement this to add a new method to the bot.
 */
public interface MoneyMethod {

    /** Display name for GUI */
    String getName();

    /** Category for filtering */
    MethodCategory getCategory();

    /** Requirements to use this method */
    MethodRequirements getRequirements();

    /** Estimated GP per hour at current level */
    int getEstimatedGpPerHour(AccountState state);

    /** Risk level for weighted scoring */
    RiskLevel getRiskLevel();

    /** Skills that gain XP from this method (for progression scoring) */
    String[] getTrainedSkills();

    /**
     * Execute one cycle of this method (e.g., mine one inventory, kill one monster).
     * Returns sleep time in ms before next loop call.
     */
    int execute();

    /** Called when switching to this method. Set up state, walk to location, etc. */
    default void onActivate() {}

    /** Called when switching away from this method. Clean up. */
    default void onDeactivate() {}

    /** Whether this method can currently run (e.g., has required items in bank) */
    default boolean isReady() { return true; }
}
```

**Step 4: Commit**

```bash
git add scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/model/
git commit -m "feat: add MoneyMethod interface, MethodRequirements, and supporting enums"
```

---

### Task 5.2: StrategySelector (Rule-Based + Weighted Scoring)

**Files:**
- Create: `core/src/main/java/com/osrs/core/strategy/StrategySelector.java`
- Create: `core/src/main/java/com/osrs/core/strategy/MethodScore.java`
- Create: `core/src/test/java/com/osrs/core/strategy/StrategySelectorTest.java`

**Step 1: Write tests**

```java
package com.osrs.core.strategy;

import com.osrs.core.skills.AccountState;
import com.osrs.core.utils.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

class StrategySelectorTest {

    private Config config;
    private AccountState state;

    @BeforeEach
    void setup() {
        config = new Config();
        state = new AccountState();
        state.setSkillLevel("MINING", 40);
        state.setSkillLevel("WOODCUTTING", 30);
        state.setCombatLevel(25);
        state.setCashStack(100_000);
        state.setMember(false);
        state.setTotalLevel(200);
    }

    @Test
    void filterMethods_shouldRemoveIneligible() {
        StrategySelector selector = new StrategySelector(config);

        // Mock methods as MethodScore data
        List<MethodScore> scores = new ArrayList<>();
        scores.add(new MethodScore("Iron Ore", 150_000, 0.0, false, new String[]{"MINING"}));
        scores.add(new MethodScore("Runite Ore", 500_000, 0.0, true, new String[]{"MINING"})); // members only

        List<MethodScore> filtered = selector.filterByAccount(scores, state);
        assertEquals(1, filtered.size());
        assertEquals("Iron Ore", filtered.get(0).getName());
    }

    @Test
    void scoreMethod_shouldProducePositiveScores() {
        StrategySelector selector = new StrategySelector(config);
        MethodScore score = new MethodScore("Iron Ore", 150_000, 0.5, false, new String[]{"MINING"});
        double result = selector.calculateScore(score, state, new HashMap<>());
        assertTrue(result > 0, "Score should be positive");
    }

    @Test
    void selectMethod_shouldReturnNonNull() {
        StrategySelector selector = new StrategySelector(config);
        List<MethodScore> methods = List.of(
            new MethodScore("Iron Ore", 150_000, 0.5, false, new String[]{"MINING"}),
            new MethodScore("Oak Logs", 40_000, 0.0, false, new String[]{"WOODCUTTING"})
        );
        MethodScore selected = selector.selectProbabilistic(methods, state, new HashMap<>());
        assertNotNull(selected);
    }
}
```

**Step 2: Implement MethodScore**

```java
package com.osrs.core.strategy;

/**
 * Lightweight data class representing a method's scoring attributes.
 * Decoupled from MoneyMethod to allow unit testing without DreamBot.
 */
public class MethodScore {
    private final String name;
    private final int estimatedGpPerHour;
    private final double riskMultiplier; // 0.0 (no risk) to 1.0 (extreme)
    private final boolean membersOnly;
    private final String[] trainedSkills;

    public MethodScore(String name, int estimatedGpPerHour, double riskMultiplier,
                       boolean membersOnly, String[] trainedSkills) {
        this.name = name;
        this.estimatedGpPerHour = estimatedGpPerHour;
        this.riskMultiplier = riskMultiplier;
        this.membersOnly = membersOnly;
        this.trainedSkills = trainedSkills;
    }

    public String getName() { return name; }
    public int getEstimatedGpPerHour() { return estimatedGpPerHour; }
    public double getRiskMultiplier() { return riskMultiplier; }
    public boolean isMembersOnly() { return membersOnly; }
    public String[] getTrainedSkills() { return trainedSkills; }
}
```

**Step 3: Implement StrategySelector**

```java
package com.osrs.core.strategy;

import com.osrs.core.skills.AccountState;
import com.osrs.core.utils.Config;
import com.osrs.core.utils.RandomUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Two-step method selection:
 * 1. Rule-based filter (remove ineligible methods)
 * 2. Weighted scoring with probabilistic selection
 */
public class StrategySelector {

    private static final double WEIGHT_GP = 0.30;
    private static final double WEIGHT_PROGRESSION = 0.20;
    private static final double WEIGHT_RECENCY = 0.20;
    private static final double WEIGHT_RISK = 0.15;
    private static final double WEIGHT_AVAILABILITY = 0.15;

    private final Config config;

    public StrategySelector(Config config) {
        this.config = config;
    }

    /**
     * Filter methods by account state (membership, skills, etc.)
     */
    public List<MethodScore> filterByAccount(List<MethodScore> methods, AccountState state) {
        return methods.stream()
            .filter(m -> !m.isMembersOnly() || state.isMember())
            .filter(m -> m.getEstimatedGpPerHour() >= config.getMinGpPerHour())
            .collect(Collectors.toList());
    }

    /**
     * Calculate weighted score for a method.
     * @param lastUsedTimes map of method name -> epoch ms when last used
     */
    public double calculateScore(MethodScore method, AccountState state,
                                  Map<String, Long> lastUsedTimes) {
        double gpScore = normalizeGp(method.getEstimatedGpPerHour());
        double progressionScore = calculateProgressionScore(method, state);
        double recencyScore = calculateRecencyScore(method.getName(), lastUsedTimes);
        double riskScore = 1.0 - method.getRiskMultiplier();
        double availabilityScore = 0.7; // Placeholder — in real impl, check player count at location

        double totalScore =
            gpScore * WEIGHT_GP +
            progressionScore * WEIGHT_PROGRESSION +
            recencyScore * WEIGHT_RECENCY +
            riskScore * WEIGHT_RISK +
            availabilityScore * WEIGHT_AVAILABILITY;

        // Apply priority modifier
        switch (config.getPriority()) {
            case "gp":
                totalScore += gpScore * 0.2;
                break;
            case "xp":
                totalScore += progressionScore * 0.2;
                break;
            // "balanced" — no modifier
        }

        return Math.max(0.01, totalScore); // Never zero
    }

    /**
     * Select a method probabilistically based on scores.
     * Higher scores = more likely, but not guaranteed.
     */
    public MethodScore selectProbabilistic(List<MethodScore> methods, AccountState state,
                                            Map<String, Long> lastUsedTimes) {
        if (methods.isEmpty()) return null;
        if (methods.size() == 1) return methods.get(0);

        double[] scores = new double[methods.size()];
        for (int i = 0; i < methods.size(); i++) {
            scores[i] = calculateScore(methods.get(i), state, lastUsedTimes);
        }

        return RandomUtil.pickWeighted(
            methods.toArray(new MethodScore[0]),
            scores
        );
    }

    private double normalizeGp(int gpPerHour) {
        // Normalize to 0-1 range. 2M/h = 1.0, 0 = 0.0
        return Math.min(1.0, gpPerHour / 2_000_000.0);
    }

    private double calculateProgressionScore(MethodScore method, AccountState state) {
        // Methods that train low-level skills score higher
        double score = 0;
        for (String skill : method.getTrainedSkills()) {
            int level = state.getSkillLevel(skill);
            // Lower levels = higher progression value
            score += Math.max(0, (99 - level) / 99.0);
        }
        return method.getTrainedSkills().length > 0
            ? score / method.getTrainedSkills().length
            : 0.0;
    }

    private double calculateRecencyScore(String methodName, Map<String, Long> lastUsedTimes) {
        if (!lastUsedTimes.containsKey(methodName)) return 1.0; // Never used = high score
        long elapsed = System.currentTimeMillis() - lastUsedTimes.get(methodName);
        long oneHour = 3_600_000L;
        return Math.min(1.0, elapsed / (double) (oneHour * 3)); // Full score after 3 hours
    }
}
```

**Step 4: Run tests — expect PASS**

```bash
./gradlew core:test
```

**Step 5: Commit**

```bash
git add core/src/main/java/com/osrs/core/strategy/ core/src/test/java/com/osrs/core/strategy/
git commit -m "feat: add StrategySelector with rule-based filtering and weighted scoring"
```

---

## Phase 6: Safety & Flee System

### Task 6.1: ThreatDetector & FleeManager

**Files:**
- Create: `core/src/main/java/com/osrs/core/movement/ThreatDetector.java`
- Create: `core/src/main/java/com/osrs/core/movement/FleeManager.java`
- Create: `core/src/main/java/com/osrs/core/movement/ThreatLevel.java`

**Step 1: Create ThreatLevel**

```java
package com.osrs.core.movement;

public enum ThreatLevel {
    NONE,
    LOW,       // Crowded area, switch spots
    MEDIUM,    // Player spotted in wildy, prepare to flee
    HIGH,      // PKer attacking, flee now
    CRITICAL   // JMod detected, stop everything
}
```

**Step 2: Implement ThreatDetector**

```java
package com.osrs.core.movement;

import com.osrs.core.utils.Config;
import org.dreambot.api.methods.combat.Combat;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.world.Worlds;
import org.dreambot.api.wrappers.interactive.Player;

import java.util.List;

/**
 * Continuously monitors for threats and returns the current threat level.
 */
public class ThreatDetector {

    private final Config config;
    private boolean jmodDetected = false;

    public ThreatDetector(Config config) {
        this.config = config;
    }

    /**
     * Assess current threat level. Call each loop iteration.
     */
    public ThreatLevel assess() {
        if (checkJmod()) return ThreatLevel.CRITICAL;
        if (checkHealth()) return ThreatLevel.HIGH;
        if (checkPkers()) return ThreatLevel.HIGH;
        if (checkPoison()) return ThreatLevel.MEDIUM;
        if (checkCrowded()) return ThreatLevel.LOW;
        return ThreatLevel.NONE;
    }

    private boolean checkJmod() {
        if (!config.isStopOnJmod()) return false;
        List<Player> players = Players.all();
        for (Player p : players) {
            if (p != null && p.getName() != null) {
                String name = p.getName();
                if (name.startsWith("Mod ") || name.startsWith("Mod_")) {
                    jmodDetected = true;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkHealth() {
        int currentHp = Skills.getBoostedLevel(Skill.HITPOINTS);
        int maxHp = Skills.getRealLevel(Skill.HITPOINTS);
        if (maxHp <= 0) return false;
        double hpPercent = (currentHp * 100.0) / maxHp;
        return hpPercent <= config.getFleeHpPercent();
    }

    private boolean checkPkers() {
        if (!isInWilderness()) return false;
        Player local = Players.getLocal();
        if (local == null) return false;
        int myCombat = local.getLevel();

        for (Player p : Players.all()) {
            if (p == null || p.equals(local)) continue;
            if (p.getName() == null) continue;
            // Check if player is within combat level range for wilderness
            int diff = Math.abs(p.getLevel() - myCombat);
            int wildyLevel = getWildernessLevel();
            if (diff <= wildyLevel && config.isFleeOnPlayerSpotted()) {
                return true;
            }
        }
        return false;
    }

    private boolean checkPoison() {
        // Check via combat poisoned status
        return Combat.isPoisoned();
    }

    private boolean checkCrowded() {
        if (!config.isHopOnCrowded()) return false;
        int worldCount = Worlds.getCurrentWorld() != null ?
            Players.all().size() : 0;
        return worldCount > 15; // More than 15 players in visible range
    }

    private boolean isInWilderness() {
        Player local = Players.getLocal();
        if (local == null) return false;
        Tile tile = local.getTile();
        // Wilderness: y >= 3520 in surface level (z=0)
        return tile != null && tile.getY() >= 3520 && tile.getZ() == 0;
    }

    private int getWildernessLevel() {
        Player local = Players.getLocal();
        if (local == null || local.getTile() == null) return 0;
        int y = local.getTile().getY();
        return Math.max(0, (y - 3520) / 8 + 1);
    }

    public boolean isJmodDetected() { return jmodDetected; }
}
```

**Step 3: Implement FleeManager**

```java
package com.osrs.core.movement;

import com.osrs.core.utils.RandomUtil;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.wrappers.interactive.Player;

/**
 * Handles fleeing from dangerous situations.
 */
public class FleeManager {

    // Safe zones
    private static final Tile LUMBRIDGE = new Tile(3222, 3218);
    private static final Tile VARROCK_SQUARE = new Tile(3212, 3422);
    private static final Tile EDGEVILLE_BANK = new Tile(3094, 3491);
    private static final Area WILDERNESS = new Area(2944, 3520, 3391, 3968);

    /**
     * Execute flee sequence based on threat level.
     * Returns true if successfully fled, false if stuck.
     */
    public boolean flee(ThreatLevel threat) {
        switch (threat) {
            case CRITICAL:
                return emergencyLogout();
            case HIGH:
                return fleeToSafety();
            case MEDIUM:
                return retreatToBankOrHop();
            case LOW:
                return worldHop();
            default:
                return true;
        }
    }

    private boolean emergencyLogout() {
        // Try to log out immediately
        Tabs.logout();
        AbstractScript.sleep(RandomUtil.gaussianBetween(500, 1000));
        return true;
    }

    private boolean fleeToSafety() {
        // 1. Try teleport
        if (tryTeleport()) return true;

        // 2. Run to nearest safe zone
        enableRun();
        Tile safeTile = getNearestSafeZone();
        Walking.walk(safeTile);
        return true;
    }

    private boolean tryTeleport() {
        // Check for teleport items in inventory
        if (Inventory.contains("Varrock teleport")) {
            Inventory.interact("Varrock teleport", "Break");
            AbstractScript.sleep(RandomUtil.gaussianBetween(1800, 2500));
            return true;
        }
        if (Inventory.contains("Teleport to house")) {
            Inventory.interact("Teleport to house", "Break");
            AbstractScript.sleep(RandomUtil.gaussianBetween(1800, 2500));
            return true;
        }
        if (Inventory.contains("Amulet of glory(1)", "Amulet of glory(2)",
                "Amulet of glory(3)", "Amulet of glory(4)",
                "Amulet of glory(5)", "Amulet of glory(6)")) {
            // Rub glory to Edgeville
            Inventory.interact(i -> i.getName().startsWith("Amulet of glory"), "Rub");
            AbstractScript.sleep(RandomUtil.gaussianBetween(1000, 1500));
            // Select Edgeville from dialogue
            return true;
        }
        return false;
    }

    private Tile getNearestSafeZone() {
        Player local = Players.getLocal();
        if (local == null) return LUMBRIDGE;

        Tile playerTile = local.getTile();
        if (playerTile == null) return LUMBRIDGE;

        // If in wilderness, run south to level 1/border
        if (WILDERNESS.contains(playerTile)) {
            return new Tile(playerTile.getX(), 3520, 0); // Wilderness border
        }

        // Otherwise, nearest bank/safe zone
        double distLumby = playerTile.distance(LUMBRIDGE);
        double distVarrock = playerTile.distance(VARROCK_SQUARE);
        double distEdge = playerTile.distance(EDGEVILLE_BANK);

        if (distEdge < distLumby && distEdge < distVarrock) return EDGEVILLE_BANK;
        if (distVarrock < distLumby) return VARROCK_SQUARE;
        return LUMBRIDGE;
    }

    private boolean retreatToBankOrHop() {
        enableRun();
        // Walk to nearest bank
        Walking.walk(getNearestSafeZone());
        return true;
    }

    private boolean worldHop() {
        // World hopping handled by caller — return true to signal "handled"
        return true;
    }

    private void enableRun() {
        if (!Walking.isRunEnabled() && Walking.getRunEnergy() > 10) {
            Walking.toggleRun();
        }
    }
}
```

**Step 4: Commit**

```bash
git add core/src/main/java/com/osrs/core/movement/
git commit -m "feat: add ThreatDetector and FleeManager with wilderness awareness"
```

---

## Phase 7: Grand Exchange System

### Task 7.1: GE Manager

**Files:**
- Create: `core/src/main/java/com/osrs/core/ge/GrandExchangeManager.java`
- Create: `core/src/main/java/com/osrs/core/ge/FlipTracker.java`
- Create: `core/src/main/java/com/osrs/core/ge/SellOrder.java`

**Step 1: Implement SellOrder**

```java
package com.osrs.core.ge;

public class SellOrder {
    private final String itemName;
    private final int quantity;
    private final String strategy; // "instant", "market", "patient"

    public SellOrder(String itemName, int quantity, String strategy) {
        this.itemName = itemName;
        this.quantity = quantity;
        this.strategy = strategy;
    }

    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public String getStrategy() { return strategy; }
}
```

**Step 2: Implement FlipTracker**

```java
package com.osrs.core.ge;

import java.util.*;

/**
 * Tracks GE flip attempts and their profitability.
 */
public class FlipTracker {

    private final Map<String, FlipRecord> activeFlips = new HashMap<>();
    private final List<FlipRecord> completedFlips = new ArrayList<>();
    private long totalProfit = 0;

    public void startFlip(String itemName, int buyPrice, int quantity) {
        activeFlips.put(itemName, new FlipRecord(itemName, buyPrice, quantity));
    }

    public void completeFlip(String itemName, int sellPrice) {
        FlipRecord record = activeFlips.remove(itemName);
        if (record != null) {
            record.setSellPrice(sellPrice);
            record.setCompleted(true);
            long profit = (long)(sellPrice - record.getBuyPrice()) * record.getQuantity();
            record.setProfit(profit);
            totalProfit += profit;
            completedFlips.add(record);
        }
    }

    public Map<String, FlipRecord> getActiveFlips() { return activeFlips; }
    public List<FlipRecord> getCompletedFlips() { return completedFlips; }
    public long getTotalProfit() { return totalProfit; }

    public static class FlipRecord {
        private final String itemName;
        private final int buyPrice;
        private final int quantity;
        private int sellPrice;
        private long profit;
        private boolean completed;

        public FlipRecord(String itemName, int buyPrice, int quantity) {
            this.itemName = itemName;
            this.buyPrice = buyPrice;
            this.quantity = quantity;
        }

        public String getItemName() { return itemName; }
        public int getBuyPrice() { return buyPrice; }
        public int getQuantity() { return quantity; }
        public int getSellPrice() { return sellPrice; }
        public void setSellPrice(int v) { this.sellPrice = v; }
        public long getProfit() { return profit; }
        public void setProfit(long v) { this.profit = v; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean v) { this.completed = v; }
    }
}
```

**Step 3: Implement GrandExchangeManager**

```java
package com.osrs.core.ge;

import com.osrs.core.utils.Config;
import com.osrs.core.utils.RandomUtil;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.grandexchange.GrandExchange;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.script.AbstractScript;

import java.util.*;

/**
 * Manages all GE operations: selling, buying, and flipping.
 */
public class GrandExchangeManager {

    private static final Tile GE_TILE = new Tile(3164, 3487);

    private final Config config;
    private final FlipTracker flipTracker = new FlipTracker();
    private final Queue<SellOrder> sellQueue = new LinkedList<>();
    private final Queue<String> buyQueue = new LinkedList<>();

    public GrandExchangeManager(Config config) {
        this.config = config;
    }

    /**
     * Queue items for selling.
     */
    public void queueSell(String itemName, int quantity) {
        sellQueue.add(new SellOrder(itemName, quantity, config.getSellStrategy()));
    }

    /**
     * Queue items for buying.
     */
    public void queueBuy(String itemName) {
        buyQueue.add(itemName);
    }

    /**
     * Walk to GE if not there.
     */
    public boolean walkToGe() {
        if (Players.getLocal() != null && Players.getLocal().getTile().distance(GE_TILE) > 10) {
            Walking.walk(GE_TILE);
            AbstractScript.sleep(RandomUtil.gaussianBetween(600, 1200));
            return false; // Still walking
        }
        return true;
    }

    /**
     * Process pending sell orders.
     */
    public boolean processSellQueue() {
        if (sellQueue.isEmpty()) return true;
        if (!GrandExchange.isOpen()) {
            GrandExchange.open();
            AbstractScript.sleep(RandomUtil.gaussianBetween(800, 1500));
            return false;
        }

        SellOrder order = sellQueue.peek();
        if (order == null) return true;

        int slot = GrandExchange.getFirstOpenSlot();
        if (slot == -1) {
            // Check for completed offers to collect
            GrandExchange.collect();
            AbstractScript.sleep(RandomUtil.gaussianBetween(600, 1000));
            return false;
        }

        // Calculate price based on strategy
        int priceModifier = 0;
        switch (order.getStrategy()) {
            case "instant": priceModifier = -5; break;
            case "market": priceModifier = 0; break;
            case "patient": priceModifier = 5; break;
        }

        // Sell the item
        GrandExchange.openSellScreen(slot);
        AbstractScript.sleep(RandomUtil.gaussianBetween(400, 800));
        GrandExchange.addSellItem(order.getItemName());
        AbstractScript.sleep(RandomUtil.gaussianBetween(300, 600));
        GrandExchange.setQuantity(order.getQuantity());
        AbstractScript.sleep(RandomUtil.gaussianBetween(200, 500));

        // Adjust price
        if (priceModifier < 0) {
            // Click decrease price button
            for (int i = 0; i < Math.abs(priceModifier / 5); i++) {
                GrandExchange.getDecreasePriceFivePercentButton().interact();
                AbstractScript.sleep(RandomUtil.gaussianBetween(200, 400));
            }
        } else if (priceModifier > 0) {
            for (int i = 0; i < priceModifier / 5; i++) {
                GrandExchange.getIncreasePriceFivePercentButton().interact();
                AbstractScript.sleep(RandomUtil.gaussianBetween(200, 400));
            }
        }

        GrandExchange.confirm();
        AbstractScript.sleep(RandomUtil.gaussianBetween(500, 1000));
        sellQueue.poll();
        return sellQueue.isEmpty();
    }

    /**
     * Collect any completed GE offers.
     */
    public void collectOffers() {
        if (!GrandExchange.isOpen()) {
            GrandExchange.open();
            AbstractScript.sleep(RandomUtil.gaussianBetween(800, 1500));
        }
        GrandExchange.collect();
        AbstractScript.sleep(RandomUtil.gaussianBetween(500, 1000));
    }

    public FlipTracker getFlipTracker() { return flipTracker; }
    public boolean hasPendingSells() { return !sellQueue.isEmpty(); }
    public boolean hasPendingBuys() { return !buyQueue.isEmpty(); }
}
```

**Step 4: Commit**

```bash
git add core/src/main/java/com/osrs/core/ge/
git commit -m "feat: add GrandExchangeManager with sell queue, buy queue, and flip tracking"
```

---

## Phase 8: First Money-Making Methods

### Task 8.1: Abstract Base Class for Methods

**Files:**
- Create: `scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/methods/AbstractMoneyMethod.java`

**Step 1: Implement base class**

```java
package com.osrs.scripts.moneymaker.methods;

import com.osrs.core.antiban.AntiBanExecutor;
import com.osrs.core.ge.GrandExchangeManager;
import com.osrs.core.utils.Config;
import com.osrs.core.utils.RandomUtil;
import com.osrs.scripts.moneymaker.model.MoneyMethod;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.script.AbstractScript;

/**
 * Base class for all money-making methods.
 * Provides common utilities for banking, walking, and anti-ban.
 */
public abstract class AbstractMoneyMethod implements MoneyMethod {

    protected Config config;
    protected AntiBanExecutor antiBan;
    protected GrandExchangeManager geManager;

    public void init(Config config, AntiBanExecutor antiBan, GrandExchangeManager geManager) {
        this.config = config;
        this.antiBan = antiBan;
        this.geManager = geManager;
    }

    /**
     * Walk to a location with route variation.
     */
    protected boolean walkTo(Tile destination) {
        if (destination.distance() <= 5) return true;

        // Add random offset for route variation (+/- 2 tiles)
        int offsetX = RandomUtil.randomBetween(-2, 2);
        int offsetY = RandomUtil.randomBetween(-2, 2);
        Tile varied = new Tile(
            destination.getX() + offsetX,
            destination.getY() + offsetY,
            destination.getZ()
        );

        if (Walking.shouldWalk()) {
            Walking.walk(varied);
            antiBan.performIdleAction();
        }
        return false;
    }

    /**
     * Walk to a location and bank all items except specified ones.
     */
    protected boolean bankAll(String... keepItems) {
        if (!Bank.isOpen()) {
            Bank.open();
            AbstractScript.sleep(RandomUtil.gaussianBetween(600, 1200));
            return false;
        }

        if (keepItems.length > 0) {
            Bank.depositAllExcept(keepItems);
        } else {
            Bank.depositAllItems();
        }
        AbstractScript.sleep(RandomUtil.gaussianBetween(300, 600));
        return true;
    }

    /**
     * Withdraw items from bank.
     */
    protected boolean withdrawItems(String itemName, int amount) {
        if (!Bank.isOpen()) {
            Bank.open();
            AbstractScript.sleep(RandomUtil.gaussianBetween(600, 1200));
            return false;
        }

        if (amount == -1) {
            Bank.withdrawAll(itemName);
        } else {
            Bank.withdraw(itemName, amount);
        }
        AbstractScript.sleep(RandomUtil.gaussianBetween(300, 600));
        return true;
    }

    /**
     * Close the bank interface.
     */
    protected void closeBank() {
        if (Bank.isOpen()) {
            Bank.close();
            AbstractScript.sleep(RandomUtil.gaussianBetween(200, 500));
        }
    }

    /**
     * Enable run if we have enough energy.
     */
    protected void ensureRunEnabled() {
        if (!Walking.isRunEnabled() && Walking.getRunEnergy() > 30) {
            Walking.toggleRun();
        }
    }

    /**
     * Return a fatigue-adjusted random delay.
     */
    protected int delay(int min, int max) {
        return antiBan.adjustDelay(RandomUtil.gaussianBetween(min, max));
    }
}
```

**Step 2: Commit**

```bash
git add scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/methods/AbstractMoneyMethod.java
git commit -m "feat: add AbstractMoneyMethod base class with common banking and walking"
```

---

### Task 8.2: Mining Iron Ore Method

**Files:**
- Create: `scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/methods/skilling/MineIronOre.java`

**Step 1: Implement**

```java
package com.osrs.scripts.moneymaker.methods.skilling;

import com.osrs.core.skills.AccountState;
import com.osrs.scripts.moneymaker.methods.AbstractMoneyMethod;
import com.osrs.scripts.moneymaker.model.*;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.Player;

public class MineIronOre extends AbstractMoneyMethod {

    private static final Area VARROCK_EAST_MINE = new Area(3281, 3360, 3290, 3370);
    private static final Area VARROCK_WEST_MINE = new Area(3170, 3364, 3178, 3372);
    private static final String IRON_ROCK = "Iron rocks";
    private static final int IRON_ORE_ID = 440;

    private enum State { MINING, BANKING, WALKING_TO_MINE, WALKING_TO_BANK }
    private State state = State.WALKING_TO_MINE;
    private Area selectedMine;

    @Override
    public String getName() { return "Mine Iron Ore"; }

    @Override
    public MethodCategory getCategory() { return MethodCategory.SKILLING; }

    @Override
    public MethodRequirements getRequirements() {
        return new MethodRequirements()
            .addSkill("MINING", 15);
    }

    @Override
    public int getEstimatedGpPerHour(AccountState state) {
        int miningLevel = state.getSkillLevel("MINING");
        // Higher mining = faster = more GP/h
        if (miningLevel >= 60) return 180_000;
        if (miningLevel >= 40) return 150_000;
        return 100_000;
    }

    @Override
    public RiskLevel getRiskLevel() { return RiskLevel.NONE; }

    @Override
    public String[] getTrainedSkills() { return new String[]{"MINING"}; }

    @Override
    public void onActivate() {
        // Pick a mine location (varies each time)
        selectedMine = com.osrs.core.utils.RandomUtil.chance(60)
            ? VARROCK_EAST_MINE : VARROCK_WEST_MINE;
        state = State.WALKING_TO_MINE;
    }

    @Override
    public int execute() {
        switch (state) {
            case WALKING_TO_MINE:
                ensureRunEnabled();
                if (walkTo(selectedMine.getRandomTile())) {
                    state = State.MINING;
                }
                return delay(600, 1200);

            case MINING:
                return handleMining();

            case WALKING_TO_BANK:
                ensureRunEnabled();
                if (Bank.open()) {
                    state = State.BANKING;
                }
                return delay(600, 1200);

            case BANKING:
                return handleBanking();
        }
        return delay(1000, 2000);
    }

    private int handleMining() {
        if (Inventory.isFull()) {
            // Queue sell if auto-sell enabled
            if (config.isAutoSell()) {
                int count = Inventory.count(IRON_ORE_ID);
                geManager.queueSell("Iron ore", count);
            }
            state = State.WALKING_TO_BANK;
            return delay(300, 600);
        }

        Player local = Players.getLocal();
        if (local != null && local.isAnimating()) {
            // Still mining — do anti-ban actions while waiting
            antiBan.performIdleAction();
            return delay(800, 1500);
        }

        // Find and mine an iron rock
        GameObject rock = GameObjects.closest(obj ->
            obj != null &&
            obj.getName().equals(IRON_ROCK) &&
            selectedMine.contains(obj) &&
            obj.hasAction("Mine")
        );

        if (rock != null) {
            if (antiBan.shouldMisclick()) {
                // Misclick nearby, then correct
                AbstractScript.sleep(delay(200, 500));
            }
            rock.interact("Mine");
            AbstractScript.sleepUntil(() -> Players.getLocal().isAnimating(), 5000);
            return delay(600, 1200);
        }

        // No available rocks — wait for respawn
        antiBan.performIdleAction();
        return delay(1000, 2000);
    }

    private int handleBanking() {
        if (bankAll()) {
            closeBank();
            state = State.WALKING_TO_MINE;
        }
        return delay(600, 1000);
    }
}
```

**Step 2: Commit**

```bash
git add scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/methods/skilling/MineIronOre.java
git commit -m "feat: add MineIronOre money-making method with state machine"
```

---

### Task 8.3: Kill Cows (Cowhides) Method

**Files:**
- Create: `scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/methods/combat/KillCows.java`

**Step 1: Implement**

```java
package com.osrs.scripts.moneymaker.methods.combat;

import com.osrs.core.skills.AccountState;
import com.osrs.scripts.moneymaker.methods.AbstractMoneyMethod;
import com.osrs.scripts.moneymaker.model.*;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.interactive.GroundItems;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.items.GroundItem;

public class KillCows extends AbstractMoneyMethod {

    private static final Area COW_PEN = new Area(3242, 3255, 3265, 3298);
    private static final Area LUMBRIDGE_BANK = new Area(3207, 3215, 3210, 3220, 2);
    private static final String[] LOOT_ITEMS = {"Cowhide", "Raw beef", "Bones"};

    private enum State { FIGHTING, LOOTING, BANKING, WALKING_TO_COWS, WALKING_TO_BANK }
    private State state = State.WALKING_TO_COWS;

    @Override
    public String getName() { return "Kill Cows"; }

    @Override
    public MethodCategory getCategory() { return MethodCategory.COMBAT; }

    @Override
    public MethodRequirements getRequirements() {
        return new MethodRequirements(); // No requirements
    }

    @Override
    public int getEstimatedGpPerHour(AccountState state) {
        return 30_000;
    }

    @Override
    public RiskLevel getRiskLevel() { return RiskLevel.NONE; }

    @Override
    public String[] getTrainedSkills() {
        return new String[]{"ATTACK", "STRENGTH", "DEFENCE", "HITPOINTS"};
    }

    @Override
    public void onActivate() {
        state = State.WALKING_TO_COWS;
    }

    @Override
    public int execute() {
        switch (state) {
            case WALKING_TO_COWS:
                ensureRunEnabled();
                if (walkTo(COW_PEN.getRandomTile())) {
                    state = State.FIGHTING;
                }
                return delay(600, 1200);

            case FIGHTING:
                return handleFighting();

            case LOOTING:
                return handleLooting();

            case WALKING_TO_BANK:
                ensureRunEnabled();
                if (walkTo(LUMBRIDGE_BANK.getRandomTile())) {
                    state = State.BANKING;
                }
                return delay(600, 1200);

            case BANKING:
                return handleBanking();
        }
        return delay(1000, 2000);
    }

    private int handleFighting() {
        if (Inventory.isFull()) {
            state = State.WALKING_TO_BANK;
            return delay(300, 600);
        }

        // Check if we need to eat
        int hpPercent = (Skills.getBoostedLevel(Skill.HITPOINTS) * 100)
            / Skills.getRealLevel(Skill.HITPOINTS);
        if (hpPercent <= config.getEatHpPercent()) {
            eatFood();
        }

        Player local = Players.getLocal();
        if (local != null && local.isInCombat()) {
            // Already fighting, loot nearby items while waiting
            antiBan.performIdleAction();
            return delay(600, 1200);
        }

        // Check for loot first
        GroundItem loot = GroundItems.closest(item ->
            item != null && COW_PEN.contains(item) &&
            (item.getName().equals("Cowhide") || item.getName().equals("Bones"))
        );
        if (loot != null && loot.distance() < 8) {
            state = State.LOOTING;
            return delay(100, 300);
        }

        // Find a cow to attack
        NPC cow = NPCs.closest(npc ->
            npc != null &&
            npc.getName().equals("Cow") &&
            !npc.isHealthBarVisible() &&
            COW_PEN.contains(npc) &&
            npc.hasAction("Attack")
        );

        if (cow != null) {
            if (antiBan.shouldMisclick()) {
                AbstractScript.sleep(delay(200, 500));
            }
            cow.interact("Attack");
            AbstractScript.sleepUntil(() -> {
                Player p = Players.getLocal();
                return p != null && p.isInCombat();
            }, 5000);
            return delay(600, 1200);
        }

        // No cows available
        antiBan.performIdleAction();
        return delay(1500, 3000);
    }

    private int handleLooting() {
        GroundItem loot = GroundItems.closest(item ->
            item != null && COW_PEN.contains(item) &&
            (item.getName().equals("Cowhide") || item.getName().equals("Bones"))
        );

        if (loot != null && !Inventory.isFull()) {
            if (loot.getName().equals("Bones") && config.isBuryBones()) {
                loot.interact("Take");
                AbstractScript.sleepUntil(() -> Inventory.contains("Bones"), 3000);
                Inventory.interact("Bones", "Bury");
                AbstractScript.sleep(delay(600, 1000));
            } else {
                loot.interact("Take");
                AbstractScript.sleepUntil(() -> !loot.exists(), 3000);
            }
            return delay(300, 600);
        }

        state = State.FIGHTING;
        return delay(200, 500);
    }

    private int handleBanking() {
        if (bankAll()) {
            closeBank();
            if (config.isAutoSell()) {
                int hides = Inventory.count("Cowhide");
                if (hides > 0) geManager.queueSell("Cowhide", hides);
            }
            state = State.WALKING_TO_COWS;
        }
        return delay(600, 1000);
    }

    private void eatFood() {
        // Eat any food in inventory
        if (Inventory.interact(item ->
            item != null && item.hasAction("Eat"), "Eat")) {
            AbstractScript.sleep(delay(300, 600));
        }
    }
}
```

**Step 2: Commit**

```bash
git add scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/methods/combat/KillCows.java
git commit -m "feat: add KillCows combat money-making method"
```

---

### Task 8.4: Tan Cowhides (Processing) Method

**Files:**
- Create: `scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/methods/processing/TanCowhides.java`

**Step 1: Implement**

```java
package com.osrs.scripts.moneymaker.methods.processing;

import com.osrs.core.skills.AccountState;
import com.osrs.scripts.moneymaker.methods.AbstractMoneyMethod;
import com.osrs.scripts.moneymaker.model.*;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.widget.Widgets;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.wrappers.interactive.NPC;

public class TanCowhides extends AbstractMoneyMethod {

    // Al Kharid tanner
    private static final Area TANNER_AREA = new Area(3271, 3189, 3277, 3195);
    private static final String TANNER_NAME = "Ellis";
    private static final Tile AL_KHARID_BANK = new Tile(3269, 3167);

    private enum State { BANKING, WALKING_TO_TANNER, TANNING, WALKING_TO_BANK }
    private State state = State.BANKING;

    @Override
    public String getName() { return "Tan Cowhides"; }

    @Override
    public MethodCategory getCategory() { return MethodCategory.PROCESSING; }

    @Override
    public MethodRequirements getRequirements() {
        return new MethodRequirements()
            .setMinCashStack(5_000); // Need gold for tanning fee
    }

    @Override
    public int getEstimatedGpPerHour(AccountState state) {
        return 100_000;
    }

    @Override
    public RiskLevel getRiskLevel() { return RiskLevel.NONE; }

    @Override
    public String[] getTrainedSkills() { return new String[]{}; }

    @Override
    public void onActivate() {
        state = State.BANKING;
    }

    @Override
    public int execute() {
        switch (state) {
            case BANKING:
                return handleBanking();

            case WALKING_TO_TANNER:
                ensureRunEnabled();
                if (walkTo(TANNER_AREA.getRandomTile())) {
                    state = State.TANNING;
                }
                return delay(600, 1200);

            case TANNING:
                return handleTanning();

            case WALKING_TO_BANK:
                ensureRunEnabled();
                if (walkTo(AL_KHARID_BANK)) {
                    state = State.BANKING;
                }
                return delay(600, 1200);
        }
        return delay(1000, 2000);
    }

    private int handleBanking() {
        if (!Bank.isOpen()) {
            Bank.open();
            return delay(800, 1500);
        }

        // Deposit leather if we have any
        if (Inventory.contains("Leather")) {
            Bank.depositAll("Leather");
            AbstractScript.sleep(delay(300, 600));
        }

        // Withdraw cowhides
        if (!Inventory.contains("Cowhide")) {
            if (Bank.contains("Cowhide")) {
                Bank.withdrawAll("Cowhide");
                AbstractScript.sleep(delay(300, 600));
            } else {
                // No cowhides left — need to buy or switch method
                closeBank();
                return -1; // Signal to switch method
            }
        }

        closeBank();
        state = State.WALKING_TO_TANNER;
        return delay(400, 800);
    }

    private int handleTanning() {
        if (!Inventory.contains("Cowhide")) {
            // All tanned, go bank
            state = State.WALKING_TO_BANK;
            if (config.isAutoSell()) {
                int leather = Inventory.count("Leather");
                if (leather > 0) geManager.queueSell("Leather", leather);
            }
            return delay(300, 600);
        }

        NPC tanner = NPCs.closest(TANNER_NAME);
        if (tanner != null) {
            tanner.interact("Trade");
            AbstractScript.sleepUntil(() -> Widgets.isOpen(), 5000);
            AbstractScript.sleep(delay(400, 800));

            // Click "Tan All" on the first option (soft leather)
            if (Widgets.isOpen()) {
                // Widget for "Tan All" soft leather
                Widgets.get(324, 92).interact("Tan All");
                AbstractScript.sleepUntil(() -> !Inventory.contains("Cowhide"), 5000);
            }
            return delay(600, 1200);
        }

        return delay(1000, 2000);
    }
}
```

**Step 2: Commit**

```bash
git add scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/methods/processing/TanCowhides.java
git commit -m "feat: add TanCowhides processing money-making method"
```

---

## Phase 9: Method Registry & Main Script

### Task 9.1: MethodRegistry

**Files:**
- Create: `scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/model/MethodRegistry.java`

**Step 1: Implement**

```java
package com.osrs.scripts.moneymaker.model;

import com.osrs.core.antiban.AntiBanExecutor;
import com.osrs.core.ge.GrandExchangeManager;
import com.osrs.core.utils.Config;
import com.osrs.scripts.moneymaker.methods.AbstractMoneyMethod;
import com.osrs.scripts.moneymaker.methods.combat.KillCows;
import com.osrs.scripts.moneymaker.methods.processing.TanCowhides;
import com.osrs.scripts.moneymaker.methods.skilling.MineIronOre;

import java.util.ArrayList;
import java.util.List;

/**
 * Central registry of all available money-making methods.
 * Add new methods here.
 */
public class MethodRegistry {

    private final List<MoneyMethod> methods = new ArrayList<>();

    public MethodRegistry(Config config, AntiBanExecutor antiBan, GrandExchangeManager geManager) {
        // Register all methods
        register(new MineIronOre(), config, antiBan, geManager);
        register(new KillCows(), config, antiBan, geManager);
        register(new TanCowhides(), config, antiBan, geManager);

        // TODO: Add more methods as they are implemented
        // register(new MineGoldOre(), config, antiBan, geManager);
        // register(new CutYewLogs(), config, antiBan, geManager);
        // register(new KillOgresses(), config, antiBan, geManager);
        // register(new HighVolumeFlipping(), config, antiBan, geManager);
        // register(new PvpWorldLooting(), config, antiBan, geManager);
    }

    private void register(AbstractMoneyMethod method, Config config,
                          AntiBanExecutor antiBan, GrandExchangeManager geManager) {
        method.init(config, antiBan, geManager);
        methods.add(method);
    }

    public List<MoneyMethod> getAllMethods() {
        return methods;
    }

    /**
     * Get methods filtered by enabled categories in config.
     */
    public List<MoneyMethod> getEnabledMethods(Config config) {
        List<MoneyMethod> enabled = new ArrayList<>();
        for (MoneyMethod method : methods) {
            switch (method.getCategory()) {
                case SKILLING:
                    if (config.isSkillingEnabled()) enabled.add(method);
                    break;
                case COMBAT:
                    if (config.isCombatEnabled()) enabled.add(method);
                    break;
                case PROCESSING:
                    if (config.isProcessingEnabled()) enabled.add(method);
                    break;
                case FLIPPING:
                    if (config.isFlippingEnabled()) enabled.add(method);
                    break;
                case LOOTING:
                    if (config.isLootingEnabled()) enabled.add(method);
                    break;
            }
        }
        return enabled;
    }
}
```

**Step 2: Commit**

```bash
git add scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/model/MethodRegistry.java
git commit -m "feat: add MethodRegistry with auto-initialization of money methods"
```

---

### Task 9.2: MoneyMakerScript (Main Entry Point)

**Files:**
- Create: `scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/MoneyMakerScript.java`

**Step 1: Implement**

```java
package com.osrs.scripts.moneymaker;

import com.osrs.core.antiban.AntiBanExecutor;
import com.osrs.core.antiban.AntiBanProfile;
import com.osrs.core.ge.GrandExchangeManager;
import com.osrs.core.movement.FleeManager;
import com.osrs.core.movement.ThreatDetector;
import com.osrs.core.movement.ThreatLevel;
import com.osrs.core.session.SessionManager;
import com.osrs.core.skills.AccountState;
import com.osrs.core.strategy.MethodScore;
import com.osrs.core.strategy.StrategySelector;
import com.osrs.core.utils.Config;
import com.osrs.core.utils.RandomUtil;
import com.osrs.core.utils.TimeUtil;
import com.osrs.scripts.moneymaker.gui.MoneyMakerGui;
import com.osrs.scripts.moneymaker.model.MethodRegistry;
import com.osrs.scripts.moneymaker.model.MoneyMethod;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.world.Worlds;
import org.dreambot.api.methods.worldhopper.WorldHopper;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@ScriptManifest(
    name = "OSRS MoneyMaker",
    version = 1.0,
    category = Category.MONEYMAKING,
    author = "OSRS Bot Framework",
    description = "AI-driven multi-method money maker with anti-ban and GE management"
)
public class MoneyMakerScript extends AbstractScript {

    // Core systems
    private Config config;
    private AntiBanProfile antiBanProfile;
    private AntiBanExecutor antiBanExecutor;
    private SessionManager sessionManager;
    private StrategySelector strategySelector;
    private ThreatDetector threatDetector;
    private FleeManager fleeManager;
    private GrandExchangeManager geManager;
    private MethodRegistry methodRegistry;

    // State
    private MoneyMethod currentMethod;
    private AccountState accountState;
    private final Map<String, Long> lastUsedTimes = new HashMap<>();
    private final List<MethodHistoryEntry> methodHistory = new ArrayList<>();
    private long startTime;
    private long totalGpEarned = 0;
    private long methodStartTime;
    private long methodStartGp;

    // GUI
    private MoneyMakerGui gui;
    private String status = "Initializing...";

    @Override
    public void onStart() {
        startTime = System.currentTimeMillis();

        // Load config (or create defaults)
        File configDir = new File(System.getProperty("user.home") + "/DreamBot/Scripts/OSRSMoneyMaker/");
        configDir.mkdirs();
        File configFile = new File(configDir, "config.json");
        config = Config.load(configFile);

        // Show startup GUI and wait for configuration
        gui = new MoneyMakerGui(config, this::onGuiStart);
        gui.show();
    }

    /**
     * Called when user clicks START in GUI.
     */
    private void onGuiStart() {
        // Save config
        File configFile = new File(
            System.getProperty("user.home") + "/DreamBot/Scripts/OSRSMoneyMaker/config.json"
        );
        config.save(configFile);

        // Initialize systems
        antiBanProfile = new AntiBanProfile(config);
        antiBanExecutor = new AntiBanExecutor(antiBanProfile);
        sessionManager = new SessionManager(config);
        strategySelector = new StrategySelector(config);
        threatDetector = new ThreatDetector(config);
        fleeManager = new FleeManager();
        geManager = new GrandExchangeManager(config);
        methodRegistry = new MethodRegistry(config, antiBanExecutor, geManager);

        // Read account state
        refreshAccountState();

        // Select first method
        selectNextMethod();

        status = "Running";
    }

    @Override
    public int onLoop() {
        if (currentMethod == null) {
            status = "Waiting for GUI...";
            return 1000;
        }

        // Update fatigue
        long sessionTime = System.currentTimeMillis() - startTime;
        antiBanProfile.getFatigueModel().update(sessionTime);

        // 1. Check session manager (breaks)
        SessionManager.SessionAction sessionAction = sessionManager.check();
        switch (sessionAction) {
            case START_BREAK:
                status = "Taking a break...";
                antiBanProfile.getFatigueModel().reset();
                Tabs.logout();
                return RandomUtil.gaussianBetween(5000, 15000);
            case ON_BREAK:
                return 30_000; // Check every 30 sec during break
            case END_BREAK:
                status = "Break over, resuming...";
                refreshAccountState();
                selectNextMethod();
                return RandomUtil.gaussianBetween(3000, 8000);
            case STOP:
                status = "Day complete!";
                stop();
                return 0;
        }

        // 2. Check threats
        ThreatLevel threat = threatDetector.assess();
        if (threat != ThreatLevel.NONE) {
            status = "Threat detected: " + threat;
            if (threat == ThreatLevel.CRITICAL) {
                fleeManager.flee(threat);
                stop();
                return 0;
            }
            fleeManager.flee(threat);
            if (threat == ThreatLevel.LOW) {
                // World hop
                WorldHopper.hopWorld(Worlds.getRandomWorld(w ->
                    w != null && !w.isMembers() == !accountState.isMember() &&
                    w.getMinimumLevel() == 0 && !w.isPVP()
                ));
                return RandomUtil.gaussianBetween(3000, 6000);
            }
            return RandomUtil.gaussianBetween(2000, 5000);
        }

        // 3. Check if it's time to switch methods
        long methodRuntime = System.currentTimeMillis() - methodStartTime;
        long switchAfter = RandomUtil.randomBetween(
            config.getMethodSwitchMinMinutes(),
            config.getMethodSwitchMaxMinutes()
        ) * 60_000L;

        if (methodRuntime > switchAfter) {
            recordMethodHistory();
            selectNextMethod();
            return RandomUtil.gaussianBetween(2000, 5000);
        }

        // 4. Process GE if we have pending orders
        if (geManager.hasPendingSells()) {
            status = "Processing GE sells...";
            // This is simplified — in practice, weave GE visits into natural bank trips
        }

        // 5. Execute current method
        status = currentMethod.getName();
        int result = currentMethod.execute();

        if (result < 0) {
            // Method signaled it can't continue — switch
            recordMethodHistory();
            selectNextMethod();
            return RandomUtil.gaussianBetween(2000, 5000);
        }

        return result;
    }

    private void selectNextMethod() {
        refreshAccountState();

        // Get eligible methods
        List<MoneyMethod> enabled = methodRegistry.getEnabledMethods(config);
        List<MethodScore> scores = enabled.stream()
            .filter(m -> m.getRequirements().isMet(accountState) && m.isReady())
            .map(m -> new MethodScore(
                m.getName(),
                m.getEstimatedGpPerHour(accountState),
                1.0 - m.getRiskLevel().getSafetyMultiplier(),
                m.getRequirements().isMembersOnly(),
                m.getTrainedSkills()
            ))
            .collect(Collectors.toList());

        // Filter and select
        List<MethodScore> filtered = strategySelector.filterByAccount(scores, accountState);
        MethodScore selected = strategySelector.selectProbabilistic(filtered, accountState, lastUsedTimes);

        if (selected != null) {
            // Find the actual method object
            for (MoneyMethod method : enabled) {
                if (method.getName().equals(selected.getName())) {
                    if (currentMethod != null) currentMethod.onDeactivate();
                    currentMethod = method;
                    currentMethod.onActivate();
                    methodStartTime = System.currentTimeMillis();
                    lastUsedTimes.put(method.getName(), System.currentTimeMillis());
                    log("Selected method: " + method.getName());
                    break;
                }
            }
        } else {
            log("No eligible methods found!");
            status = "No methods available";
        }
    }

    private void refreshAccountState() {
        accountState = new AccountState();
        for (Skill skill : Skill.values()) {
            accountState.setSkillLevel(skill.name(), Skills.getRealLevel(skill));
        }
        if (Players.getLocal() != null) {
            accountState.setCombatLevel(Players.getLocal().getLevel());
        }
        accountState.setTotalLevel(Skills.getTotalLevel());
        // Cash stack: check inventory + bank
        accountState.setCashStack(Inventory.count("Coins"));
        accountState.setMember(Worlds.getCurrentWorld() != null &&
            Worlds.getCurrentWorld().isMembers());
    }

    private void recordMethodHistory() {
        if (currentMethod != null) {
            long runtime = System.currentTimeMillis() - methodStartTime;
            methodHistory.add(new MethodHistoryEntry(
                currentMethod.getName(),
                methodStartTime,
                runtime,
                0 // GP tracking per method to be implemented with GE price lookup
            ));
        }
    }

    @Override
    public void onPaint(Graphics2D g) {
        if (gui != null) {
            gui.paintDashboard(g, this);
        }
    }

    @Override
    public void onExit() {
        if (gui != null) gui.close();
    }

    // Getters for GUI
    public String getStatus() { return status; }
    public long getStartTime() { return startTime; }
    public long getTotalGpEarned() { return totalGpEarned; }
    public MoneyMethod getCurrentMethod() { return currentMethod; }
    public AccountState getAccountState() { return accountState; }
    public SessionManager getSessionManager() { return sessionManager; }
    public AntiBanExecutor getAntiBanExecutor() { return antiBanExecutor; }
    public AntiBanProfile getAntiBanProfile() { return antiBanProfile; }
    public List<MethodHistoryEntry> getMethodHistory() { return methodHistory; }

    /**
     * Record of a method execution for the history panel.
     */
    public static class MethodHistoryEntry {
        public final String methodName;
        public final long startTime;
        public final long durationMs;
        public final long gpEarned;

        public MethodHistoryEntry(String methodName, long startTime, long durationMs, long gpEarned) {
            this.methodName = methodName;
            this.startTime = startTime;
            this.durationMs = durationMs;
            this.gpEarned = gpEarned;
        }
    }
}
```

**Step 2: Commit**

```bash
git add scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/MoneyMakerScript.java
git commit -m "feat: add MoneyMakerScript main entry point with full lifecycle"
```

---

## Phase 10: GUI Implementation

### Task 10.1: Startup GUI

**Files:**
- Create: `scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/gui/MoneyMakerGui.java`

**Step 1: Implement** (simplified — full Swing UI)

```java
package com.osrs.scripts.moneymaker.gui;

import com.osrs.core.utils.Config;
import com.osrs.core.utils.TimeUtil;
import com.osrs.scripts.moneymaker.MoneyMakerScript;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MoneyMakerGui {

    private final Config config;
    private final Runnable onStart;
    private JFrame frame;

    public MoneyMakerGui(Config config, Runnable onStart) {
        this.config = config;
        this.onStart = onStart;
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("OSRS MoneyMaker v1.0");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(500, 650);
            frame.setLocationRelativeTo(null);

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Algemeen", createGeneralPanel());
            tabs.addTab("Configuratie", createConfigPanel());
            tabs.addTab("Geavanceerd", createAdvancedPanel());

            JButton startButton = new JButton("START");
            startButton.setFont(new Font("Arial", Font.BOLD, 16));
            startButton.setPreferredSize(new Dimension(200, 50));
            startButton.addActionListener(e -> {
                frame.dispose();
                onStart.run();
            });

            JPanel bottomPanel = new JPanel();
            bottomPanel.add(startButton);

            frame.setLayout(new BorderLayout());
            frame.add(tabs, BorderLayout.CENTER);
            frame.add(bottomPanel, BorderLayout.SOUTH);
            frame.setVisible(true);
        });
    }

    private JPanel createGeneralPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Mode selection
        panel.add(createSection("Modus", () -> {
            JPanel p = new JPanel(new GridLayout(3, 1));
            ButtonGroup modeGroup = new ButtonGroup();
            JRadioButton auto = new JRadioButton("Automatisch (bot kiest alles zelf)", true);
            JRadioButton focus = new JRadioButton("Focus categorie");
            JRadioButton specific = new JRadioButton("Specifieke methode");
            modeGroup.add(auto);
            modeGroup.add(focus);
            modeGroup.add(specific);
            p.add(auto);
            p.add(focus);
            p.add(specific);
            return p;
        }));

        // Category toggles
        panel.add(createSection("Categorieen", () -> {
            JPanel p = new JPanel(new GridLayout(2, 3));
            JCheckBox skilling = new JCheckBox("Skilling", config.isSkillingEnabled());
            skilling.addActionListener(e -> config.setSkillingEnabled(skilling.isSelected()));
            JCheckBox combat = new JCheckBox("Combat", config.isCombatEnabled());
            combat.addActionListener(e -> config.setCombatEnabled(combat.isSelected()));
            JCheckBox processing = new JCheckBox("Processing", config.isProcessingEnabled());
            processing.addActionListener(e -> config.setProcessingEnabled(processing.isSelected()));
            JCheckBox flipping = new JCheckBox("Flipping", config.isFlippingEnabled());
            flipping.addActionListener(e -> config.setFlippingEnabled(flipping.isSelected()));
            JCheckBox looting = new JCheckBox("Looting", config.isLootingEnabled());
            looting.addActionListener(e -> config.setLootingEnabled(looting.isSelected()));
            JCheckBox wilderness = new JCheckBox("Wilderness", config.isWildernessAllowed());
            wilderness.addActionListener(e -> config.setWildernessAllowed(wilderness.isSelected()));
            p.add(skilling); p.add(combat); p.add(processing);
            p.add(flipping); p.add(looting); p.add(wilderness);
            return p;
        }));

        // Anti-ban slider
        panel.add(createSection("Anti-Ban Intensiteit", () -> {
            JPanel p = new JPanel(new BorderLayout());
            JSlider slider = new JSlider(0, 100, config.getAntiBanIntensity());
            JLabel label = new JLabel(config.getAntiBanIntensity() + "%");
            slider.addChangeListener((ChangeEvent e) -> {
                config.setAntiBanIntensity(slider.getValue());
                label.setText(slider.getValue() + "%");
            });
            p.add(slider, BorderLayout.CENTER);
            p.add(label, BorderLayout.EAST);
            return p;
        }));

        return panel;
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Money Making settings
        panel.add(createSection("Money Making", () -> {
            JPanel p = new JPanel(new GridLayout(3, 2, 5, 5));
            p.add(new JLabel("Min GP/uur:"));
            JSpinner minGp = new JSpinner(new SpinnerNumberModel(config.getMinGpPerHour(), 0, 2000000, 10000));
            minGp.addChangeListener(e -> config.setMinGpPerHour((int) minGp.getValue()));
            p.add(minGp);
            p.add(new JLabel("Max methodes/sessie:"));
            JSpinner maxMethods = new JSpinner(new SpinnerNumberModel(config.getMaxMethodsPerSession(), 1, 20, 1));
            maxMethods.addChangeListener(e -> config.setMaxMethodsPerSession((int) maxMethods.getValue()));
            p.add(maxMethods);
            p.add(new JLabel("Prioriteit:"));
            JComboBox<String> priority = new JComboBox<>(new String[]{"balanced", "gp", "xp"});
            priority.setSelectedItem(config.getPriority());
            priority.addActionListener(e -> config.setPriority((String) priority.getSelectedItem()));
            p.add(priority);
            return p;
        }));

        // Combat settings
        panel.add(createSection("Combat", () -> {
            JPanel p = new JPanel(new GridLayout(3, 2, 5, 5));
            p.add(new JLabel("Eet bij HP%:"));
            JSpinner eatHp = new JSpinner(new SpinnerNumberModel(config.getEatHpPercent(), 10, 90, 5));
            eatHp.addChangeListener(e -> config.setEatHpPercent((int) eatHp.getValue()));
            p.add(eatHp);
            p.add(new JLabel("Flee bij HP%:"));
            JSpinner fleeHp = new JSpinner(new SpinnerNumberModel(config.getFleeHpPercent(), 5, 50, 5));
            fleeHp.addChangeListener(e -> config.setFleeHpPercent((int) fleeHp.getValue()));
            p.add(fleeHp);
            p.add(new JLabel("Min loot waarde:"));
            JSpinner lootMin = new JSpinner(new SpinnerNumberModel(config.getLootMinValue(), 0, 100000, 100));
            lootMin.addChangeListener(e -> config.setLootMinValue((int) lootMin.getValue()));
            p.add(lootMin);
            return p;
        }));

        // GE settings
        panel.add(createSection("Grand Exchange", () -> {
            JPanel p = new JPanel(new GridLayout(3, 2, 5, 5));
            p.add(new JLabel("Max flip investering %:"));
            JSpinner flipPct = new JSpinner(new SpinnerNumberModel(config.getMaxFlipInvestmentPercent(), 10, 90, 5));
            flipPct.addChangeListener(e -> config.setMaxFlipInvestmentPercent((int) flipPct.getValue()));
            p.add(flipPct);
            p.add(new JLabel("Max flip slots:"));
            JSpinner flipSlots = new JSpinner(new SpinnerNumberModel(config.getMaxFlipSlots(), 1, 6, 1));
            flipSlots.addChangeListener(e -> config.setMaxFlipSlots((int) flipSlots.getValue()));
            p.add(flipSlots);
            p.add(new JLabel("Sell strategie:"));
            JComboBox<String> sellStrat = new JComboBox<>(new String[]{"instant", "market", "patient"});
            sellStrat.setSelectedItem(config.getSellStrategy());
            sellStrat.addActionListener(e -> config.setSellStrategy((String) sellStrat.getSelectedItem()));
            p.add(sellStrat);
            return p;
        }));

        return panel;
    }

    private JPanel createAdvancedPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Session settings
        panel.add(createSection("Sessie", () -> {
            JPanel p = new JPanel(new GridLayout(4, 2, 5, 5));
            p.add(new JLabel("Min sessie (min):"));
            JSpinner minSess = new JSpinner(new SpinnerNumberModel(config.getMinSessionMinutes(), 10, 180, 5));
            minSess.addChangeListener(e -> config.setMinSessionMinutes((int) minSess.getValue()));
            p.add(minSess);
            p.add(new JLabel("Max sessie (min):"));
            JSpinner maxSess = new JSpinner(new SpinnerNumberModel(config.getMaxSessionMinutes(), 30, 300, 10));
            maxSess.addChangeListener(e -> config.setMaxSessionMinutes((int) maxSess.getValue()));
            p.add(maxSess);
            p.add(new JLabel("Max dagelijks (uur):"));
            JSpinner maxDay = new JSpinner(new SpinnerNumberModel(config.getMaxDailyHours(), 1, 16, 1));
            maxDay.addChangeListener(e -> config.setMaxDailyHours((int) maxDay.getValue()));
            p.add(maxDay);
            p.add(new JLabel("Dagelijkse variatie:"));
            JCheckBox variation = new JCheckBox("", config.isDailyVariation());
            variation.addActionListener(e -> config.setDailyVariation(variation.isSelected()));
            p.add(variation);
            return p;
        }));

        // Safety settings
        panel.add(createSection("Veiligheid", () -> {
            JPanel p = new JPanel(new GridLayout(3, 2, 5, 5));
            JCheckBox jmod = new JCheckBox("Stop bij JMod", config.isStopOnJmod());
            jmod.addActionListener(e -> config.setStopOnJmod(jmod.isSelected()));
            p.add(jmod);
            JCheckBox screenshot = new JCheckBox("Screenshot bij ban", config.isScreenshotOnBan());
            screenshot.addActionListener(e -> config.setScreenshotOnBan(screenshot.isSelected()));
            p.add(screenshot);
            JCheckBox hopCrowded = new JCheckBox("Hop bij drukte", config.isHopOnCrowded());
            hopCrowded.addActionListener(e -> config.setHopOnCrowded(hopCrowded.isSelected()));
            p.add(hopCrowded);
            p.add(new JLabel("Max wildy level:"));
            JSpinner wildyLvl = new JSpinner(new SpinnerNumberModel(config.getMaxWildernessLevel(), 1, 56, 1));
            wildyLvl.addChangeListener(e -> config.setMaxWildernessLevel((int) wildyLvl.getValue()));
            p.add(wildyLvl);
            return p;
        }));

        // Profile buttons
        panel.add(createSection("Profiel", () -> {
            JPanel p = new JPanel(new FlowLayout());
            JButton save = new JButton("Opslaan");
            JButton load = new JButton("Laden");
            JButton reset = new JButton("Reset");
            save.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                if (fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    config.save(fc.getSelectedFile());
                }
            });
            load.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    Config loaded = Config.load(fc.getSelectedFile());
                    // Would need to refresh GUI with loaded values
                }
            });
            reset.addActionListener(e -> {
                // Reset to defaults
                Config defaults = new Config();
                config.setAntiBanIntensity(defaults.getAntiBanIntensity());
                // ... reset all fields
            });
            p.add(save); p.add(load); p.add(reset);
            return p;
        }));

        return panel;
    }

    private JPanel createSection(String title, PanelSupplier contentSupplier) {
        JPanel section = new JPanel(new BorderLayout());
        section.setBorder(BorderFactory.createTitledBorder(title));
        section.add(contentSupplier.create(), BorderLayout.CENTER);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height + 20));
        return section;
    }

    /**
     * Paint the live dashboard on the game canvas.
     */
    public void paintDashboard(Graphics2D g, MoneyMakerScript script) {
        int x = 10, y = 30;
        int lineHeight = 18;

        // Background
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(5, 5, 300, 280);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 12));

        // Header
        g.drawString("OSRS MoneyMaker — " + script.getStatus(), x, y);
        y += lineHeight + 5;

        g.setFont(new Font("Arial", Font.PLAIN, 11));

        // Runtime
        long runtime = System.currentTimeMillis() - script.getStartTime();
        g.drawString("Runtime: " + TimeUtil.formatRuntime(runtime), x, y);
        y += lineHeight;

        // GP
        g.drawString("GP verdiend: " + TimeUtil.formatGp(script.getTotalGpEarned()), x, y);
        y += lineHeight;
        g.drawString("GP/uur: " + TimeUtil.formatGp(TimeUtil.gpPerHour(script.getTotalGpEarned(), runtime)), x, y);
        y += lineHeight + 5;

        // Current method
        if (script.getCurrentMethod() != null) {
            g.setColor(Color.YELLOW);
            g.drawString("Methode: " + script.getCurrentMethod().getName(), x, y);
            g.setColor(Color.WHITE);
            y += lineHeight;
        }

        // Session info
        if (script.getSessionManager() != null) {
            long untilBreak = script.getSessionManager().getMillisUntilBreak();
            g.drawString("Sessie: " + (script.getSessionManager().getCurrentSessionIndex() + 1) +
                "/" + script.getSessionManager().getTotalSessions() +
                " | Break in: " + TimeUtil.formatRuntime(untilBreak), x, y);
            y += lineHeight + 5;
        }

        // Anti-ban stats
        if (script.getAntiBanExecutor() != null) {
            g.setColor(Color.CYAN);
            g.drawString("Anti-Ban Stats:", x, y);
            y += lineHeight;
            g.setColor(Color.WHITE);
            g.drawString("Camera: " + script.getAntiBanExecutor().getCameraMovesCount() +
                " | Tabs: " + script.getAntiBanExecutor().getTabSwitchesCount() +
                " | Misclicks: " + script.getAntiBanExecutor().getMisclickCount() +
                " | Idle: " + script.getAntiBanExecutor().getIdleMomentsCount(), x, y);
            y += lineHeight;

            if (script.getAntiBanProfile() != null) {
                double fatigue = script.getAntiBanProfile().getFatigueModel().getFatigueLevel();
                g.drawString("Fatigue: " + String.format("%.0f%%", fatigue * 100), x, y);
                y += lineHeight;
            }
        }

        // Method history (last 5)
        y += 5;
        g.setColor(Color.GREEN);
        g.drawString("Historie:", x, y);
        y += lineHeight;
        g.setColor(Color.WHITE);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        int count = 0;
        for (int i = script.getMethodHistory().size() - 1; i >= 0 && count < 5; i--, count++) {
            MoneyMakerScript.MethodHistoryEntry entry = script.getMethodHistory().get(i);
            g.drawString(sdf.format(new Date(entry.startTime)) + "  " +
                entry.methodName + "  " +
                TimeUtil.formatRuntime(entry.durationMs), x, y);
            y += lineHeight;
        }
    }

    public void close() {
        if (frame != null) frame.dispose();
    }

    @FunctionalInterface
    private interface PanelSupplier {
        JPanel create();
    }
}
```

**Step 2: Commit**

```bash
git add scripts/moneymaker/src/main/java/com/osrs/scripts/moneymaker/gui/MoneyMakerGui.java
git commit -m "feat: add MoneyMakerGui with startup config and live paint dashboard"
```

---

## Phase 11: Additional Money-Making Methods

### Task 11.1 - 11.X: Implement Remaining Methods

Each method follows the same pattern as Tasks 8.2-8.4. Implement in priority order:

1. **KillOgresses** (`methods/combat/KillOgresses.java`) — Best F2P combat GP
2. **CutYewLogs** (`methods/skilling/CutYewLogs.java`) — High WC GP
3. **FishLobsters** (`methods/skilling/FishLobsters.java`) — Good mid-level
4. **MakePieShells** (`methods/processing/MakePieShells.java`) — No-req processing
5. **CraftGoldAmulets** (`methods/processing/CraftGoldAmulets.java`) — Easy crafting GP
6. **SmeltBars** (`methods/processing/SmeltBars.java`) — Smithing XP + GP
7. **HighVolumeFlipping** (`methods/flipping/HighVolumeFlipping.java`) — GE flipping
8. **KillGreenDragons** (`methods/combat/KillGreenDragons.java`) — Best P2P combat
9. **KillChaosDruids** (`methods/combat/KillChaosDruids.java`) — Herb drops
10. **PvpWorldLooting** (`methods/looting/PvpWorldLooting.java`) — Zero-req GP

Each method:
- Extends `AbstractMoneyMethod`
- Implements all `MoneyMethod` interface methods
- Has a state machine (enum State) for its workflow
- Uses anti-ban delays and route variation
- Gets registered in `MethodRegistry`

Register each new method in `MethodRegistry` constructor after implementing.

**Commit after each method:**
```bash
git commit -m "feat: add [MethodName] money-making method"
```

---

## Phase 12: Testing & Polish

### Task 12.1: Integration Verification

**Step 1: Build full JAR**
```bash
./gradlew clean build
```

**Step 2: Verify JAR output**
```bash
ls -la ~/DreamBot/Scripts/OSRSMoneyMaker.jar
```

**Step 3: Verify JAR contents include core classes**
```bash
jar tf ~/DreamBot/Scripts/OSRSMoneyMaker.jar | grep com/osrs/core
```

### Task 12.2: Add JUnit to build

Update `core/build.gradle`:
```groovy
dependencies {
    implementation 'com.google.gson:gson:2.10.1'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.1'
}

test {
    useJUnitPlatform()
}
```

### Task 12.3: Run all tests

```bash
./gradlew test
```

### Task 12.4: Final commit

```bash
git add -A
git commit -m "feat: complete MoneyMaker bot v1.0 with core framework"
```
