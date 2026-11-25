package dev.randomizer;

import org.bukkit.inventory.ItemStack;

/** One rolled item. {@code detail} is optional extra text such as "Sharpness V". */
public record RollResult(ItemStack stack, String detail, boolean rare) {
}
