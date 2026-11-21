package org.geysermc.geyser.api.recipe;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.util.Identifier;

import java.util.List;
import java.util.Map;

/**
 * A crafting-table recipe that only exists on the server, offered to Bedrock clients.
 *
 * <p>Java plugin recipes never reach the recipe packet Geyser translates, so a Bedrock client does
 * not know they exist and cannot craft them. An extension registers them here instead; ingredients
 * and result are Java item identifiers, which is what the item registry can map.
 */
public sealed interface CustomRecipe permits CustomRecipe.Shaped, CustomRecipe.Shapeless {

    /** One stack of a Java item: the identifier as the server writes it, e.g. {@code minecraft:stick}. */
    record Ingredient(@NonNull String javaIdentifier, int count) {
        public Ingredient {
            if (count < 1) {
                throw new IllegalArgumentException("count must be at least 1");
            }
        }
    }

    /** A shaped recipe; a space in the pattern is an empty cell. */
    record Shaped(@NonNull Identifier identifier, @NonNull List<String> pattern,
                  @NonNull Map<Character, Ingredient> key, @NonNull Ingredient result) implements CustomRecipe {
        public Shaped {
            if (pattern.isEmpty()) {
                throw new IllegalArgumentException("pattern must have at least one row");
            }
        }
    }

    /** A shapeless recipe: the ingredients in any arrangement. */
    record Shapeless(@NonNull Identifier identifier, @NonNull List<Ingredient> ingredients,
                     @NonNull Ingredient result) implements CustomRecipe {
        public Shapeless {
            if (ingredients.isEmpty()) {
                throw new IllegalArgumentException("ingredients must not be empty");
            }
        }
    }

    @NonNull Identifier identifier();
}
