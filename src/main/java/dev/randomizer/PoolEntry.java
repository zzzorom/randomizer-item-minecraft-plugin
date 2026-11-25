package dev.randomizer;

import java.util.Random;
import java.util.function.Function;

/** One "chance" in the roll. Every entry has equal odds unless its weight says otherwise. */
public record PoolEntry(String id, double weight, boolean rare, Function<Random, RollResult> maker) {
}
