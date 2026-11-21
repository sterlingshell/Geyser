package org.geysermc.geyser.event.type;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomRecipesEvent;
import org.geysermc.geyser.api.recipe.CustomRecipe;

import java.util.Collection;
import java.util.List;

public final class GeyserDefineCustomRecipesEventImpl implements GeyserDefineCustomRecipesEvent {

    private final List<CustomRecipe> recipes;

    public GeyserDefineCustomRecipesEventImpl(List<CustomRecipe> recipes) {
        this.recipes = recipes;
    }

    @Override
    public boolean register(@NonNull CustomRecipe recipe) {
        // A duplicate id would make the Bedrock recipe book pick one at random; the second one
        // loses rather than replacing a recipe another extension already owns.
        if (recipe == null || recipes.stream().anyMatch(known -> known.identifier().equals(recipe.identifier()))) {
            return false;
        }
        recipes.add(recipe);
        return true;
    }

    @Override
    public @NonNull Collection<CustomRecipe> recipes() {
        return List.copyOf(recipes);
    }
}
