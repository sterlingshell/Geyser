/*
 * Copyright (c) 2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.registry.mappings.versions.biome;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.biome.custom.CustomBiomeAppearance;
import org.geysermc.geyser.api.biome.custom.CustomBiomeDefinition;
import org.geysermc.geyser.api.biome.custom.CustomBiomeDefinitionRegisterException;
import org.geysermc.geyser.api.biome.custom.CustomBiomePrecipitation;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.biome.custom.GeyserCustomBiomeDefinition;
import org.geysermc.geyser.item.exception.InvalidCustomMappingsFileException;
import org.geysermc.geyser.registry.mappings.MappingsReader;
import org.geysermc.geyser.registry.mappings.util.MappingsUtil;
import org.geysermc.geyser.registry.mappings.util.NodeReader;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BiomeMappingsReader_v1 implements MappingsReader<Identifier, CustomBiomeDefinition> {
    private static final String AMBIENT_PARTICLES = "minecraft:visual/ambient_particles";

    /**
     * Java's ambient particle types that Bedrock's precipitation particles can show.
     */
    private static final Map<String, CustomBiomePrecipitation.Type> PARTICLE_TYPES = Map.of(
        "minecraft:ash", CustomBiomePrecipitation.Type.ASH,
        "minecraft:white_ash", CustomBiomePrecipitation.Type.WHITE_ASH,
        "minecraft:crimson_spore", CustomBiomePrecipitation.Type.RED_SPORES,
        "minecraft:warped_spore", CustomBiomePrecipitation.Type.BLUE_SPORES);

    /**
     * Bedrock's precipitation densities are not Java's spawn probabilities, and no vanilla
     * pair is documented on both sides. The conversion is calibrated on basalt deltas, whose
     * Java white ash probability shows as the density {@code 2.0} the API mentions; it is an
     * approximation, and a mapping can always set {@code geyser.precipitation} explicitly.
     */
    private static final float DENSITY_PER_PROBABILITY = 2.0F / 0.118093334F;

    @Override
    public void read(Path file, JsonObject mappings, BiConsumer<Identifier, CustomBiomeDefinition> consumer) {
        read(file, new JsonObject(), mappings, consumer);
    }

    @Override
    public void read(Path file, JsonObject root, JsonObject mappings, BiConsumer<Identifier, CustomBiomeDefinition> consumer) {
        UUID packUuid;
        try {
            packUuid = readPackUuid(root);
        } catch (InvalidCustomMappingsFileException exception) {
            GeyserImpl.getInstance().getLogger().error("Error reading mapping_options in custom mappings file: " + file, exception);
            return;
        }
        // Sorted so registration conflicts don't depend on the file's property order
        mappings.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getValue().isJsonObject()) {
                try {
                    Identifier javaIdentifier = Identifier.of(entry.getKey());
                    consumer.accept(javaIdentifier, readDefinition(javaIdentifier, entry.getValue().getAsJsonObject(), packUuid, "biome " + javaIdentifier));
                } catch (InvalidCustomMappingsFileException | IllegalArgumentException | CustomBiomeDefinitionRegisterException exception) {
                    GeyserImpl.getInstance().getLogger().error("Error reading custom biome " + entry.getKey() + " in custom mappings file: " + file.toString(), exception);
                }
            } else {
                GeyserImpl.getInstance().getLogger().error("Custom biome key " + entry.getKey() + " in custom mappings file " + file.toString() + " was not an object!");
            }
        });
    }

    /**
     * A file can name a provided resource pack that styles its biomes in place of the generated one,
     * under {@code mapping_options.biomes.pack_uuid}.
     */
    private @Nullable UUID readPackUuid(JsonObject root) throws InvalidCustomMappingsFileException {
        JsonObject options = readObject(root, "mapping_options", "custom biome mappings");
        JsonObject biomeOptions = options == null ? null : readObject(options, "biomes", "custom biome mappings");
        if (biomeOptions == null) {
            return null;
        }
        return MappingsUtil.readOrDefault(biomeOptions, "pack_uuid", NodeReader.UUID, null, "custom biome mappings");
    }

    /**
     * Reads one biome. Colors are read from the same {@code effects} and {@code attributes}
     * keys a Java biome uses, so the plain values of a Java biome definition can be copied
     * in as-is; the optional {@code geyser} object holds what Bedrock needs on top of that.
     * When no Bedrock identifier is named, one is derived from the Java identifier. In a
     * file whose biome options name a {@code pack_uuid}, that pack provides the visuals, so
     * appearance values are rejected.
     */
    private CustomBiomeDefinition readDefinition(Identifier javaIdentifier, JsonObject object, @Nullable UUID packUuid, String... context) throws InvalidCustomMappingsFileException {
        JsonObject geyser = readObject(object, "geyser", context);
        GeyserCustomBiomeDefinition.Builder builder;
        if (geyser != null && geyser.has("bedrock_identifier")) {
            builder = new GeyserCustomBiomeDefinition.Builder(
                MappingsUtil.readOrThrow(geyser, "bedrock_identifier", NodeReader.GEYSER_IDENTIFIER, context));
        } else {
            builder = GeyserCustomBiomeDefinition.derivedBuilder(javaIdentifier);
        }
        if (geyser != null) {
            MappingsUtil.readArrayIfPresent(geyser, "tags", tags -> tags.forEach(builder::tag), NodeReader.NON_EMPTY_STRING, context);
        }

        CustomBiomeAppearance.Builder appearance = CustomBiomeAppearance.builder();
        // A pack that provides the visuals styles the particles too, so nothing is derived
        if (readAppearance(appearance, readObject(object, "effects", context), readObject(object, "attributes", context), geyser, packUuid == null, context)) {
            if (packUuid != null) {
                throw new InvalidCustomMappingsFileException("reading appearance values", "the file names a pack_uuid that provides the visuals; remove them", context);
            }
            builder.appearance(appearance);
        }
        if (packUuid != null) {
            builder.packUuid(packUuid);
        }
        return builder.build();
    }

    /**
     * Reads the appearance values, returning whether any was present, as the API rejects
     * an empty appearance.
     */
    private boolean readAppearance(CustomBiomeAppearance.Builder builder, @Nullable JsonObject effects,
                                   @Nullable JsonObject attributes, @Nullable JsonObject geyser,
                                   boolean deriveParticles, String... context) throws InvalidCustomMappingsFileException {
        boolean set = false;
        if (effects != null) {
            set |= readValue(effects, "water_color", builder::waterSurfaceColor, NodeReader.COLOR, context);
            set |= readValue(effects, "grass_color", builder::grassColor, NodeReader.COLOR, context);
            set |= readValue(effects, "foliage_color", builder::foliageColor, NodeReader.COLOR, context);
            set |= readValue(effects, "dry_foliage_color", builder::dryFoliageColor, NodeReader.COLOR, context);
        }
        if (attributes != null) {
            set |= readAttribute(attributes, "minecraft:visual/sky_color", builder::skyColor, NodeReader.COLOR, context);
            set |= readAttribute(attributes, "minecraft:visual/fog_color", builder::fogColor, NodeReader.COLOR, context);
            set |= readAttribute(attributes, "minecraft:visual/water_fog_color", builder::waterFogColor, NodeReader.COLOR, context);
            set |= readAttribute(attributes, "minecraft:visual/water_fog_end_distance", builder::waterFogEndDistance, NodeReader.FLOAT, context);
        }

        // Bedrock can only show ash and spores, so a Java biome's ambient particles are
        // translated when they are one of those; the geyser value below overrides them
        if (deriveParticles && (geyser == null || !geyser.has("precipitation"))) {
            CustomBiomePrecipitation particles = readAmbientParticles(attributes, effects);
            if (particles != null) {
                builder.precipitation(particles);
                set = true;
            }
        }

        // The geyser values are read last, so they override the Java-shaped ones
        if (geyser != null) {
            set |= readValue(geyser, "water_surface_opacity", builder::waterSurfaceOpacity, NodeReader.FLOAT, context);
            set |= readValue(geyser, "water_fog_end_distance", builder::waterFogEndDistance, NodeReader.FLOAT, context);
            JsonObject precipitation = readObject(geyser, "precipitation", context);
            if (precipitation != null) {
                builder.precipitation(CustomBiomePrecipitation.of(
                    MappingsUtil.readOrThrow(precipitation, "type", NodeReader.PRECIPITATION_TYPE, context),
                    MappingsUtil.readOrThrow(precipitation, "density", NodeReader.FLOAT, context)));
                set = true;
            }
        }
        return set;
    }

    /**
     * Reads a Java biome's ambient particles, which are the only particle source Bedrock's
     * precipitation component can reproduce. Particle types without a Bedrock counterpart
     * are skipped, so a biome showing e.g. cherry leaves simply gets no precipitation.
     */
    private @Nullable CustomBiomePrecipitation readAmbientParticles(@Nullable JsonObject attributes, @Nullable JsonObject effects) {
        JsonElement value = attributes == null ? null : attributes.get(AMBIENT_PARTICLES);
        if (value instanceof JsonArray particles) {
            for (JsonElement particle : particles) {
                if (!particle.isJsonObject()) {
                    continue;
                }
                JsonObject entry = particle.getAsJsonObject();
                CustomBiomePrecipitation precipitation = precipitation(entry.get("particle"), entry.get("probability"));
                if (precipitation != null) {
                    return precipitation;
                }
            }
            return null;
        }
        // Java 1.21.6 and earlier keep the particles in effects.particle
        if (effects != null && effects.get("particle") instanceof JsonObject particle) {
            // The particle options are nested under "options" there
            JsonElement options = particle.get("options");
            return precipitation(options == null ? particle.get("particle") : options, particle.get("probability"));
        }
        return null;
    }

    private @Nullable CustomBiomePrecipitation precipitation(@Nullable JsonElement particle, @Nullable JsonElement probability) {
        String type;
        if (particle != null && particle.isJsonPrimitive()) {
            type = particle.getAsString();
        } else if (particle != null && particle.isJsonObject()) {
            JsonElement typeElement = particle.getAsJsonObject().get("type");
            if (typeElement == null || !typeElement.isJsonPrimitive()) {
                return null;
            }
            type = typeElement.getAsString();
        } else {
            return null;
        }
        CustomBiomePrecipitation.Type bedrockType = PARTICLE_TYPES.get(type.indexOf(':') < 0 ? "minecraft:" + type : type);
        if (bedrockType == null) {
            return null;
        }
        // Java defaults an omitted probability to always, which is far denser than any
        // vanilla biome; only an explicit, positive probability is translated
        if (probability == null || !probability.isJsonPrimitive() || !probability.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        float chance = probability.getAsFloat();
        if (!Float.isFinite(chance) || chance <= 0.0F) {
            return null;
        }
        return CustomBiomePrecipitation.of(bedrockType, chance * DENSITY_PER_PROBABILITY);
    }

    private <T> boolean readValue(JsonObject object, String key, Consumer<T> consumer, NodeReader<T> reader, String... context) throws InvalidCustomMappingsFileException {
        if (!object.has(key)) {
            return false;
        }
        consumer.accept(MappingsUtil.readOrThrow(object, key, reader, context));
        return true;
    }

    /**
     * Object-form attribute modifiers have no fixed result to translate, so they are
     * skipped rather than failing the biome.
     */
    private <T> boolean readAttribute(JsonObject object, String key, Consumer<T> consumer, NodeReader<T> reader, String... context) throws InvalidCustomMappingsFileException {
        if (object.get(key) instanceof JsonObject) {
            return false;
        }
        return readValue(object, key, consumer, reader, context);
    }

    private @Nullable JsonObject readObject(JsonObject object, String key, String... context) throws InvalidCustomMappingsFileException {
        JsonElement element = object.get(key);
        if (element == null) {
            return null;
        } else if (!element.isJsonObject()) {
            throw new InvalidCustomMappingsFileException("reading " + key, key + " must be an object", context);
        }
        return element.getAsJsonObject();
    }
}
