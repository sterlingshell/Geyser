package org.geysermc.geyser.registry;

import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.recipe.CustomRecipe;
import org.geysermc.geyser.event.type.GeyserDefineCustomRecipesEventImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * The extension-registered recipes, loaded once on first use.
 *
 * <p>Lazy rather than fired during boot: a session asks for the list when it builds its recipe
 * packet, which is after every extension has loaded, so the load order stops mattering.
 */
public final class CustomRecipeRegistry {

    private static final List<CustomRecipe> RECIPES = new ArrayList<>();
    private static boolean loaded;

    private CustomRecipeRegistry() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        GeyserImpl.getInstance().eventBus().fire(new GeyserDefineCustomRecipesEventImpl(RECIPES));
    }

    public static List<CustomRecipe> recipes() {
        load();
        return List.copyOf(RECIPES);
    }
}
