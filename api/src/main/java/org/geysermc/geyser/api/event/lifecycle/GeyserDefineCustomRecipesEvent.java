package org.geysermc.geyser.api.event.lifecycle;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.event.Event;
import org.geysermc.geyser.api.recipe.CustomRecipe;

import java.util.Collection;

/**
 * Called before the first Bedrock session receives its recipe book.
 *
 * <p>Recipes registered here are sent to every Bedrock client alongside the server's own, which is
 * the only way a recipe the server holds outside its recipe packet can be crafted there.
 */
public interface GeyserDefineCustomRecipesEvent extends Event {

    /**
     * Registers one recipe. An invalid entry is refused rather than kept.
     *
     * @return whether it was accepted
     */
    boolean register(@NonNull CustomRecipe recipe);

    /** The recipes registered so far, in registration order. */
    @NonNull Collection<CustomRecipe> recipes();
}
