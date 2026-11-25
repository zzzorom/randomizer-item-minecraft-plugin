package dev.randomizer;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.OminousBottleMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Builds the list of "chances" and rolls from it.
 *
 * Every entry is equally likely (unless weighted in the config). An entry is one of:
 *  - a single item (e.g. Diamond, each spawn egg)
 *  - a group of similar items (e.g. any wool color)
 *  - a variant family at its max level (enchanted book, potions, goat horn, ...)
 */
public final class ItemPool {

    /** Materials handled by dedicated variant entries instead of as plain items. */
    private static final Set<Material> SPECIALS = EnumSet.of(
            Material.ENCHANTED_BOOK, Material.POTION, Material.SPLASH_POTION,
            Material.LINGERING_POTION, Material.TIPPED_ARROW, Material.GOAT_HORN,
            Material.SUSPICIOUS_STEW, Material.OMINOUS_BOTTLE);

    /** Potions without any effect. */
    private static final Set<String> EFFECTLESS = Set.of("water", "mundane", "thick", "awkward");

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private final Logger log;
    private final List<PoolEntry> entries = new ArrayList<>();
    private final Map<String, Map<Enchantment, Integer>> presets = new HashMap<>();
    private double totalWeight;

    public ItemPool(Logger log) {
        this.log = log;
    }

    public int size() {
        return entries.size();
    }

    // ------------------------------------------------------------------ rolling

    public RollResult roll(Random random) {
        if (entries.isEmpty()) {
            return null;
        }
        double x = random.nextDouble() * totalWeight;
        PoolEntry chosen = entries.get(entries.size() - 1);
        for (PoolEntry e : entries) {
            x -= e.weight();
            if (x < 0) {
                chosen = e;
                break;
            }
        }
        RollResult r = chosen.maker().apply(random);
        return new RollResult(r.stack(), r.detail(), chosen.rare());
    }

    // ------------------------------------------------------------------ building

    public void build(World world, FileConfiguration cfg) {
        entries.clear();
        presets.clear();
        totalWeight = 0;

        Map<String, List<String>> tokens = new HashMap<>();
        ConfigurationSection tokenSec = cfg.getConfigurationSection("tokens");
        if (tokenSec != null) {
            for (String k : tokenSec.getKeys(false)) {
                tokens.put(k, tokenSec.getStringList(k));
            }
        }

        Registry<Enchantment> enchantRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        loadPresets(cfg, enchantRegistry);

        List<Pattern> excludes = compileAll(cfg.getStringList("exclude-materials"), tokens);

        // 1. all obtainable items enabled in this world
        List<Material> candidates = new ArrayList<>();
        for (Material m : Material.values()) {
            String n = m.name();
            if (n.startsWith("LEGACY_") || !m.isItem() || m.isAir()) {
                continue;
            }
            if (!m.isEnabledByFeature(world)) {
                continue;
            }
            if (matchesAny(excludes, n)) {
                continue;
            }
            candidates.add(m);
        }

        // 2. variant families (enchanted book, potions, ...)
        addSpecials(world, cfg, enchantRegistry);
        candidates.removeAll(SPECIALS);

        // 3. copper families (oxidation / waxed)
        if (cfg.getBoolean("group-copper", true)) {
            groupCopper(candidates);
        }

        // 4. configured groups
        ConfigurationSection groupSec = cfg.getConfigurationSection("groups");
        if (groupSec != null) {
            for (String name : groupSec.getKeys(false)) {
                List<Pattern> patterns = compileAll(groupSec.getStringList(name), tokens);
                List<Material> members = new ArrayList<>();
                Iterator<Material> it = candidates.iterator();
                while (it.hasNext()) {
                    Material m = it.next();
                    if (matchesAny(patterns, m.name())) {
                        members.add(m);
                        it.remove();
                    }
                }
                addGroup(name, members);
            }
        }

        // 5. everything left is its own chance
        List<Map<?, ?>> weightRules = cfg.getMapList("weights");
        for (Material m : candidates) {
            double weight = weightFor(m, weightRules);
            boolean rare = weight < 1.0;
            add(new PoolEntry(m.name().toLowerCase(Locale.ROOT), weight, rare, r -> {
                ItemStack stack = new ItemStack(m);
                String detail = applyPreset(stack);
                return new RollResult(stack, detail, false);
            }));
        }

        log.info("Item pool built: " + entries.size() + " chances (" + candidates.size() + " single items).");
    }

    private void add(PoolEntry e) {
        entries.add(e);
        totalWeight += e.weight();
    }

    private void addGroup(String name, List<Material> members) {
        if (members.isEmpty()) {
            return;
        }
        List<Material> list = List.copyOf(members);
        add(new PoolEntry("group:" + name, 1.0, false, r -> {
            Material m = list.get(r.nextInt(list.size()));
            return new RollResult(new ItemStack(m), null, false);
        }));
    }

