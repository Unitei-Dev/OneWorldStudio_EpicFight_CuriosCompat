package com.oneworldstudio.epicfightcurioscompat;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(EpicFightCuriosCompatStandalone.MOD_ID)
public class EpicFightCuriosCompatStandalone {
    public static final String MOD_ID = "epicfight_curios_compat";

    public EpicFightCuriosCompatStandalone() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BackpackedCuriosBridge.register(modBus);
        ClientCuriosCompat.register(modBus);
        CuriosSlotAttachmentFixer.register(modBus);
        CuriousLanternsLightHandler.register();
    }
}
