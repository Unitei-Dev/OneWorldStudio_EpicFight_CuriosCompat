package com.oneworldstudio.epicfightcurioscompat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotTypeMessage;
import top.theillusivec4.curios.api.SlotTypePreset;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class BackpackedCuriosBridge {
    private static final ResourceLocation BACKPACKED_BACKPACK_ID = new ResourceLocation("backpacked", "backpack");

    private BackpackedCuriosBridge() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(BackpackedCuriosBridge::onInterModEnqueue);
        modBus.addListener(BackpackedCuriosBridge::onCommonSetup);
    }

    private static void onInterModEnqueue(InterModEnqueueEvent event) {
        InterModComms.sendTo(
                "curios",
                SlotTypeMessage.REGISTER_TYPE,
                () -> SlotTypePreset.BACK.getMessageBuilder().size(1).build()
        );
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (!ModList.get().isLoaded("backpacked")) {
                return;
            }

            Item item = ForgeRegistries.ITEMS.getValue(BACKPACKED_BACKPACK_ID);
            if (item != null) {
                CuriosApi.registerCurio(item, BackpackedCurioItem.INSTANCE);
            }
        });
    }

    private enum BackpackedCurioItem implements ICurioItem {
        INSTANCE;

        @Override
        public boolean canEquip(SlotContext slotContext, ItemStack stack) {
            return SlotTypePreset.BACK.getIdentifier().equals(slotContext.identifier());
        }

        @Override
        public boolean canRightClickEquip(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
            return false;
        }
    }
}