    private void groupCopper(List<Material> candidates) {
        Map<String, List<Material>> families = new LinkedHashMap<>();
        for (Material m : candidates) {
            String n = m.name();
            if (!n.contains("COPPER")) {
                continue;
            }
            String base = n.startsWith("WAXED_") ? n.substring(6) : n;
            for (String prefix : new String[]{"EXPOSED_", "WEATHERED_", "OXIDIZED_"}) {
                if (base.startsWith(prefix)) {
                    base = base.substring(prefix.length());
                    break;
                }
            }
            if (base.equals("COPPER")) {
                base = "COPPER_BLOCK";
            }
            families.computeIfAbsent(base, k -> new ArrayList<>()).add(m);
        }
        for (Map.Entry<String, List<Material>> f : families.entrySet()) {
            if (f.getValue().size() >= 2) {
                candidates.removeAll(f.getValue());
                addGroup("copper:" + f.getKey().toLowerCase(Locale.ROOT), f.getValue());
            }
        }
    }

    // ------------------------------------------------------------------ weights / presets

    private double weightFor(Material m, List<Map<?, ?>> rules) {
        double w = 1.0;
        for (Map<?, ?> rule : rules) {
            Object p = rule.get("pattern");
            Object mult = rule.get("multiplier");
            if (p == null || !(mult instanceof Number)) {
                continue;
            }
            if (Pattern.compile(String.valueOf(p)).matcher(m.name()).find()) {
                w *= ((Number) mult).doubleValue();
            }
        }
        return w;
    }

    private void loadPresets(FileConfiguration cfg, Registry<Enchantment> registry) {
        ConfigurationSection sec = cfg.getConfigurationSection("netherite-presets");
        if (sec == null) {
            return;
        }
        for (String kind : sec.getKeys(false)) {
            ConfigurationSection inner = sec.getConfigurationSection(kind);
            if (inner == null) {
                continue;
            }
            Map<Enchantment, Integer> map = new LinkedHashMap<>();
            for (String enchantName : inner.getKeys(false)) {
                Enchantment e = registry.get(NamespacedKey.minecraft(enchantName.toLowerCase(Locale.ROOT)));
                if (e == null) {
                    log.warning("Unknown enchantment in netherite-presets." + kind + ": " + enchantName);
                    continue;
                }
                map.put(e, inner.getInt(enchantName));
            }
            presets.put(kind.toLowerCase(Locale.ROOT), map);
        }
    }

    /** Enchants netherite gear with its preset. Returns a detail string, or null if nothing applied. */
    private String applyPreset(ItemStack stack) {
        String n = stack.getType().name();
        if (!n.startsWith("NETHERITE_")) {
            return null;
        }
        String suffix = n.substring(n.lastIndexOf('_') + 1).toLowerCase(Locale.ROOT);
        Map<Enchantment, Integer> preset = presets.get(suffix);
        if (preset == null || preset.isEmpty()) {
            return null;
        }
        for (Map.Entry<Enchantment, Integer> e : preset.entrySet()) {
            stack.addUnsafeEnchantment(e.getKey(), e.getValue());
        }
        return "fully enchanted";
    }

    // ------------------------------------------------------------------ variant families

    private void addSpecials(World world, FileConfiguration cfg, Registry<Enchantment> enchantRegistry) {
        guarded("enchanted book", () -> addEnchantedBook(world, cfg, enchantRegistry));
        guarded("potions", () -> addPotions(world));
        guarded("goat horn", () -> addGoatHorn(world));
        guarded("suspicious stew", () -> addSuspiciousStew(world));
        guarded("ominous bottle", () -> addOminousBottle(world));
    }

