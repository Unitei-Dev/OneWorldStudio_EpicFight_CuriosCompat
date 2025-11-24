package com.oneworldstudio.epicfightcurioscompat;

import java.util.Map;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.item.*;
import org.apache.commons.lang3.mutable.MutableBoolean;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer.HumanoidRender;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.client.render.CuriosLayer;

import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.Mesh.DrawingFunction;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.client.model.transformer.HumanoidModelBaker;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.renderer.EpicFightRenderTypes;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.ModelRenderLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Standalone Curios compatibility for EpicFight.
 * Only Curios integration is touched.
 */
@OnlyIn(Dist.CLIENT)
public class ClientCuriosCompat {

    public static void register(IEventBus modBus) {
        modBus.<PatchedRenderersEvent.Modify>addListener((event) -> {
            if (event.get(EntityType.PLAYER) instanceof PatchedLivingEntityRenderer patchedlivingrenderer) {
                patchedlivingrenderer.addPatchedLayerAlways(CuriosLayer.class, new PatchedCuriosLayerRenderer());
            }
        });

        modBus.<EntityRenderersEvent.AddLayers>addListener((event) -> {
            PatchedCuriosLayerRenderer.CURIO_MESHES.values().forEach(SkinnedMesh::destroy);
            PatchedCuriosLayerRenderer.CURIO_MESHES.clear();

            removeCuriosBackSlotLayer(event);
        });
    }


