package org.geysermc.geyser.inventory.recipe;

import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.RecipeUnlockingRequirement;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.geysermc.geyser.api.recipe.CustomRecipe;
import org.geysermc.geyser.registry.CustomRecipeRegistry;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.geyser.util.MinecraftKey;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Appends extension recipes to the recipe book Geyser sends a Bedrock client. */
public final class CustomRecipeTranslator {

    private static final String STATION = "crafting_table";

    private CustomRecipeTranslator() {
    }

    public static void append(GeyserSession session, CraftingDataPacket packet) {
        for (CustomRecipe recipe : CustomRecipeRegistry.recipes()) {
            if (recipe instanceof CustomRecipe.Shaped shaped) {
                appendShaped(session, packet, shaped);
            } else if (recipe instanceof CustomRecipe.Shapeless shapeless) {
                appendShapeless(session, packet, shapeless);
            }
        }
    }

    private static void appendShaped(GeyserSession session, CraftingDataPacket packet, CustomRecipe.Shaped recipe) {
        int height = recipe.pattern().size();
        int width = 0;
        for (String row : recipe.pattern()) {
            width = Math.max(width, row.length());
        }
        if (width == 0) {
            return;
        }
        List<ItemDescriptorWithCount> ingredients = new ArrayList<>(width * height);
        for (String row : recipe.pattern()) {
            for (int column = 0; column < width; column++) {
                char symbol = column < row.length() ? row.charAt(column) : ' ';
                CustomRecipe.Ingredient ingredient = recipe.key().get(symbol);
                ingredients.add(symbol == ' ' || ingredient == null
                        ? ItemDescriptorWithCount.EMPTY
                        : descriptor(session, ingredient));
            }
        }
        ItemData output = output(session, recipe.result());
        if (output == null) {
            return;
        }
        packet.getShapedData().add(ShapedRecipeData.shaped(recipe.identifier().toString(), width, height,
                ingredients, List.of(output), UUID.randomUUID(), STATION, 0,
                session.getLastRecipeNetId().incrementAndGet(), true, RecipeUnlockingRequirement.INVALID));
    }

    private static void appendShapeless(GeyserSession session, CraftingDataPacket packet, CustomRecipe.Shapeless recipe) {
        List<ItemDescriptorWithCount> ingredients = new ArrayList<>(recipe.ingredients().size());
        for (CustomRecipe.Ingredient ingredient : recipe.ingredients()) {
            ingredients.add(descriptor(session, ingredient));
        }
        ItemData output = output(session, recipe.result());
        if (output == null) {
            return;
        }
        packet.getShapelessData().add(ShapelessRecipeData.shapeless(recipe.identifier().toString(), ingredients,
                List.of(output), UUID.randomUUID(), STATION, 0,
                session.getLastRecipeNetId().incrementAndGet(), RecipeUnlockingRequirement.INVALID));
    }

    private static ItemDescriptorWithCount descriptor(GeyserSession session, CustomRecipe.Ingredient ingredient) {
        ItemMapping mapping = session.getItemMappings().getMapping(ingredient.javaIdentifier());
        if (mapping == null) {
            return ItemDescriptorWithCount.EMPTY;
        }
        return new ItemDescriptorWithCount(new DefaultDescriptor(mapping.getBedrockDefinition(),
                mapping.getBedrockData()), ingredient.count());
    }

    private static ItemData output(GeyserSession session, CustomRecipe.Result result) {
        ItemMapping mapping = session.getItemMappings().getMapping(result.javaIdentifier());
        if (mapping == null) {
            return null;
        }
        GeyserItemStack stack;
        if (result.itemModel() == null) {
            stack = GeyserItemStack.of(session, mapping.getJavaItem().javaId(), result.count());
        } else {
            // The same lookup CustomItemTranslator uses: a Java item plus its item model is a custom
            // item, and translating the stack carries its Bedrock NBT with it.
            DataComponents components = new DataComponents(new Reference2ObjectOpenHashMap<>());
            components.put(DataComponentTypes.ITEM_MODEL, MinecraftKey.key(result.itemModel().toString()));
            stack = GeyserItemStack.of(session, mapping.getJavaItem().javaId(), result.count(), components);
        }
        ItemData data = ItemTranslator.translateToBedrock(session, stack);
        return data.isValid() ? data : null;
    }
}