    private void guarded(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            log.warning("Could not set up '" + what + "' (skipped): " + t);
        }
    }

    private void addEnchantedBook(World world, FileConfiguration cfg, Registry<Enchantment> registry) {
        if (!Material.ENCHANTED_BOOK.isEnabledByFeature(world)) {
            return;
        }
        Set<String> excluded = Set.copyOf(cfg.getStringList("excluded-enchants"));
        List<Enchantment> list = new ArrayList<>();
        for (Enchantment e : registry) {
            String key = e.getKey().getKey();
            if (key.endsWith("_curse") || excluded.contains(key)) {
                continue;
            }
            list.add(e);
        }
        if (list.isEmpty()) {
            return;
        }
        add(new PoolEntry("enchanted_book", 1.0, false, r -> {
            Enchantment e = list.get(r.nextInt(list.size()));
            int level = e.getMaxLevel();
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            meta.addStoredEnchant(e, level, true);
            book.setItemMeta(meta);
            return new RollResult(book, humanize(e.getKey().getKey()) + " " + roman(level), false);
        }));
    }

    private void addPotions(World world) {
        // best version of each potion: strong (higher level) > long (longer duration) > base
        Map<String, PotionType> best = new LinkedHashMap<>();
        Map<String, Integer> rank = new HashMap<>();
        for (PotionType type : Registry.POTION) {
            String key = type.getKey().getKey();
            if (EFFECTLESS.contains(key)) {
                continue;
            }
            int r = 0;
            String base = key;
            if (key.startsWith("strong_")) {
                r = 2;
                base = key.substring(7);
            } else if (key.startsWith("long_")) {
                r = 1;
                base = key.substring(5);
            }
            Integer current = rank.get(base);
            if (current == null || r > current) {
                rank.put(base, r);
                best.put(base, type);
            }
        }
        List<PotionType> types = new ArrayList<>(best.values());
        if (types.isEmpty()) {
            return;
        }
        for (Material mat : new Material[]{Material.POTION, Material.SPLASH_POTION,
                Material.LINGERING_POTION, Material.TIPPED_ARROW}) {
            if (!mat.isEnabledByFeature(world)) {
                continue;
            }
            add(new PoolEntry(mat.name().toLowerCase(Locale.ROOT), 1.0, false, r -> {
                PotionType t = types.get(r.nextInt(types.size()));
                ItemStack stack = new ItemStack(mat);
                PotionMeta meta = (PotionMeta) stack.getItemMeta();
                meta.setBasePotionType(t);
                stack.setItemMeta(meta);
                return new RollResult(stack, humanize(t.getKey().getKey()), false);
            }));
        }
    }

    private void addGoatHorn(World world) {
        if (!Material.GOAT_HORN.isEnabledByFeature(world)) {
            return;
        }
        List<MusicInstrument> list = new ArrayList<>();
        for (MusicInstrument i : Registry.INSTRUMENT) {
            list.add(i);
        }
        if (list.isEmpty()) {
            return;
        }
        add(new PoolEntry("goat_horn", 1.0, false, r -> {
            MusicInstrument inst = list.get(r.nextInt(list.size()));
            ItemStack stack = new ItemStack(Material.GOAT_HORN);
            MusicInstrumentMeta meta = (MusicInstrumentMeta) stack.getItemMeta();
            meta.setInstrument(inst);
            stack.setItemMeta(meta);
            return new RollResult(stack, humanize(inst.getKey().getKey()), false);
        }));
    }

    private void addSuspiciousStew(World world) {
        if (!Material.SUSPICIOUS_STEW.isEnabledByFeature(world)) {
            return;
        }
        // vanilla durations (ticks) for each flower effect
        List<PotionEffect> effects = List.of(
                new PotionEffect(PotionEffectType.NIGHT_VISION, 100, 0),
                new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 80, 0),
                new PotionEffect(PotionEffectType.BLINDNESS, 160, 0),
                new PotionEffect(PotionEffectType.SATURATION, 7, 0),
                new PotionEffect(PotionEffectType.JUMP_BOOST, 120, 0),
                new PotionEffect(PotionEffectType.POISON, 240, 0),
                new PotionEffect(PotionEffectType.REGENERATION, 160, 0),
                new PotionEffect(PotionEffectType.WEAKNESS, 180, 0),
                new PotionEffect(PotionEffectType.WITHER, 160, 0));
        add(new PoolEntry("suspicious_stew", 1.0, false, r -> {
            PotionEffect effect = effects.get(r.nextInt(effects.size()));
            ItemStack stack = new ItemStack(Material.SUSPICIOUS_STEW);
            SuspiciousStewMeta meta = (SuspiciousStewMeta) stack.getItemMeta();
            meta.addCustomEffect(effect, true);
            stack.setItemMeta(meta);
            return new RollResult(stack, humanize(effect.getType().getKey().getKey()), false);
        }));
    }

    private void addOminousBottle(World world) {
        if (!Material.OMINOUS_BOTTLE.isEnabledByFeature(world)) {
            return;
        }
        add(new PoolEntry("ominous_bottle", 1.0, false, r -> {
            ItemStack stack = new ItemStack(Material.OMINOUS_BOTTLE);
            OminousBottleMeta meta = (OminousBottleMeta) stack.getItemMeta();
            meta.setAmplifier(4);
            stack.setItemMeta(meta);
            return new RollResult(stack, "Bad Omen V", false);
        }));
    }

    // ------------------------------------------------------------------ helpers

    private static List<Pattern> compileAll(List<String> raw, Map<String, List<String>> tokens) {
        List<Pattern> out = new ArrayList<>();
        for (String s : raw) {
            String expanded = s;
            for (Map.Entry<String, List<String>> t : tokens.entrySet()) {
                expanded = expanded.replace("{" + t.getKey() + "}", "(" + String.join("|", t.getValue()) + ")");
            }
            out.add(Pattern.compile(expanded));
        }
        return out;
    }

    private static boolean matchesAny(List<Pattern> patterns, String name) {
        for (Pattern p : patterns) {
            if (p.matcher(name).find()) {
                return true;
            }
        }
        return false;
    }

    static String humanize(String key) {
        StringBuilder sb = new StringBuilder();
        for (String part : key.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    static String roman(int n) {
        return n >= 1 && n < ROMAN.length ? ROMAN[n] : String.valueOf(n);
    }
}
