# Starsector Bad Apple!! Political Map Player

A mod for [Starsector](https://fractalsoftworks.com/) that renders the **Bad Apple!!** shadow art music video directly onto the campaign sector map using political spheres of influence and territorial merging powered by **Klark Morrigan's Utilities (KMU)**.

---

## ⚠️ Important Warning

> **THIS MOD MODIFIES NEW GAME SECTOR GENERATION!**
>
> When enabled, this mod injects a custom high-density procedural generation pass (`BadAppleSectorGenerator`) during New Game creation to tile ~1,100 star systems uniformly across hyperspace.
>
> **Remember to DEACTIVATE this mod before starting or loading normal playthroughs**, unless you intentionally want a hyper-dense galaxy populated with thousands of stars.

---

## Requirements & Dependencies

Ensure the following mods are installed and enabled in the Starsector launcher:
1. **[Klark Morrigan's Utilities (KMU)](https://fractalsoftworks.com/)** (Required for campaign political map & sphere of influence rendering).
2. **[LazyWizard's Console Commands](https://fractalsoftworks.com/forum/index.php?topic=4106.0)** (Required to run preprocessing and playback commands).
3. **Bad Apple Frame Pack** (See installation instructions below).

---

## Installation & Frame Setup

### 1. Download Video Frames
The frame images (`output_0001.jpg` to `output_6572.jpg`) are hosted separately due to repository size limits.

* Download or clone the frames repository:
  👉 **[https://github.com/Felixoofed/badapple-frames](https://github.com/Felixoofed/badapple-frames)**

### 2. Extract Frames to Target Location
Extract the JPEG frames into the mod's `graphics/badapple/frames/` directory so that the file structure looks like:

```text
starsector-badapple/
├── data/
├── graphics/
│   └── badapple/
│       └── frames/
│           ├── output_0001.jpg
│           ├── output_0002.jpg
│           ├── ...
│           └── output_6572.jpg
├── jars/
│   └── starsector-badapple.jar
├── mod_info.json
└── README.md
```

---

## How to Run

### Step 1: Start a New Game
1. Enable `KMU`, `Console Commands`, and `starsector-badapple` in the Starsector launcher.
2. Start a **New Game**. The procedural generator will automatically establish a dense, 4:3-calibrated constellation canvas across hyperspace while keeping the vanilla Core Worlds intact.

### Step 2: Initialize Sector Markets (`badapple_init`)
Open the in-game console (`Ctrl+Backspace` by default) and run:
```text
badapple_init
```
* Spawns orbital stations and colony markets across all uninhabited star systems.
* Sets all sector markets to `hegemony` (black background state).
* Explores and fully surveys all systems to ensure political influence spheres are immediately visible.

### Step 3: Preprocess Frames (`badapple_prep`)
Run:
```text
badapple_prep
```
* Analyzes all star systems in hyperspace, constructs a pixel lookup table matching the video frame dimensions (480×360), and extracts delta faction flips (`hegemony` vs `sindrian_diktat`) across all frames.
* You can optionally limit the frame count or tweak the brightness threshold:
  ```text
  badapple_prep [maxFrames] [brightnessThreshold (0.0-1.0)]
  ```

### Step 4: Play Bad Apple (`badapple_play`)
Open the Campaign Map screen (`Tab`), zoom out to view the sector, and run:
```text
badapple_play 0.2
```
* **Syntax**: `badapple_play [frameDelaySeconds] [startFrame]`
* `badapple_play 0.2` plays each frame with a 200ms delay (5 FPS).
* `badapple_play 0.1 200` jumps straight to frame 200 at 100ms per frame (10 FPS).
* `badapple_play pause` / `badapple_play resume` to pause/resume playback.
* `badapple_play stop` to stop playback.

---

## Additional Commands

* `badapple_poc [intervalSeconds]`: Proof-of-concept command that alternates Chicomoztoc's market between `hegemony` and `sindrian_diktat` continuously to test KMU map rendering.
