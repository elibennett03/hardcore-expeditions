**Hardcore, but together.** Every run is an *expedition*: a brand-new Overworld, Nether and End with a random seed. If anyone in the group dies, everyone is sent to a fresh expedition and starts over from nothing. Beat the Ender Dragon to finish the run and lock in your time.

> ⚠️ **This mod deletes worlds.** When an expedition ends, its Overworld, Nether and End are deleted automatically. Install it on a fresh server or world, not one you want to keep.

## Features

- **Shared runs.** One death resets the whole group: new seed, empty inventories and ender chests, and reset XP, advancements and recipes.
- **Death countdown.** Everyone sees who died and how, with a 5-second countdown before the new world. Nobody gets disconnected; it all happens inside the running server.
- **Run timer.** Tracks how long the group has survived. It only counts while at least one player is online.
- **Ender Dragon = victory.** Killing the dragon completes the expedition, stops the timer and saves your best time.
- **Stats.** Total and per-player deaths, longest run and fastest win, in the stats panel (hold Tab) and with `/expedition stats`.
- **Everything still works.** Nether and End portals, advancements like *We Need to Go Deeper* and *The End?*, phantoms, raids and wandering traders all behave like vanilla inside expeditions.

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
