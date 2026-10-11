**Hardcore, but together.** Every run is an *expedition*: a brand-new Overworld, Nether and End with a random seed. If anyone in the group dies, everyone is sent to a fresh expedition and starts over from nothing. Beat the Ender Dragon to finish the run and lock in your time.

> ⚠️ **This mod deletes worlds.** When an expedition ends, its Overworld, Nether and End are deleted automatically. Install it on a fresh server or world, not one you want to keep.

![The HUD: expedition number, run timer and total deaths](https://cdn.modrinth.com/data/arbXTSM0/images/d53353fa854a147400cdf9aa9b01233bfd741d9b.png)

## Features

- **One death resets everyone.** When anyone dies, the whole group is moved to a brand-new world with a new seed. Inventories, ender chests, XP, advancements and recipes are wiped.
- **Death countdown.** Everyone sees who died and how, then a 5-second countdown to the new world. Nobody is disconnected; the server keeps running.
- **Run timer.** Counts how long the current expedition has lasted, only while someone is online.
- **Beat the dragon to win.** Killing the Ender Dragon ends the expedition and records your time.
- **Death stats.** A leaderboard of who has died the most, plus total deaths, expeditions played, longest run and fastest win. Hold Tab, or use `/expedition stats`.

![The death countdown: "Mistxzx has died", new world in 4](https://cdn.modrinth.com/data/arbXTSM0/images/f0ac302730e1e710278675c7eb90c4585163e30d.png)

![The stats panel: death leaderboard, expeditions, longest run and fastest win](https://cdn.modrinth.com/data/arbXTSM0/images/582d8c6a2bec477616bb0a6a6a00cd7d4412c15e.png)

## Installing

**Server (required):** put the jar matching your Minecraft version, plus [Fabric API](https://modrinth.com/mod/fabric-api), in the server's `mods/` folder. For hardcore hearts, set `hardcore=true` in `server.properties`.

**Players (optional, recommended):** install the same jar and Fabric API on your client to get the HUD (`Expedition #N  time  deaths`), the stats panel and the death overlay. Players without the mod can still join and see everything through a boss bar and on-screen titles.

**Singleplayer:** install it on your client and create a new world.

## HUD

Move and resize the timer and stats panel with the layout editor. Open it from the config button in [Mod Menu](https://modrinth.com/mod/modmenu), with the `/expeditionhud` command, or with the "Edit HUD Layout" key bind. The stats key (default **Tab**) can be rebound and switched between hold and toggle.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `/expedition` | Everyone | Current expedition number, status, time and seed |
| `/expedition stats` | Everyone | All-time deaths per player, best time, recent runs |
| `/expedition new` | Operators | Abandon the current expedition and start a new one (also how you start again after beating the dragon) |
| `/expedition reset confirm` | Operators | Wipe **all** stats and start over at Expedition #1 |