    public static class PatchedCuriosLayerRenderer extends ModelRenderLayer<
                LivingEntity,
                LivingEntityPatch<LivingEntity>,
                EntityModel<LivingEntity>,
                CuriosLayer<LivingEntity, EntityModel<LivingEntity>>,
                SkinnedMesh> {

        private static final Map<HumanoidModel<LivingEntity>, SkinnedMesh> CURIO_MESHES = Maps.newHashMap();

        public PatchedCuriosLayerRenderer() {
            super(null);
        }

        @Override
        protected void renderLayer(
                LivingEntityPatch<LivingEntity> entitypatch,
                LivingEntity livingEntity,
                CuriosLayer<LivingEntity, EntityModel<LivingEntity>> vanillaLayer,
                PoseStack poseStack,
                MultiBufferSource buffers,
                int packedLight,
                OpenMatrix4f[] poses,
                float bob,
                float yRot,
                float xRot,
                float partialTicks
        ) {
            MutableBoolean renderedEpicFightModel = new MutableBoolean(false);

            CuriosApi.getCuriosInventory(livingEntity).ifPresent(handler -> {
                handler.getCurios().forEach((slotId, stacksHandler) -> {
                    IDynamicStackHandler stackHandler = stacksHandler.getStacks();
                    IDynamicStackHandler cosmeticStacksHandler = stacksHandler.getCosmeticStacks();

                    for (int i = 0; i < stackHandler.getSlots(); i++) {
                        ItemStack stack = cosmeticStacksHandler.getStackInSlot(i);
                        boolean cosmetic = true;
                        NonNullList<Boolean> renderStates = stacksHandler.getRenders();
                        boolean renderable = renderStates.size() > i && renderStates.get(i);

                        if (stack.isEmpty() && renderable) {
                            stack = stackHandler.getStackInSlot(i);
                            cosmetic = false;
                        }
                        if (stack.isEmpty()) {
                            continue;
                        }

                        final ItemStack finalStack = stack;
                        SlotContext slotContext = new SlotContext(slotId, livingEntity, i, cosmetic, renderable);

                        // 1) Пытаемся использовать Curios-renderer (Relics, крылья, обручи и т.п.)
                        var optionalRenderer = CuriosRendererRegistry.getRenderer(finalStack.getItem());
                        if (optionalRenderer.isPresent()) {
                            var curioRenderer = optionalRenderer.get();
                            SlotRule rule = SlotRule.forSlotAndStack(slotId, finalStack);

                            if (curioRenderer instanceof HumanoidRender humanoidRenderer) {
                                // Полноценный 3D-рендер через EpicFight-скелет (Relics и др.)
                                HumanoidModel<LivingEntity> curioModel =
                                        humanoidRenderer.getModel(finalStack, slotContext);
                                SkinnedMesh skinnedMesh;

                                if (ClientEngine.getInstance().isVanillaModelDebuggingMode()
                                        || !CURIO_MESHES.containsKey(curioModel)) {

                                    poseStack.pushPose();
                                    poseStack.translate(10000.0D, 0.0D, 0.0D);

                                    LivingEntityRenderer<?, ?> parentRenderer;
                                    if (livingEntity instanceof AbstractClientPlayer abstractClientPlayer) {
                                        parentRenderer = (LivingEntityRenderer<?, ?>) Minecraft.getInstance()
                                                .getEntityRenderDispatcher()
                                                .getSkinMap()
                                                .get(abstractClientPlayer.getModelName());
                                    } else {
                                        parentRenderer = (LivingEntityRenderer<?, ?>) Minecraft.getInstance()
                                                .getEntityRenderDispatcher()
                                                .getSkinMap()
                                                .get("default");
                                    }

                                    // Один раз рендерим где-то далеко, чтобы EpicFight смог "испечь" модель
                                    curioRenderer.render(finalStack, slotContext, poseStack, parentRenderer, buffers,
                                            0, 0, 0, 0, 0, 0, 0);
                                    poseStack.popPose();

                                    skinnedMesh = HumanoidModelBaker.VANILLA_TRANSFORMER
                                            .transformArmorModel(curioModel);
                                    CURIO_MESHES.put(curioModel, skinnedMesh);
                                } else {
                                    skinnedMesh = CURIO_MESHES.get(curioModel);
                                }

                                poseStack.pushPose();
                                applyRuleTransform(poseStack, entitypatch, poses, rule, livingEntity);

                                skinnedMesh.drawPosed(
                                        poseStack,
                                        buffers.getBuffer(EpicFightRenderTypes.getTriangulated(
                                                RenderType.entityCutoutNoCull(
                                                        humanoidRenderer.getModelTexture(finalStack, slotContext)
                                                ))),
                                        DrawingFunction.NEW_ENTITY,
                                        packedLight,
                                        1.0F, 1.0F, 1.0F, 1.0F,
                                        OverlayTexture.NO_OVERLAY,
                                        entitypatch.getArmature(),
                                        poses
                                );

                                poseStack.popPose();
                                renderedEpicFightModel.setTrue();
                            } else {
                                // 2) Если рендерер есть, но он не Humanoid — рисуем просто item-модель с нашими смещениями
                                poseStack.pushPose();
                                applyRuleTransform(poseStack, entitypatch, poses, rule, livingEntity);

                                Minecraft.getInstance().getItemRenderer().renderStatic(
                                        finalStack,
                                        ItemDisplayContext.FIXED,
                                        packedLight,
                                        OverlayTexture.NO_OVERLAY,
                                        poseStack,
                                        buffers,
                                        livingEntity.level(),
                                        0
                                );

                                poseStack.popPose();
                                renderedEpicFightModel.setTrue();
                            }

                            continue; // этот слот уже отрисован через CuriosRenderer
                        }

                        // 3) Вообще нет Curios-renderer (Curios Back Slot, ванильные вещи и т.п.) —
                        //    наш простой fallback через item-модель
                        SlotRule rule = SlotRule.forSlotAndStack(slotId, finalStack);

                        poseStack.pushPose();
                        applyRuleTransform(poseStack, entitypatch, poses, rule, livingEntity);

                        Minecraft.getInstance().getItemRenderer().renderStatic(
                                finalStack,
                                ItemDisplayContext.FIXED,
                                packedLight,
                                OverlayTexture.NO_OVERLAY,
                                poseStack,
                                buffers,
                                livingEntity.level(),
                                0
                        );

                        poseStack.popPose();
                        renderedEpicFightModel.setTrue();
                    }});
            });

            // Если ничего не отрисовали через EpicFight-слой — отдаём обратно ванильному Curios-слою
            if (!renderedEpicFightModel.booleanValue()) {
                poseStack.pushPose();
                var rootJ = entitypatch.getArmature().searchJointByName("Root");
                if (rootJ != null) {
                    OpenMatrix4f modelMatrix = poses[rootJ.getId()];
                    MathUtils.mulStack(poseStack, modelMatrix);
                }
                poseStack.translate(0.0F, 0.75F, 0.0F);
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                vanillaLayer.render(poseStack, buffers, packedLight, livingEntity,
                        livingEntity.walkAnimation.position(partialTicks),
                        livingEntity.walkAnimation.speed(partialTicks),
                        partialTicks, bob, yRot, xRot);
                poseStack.popPose();
            }
        }



        static void applyRuleTransform(
                PoseStack poseStack,
                LivingEntityPatch<LivingEntity> patch,
                OpenMatrix4f[] poses,
                SlotRule rule,
                LivingEntity livingEntity
        ) {
            var armature = patch.getArmature();
            var joint = armature.searchJointByName(rule.bone);
            if (joint == null) {
                joint = armature.searchJointByName("Root");
                if (joint == null) joint = armature.searchJointByName("Chest");
            }
            if (joint != null) {
                OpenMatrix4f boneMat = poses[joint.getId()];
                MathUtils.mulStack(poseStack, boneMat);
            }

            poseStack.translate(rule.offX, rule.offY, rule.offZ);

            if (rule.rotX != 0) poseStack.mulPose(Axis.XP.rotationDegrees(rule.rotX));
            if (rule.rotY != 0) poseStack.mulPose(Axis.YP.rotationDegrees(rule.rotY));
            if (rule.rotZ != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(rule.rotZ));

            if (livingEntity != null && livingEntity.isCrouching()
                    && "back_weapon".equals(rule.id)) {
                // при приседе оружие чуть опускается
                poseStack.translate(0.0F, -0.04F, -0.02F);
                poseStack.mulPose(Axis.XP.rotationDegrees(10.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(9.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(0.0F));
            }

            if (livingEntity != null && livingEntity.isCrouching()
                    && "book_on_hip".equals(rule.id)) {
                poseStack.translate(0.0F, 0.06F, -0.10F);
                poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(9.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(6.0F));
            }
            poseStack.scale(rule.scale, rule.scale, rule.scale);
        }
    }

    private static class SlotRule {
        final String id;
        final String bone;
        final float offX, offY, offZ;
        final float rotX, rotY, rotZ;
        final float scale;

        SlotRule(String id, String bone,
                 float offX, float offY, float offZ,
                 float rotX, float rotY, float rotZ,
                 float scale) {
            this.id = id;
            this.bone = bone;
            this.offX = offX; this.offY = offY; this.offZ = offZ;
            this.rotX = rotX; this.rotY = rotY; this.rotZ = rotZ;
            this.scale = scale;
        }

        private static final SlotRule BACK_TRIDENT_ON_BACK =
                new SlotRule("back_trident",
                        "Chest",
                        0.0F, 0.10F, 0.20F,
                        0F, 0F, 90F,
                        1.0F);

        private static final SlotRule BACK_SHIELD_ON_BACK =
                new SlotRule("back_shield",
                        "Chest",
                        0.12F, 0.04F, 0.08F,
                        0F, 180F, 0F,
                        1.0F);

        private static final SlotRule BACK_L2_BACKPACK_ON_BACK =
                new SlotRule("l2_backpack",
                        "Chest",
                        0.0F, 0.05F, 0.1F,
                        0F, 180F, 0F,
                        0.75F);

        private static final SlotRule BACK_BACKPACKEDBACKPACK_ON_BACK =
                new SlotRule("l2_backpack",
                        "Chest",
                        0.0F, 0.05F, 0.1F,
                        0F, 180F, 0F,
                        0.75F);

        private static final SlotRule BACK_CHEST_ON_BACK =
                new SlotRule("back_chest",
                        "Chest",
                        0.0F, 0.15F, 0.20F,
                        0F, 180F, 0F,
                        1.0F);

        private static final SlotRule BOOK_ON_HIP =
                new SlotRule("book_on_hip", "Hips",
                        0.30F, 0.14F, 0.0F,
                        0.0F, 0.0F, 0.0F,
                        0.50F);

        private static final SlotRule BACK_WEAPON_ON_BACK =
                new SlotRule("back_weapon",
                        "Chest",
                        0.0F, 0.08F, 0.14F,
                        0F, 180F, 180F,
                        0.75F);

        private static final SlotRule RELICS_BACK =
                new SlotRule("relics_back",
                        "Chest",
                        0.0F, 0.14F, 0.10F,
                        0F, 180F, 0F,
                        0.9F);

        private static final SlotRule RELICS_BELT =
                new SlotRule("relics_belt",
                        "Chest",
                        0.0F, 0.02F, 0.10F,
                        0F, 180F, 0F,
                        0.9F);

        private static final SlotRule RELICS_NECKLACE =
                new SlotRule("relics_necklace",
                        "Chest",
                        0.0F, 0.12F, -0.02F,  // чуть перед грудью
                        0F, 180F, 0F,
                        0.75F);

        private static final SlotRule RELICS_RING =
                new SlotRule("relics_ring",
                        "Chest",
                        0.15F, 0.05F, 0.02F,
                        0F, 180F, 0F,
                        0.6F);

        private static final SlotRule RELICS_CHARM =
                new SlotRule("relics_charm",
                        "Chest",
                        0.10F, 0.08F, 0.06F,
                        0F, 180F, 0F,
                        0.75F);

        static SlotRule forSlotAndStack(String slotId, ItemStack stack) {
            if (isBookLike(stack)) {
                return BOOK_ON_HIP;
            }

            if (isBackpackedBackpack(stack)) {
                return BACK_BACKPACKEDBACKPACK_ON_BACK;
            }

            if (isL2Backpack(stack)) {
                return BACK_L2_BACKPACK_ON_BACK;
            }

            if (isRelicsItem(stack)) {
                // slotId может прийти с namespace (типа "relics:necklace"), на всякий случай нормализуем
                String id = slotId;
                int colon = id.indexOf(':');
                if (colon >= 0) {
                    id = id.substring(colon + 1);
                }

                switch (id) {
                    case "back":
                        return RELICS_BACK;
                    case "belt":
                        return RELICS_BELT;
                    case "necklace":
                        return RELICS_NECKLACE;
                    case "ring":
                        return RELICS_RING;
                    case "charm":
                        return RELICS_CHARM;
                    default:
                        break;
                }
            }

            if ("back_weapon".equals(slotId)) {
                if (isTrident(stack)) {
                    return BACK_TRIDENT_ON_BACK;
                }
                if (isShield(stack)) {
                    return BACK_SHIELD_ON_BACK;
                }
                if (isChestLike(stack)) {
                    return BACK_CHEST_ON_BACK;
                }
                return BACK_WEAPON_ON_BACK;
            }

            return forId(slotId);
        }

        private static boolean isL2Backpack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;

            Item item = stack.getItem();
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null) return false;

            // Целимся строго в мод l2backpack
            if (!"l2backpack".equals(key.getNamespace())) return false;

            String path = key.getPath();
            return path.equals("backpack") || path.startsWith("backpack_");
        }

        private static boolean isBackpackedBackpack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;

            Item item = stack.getItem();
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null) return false;

