# AE2 Universal Pattern

[![Build](https://github.com/Jhown/ae2universalpattern/actions/workflows/build.yml/badge.svg)](https://github.com/Jhown/ae2universalpattern/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.251+-orange.svg)](https://neoforged.net/)

A high-performance **Applied Energistics 2** addon for **Minecraft 1.21.1 (NeoForge)** that introduces the **Universal Crafting Pattern** — dynamically exposing and indexing crafting table recipes across the entire ME network without requiring manual encoding of individual patterns.

Compatible with standard **AE2 Pattern Providers** and **ExtendedAE Assembler Matrix** multiblocks!

---

## ✨ Features

- **Universal Pattern (`ae2universalpattern:wildcard_pattern`)**:
  - Insert into any AE2 **Pattern Provider** or ExtendedAE **Assembler Matrix**.
  - Automatically loads and encodes crafting table recipes on-the-fly.
- **Dynamic Terminal-Aware Search**:
  - Automatically scans and supplies patterns matching the active search query typed into your ME Terminal / Crafting Terminal.
  - Supports all vanilla and modded crafting table (`RecipeType.CRAFTING`) recipes.
- **Smart Inventory Scoring**:
  - Scores recipes using your ME system's live inventory (`systemInventory`).
  - Prioritizes recipes whose ingredients you already have in storage.
  - Rejects unwanted inferior variants when superior materials exist (e.g. chooses leather over cardboard when cow essence/leather is available).
- **Complete BFS Sub-Crafting Trees**:
  - Recursively resolves multi-tier crafting chains up to 32 steps deep (e.g. 1048M, 256M storage components).
  - Handles bi-directional metal conversion cycles (nugget $\leftrightarrow$ ingot $\leftrightarrow$ block) so crafting steps are never missing.
- **ExtendedAE Integration**:
  - Fully compatible with the fast multiblock Assembler Matrix from ExtendedAE.
- **Optimized & Safe**:
  - Non-recursive Breadth-First Search (BFS) with visited-set cycle prevention, ensuring 0 lag even in heavy modpacks like *All the Mods 10 (ATM10)*.

---

## 🛠️ Usage

1. Craft a **Universal Pattern** in a crafting table (4 Blank Patterns + 1 Crafting Table).
2. Insert the **Universal Pattern** into:
   - A standard **AE2 Pattern Provider** (flat or block).
   - An **ExtendedAE Assembler Matrix**.
3. Open your ME Terminal / Crafting Terminal and search for any item you wish to craft.
4. The system dynamically indexes the matching recipes and their entire sub-crafting tree directly into the auto-crafting manager!

---

## 📦 Requirements

- **Minecraft**: `1.21.1`
- **Mod Loader**: `NeoForge 21.1.251+`
- **Java**: `21`
- **Dependencies**:
  - [Applied Energistics 2](https://curseforge.com/minecraft/mc-mods/applied-energistics-2) (`19.2.17+`)
  - *(Optional)* [ExtendedAE](https://curseforge.com/minecraft/mc-mods/extendedae)

---

## 🔨 Building from Source

Clone the repository and build using Gradle:

```bash
git clone https://github.com/Jhown/ae2universalpattern.git
cd ae2universalpattern
./gradlew build
```

The compiled mod JAR will be located in `build/libs/ae2universalpattern-1.0.0.jar`.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
