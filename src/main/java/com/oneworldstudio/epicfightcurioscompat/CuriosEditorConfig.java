package com.oneworldstudio.epicfightcurioscompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.SlotContext;

@OnlyIn(Dist.CLIENT)
final class CuriosEditorConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("epicfight_curios_compat_positions.json");
    private static boolean loaded;
    private static ConfigData config = new ConfigData();

    private CuriosEditorConfig() {
    }

    static TransformOverride getOverride(SlotContext slotContext, ItemStack stack) {
        if (slotContext == null) {
            return TransformOverride.IDENTITY;
        }
        return getStandingOverride(slotContext.identifier(), slotContext.index(), stack);
    }

    static TransformOverride getOverride(String slotId, int slotIndex, ItemStack stack) {
        return getStandingOverride(slotId, slotIndex, stack);
    }

    static TransformOverride getStandingOverride(SlotContext slotContext, ItemStack stack) {
        if (slotContext == null) {
            return TransformOverride.IDENTITY;
        }
        return getStandingOverride(slotContext.identifier(), slotContext.index(), stack);
    }

    static TransformOverride getStandingOverride(String slotId, int slotIndex, ItemStack stack) {
        return getOverride(slotId, slotIndex, stack, false);
    }

    static TransformOverride getSittingOverride(SlotContext slotContext, ItemStack stack) {
        if (slotContext == null) {
            return TransformOverride.IDENTITY;
        }
        return getSittingOverride(slotContext.identifier(), slotContext.index(), stack);
    }

    static TransformOverride getSittingOverride(String slotId, int slotIndex, ItemStack stack) {
        return getOverride(slotId, slotIndex, stack, true);
    }

    private static TransformOverride getOverride(String slotId, int slotIndex, ItemStack stack, boolean sitting) {
        ensureLoaded();
        String key = buildKey(slotId, slotIndex, stack);
        if (key == null) {
            return TransformOverride.IDENTITY;
        }

        Map<String, TransformOverride> target = sitting ? config.sittingOverrides : config.overrides;
        TransformOverride override = target.get(key);
        return override != null ? override.copy() : TransformOverride.IDENTITY;
    }

    static void setOverride(String slotId, int slotIndex, ItemStack stack, TransformOverride override) {
        setStandingOverride(slotId, slotIndex, stack, override);
    }

    static void setStandingOverride(String slotId, int slotIndex, ItemStack stack, TransformOverride override) {
        setOverride(slotId, slotIndex, stack, override, false);
    }

    static void setSittingOverride(String slotId, int slotIndex, ItemStack stack, TransformOverride override) {
        setOverride(slotId, slotIndex, stack, override, true);
    }

    private static void setOverride(String slotId, int slotIndex, ItemStack stack, TransformOverride override, boolean sitting) {
        ensureLoaded();
        String key = buildKey(slotId, slotIndex, stack);
        if (key == null) {
            return;
        }

        TransformOverride value = override == null ? TransformOverride.IDENTITY : override.normalizedCopy();
        Map<String, TransformOverride> target = sitting ? config.sittingOverrides : config.overrides;
        if (value.isIdentity()) {
            target.remove(key);
        } else {
            target.put(key, value);
        }

        save();
    }

    static void clearOverride(String slotId, int slotIndex, ItemStack stack) {
        clearStandingOverride(slotId, slotIndex, stack);
    }

    static void clearStandingOverride(String slotId, int slotIndex, ItemStack stack) {
        clearOverride(slotId, slotIndex, stack, false);
    }

    static void clearSittingOverride(String slotId, int slotIndex, ItemStack stack) {
        clearOverride(slotId, slotIndex, stack, true);
    }

    static void clearAll() {
        ensureLoaded();
        if (config.overrides.isEmpty() && config.sittingOverrides.isEmpty()) {
            return;
        }

        config.overrides.clear();
        config.sittingOverrides.clear();
        save();
    }

    private static void clearOverride(String slotId, int slotIndex, ItemStack stack, boolean sitting) {
        ensureLoaded();
        String key = buildKey(slotId, slotIndex, stack);
        if (key == null) {
            return;
        }

        Map<String, TransformOverride> target = sitting ? config.sittingOverrides : config.overrides;
        if (target.remove(key) != null) {
            save();
        }
    }

    private static String buildKey(String slotId, int slotIndex, ItemStack stack) {
        if (slotId == null || stack == null || stack.isEmpty()) {
            return null;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return null;
        }

        return slotId + "|" + slotIndex + "|" + itemId;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        loaded = true;
        if (!Files.exists(CONFIG_PATH)) {
            config = new ConfigData();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            ConfigData loadedConfig = GSON.fromJson(reader, ConfigData.class);
            config = loadedConfig != null ? loadedConfig : new ConfigData();
            if (config.overrides == null) {
                config.overrides = new LinkedHashMap<>();
            }
            if (config.sittingOverrides == null) {
                config.sittingOverrides = new LinkedHashMap<>();
            }
        } catch (IOException | JsonSyntaxException ignored) {
            config = new ConfigData();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static final class ConfigData {
        private Map<String, TransformOverride> overrides = new LinkedHashMap<>();
        private Map<String, TransformOverride> sittingOverrides = new LinkedHashMap<>();
    }

    static final class TransformOverride {
        static final TransformOverride IDENTITY = new TransformOverride(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);

        float x;
        float y;
        float z;
        float rotX;
        float rotY;
        float rotZ;
        float scale = 1.0F;

        TransformOverride() {
        }

        TransformOverride(float x, float y, float z, float rotX, float rotY, float rotZ, float scale) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
            this.scale = scale;
        }

        boolean isIdentity() {
            return isNearZero(this.x)
                    && isNearZero(this.y)
                    && isNearZero(this.z)
                    && isNearZero(this.rotX)
                    && isNearZero(this.rotY)
                    && isNearZero(this.rotZ)
                    && Math.abs(this.scale - 1.0F) < 0.0001F;
        }

        TransformOverride normalizedCopy() {
            TransformOverride copy = copy();
            if (copy.scale <= 0.0F || !Float.isFinite(copy.scale)) {
                copy.scale = 1.0F;
            }
            return copy;
        }

        TransformOverride copy() {
            return new TransformOverride(this.x, this.y, this.z, this.rotX, this.rotY, this.rotZ, this.scale);
        }

        private static boolean isNearZero(float value) {
            return Math.abs(value) < 0.0001F;
        }
    }
}
