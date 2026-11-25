# RandomItemPlugin

A server-side **Paper plugin**: every survival player gets one random item every 45 seconds
(configurable), with a countdown sidebar. Players need **nothing installed** (vanilla clients work).

## 1. Build the .jar (no software needed: use GitHub)

1. Create a free GitHub account and a new repository.
2. Upload everything in this folder to it (drag and drop in the web UI; keep the `.github` folder).
3. Open the **Actions** tab, wait for "Build plugin" to finish (~2 min), open the run,
   and download the **RandomItemPlugin** artifact. Unzip it to get `RandomItemPlugin-1.0.0.jar`.

**Set your Minecraft version first** in `gradle.properties` (before uploading):
- Minecraft 1.21.x server: leave the defaults.
- Minecraft 26.x server: comment out the two default lines and uncomment the 26.x ones.

If the build fails, send me the error log and I'll fix it.

(Local build alternative: install JDK + Gradle, then run `gradle build`; the jar lands in `build/libs/`.)

## 2. Install on Aternos

1. In the Aternos panel go to **Software** and choose **Paper** (matching your version).
2. Go to **Files > plugins** and upload the `.jar` (or use the **Plugins** tab if shown).
3. Start the server. A `RandomItemPlugin/config.yml` appears in `plugins`.
4. Make yourself operator: type `op YourName` in the Aternos **Console**.
5. Join and run `/start-randomizer`.

## 3. Commands (ops only)

| Command | What it does |
|---|---|
| `/start-randomizer` | Starts the timer and shows the sidebar |
| `/stop-randomizer` | Stops it and removes the sidebar |
| `/randomitem interval <seconds>` | Change the interval live (min 5) |
| `/randomitem reload` | Reload `config.yml` |
| `/randomitem status` | Show running state, interval, rounds, pool size |

## 4. How rolling works

1. Pick one **chance** (all equally likely, except low-odds netherite gear).
2. If the chance is a group or variant family, pick one member at random at its **max** form.

- Single items: every item is its own chance (each spawn egg too).
- Groups (one chance each): wool, carpet, beds, banners, candles, dyes, concrete, concrete powder,
  terracotta, glazed terracotta, stained glass and panes, shulker boxes, each wood item kind,
  stairs, slabs, walls, music discs, pottery sherds, trim templates, banner patterns, corals, heads,
  and each copper item (all oxidation/waxed versions).
- Variant families at max: enchanted book (max level, no curses), potion / splash / lingering /
  tipped arrow (4 separate chances, strong > long > base, effectless potions excluded),
  goat horn, suspicious stew, ominous bottle (Bad Omen V).
- Tools: wood/iron/diamond/netherite sword, pickaxe, axe, shovel. Armor: leather/gold/iron/netherite.
  Netherite gear is 25% as likely and comes fully enchanted (presets in config).
- Excluded: written books, filled maps, knowledge book.
- Items disabled in your world (experimental features) are skipped automatically.

Full inventory: the item drops at the player's feet. Only survival players receive items.

## 5. Notes

- The sidebar is on the right (vanilla can't place it on the left). Its small red numbers
  can't be hidden without extra APIs.
- Rare rolls (netherite gear) play the challenge-complete sound; others play the XP-orb sound.
- New dye colors or wood types in future updates: add them to `tokens` in `config.yml`.
