# Hardcore Expeditions

A Fabric mod for Minecraft **26.2** and **26.3** that turns hardcore into a shared run for you and your friends.

- Each run is an **expedition**: a brand-new Overworld, Nether and End with a random seed.
- A **timer** tracks how long the group has survived. It only counts while at least one player is online.
- If **anyone** dies, everyone sees "*name* has died" with a **5-second countdown**. Then the whole group is sent to a fresh expedition with a new seed, empty inventories, and reset XP, advancements and recipes.
- **Deaths are tallied**, both in total and per player, and shown in the mod's stats panel (hold Tab) and `/expedition stats`.
- Killing the **Ender Dragon** completes the expedition and stops the timer at your final time. The best time is saved.

Everything happens inside the running server. Nobody gets disconnected, and old expedition worlds are deleted automatically.

## Installing

Requires Fabric Loader 0.19.5+ and [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version. Use the jar that matches your version: `hardcore-expeditions-1.0.1+mc26.2.jar` or `hardcore-expeditions-1.0.1+mc26.3.jar`. The server and every player must use the same Minecraft version. The world-creation library ([Fantasy](https://github.com/NucleoidMC/fantasy)) is bundled inside the jar.

**Server:** put the matching `hardcore-expeditions` jar and Fabric API in the server's `mods/` folder.

**Players (optional, recommended):** add the same jar and Fabric API to your client. In Lunar Client, open the Fabric add-on settings and drag the jars in. With the mod installed you get a small `Expedition #N  time  deaths` line in the top-left, a stats panel (most deaths, best time) while holding **Tab**, and the death overlay. Without it, you still see everything through a boss bar and on-screen titles.

**Singleplayer:** install it on your client and create a world. Works the same way.

### Recommended `server.properties`

```properties
hardcore=true        # hardcore hearts, difficulty locked to Hard
```

Spawn protection is automatically turned off inside expeditions, since every run starts at spawn.

## Customizing the HUD (players with the mod)

- **Stats key:** Options → Controls → Key Binds → *Hardcore Expeditions* → "Show Expedition Stats" (default **Tab**).
- **Move and resize:** open the layout editor in any of three ways: the config button in **Mod Menu**, the "Edit HUD Layout" key bind (unbound by default), or the **`/expeditionhud`** command. Drag the timer and stats panel where you want them, and use the sliders to resize them. You can also switch the stats key between *Hold* and *Toggle*.
- Your layout is saved in `config/hardcore_expeditions-client.json`.

## Commands

| Command | Who | What it does |
| --- | --- | --- |
| `/expedition` | Everyone | Current expedition number, status, time and seed |
| `/expedition stats` | Everyone | All-time deaths per player, best time, recent runs |
| `/expedition new` | Operators | Abandon the current expedition and start a new one (also how you start again after beating the dragon) |
| `/expedition reset confirm` | Operators | Wipe **all** stats (deaths, run history, best times) and start over at Expedition #1 |

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
./gradlew build                 # 26.2 jar in build/libs/
./gradlew build -Pmc=26.3       # 26.3 jar in build/libs/
./gradlew runServer -Pmc=26.3   # local test server in run/26.3 (accept the EULA in run/26.3/eula.txt first)
./gradlew runClient             # test client
```

**Testing with several players on one Mojang account:** the local test server (`run/<mc>/server.properties`) runs in offline mode, so development clients can join under any name. Start `./gradlew runServer`, then `./gradlew runClient -Pplayer=Alex` and `./gradlew runClient -Pplayer=Bob` in other terminals. Each opens its own client that joins `localhost` automatically. Your real account can join `localhost` from Lunar too.

`./gradlew runClientGameTest` (add `-Pmc=26.3` for 26.3) runs the automated end-to-end test in `src/gametest`. It opens a real Minecraft client that joins a real dedicated server, then checks portals, advancements, a death and the full reset (inventory, ender chest, XP, advancements, old worlds deleted), the dragon kill, and `/expedition new`. Screenshots of the HUD are saved to `build/run/clientGameTest/screenshots/`.

The mod icon is pixel art drawn by `tools/make_icon.py`; run `python3 tools/make_icon.py src/main/resources/assets/hardcore_expeditions/icon.png` after editing it.

Most code is shared. The few calls that differ between Minecraft versions live in `src/versions/<mc>/`, and each version's dependency numbers are in `versions/<mc>.properties`. To add a new Minecraft version, add both, then fix whatever fails to compile.

In VS Code, install the **Extension Pack for Java** and open this folder.
