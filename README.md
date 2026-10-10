# Hardcore Expeditions

A Fabric mod for Minecraft **26.2** that turns hardcore into a shared run for you and your friends.

- Each run is an **expedition**: a brand-new Overworld, Nether and End with a random seed.
- A **timer** tracks how long the group has survived. It only counts while at least one player is online.
- If **anyone** dies, everyone sees "*name* has died" with a **5-second countdown**. Then the whole group is sent to a fresh expedition with a new seed, empty inventories, and reset XP, advancements and recipes.
- **Deaths are tallied**, both in total and per player. Each player's count shows next to their name in the Tab list.
- Killing the **Ender Dragon** completes the expedition and stops the timer at your final time. The best time is saved.

Everything happens inside the running server. Nobody gets disconnected, and old expedition worlds are deleted automatically.

## Installing

Requires Fabric Loader 0.19.5+ and [Fabric API](https://modrinth.com/mod/fabric-api) for 26.2. The world-creation library ([Fantasy](https://github.com/NucleoidMC/fantasy)) is bundled inside the jar.

**Server:** put `hardcore-expeditions-x.y.z.jar` and Fabric API in the server's `mods/` folder.

**Players (optional, recommended):** add the same jar and Fabric API to your client. In Lunar Client, open the Fabric add-on settings and drag the jars in. With the mod installed you get the custom on-screen timer panel and death overlay. Without it, you still see everything through a boss bar and on-screen titles.

**Singleplayer:** install it on your client and create a world. Works the same way.

### Recommended `server.properties`

```properties
hardcore=true        # hardcore hearts, difficulty locked to Hard
```

Spawn protection is automatically turned off inside expeditions, since every run starts at spawn.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `/expedition` | Everyone | Current expedition number, status, time and seed |
| `/expedition stats` | Everyone | All-time deaths per player, best time, recent runs |
| `/expedition new` | Operators | Abandon the current expedition and start a new one (also how you start again after beating the dragon) |

## How it works

| File | Purpose |
| --- | --- |
| `ExpeditionManager` | Timer, deaths, countdown, and moving players into new worlds |
| `ExpeditionWorlds` | Creates and deletes an expedition's Overworld, Nether and End, and finds a safe spawn |
| `ExpeditionData` | Stats saved to `world/hardcore_expeditions.json` |
| `mixin/` | Small hooks into Minecraft: deaths, Nether/End portals, End gateways, dragon kill, spawn protection |
| `client/` | The HUD panel and death overlay for players with the mod |

The vanilla `minecraft:overworld` world still exists but is only a placeholder. Players always play in the expedition worlds (`hardcore_expeditions:expedition_<n>_overworld` and so on).

## Developing

You need **Java 25**.

```sh
./gradlew build        # jar ends up in build/libs/
./gradlew runServer    # local test server in ./run (accept the EULA in run/eula.txt first)
./gradlew runClient    # test client
```

In VS Code, install the **Extension Pack for Java** and open this folder.
