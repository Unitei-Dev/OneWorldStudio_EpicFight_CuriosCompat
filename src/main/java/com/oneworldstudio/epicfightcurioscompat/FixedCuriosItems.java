package com.oneworldstudio.epicfightcurioscompat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class FixedCuriosItems {
    public static final Map<ResourceLocation, FixedTarget> ITEMS;

    static {
        Map<ResourceLocation, FixedTarget> items = new LinkedHashMap<>();
        items.put(new ResourceLocation("create", "goggles"), FixedTarget.HEAD);
        ITEMS = Collections.unmodifiableMap(items);
    }

    private FixedCuriosItems() {
    }

    public enum FixedTarget {
        HEAD,
        BODY,
        BACK,
        GLOVES,
        BOOTS
    }
}