            // Мод backpacked – почти все его предметы связаны с рюкзаками
            if (!"backpacked".equals(key.getNamespace())) return false;

            String path = key.getPath();
            // основная логика мода – предметы с "backpack" в имени
            return path.contains("backpack");
        }

        private static boolean isRelicsItem(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null) return false;

            return "relics".equals(key.getNamespace());
        }

        private static boolean isBookLike(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null) return false;

            String ns = key.getNamespace();
            String path = key.getPath();
            if (ns.equals("minecraft") && path.equals("book")) return true;
            if (ns.equals("epicfight") && path.equals("skillbook")) return true;
            if (ns.equals("irons_spellbooks") && path.contains("spell_book")) return true;
            return false;
        }

        private static boolean isTrident(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            Item item = stack.getItem();

            // ванильный трезубец
            if (item instanceof TridentItem) return true;
            if (stack.is(Items.TRIDENT)) return true;

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null && key.getPath().contains("trident")) return true;

            return false;
        }

        private static boolean isShield(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            Item item = stack.getItem();

            if (item instanceof ShieldItem) return true;
            if (stack.is(Items.SHIELD)) return true;

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null && key.getPath().contains("shield")) return true;

            return false;
        }

        private static boolean isChestLike(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            Item item = stack.getItem();

            // ванильные сундуки/бочки как рюкзак
            if (stack.is(Items.CHEST) || stack.is(Items.TRAPPED_CHEST) || stack.is(Items.BARREL)) {
                return true;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null) return false;

            String path = key.getPath();
            // большинство "рюкзаков" и сундуков в модах так или иначе содержат эти слова
            if (path.contains("chest")) return true;
            if (path.contains("backpack")) return true;
            if (path.contains("rucksack")) return true;

            return false;
        }


        private static final Map<String, SlotRule> SLOT_RULES = Map.ofEntries(
                Map.entry("back",
                        new SlotRule("back", "Chest",
                                0.0F, 0.07F, 0.05F,
                                0F, 180F, 0F,
                                1.0F)),
                Map.entry("back_weapon", BACK_WEAPON_ON_BACK),
                Map.entry("belt",
                        new SlotRule("belt", "Hips",
                                0.15F, 0.10F, -0.12F,
                                0F, 90F, 90F,
                                0.75F)),
                Map.entry("waist",
                        new SlotRule("waist", "Hips",
                                0.15F, 0.10F, -0.12F,
                                0F, 90F, 90F,
                                0.75F)),
                Map.entry("necklace",
                        new SlotRule("necklace", "Chest",
                                0.0F, 0.55F, -0.05F,
                                0F, 180F, 0F,
                                1.0F)),
                Map.entry("charm",
                        new SlotRule("charm", "Chest",
                                0.0F, 0.50F, -0.05F,
                                0F, 180F, 0F,
                                1.0F)),
                Map.entry("head",
                        new SlotRule("head", "Head",
                                0.0F, 0.15F, 0.0F,
                                0F, 180F, 0F,
                                1.0F)),
                Map.entry("ring",
                        new SlotRule("ring", "RightArm",
                                0.05F, 0.25F, 0.0F,
                                0F, 0F, 0F,
                                1.0F)),
                Map.entry("bracelet",
                        new SlotRule("bracelet", "RightArm",
                                0.05F, 0.25F, 0.0F,
                                0F, 0F, 0F,
                                1.0F))
        );

        private static final SlotRule DEFAULT =
                new SlotRule("default", "Chest",
                        0.0F, 0.35F, 0.0F,
                        0F, 180F, 0F,
                        1.0F);

        static SlotRule forId(String id) {
            if (id == null) return DEFAULT;
            return SLOT_RULES.getOrDefault(id, DEFAULT);
        }
    }


    private static void removeCuriosBackSlotLayer(EntityRenderersEvent.AddLayers event) {
        // Если мода Curios Back Slot нет – ничего не делаем
        Class<?> backRendererClass;
        try {
            backRendererClass = Class.forName("com.kermitemperor.curiosbackslot.render.BackWeaponRenderer");
        } catch (ClassNotFoundException e) {
            return;
        }

        // Ищем поле-список слоёв LivingEntityRenderer по типу, а не по имени (обфускация)
        java.lang.reflect.Field layersField = null;
        for (java.lang.reflect.Field f : net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredFields()) {
            Class<?> type = f.getType();
            if (java.util.List.class.isAssignableFrom(type)
                    || "com.google.common.collect.ImmutableList".equals(type.getName())) {
                layersField = f;
                break;
            }
        }
        if (layersField == null) {
            return;
        }
        layersField.setAccessible(true);

        for (String skin : event.getSkins()) {
            Object rendererObj = event.getSkin(skin);
            if (!(rendererObj instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> livingRenderer)) {
                continue;
            }

            Object rawList;
            try {
                rawList = layersField.get(livingRenderer);
            } catch (IllegalAccessException e) {
                continue;
            }

            if (!(rawList instanceof java.util.List<?> layers)) {
                continue;
            }

            // удаляем все RenderLayer, которые являются BackWeaponRenderer
            boolean removed = layers.removeIf(layer -> backRendererClass.isAssignableFrom(layer.getClass()));

            if (removed) {
                System.out.println("[EpicFightCuriosCompat] Removed Curios Back Slot layer for skin: " + skin);
            }
        }
    }

}