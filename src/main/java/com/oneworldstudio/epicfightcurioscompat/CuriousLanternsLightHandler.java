package com.oneworldstudio.epicfightcurioscompat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class CuriousLanternsLightHandler {
    private static final Map<UUID, ResourceKey<Level>> ACTIVE_LIGHT_DIMENSIONS = new HashMap<>();
    private static final Map<UUID, BlockPos> ACTIVE_LIGHT_POSITIONS = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_LIGHT_LEVELS = new HashMap<>();
    private static final int LIGHT_UPDATE_FLAGS = Block.UPDATE_ALL_IMMEDIATE;

    private CuriousLanternsLightHandler() {
    }

    static void register() {
        if (ModList.get().isLoaded("curiouslanterns")) {
            MinecraftForge.EVENT_BUS.register(new CuriousLanternsLightHandler());
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }

        if (event.player instanceof ServerPlayer serverPlayer) {
            syncLight(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clearLight(event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        clearLight(event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearLight(event.getEntity());
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            clearLight(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        for (Map.Entry<UUID, BlockPos> entry : ACTIVE_LIGHT_POSITIONS.entrySet()) {
            ResourceKey<Level> dimension = ACTIVE_LIGHT_DIMENSIONS.get(entry.getKey());
            BlockPos pos = entry.getValue();

            if (dimension == null || pos == null) {
                continue;
            }

            ServerLevel level = event.getServer().getLevel(dimension);
            if (level != null && level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), LIGHT_UPDATE_FLAGS);
            }
        }

        ACTIVE_LIGHT_DIMENSIONS.clear();
        ACTIVE_LIGHT_POSITIONS.clear();
        ACTIVE_LIGHT_LEVELS.clear();
    }

    private static void syncLight(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ResourceKey<Level> currentDimension = ACTIVE_LIGHT_DIMENSIONS.get(playerId);
        BlockPos currentPos = ACTIVE_LIGHT_POSITIONS.get(playerId);
        int desiredLightLevel = getEquippedLanternLightLevel(player);

        if (!player.isAlive() || player.isSpectator() || desiredLightLevel <= 0) {
            if (currentDimension == null || currentPos == null) {
                return;
            }

            removeTrackedLight(playerId);
            refreshPlacement(player.serverLevel().getServer(), currentDimension, currentPos);

            return;
        }

        BlockPos desiredPos = findLightPlacement(player.serverLevel(), player);
        if (desiredPos == null) {
            if (currentDimension == null || currentPos == null) {
                return;
            }

            removeTrackedLight(playerId);
            refreshPlacement(player.serverLevel().getServer(), currentDimension, currentPos);
            return;
        }

        ResourceKey<Level> desiredDimension = player.serverLevel().dimension();
        ACTIVE_LIGHT_DIMENSIONS.put(playerId, desiredDimension);
        ACTIVE_LIGHT_POSITIONS.put(playerId, desiredPos.immutable());
        ACTIVE_LIGHT_LEVELS.put(playerId, desiredLightLevel);

        if (currentDimension != null
                && currentPos != null
                && (!currentDimension.equals(desiredDimension) || !currentPos.equals(desiredPos))) {
            refreshPlacement(player.serverLevel().getServer(), currentDimension, currentPos);
        }

        // Retry placement every tick so the light still appears after initial world join
        // even if the first placement attempt happened before the target block/chunk was ready.
        refreshPlacement(player.serverLevel().getServer(), desiredDimension, desiredPos);
    }

    private static void clearLight(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        UUID playerId = serverPlayer.getUUID();
        ResourceKey<Level> trackedDimension = ACTIVE_LIGHT_DIMENSIONS.get(playerId);
        BlockPos trackedPos = ACTIVE_LIGHT_POSITIONS.get(playerId);

        removeTrackedLight(playerId);

        if (trackedDimension != null && trackedPos != null) {
            refreshPlacement(serverPlayer.serverLevel().getServer(), trackedDimension, trackedPos);
        }
    }

    private static void removeTrackedLight(UUID playerId) {
        ACTIVE_LIGHT_DIMENSIONS.remove(playerId);
        ACTIVE_LIGHT_POSITIONS.remove(playerId);
        ACTIVE_LIGHT_LEVELS.remove(playerId);
    }

    private static int getEquippedLanternLightLevel(ServerPlayer player) {
        final int[] maxLight = {0};

        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.getCurios().forEach((slotId, stacksHandler) -> {
                    if (!"belt".equals(slotId) && !"waist".equals(slotId)) {
                        return;
                    }

                    IDynamicStackHandler stacks = stacksHandler.getStacks();
                    IDynamicStackHandler cosmetics = stacksHandler.getCosmeticStacks();

                    for (int i = 0; i < stacks.getSlots(); i++) {
                        maxLight[0] = Math.max(maxLight[0], getLanternLightLevel(stacks.getStackInSlot(i)));
                        maxLight[0] = Math.max(maxLight[0], getLanternLightLevel(cosmetics.getStackInSlot(i)));
                    }
                })
        );

        return maxLight[0];
    }

    private static int getLanternLightLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String path = key != null ? key.getPath() : "";

        if (!path.contains("lantern")) {
            return 0;
        }

        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState().getLightEmission();
        }

        if (path.contains("unlit")) {
            return 0;
        }

        return path.contains("soul") ? 10 : 15;
    }

    private static BlockPos findLightPlacement(ServerLevel level, ServerPlayer player) {
        BlockPos[] candidates = new BlockPos[] {
                BlockPos.containing(player.getX(), player.getY() + 0.9D, player.getZ()),
                player.blockPosition(),
                player.blockPosition().above(),
                BlockPos.containing(player.getX() + 0.35D, player.getY() + 0.9D, player.getZ()),
                BlockPos.containing(player.getX() - 0.35D, player.getY() + 0.9D, player.getZ()),
                BlockPos.containing(player.getX(), player.getY() + 0.9D, player.getZ() + 0.35D),
                BlockPos.containing(player.getX(), player.getY() + 0.9D, player.getZ() - 0.35D)
        };

        for (BlockPos candidate : candidates) {
            if (canHostLight(level, candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static boolean canHostLight(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(Blocks.LIGHT);
    }

    private static void refreshPlacement(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null || !level.isLoaded(pos)) {
            return;
        }

        int maxLight = 0;
        for (Map.Entry<UUID, BlockPos> entry : ACTIVE_LIGHT_POSITIONS.entrySet()) {
            UUID playerId = entry.getKey();
            ResourceKey<Level> trackedDimension = ACTIVE_LIGHT_DIMENSIONS.get(playerId);
            BlockPos trackedPos = entry.getValue();

            if (trackedDimension != null && trackedDimension.equals(dimension) && trackedPos.equals(pos)) {
                maxLight = Math.max(maxLight, ACTIVE_LIGHT_LEVELS.getOrDefault(playerId, 0));
            }
        }

        BlockState currentState = level.getBlockState(pos);

        if (maxLight <= 0) {
            if (currentState.is(Blocks.LIGHT)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), LIGHT_UPDATE_FLAGS);
            }
            return;
        }

        if (!currentState.isAir() && !currentState.is(Blocks.LIGHT)) {
            return;
        }

        BlockState desiredState = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, maxLight);
        if (!currentState.equals(desiredState)) {
            level.setBlock(pos, desiredState, LIGHT_UPDATE_FLAGS);
        }
    }
}
