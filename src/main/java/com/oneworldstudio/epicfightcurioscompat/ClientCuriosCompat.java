package com.oneworldstudio.epicfightcurioscompat;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.WritableBookItem;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;
import top.theillusivec4.curios.api.client.ICurioRenderer.HumanoidRender;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.client.render.CuriosLayer;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.Mesh.DrawingFunction;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.client.model.transformer.HumanoidModelBaker;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.renderer.EpicFightRenderTypes;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.ModelRenderLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@OnlyIn(Dist.CLIENT)
public class ClientCuriosCompat {
    private static final KeyMapping OPEN_EDITOR_KEY = new KeyMapping(
            "key.epicfight_curios_compat.open_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            "key.categories.epicfight_curios_compat"
    );
    private static Object createGogglesRenderer;
    private static Object curiousLanternRenderer;
    private static Object curiousMediumLanternRenderer;
    private static Object curiousLargeLanternRenderer;

    private static boolean backpackedReflectionReady;
    private static Class<?> backpackedLayerClass;
    private static Method backpackedGetModel;
    private static Method backpackedCanRenderWithElytra;
    private static Method backpackedCanShowGlint;
    private static Method backpackedTransformToBody;
    private static Method backpackedSetupAngles;
    private static Method backpackedGetTexture;
    private static Method backpackedRenderType;
    private static Method backpackedRenderToBuffer;
    private static Field playerModelBodyField;

    private static boolean l2BackpackReflectionReady;
    private static Class<?> l2BackpackModelItemClass;
    private static Class<?> l2ItemOnBackItemClass;
    private static Method l2BackpackModelTexture;
    private static Method l2ItemOnBackShouldRender;
    private static Constructor<?> l2BackpackModelConstructor;
    private static ModelLayerLocation l2BackpackModelLayer;
    private static HumanoidModel<LivingEntity> l2BackpackModel;

    private static ICurioRenderer getCreateGogglesRendererSafe() {
        try {
            if (createGogglesRenderer instanceof ICurioRenderer renderer) {
                return renderer;
            }

            Class<?> clazz = Class.forName("com.simibubi.create.compat.curios.GogglesCurioRenderer");
            Object layer = clazz.getField("LAYER").get(null);
            ModelPart baked = Minecraft.getInstance()
                    .getEntityModels()
                    .bakeLayer((ModelLayerLocation) layer);
            Constructor<?> constructor = clazz.getConstructor(ModelPart.class);
            Object instance = constructor.newInstance(baked);
            createGogglesRenderer = instance;
            return (ICurioRenderer) instance;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ICurioRenderer getCuriousLanternRendererSafe(ItemStack stack) {
        try {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            String path = key != null ? key.getPath() : "";

            if (path.contains("large")) {
                if (curiousLargeLanternRenderer instanceof ICurioRenderer renderer) {
                    return renderer;
                }

                Class<?> clazz = Class.forName("com.psilocke.curiouslanterns.curios.LargeLanternRenderer");
                Object instance = clazz.getConstructor().newInstance();
                curiousLargeLanternRenderer = instance;
                return (ICurioRenderer) instance;
            }

            if (path.contains("medium")) {
                if (curiousMediumLanternRenderer instanceof ICurioRenderer renderer) {
                    return renderer;
                }

                Class<?> clazz = Class.forName("com.psilocke.curiouslanterns.curios.MediumLanternRenderer");
                Object instance = clazz.getConstructor().newInstance();
                curiousMediumLanternRenderer = instance;
                return (ICurioRenderer) instance;
            }

            if (curiousLanternRenderer instanceof ICurioRenderer renderer) {
                return renderer;
            }

            Class<?> clazz = Class.forName("com.psilocke.curiouslanterns.curios.LanternRenderer");
            Object instance = clazz.getConstructor().newInstance();
            curiousLanternRenderer = instance;
            return (ICurioRenderer) instance;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static LivingEntityRenderer<?, ?> getParentRenderer(LivingEntity livingEntity) {
        if (livingEntity instanceof AbstractClientPlayer player) {
            return (LivingEntityRenderer<?, ?>) Minecraft.getInstance()
                    .getEntityRenderDispatcher()
                    .getSkinMap()
                    .get(player.getModelName());
        }

        return (LivingEntityRenderer<?, ?>) Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getSkinMap()
                .get("default");
    }

    public static void register(IEventBus modBus) {
        modBus.<PatchedRenderersEvent.Modify>addListener(event -> {
            if (event.get(EntityType.PLAYER) instanceof PatchedLivingEntityRenderer patchedLivingRenderer) {
                patchedLivingRenderer.addPatchedLayerAlways(CuriosLayer.class, new PatchedCuriosLayerRenderer());
            }
        });
        modBus.<RegisterKeyMappingsEvent>addListener(event -> event.register(OPEN_EDITOR_KEY));
        MinecraftForge.EVENT_BUS.addListener(ClientCuriosCompat::onClientTick);

        modBus.<EntityRenderersEvent.AddLayers>addListener(event -> {
            PatchedCuriosLayerRenderer.CURIO_MESHES.values().forEach(SkinnedMesh::destroy);
            PatchedCuriosLayerRenderer.CURIO_MESHES.clear();
            PatchedCuriosLayerRenderer.RELICS_CURIO_MESHES.values().forEach(SkinnedMesh::destroy);
            PatchedCuriosLayerRenderer.RELICS_CURIO_MESHES.clear();
            PatchedCuriosLayerRenderer.PARTIAL_CURIO_MESHES.values().forEach(SkinnedMesh::destroy);
            PatchedCuriosLayerRenderer.PARTIAL_CURIO_MESHES.clear();
            l2BackpackModel = null;

            if (ModList.get().isLoaded("curiosbackslot")) {
                tryRemoveInjectedBackSlotLayer(event);
            }

            if (ModList.get().isLoaded("l2backpack")) {
                tryRemoveInjectedL2BackpackLayers(event);
            }

            if (ModList.get().isLoaded("supplementaries")) {
                tryRemoveInjectedSupplementariesLayers(event);
            }
        });
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        while (OPEN_EDITOR_KEY.consumeClick()) {
            if (minecraft.screen == null) {
                if (canOpenEditor(minecraft)) {
                    minecraft.setScreen(new CuriosPositionEditorScreen(minecraft.player));
                } else {
                    minecraft.player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.epicfight_curios_compat.editor_requires_permissions"),
                            true
                    );
                }
            }
        }
    }

    private static boolean canOpenEditor(Minecraft minecraft) {
        return minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    static boolean isEditableCurio(String slotId, ItemStack stack) {
        return hasEditableCurioRenderPath(slotId, stack);
    }

    static ItemStack getEditorCurioStack(ItemStack normalStack, ItemStack cosmeticStack) {
        if (cosmeticStack != null && !cosmeticStack.isEmpty()) {
            return cosmeticStack.copy();
        }
        if (normalStack != null && !normalStack.isEmpty()) {
            return normalStack.copy();
        }
        return ItemStack.EMPTY;
    }

    static boolean hasEditableCurioRenderPath(String slotId, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String normalizedSlotId = SlotRule.normalizeSlotId(slotId);
        if (SlotRule.isCreateGoggles(stack)) {
            return true;
        }
        if (SlotRule.isLanternLike(stack) && "belt".equals(normalizedSlotId)) {
            return true;
        }
        if (isBackpackedStack(stack) || isL2BackpackNamespaceItem(stack)) {
            return true;
        }
        if (SlotRule.isBookLike(stack)) {
            return true;
        }
        if (CuriosRendererRegistry.getRenderer(stack.getItem()).isPresent()) {
            return true;
        }

        return SlotRule.isSupportedFallback(normalizedSlotId, stack);
    }

    private static void tryRemoveInjectedBackSlotLayer(EntityRenderersEvent.AddLayers event) {
        final String targetLayerClassName = "com.kermitemperor.curiosbackslot.render.BackWeaponRenderer";

        for (String skin : event.getSkins()) {
            LivingEntityRenderer<?, ?> renderer = event.getSkin(skin);
            if (renderer == null) {
                continue;
            }

            try {
                Field layersField;

                try {
                    layersField = LivingEntityRenderer.class.getDeclaredField("layers");
                } catch (NoSuchFieldException missingLayersField) {
                    layersField = null;

                    for (Field field : LivingEntityRenderer.class.getDeclaredFields()) {
                        if (List.class.isAssignableFrom(field.getType())) {
                            layersField = field;
                            break;
                        }
                    }

                    if (layersField == null) {
                        return;
                    }
                }

                layersField.setAccessible(true);
                Object value = layersField.get(renderer);
                if (!(value instanceof List<?> layers)) {
                    continue;
                }

                boolean removed = layers.removeIf(layer ->
                        layer != null && targetLayerClassName.equals(layer.getClass().getName()));

                if (!removed && renderer.getClass().getSuperclass() != null) {
                    for (Field field : renderer.getClass().getSuperclass().getDeclaredFields()) {
                        if (!List.class.isAssignableFrom(field.getType())) {
                            continue;
                        }

                        field.setAccessible(true);
                        Object secondaryValue = field.get(renderer);
                        if (secondaryValue instanceof List<?> secondaryLayers) {
                            secondaryLayers.removeIf(layer ->
                                    layer != null && targetLayerClassName.equals(layer.getClass().getName()));
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void tryRemoveInjectedL2BackpackLayers(EntityRenderersEvent.AddLayers event) {
        try {
            Class<?> itemOnBackLayer = Class.forName("dev.xkmc.l2backpack.content.render.ItemOnBackLayerRenderer");
            Class<?> backpackLayer = Class.forName("dev.xkmc.l2backpack.content.render.BackpackLayerRenderer");
            removeLayerFromPlayerRenderer(event, itemOnBackLayer);
            removeLayerFromPlayerRenderer(event, backpackLayer);
        } catch (Throwable ignored) {
        }
    }

    private static void tryRemoveInjectedSupplementariesLayers(EntityRenderersEvent.AddLayers event) {
        try {
            Class<?> quiverLayer = Class.forName("net.mehvahdjukaar.supplementaries.client.renderers.entities.layers.QuiverLayer");
            removeLayerFromPlayerRenderer(event, quiverLayer);
        } catch (Throwable ignored) {
        }
    }

    private static void removeLayerFromPlayerRenderer(EntityRenderersEvent.AddLayers event, Class<?> layerClass) {
        try {
            LivingEntityRenderer<?, ?> defaultRenderer = (LivingEntityRenderer<?, ?>) Minecraft.getInstance()
                    .getEntityRenderDispatcher()
                    .getSkinMap()
                    .get("default");
            LivingEntityRenderer<?, ?> slimRenderer = (LivingEntityRenderer<?, ?>) Minecraft.getInstance()
                    .getEntityRenderDispatcher()
                    .getSkinMap()
                    .get("slim");

            if (defaultRenderer != null) {
                removeLayerFromRenderer(defaultRenderer, layerClass);
            }

            if (slimRenderer != null) {
                removeLayerFromRenderer(slimRenderer, layerClass);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void removeLayerFromRenderer(LivingEntityRenderer<?, ?> renderer, Class<?> layerClass) {
        try {
            for (String fieldName : new String[]{"layers", "f_115291_", "f_115290_"}) {
                try {
                    Field field = LivingEntityRenderer.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(renderer);
                    if (value instanceof List<?> list) {
                        list.removeIf(layer -> layer != null && layerClass.isAssignableFrom(layer.getClass()));
                        return;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }

            for (Field field : LivingEntityRenderer.class.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                field.setAccessible(true);
                Object value = field.get(renderer);
                if (value instanceof List<?> list) {
                    list.removeIf(layer -> layer != null && layerClass.isAssignableFrom(layer.getClass()));
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isBackpackedStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && "backpacked".equals(key.getNamespace()) && key.getPath().contains("backpack");
    }

    private static boolean isL2BackpackNamespaceItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && "l2backpack".equals(key.getNamespace());
    }

    private static void applyL2BackpackOnBackTransform(PoseStack poseStack, LivingEntity livingEntity) {
        poseStack.translate(0.0D, 1.0D, -0.25D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(1.0F, -1.0F, -1.0F);

        if (livingEntity.isCrouching()) {
            poseStack.translate(0.0D, 0.03125D, 0.0D);
            poseStack.scale(0.7F, 0.7F, 0.7F);
            poseStack.translate(0.0D, 1.0D, 0.0D);
        }
    }

    private static void renderItemInHeadContext(
            LivingEntity livingEntity,
            ItemStack stack,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getItemInHandRenderer()
                .renderItem(livingEntity, stack, ItemDisplayContext.HEAD, false, poseStack, buffers, packedLight);
    }

    private static boolean renderSupplementariesQuiver(
            String slotId,
            LivingEntity livingEntity,
            ItemStack stack,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        if (!SlotRule.isSupplementaries(stack)
                || (!"belt".equals(slotId) && !"waist".equals(slotId))) {
            return false;
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        if (livingEntity.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) {
            poseStack.scale(-1.0F, 1.0F, -1.0F);
        }
        poseStack.translate(0.0D, 0.4D, -0.1875D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-22.5F));
        renderItemInHeadContext(livingEntity, stack, poseStack, buffers, packedLight);
        return true;
    }

    private static void initL2BackpackReflection() {
        if (l2BackpackReflectionReady) {
            return;
        }

        l2BackpackReflectionReady = true;

        try {
            l2BackpackModelItemClass = Class.forName("dev.xkmc.l2backpack.content.common.BackpackModelItem");
            l2ItemOnBackItemClass = Class.forName("dev.xkmc.l2backpack.content.render.ItemOnBackItem");
            l2BackpackModelTexture = l2BackpackModelItemClass.getMethod("getModelTexture", ItemStack.class);
            l2ItemOnBackShouldRender = l2ItemOnBackItemClass.getMethod("shouldRender");

            Class<?> backpackLayerRendererClass = Class.forName("dev.xkmc.l2backpack.content.render.BackpackLayerRenderer");
            Object layer = backpackLayerRendererClass.getField("MLL").get(null);
            if (layer instanceof ModelLayerLocation modelLayerLocation) {
                l2BackpackModelLayer = modelLayerLocation;
            }

            Class<?> backpackModelClass = Class.forName("dev.xkmc.l2backpack.content.render.BackpackModel");
            l2BackpackModelConstructor = backpackModelClass.getConstructor(ModelPart.class);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static HumanoidModel<LivingEntity> getOrCreateL2BackpackModel() {
        initL2BackpackReflection();
        if (l2BackpackModel != null) {
            return l2BackpackModel;
        }
        if (l2BackpackModelConstructor == null || l2BackpackModelLayer == null) {
            return null;
        }

        try {
            Object model = l2BackpackModelConstructor.newInstance(
                    Minecraft.getInstance().getEntityModels().bakeLayer(l2BackpackModelLayer)
            );
            if (model instanceof HumanoidModel<?> humanoidModel) {
                l2BackpackModel = (HumanoidModel<LivingEntity>) humanoidModel;
            }
        } catch (Throwable ignored) {
        }

        return l2BackpackModel;
    }

    private static boolean renderL2BackpackModel(
            ItemStack stack,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        HumanoidModel<LivingEntity> backpackModel = getOrCreateL2BackpackModel();
        if (backpackModel == null || l2BackpackModelTexture == null) {
            return false;
        }

        try {
            ResourceLocation texture = (ResourceLocation) l2BackpackModelTexture.invoke(stack.getItem(), stack);
            if (texture == null) {
                return false;
            }

            VertexConsumer vertexConsumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
            poseStack.scale(0.6F, -0.6F, -0.6F);
            backpackModel.body.getChild("main_body").render(
                    poseStack,
                    vertexConsumer,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean renderL2ItemOnBack(
            LivingEntity livingEntity,
            ItemStack stack,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        try {
            if (l2ItemOnBackShouldRender != null
                    && !Boolean.TRUE.equals(l2ItemOnBackShouldRender.invoke(stack.getItem()))) {
                return true;
            }

            applyL2BackpackOnBackTransform(poseStack, livingEntity);
            renderItemInHeadContext(livingEntity, stack, poseStack, buffers, packedLight);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean renderL2BackpackCompat(
            LivingEntityPatch<LivingEntity> entityPatch,
            LivingEntity livingEntity,
            SlotContext slotContext,
            ItemStack stack,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            OpenMatrix4f[] poses
    ) {
        if (!isL2BackpackNamespaceItem(stack)) {
            return false;
        }

        initL2BackpackReflection();
        Item item = stack.getItem();

        if (l2BackpackModelItemClass != null && l2BackpackModelItemClass.isInstance(item)) {
            poseStack.pushPose();
            PatchedCuriosLayerRenderer.applyRuleTransform(
                    poseStack,
                    entityPatch,
                    poses,
                    SlotRule.BACK_L2_BACKPACK_ON_BACK,
                    livingEntity
            );
            PatchedCuriosLayerRenderer.applyCurioEditorTransform(poseStack, slotContext, stack);
            boolean rendered = renderL2BackpackModel(stack, poseStack, buffers, packedLight);
            poseStack.popPose();
            return rendered;
        }

        if (l2ItemOnBackItemClass != null && l2ItemOnBackItemClass.isInstance(item)) {
            poseStack.pushPose();
            PatchedCuriosLayerRenderer.applyRuleTransform(
                    poseStack,
                    entityPatch,
                    poses,
                    SlotRule.BACK_L2_BACKPACK_ON_BACK,
                    livingEntity
            );
            PatchedCuriosLayerRenderer.applyCurioEditorTransform(poseStack, slotContext, stack);
            boolean rendered = renderL2ItemOnBack(livingEntity, stack, poseStack, buffers, packedLight);
            poseStack.popPose();
            return rendered;
        }

        return false;
    }

    private static void initBackpackedReflection() {
        if (backpackedReflectionReady) {
            return;
        }

        backpackedReflectionReady = true;

        try {
            backpackedLayerClass = Class.forName("com.mrcrayfish.backpacked.client.renderer.entity.layers.BackpackLayer");
            backpackedGetModel = backpackedLayerClass.getMethod("getModel", String.class);
            backpackedCanRenderWithElytra = backpackedLayerClass.getMethod("canRenderWithElytra", ItemStack.class);
            backpackedCanShowGlint = backpackedLayerClass.getMethod("canShowEnchantmentGlint", ItemStack.class);

            Class<?> backpackModelClass = Class.forName("com.mrcrayfish.backpacked.client.model.backpack.BackpackModel");

            for (Method method : backpackModelClass.getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2
                        && "net.minecraft.client.model.geom.ModelPart".equals(params[0].getName())
                        && params[1] == boolean.class
                        && method.getName().toLowerCase().contains("transform")) {
                    backpackedTransformToBody = method;
                    break;
                }
            }

            if (backpackedTransformToBody == null) {
                for (Method method : backpackModelClass.getMethods()) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 2
                            && "net.minecraft.client.model.geom.ModelPart".equals(params[0].getName())
                            && params[1] == boolean.class) {
                        backpackedTransformToBody = method;
                        break;
                    }
                }
            }

            for (Method method : backpackModelClass.getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 3
                        && Player.class.isAssignableFrom(params[0])
                        && params[1] == int.class
                        && params[2] == float.class
                        && method.getName().toLowerCase().contains("setup")) {
                    backpackedSetupAngles = method;
                    break;
                }
            }

            if (backpackedSetupAngles == null) {
                for (Method method : backpackModelClass.getMethods()) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 3
                            && Player.class.isAssignableFrom(params[0])
                            && params[1] == int.class
                            && params[2] == float.class) {
                        backpackedSetupAngles = method;
                        break;
                    }
                }
            }

            backpackedGetTexture = backpackModelClass.getMethod("getTextureLocation");

            for (Method method : backpackModelClass.getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1
                        && params[0] == ResourceLocation.class
                        && RenderType.class.getName().equals(method.getReturnType().getName())) {
                    backpackedRenderType = method;
                    break;
                }
            }

            for (Method method : backpackModelClass.getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 8
                        && params[0] == PoseStack.class
                        && VertexConsumer.class.getName().equals(params[1].getName())
                        && params[2] == int.class
                        && params[3] == int.class
                        && params[4] == float.class
                        && params[5] == float.class
                        && params[6] == float.class
                        && params[7] == float.class) {
                    backpackedRenderToBuffer = method;
                    break;
                }
            }

            try {
                playerModelBodyField = PlayerModel.class.getDeclaredField("body");
            } catch (Throwable primaryFailure) {
                try {
                    playerModelBodyField = PlayerModel.class.getDeclaredField("f_102810_");
                } catch (Throwable fallbackFailure) {
                    for (Field field : PlayerModel.class.getDeclaredFields()) {
                        if (ModelPart.class.getName().equals(field.getType().getName())) {
                            playerModelBodyField = field;
                            break;
                        }
                    }
                }
            }

            if (playerModelBodyField != null) {
                playerModelBodyField.setAccessible(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean renderBackpackedBackpackModel(
            Player player,
            ItemStack stack,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            float partialTicks
    ) {
        initBackpackedReflection();
        if (backpackedGetModel == null || backpackedRenderToBuffer == null) {
            return false;
        }

        try {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            boolean hasChestItem = !chest.isEmpty();
            if (chest.is(Items.ELYTRA)) {
                boolean canRenderWithElytra = backpackedCanRenderWithElytra == null
                        || Boolean.TRUE.equals(backpackedCanRenderWithElytra.invoke(null, stack));
                if (!canRenderWithElytra) {
                    return true;
                }
            }

            String modelId = stack.getOrCreateTag().getString("BackpackModel");
            Object supplierObject = backpackedGetModel.invoke(null, modelId);
            if (!(supplierObject instanceof Supplier<?> supplier)) {
                return false;
            }

            Object backpackModel = supplier.get();
            if (backpackModel == null) {
                return false;
            }

            LivingEntityRenderer<?, ?> parentRenderer = getParentRenderer(player);
            if (parentRenderer != null && parentRenderer.getModel() instanceof PlayerModel<?> playerModel) {
                if (playerModelBodyField != null && backpackedTransformToBody != null) {
                    Object bodyPart = playerModelBodyField.get(playerModel);
                    backpackedTransformToBody.invoke(backpackModel, bodyPart, hasChestItem);
                }
            }

            if (backpackedSetupAngles != null) {
                backpackedSetupAngles.invoke(backpackModel, player, player.tickCount, partialTicks);
            }

            ResourceLocation texture = (ResourceLocation) backpackedGetTexture.invoke(backpackModel);
            RenderType renderType = backpackedRenderType != null
                    ? (RenderType) backpackedRenderType.invoke(backpackModel, texture)
                    : RenderType.entityCutoutNoCull(texture);
            boolean glint = stack.hasFoil()
                    && (backpackedCanShowGlint == null
                    || Boolean.TRUE.equals(backpackedCanShowGlint.invoke(null, stack)));
            VertexConsumer vertexConsumer =
                    ItemRenderer.getArmorFoilBuffer(buffers, renderType, false, glint);

            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.scale(1.05F, -1.05F, -1.05F);
            poseStack.translate(0.0D, -0.06D, (hasChestItem ? 3 : 2) * 0.0625D);
            backpackedRenderToBuffer.invoke(
                    backpackModel,
                    poseStack,
                    vertexConsumer,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );
            poseStack.popPose();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void removeCuriosBackSlotLayer(EntityRenderersEvent.AddLayers event) {
        Class<?> backRendererClass;
        try {
            backRendererClass = Class.forName("com.kermitemperor.curiosbackslot.render.BackWeaponRenderer");
        } catch (ClassNotFoundException ignored) {
            return;
        }

        Field layersField = null;
        for (Field field : LivingEntityRenderer.class.getDeclaredFields()) {
            Class<?> type = field.getType();
            if (List.class.isAssignableFrom(type)
                    || "com.google.common.collect.ImmutableList".equals(type.getName())) {
                layersField = field;
                break;
            }
        }

        if (layersField == null) {
            return;
        }

        layersField.setAccessible(true);

        for (String skin : event.getSkins()) {
            Object rendererObject = event.getSkin(skin);
            if (!(rendererObject instanceof LivingEntityRenderer<?, ?> livingRenderer)) {
                continue;
            }

            Object rawList;
            try {
                rawList = layersField.get(livingRenderer);
            } catch (IllegalAccessException ignored) {
                continue;
            }

            if (!(rawList instanceof List<?> layers)) {
                continue;
            }

            boolean removed = layers.removeIf(layer -> backRendererClass.isAssignableFrom(layer.getClass()));
            if (removed) {
                System.out.println("[EpicFightCuriosCompat] Removed Curios Back Slot layer for skin: " + skin);
            }
        }
    }

    private static void removeVanillaCuriosLayer(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            Object rendererObject = event.getSkin(skin);
            if (!(rendererObject instanceof LivingEntityRenderer<?, ?> livingRenderer)) {
                continue;
            }

            boolean anyRemoved = false;

            for (Class<?> type = livingRenderer.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!List.class.isAssignableFrom(field.getType())) {
                        continue;
                    }

                    field.setAccessible(true);

                    try {
                        Object raw = field.get(livingRenderer);
                        if (!(raw instanceof List<?> list)) {
                            continue;
                        }

                        boolean hasCurios = false;
                        for (Object layer : list) {
                            if (layer == null) {
                                continue;
                            }

                            if (layer instanceof CuriosLayer
                                    || "top.theillusivec4.curios.client.render.CuriosLayer".equals(layer.getClass().getName())) {
                                hasCurios = true;
                                break;
                            }
                        }

                        if (!hasCurios) {
                            continue;
                        }

                        try {
                            boolean removed = list.removeIf(layer -> {
                                if (layer == null) {
                                    return false;
                                }

                                return layer instanceof CuriosLayer
                                        || "top.theillusivec4.curios.client.render.CuriosLayer".equals(layer.getClass().getName());
                            });
                            anyRemoved |= removed;
                        } catch (UnsupportedOperationException immutableList) {
                            ArrayList<Object> copy = new ArrayList<>(list.size());
                            for (Object layer : list) {
                                if (layer == null) {
                                    copy.add(null);
                                    continue;
                                }

                                if (!(layer instanceof CuriosLayer)
                                        && !"top.theillusivec4.curios.client.render.CuriosLayer".equals(layer.getClass().getName())) {
                                    copy.add(layer);
                                }
                            }

                            field.set(livingRenderer, copy);
                            anyRemoved = true;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

            if (anyRemoved) {
                System.out.println("[EpicFightCuriosCompat] Removed vanilla CuriosLayer for skin: " + skin);
            }
        }
    }

    public static class PatchedCuriosLayerRenderer extends ModelRenderLayer<
            LivingEntity,
            LivingEntityPatch<LivingEntity>,
            EntityModel<LivingEntity>,
            CuriosLayer<LivingEntity, EntityModel<LivingEntity>>,
            SkinnedMesh> {
        private static final Map<HumanoidModel<LivingEntity>, SkinnedMesh> CURIO_MESHES = Maps.newHashMap();
        private static final Map<String, SkinnedMesh> RELICS_CURIO_MESHES = Maps.newHashMap();
        private static final Map<String, SkinnedMesh> PARTIAL_CURIO_MESHES = Maps.newHashMap();
        // Fine-tuning values for Relics boots. X is left/right, Z is front/back in player space.
        private static final double RELICS_LEFT_FOOT_X_OFFSET = 0.0D;
        private static final double RELICS_LEFT_FOOT_Z_OFFSET = -0.050D;
        private static final double RELICS_RIGHT_FOOT_X_OFFSET = 0.26875D;
        private static final double RELICS_RIGHT_FOOT_Z_OFFSET = -0.050D;
        private static final double RELICS_RIGHT_FOOT_IDLE_Z_OFFSET = 0.125D;
        private static final float RELICS_FEET_SCALE = 0.9F;

        public PatchedCuriosLayerRenderer() {
            super(null);
        }

        @Override
        protected void renderLayer(
                LivingEntityPatch<LivingEntity> entityPatch,
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
            CuriosApi.getCuriosInventory(livingEntity).ifPresent(handler ->
                    handler.getCurios().forEach((slotId, stacksHandler) -> {
                        IDynamicStackHandler stackHandler = stacksHandler.getStacks();
                        IDynamicStackHandler cosmeticStacksHandler = stacksHandler.getCosmeticStacks();

                        for (int i = 0; i < stackHandler.getSlots(); i++) {
                            ItemStack stack = cosmeticStacksHandler.getStackInSlot(i);
                            boolean cosmetic = true;
                            NonNullList<Boolean> renderStates = stacksHandler.getRenders();
                            boolean renderable = renderStates.size() > i && renderStates.get(i);
                            String normalizedSlotId = SlotRule.normalizeSlotId(slotId);

                            if (stack.isEmpty() && renderable) {
                                stack = stackHandler.getStackInSlot(i);
                                cosmetic = false;
                            }

                            if (stack.isEmpty()) {
                                continue;
                            }

                            SlotContext slotContext = new SlotContext(slotId, livingEntity, i, cosmetic, renderable);

                            if (SlotRule.isCreateGoggles(stack)) {
                                ICurioRenderer renderer = getCreateGogglesRendererSafe();
                                if (renderer != null) {
                                    SlotRule rule = SlotRule.CREATE_GOGGLES;
                                    poseStack.pushPose();
                                    applyRuleTransform(poseStack, entityPatch, poses, rule, livingEntity);
                                    applyCurioEditorTransform(poseStack, slotContext, stack);

                                    LivingEntityRenderer<?, ?> parentRenderer = getParentRenderer(livingEntity);
                                    float bodyYaw = Mth.rotLerp(partialTicks, livingEntity.yBodyRotO, livingEntity.yBodyRot);
                                    float headYaw = Mth.rotLerp(partialTicks, livingEntity.yHeadRotO, livingEntity.getYHeadRot());
                                    float netHeadYaw = headYaw - bodyYaw;
                                    float headPitch = Mth.lerp(partialTicks, livingEntity.xRotO, livingEntity.getXRot());

                                    renderer.render(
                                            stack,
                                            slotContext,
                                            poseStack,
                                            parentRenderer,
                                            buffers,
                                            packedLight,
                                            livingEntity.walkAnimation.position(partialTicks),
                                            livingEntity.walkAnimation.speed(partialTicks),
                                            partialTicks,
                                            livingEntity.tickCount + partialTicks,
                                            netHeadYaw,
                                            headPitch
                                    );
                                    poseStack.popPose();
                                    continue;
                                }
                            }

                            if (SlotRule.isLanternLike(stack) && "belt".equals(normalizedSlotId)) {
                                ICurioRenderer lanternRenderer = getCuriousLanternRendererSafe(stack);
                                if (lanternRenderer != null) {
                                    SlotRule rule = SlotRule.BELT_LANTERN;
                                    poseStack.pushPose();
                                    applyRuleTransform(poseStack, entityPatch, poses, rule, livingEntity);
                                    applyCurioEditorTransform(poseStack, slotContext, stack);

                                    LivingEntityRenderer<?, ?> parentRenderer = getParentRenderer(livingEntity);
                                    float bodyYaw = Mth.rotLerp(partialTicks, livingEntity.yBodyRotO, livingEntity.yBodyRot);
                                    float headYaw = Mth.rotLerp(partialTicks, livingEntity.yHeadRotO, livingEntity.getYHeadRot());
                                    float netHeadYaw = headYaw - bodyYaw;
                                    float headPitch = Mth.lerp(partialTicks, livingEntity.xRotO, livingEntity.getXRot());

                                    lanternRenderer.render(
                                            stack,
                                            slotContext,
                                            poseStack,
                                            parentRenderer,
                                            buffers,
                                            packedLight,
                                            livingEntity.walkAnimation.position(partialTicks),
                                            livingEntity.walkAnimation.speed(partialTicks),
                                            partialTicks,
                                            livingEntity.tickCount + partialTicks,
                                            netHeadYaw,
                                            headPitch
                                    );
                                    poseStack.popPose();
                                    continue;
                                }
                            }

                            if (isBackpackedStack(stack) && livingEntity instanceof Player player) {
                                poseStack.pushPose();
                                applyRuleTransform(poseStack, entityPatch, poses, SlotRule.BACKPACKED_MODEL, livingEntity);
                                applyCurioEditorTransform(poseStack, slotContext, stack);
                                if (renderBackpackedBackpackModel(player, stack, poseStack, buffers, packedLight, partialTicks)) {
                                    poseStack.popPose();
                                    continue;
                                }
                                poseStack.popPose();
                            }

                            if (renderL2BackpackCompat(entityPatch, livingEntity, slotContext, stack, poseStack, buffers, packedLight, poses)) {
                                continue;
                            } else if (SlotRule.isBookLike(stack)) {
                                SlotRule rule = SlotRule.forSlotAndStack(normalizedSlotId, stack);
                                poseStack.pushPose();
                                applyRuleTransform(poseStack, entityPatch, poses, rule, livingEntity);
                                applyCurioEditorTransform(poseStack, slotContext, stack);
                                Minecraft.getInstance()
                                        .getEntityRenderDispatcher()
                                        .getItemInHandRenderer()
                                        .renderItem(livingEntity, stack, ItemDisplayContext.FIXED, false, poseStack, buffers, packedLight);
                                poseStack.popPose();
                            } else {
                                Optional<ICurioRenderer> optionalRenderer = CuriosRendererRegistry.getRenderer(stack.getItem());
                                if (optionalRenderer.isPresent()) {
                                    ICurioRenderer curioRenderer = optionalRenderer.get();
                                    if (renderRelicsFeetHumanoidCurio(
                                            slotId,
                                            slotContext,
                                            curioRenderer,
                                            entityPatch,
                                            livingEntity,
                                            stack,
                                            poseStack,
                                            buffers,
                                            packedLight,
                                            poses,
                                            partialTicks
                                    )) {
                                        continue;
                                    }
                                    if (renderBodyPartHumanoidCurio(
                                            slotId,
                                            slotContext,
                                            curioRenderer,
                                            entityPatch,
                                            livingEntity,
                                            stack,
                                            poseStack,
                                            buffers,
                                            packedLight,
                                            poses
                                    )) {
                                        continue;
                                    }
                                    SlotRule rule = SlotRule.forSlotAndStack(normalizedSlotId, stack, true);

                                    if (curioRenderer instanceof HumanoidRender humanoidRenderer) {
                                        HumanoidModel<LivingEntity> curioModel = humanoidRenderer.getModel(stack, slotContext);
                                        SkinnedMesh skinnedMesh;

                                        if (!ClientEngine.getInstance().isVanillaModelDebuggingMode()
                                                && CURIO_MESHES.containsKey(curioModel)) {
                                            skinnedMesh = CURIO_MESHES.get(curioModel);
                                        } else {
                                            poseStack.pushPose();
                                            poseStack.translate(10000.0D, 0.0D, 0.0D);
                                            LivingEntityRenderer<?, ?> parentRenderer = getParentRenderer(livingEntity);
                                            curioRenderer.render(
                                                    stack,
                                                    slotContext,
                                                    poseStack,
                                                    parentRenderer,
                                                    buffers,
                                                    0,
                                                    0.0F,
                                                    0.0F,
                                                    0.0F,
                                                    0.0F,
                                                    0.0F,
                                                    0.0F
                                            );
                                            poseStack.popPose();

                                            skinnedMesh = HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(curioModel);
                                            CURIO_MESHES.put(curioModel, skinnedMesh);
                                        }

                                        poseStack.pushPose();
                                        applyRuleTransform(poseStack, entityPatch, poses, rule, livingEntity);
                                        applyCurioEditorTransform(poseStack, slotContext, stack);
                                        skinnedMesh.drawPosed(
                                                poseStack,
                                                buffers.getBuffer(EpicFightRenderTypes.getTriangulated(
                                                        RenderType.entityCutoutNoCull(humanoidRenderer.getModelTexture(stack, slotContext))
                                                )),
                                                DrawingFunction.NEW_ENTITY,
                                                packedLight,
                                                1.0F,
                                                1.0F,
                                                1.0F,
                                                1.0F,
                                                OverlayTexture.NO_OVERLAY,
                                                entityPatch.getArmature(),
                                                poses
                                        );
                                        poseStack.popPose();
                                    } else {
                                        poseStack.pushPose();
                                        applyRuleTransform(poseStack, entityPatch, poses, rule, livingEntity);
                                        applyCurioEditorTransform(poseStack, slotContext, stack);
                                        LivingEntityRenderer<?, ?> parentRenderer = getParentRenderer(livingEntity);
                                        curioRenderer.render(
                                                stack,
                                                slotContext,
                                                poseStack,
                                                parentRenderer,
                                                buffers,
                                                packedLight,
                                                livingEntity.walkAnimation.position(partialTicks),
                                                livingEntity.walkAnimation.speed(partialTicks),
                                                partialTicks,
                                                livingEntity.tickCount + partialTicks,
                                                yRot,
                                                xRot
                                        );
                                        poseStack.popPose();
                                    }
                                } else if (SlotRule.isSupportedFallback(normalizedSlotId, stack)) {
                                    SlotRule rule = SlotRule.forSlotAndStack(normalizedSlotId, stack, false);
                                    poseStack.pushPose();
                                    applyRuleTransform(poseStack, entityPatch, poses, rule, livingEntity);
                                    applyCurioEditorTransform(poseStack, slotContext, stack);
                                    if (!renderSupplementariesQuiver(slotId, livingEntity, stack, poseStack, buffers, packedLight)) {
                                        Minecraft.getInstance().getItemRenderer().renderStatic(
                                                stack,
                                                ItemDisplayContext.FIXED,
                                                packedLight,
                                                OverlayTexture.NO_OVERLAY,
                                                poseStack,
                                                buffers,
                                                livingEntity.level(),
                                                0
                                        );
                                    }
                                    poseStack.popPose();
                                }
                            }
                        }
                    }));
        }

        static void applyRuleTransform(
                PoseStack poseStack,
                LivingEntityPatch<LivingEntity> patch,
                OpenMatrix4f[] poses,
                SlotRule rule,
                LivingEntity livingEntity
        ) {
            Armature armature = patch.getArmature();
            Joint joint = armature.searchJointByName(rule.bone);
            boolean usedAliasBone = false;

            if (joint == null) {
                if ("Hips".equals(rule.bone)) {
                    joint = armature.searchJointByName("Pelvis");
                    if (joint == null) {
                        joint = armature.searchJointByName("Hip");
                    }
                    if (joint == null) {
                        joint = armature.searchJointByName("Body");
                    }
                    usedAliasBone = joint != null;
                } else if ("Chest".equals(rule.bone)) {
                    joint = armature.searchJointByName("Body");
                    if (joint == null) {
                        joint = armature.searchJointByName("Spine");
                    }
                    usedAliasBone = joint != null;
                } else if ("Head".equals(rule.bone)) {
                    joint = armature.searchJointByName("Neck");
                    usedAliasBone = joint != null;
                }
            }

            if (joint == null) {
                joint = armature.searchJointByName("Root");
                if (joint == null) {
                    joint = armature.searchJointByName("Chest");
                }
            }

            if (joint != null) {
                OpenMatrix4f boneMatrix = poses[joint.getId()];
                MathUtils.mulStack(poseStack, boneMatrix);

                if (usedAliasBone && "Hips".equals(rule.bone)) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
                }
            }

            poseStack.translate(rule.ox(livingEntity), rule.oy(livingEntity), rule.oz(livingEntity));

            float rotX = rule.rx(livingEntity);
            float rotY = rule.ry(livingEntity);
            float rotZ = rule.rz(livingEntity);

            if (rotX != 0.0F) {
                poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
            }
            if (rotY != 0.0F) {
                poseStack.mulPose(Axis.YP.rotationDegrees(rotY));
            }
            if (rotZ != 0.0F) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(rotZ));
            }

            float scale = rule.scale;
            if (!Float.isFinite(scale) || scale <= 0.0F) {
                scale = 1.0F;
            }
            if (scale != 1.0F) {
                poseStack.scale(scale, scale, scale);
            }
        }

        static void applyCurioEditorTransform(PoseStack poseStack, SlotContext slotContext, ItemStack stack) {
            applyCurioEditorTransform(poseStack, CuriosEditorConfig.getStandingOverride(slotContext, stack));
            if (slotContext != null && isEditorSittingPose(slotContext.entity())) {
                applyCurioEditorTransform(poseStack, CuriosEditorConfig.getSittingOverride(slotContext, stack));
            }
        }

        static void applyCurioEditorTransform(PoseStack poseStack, CuriosEditorConfig.TransformOverride override) {
            if (override == null) {
                return;
            }

            if (override.x != 0.0F || override.y != 0.0F || override.z != 0.0F) {
                poseStack.translate(override.x, override.y, override.z);
            }
            if (override.rotX != 0.0F) {
                poseStack.mulPose(Axis.XP.rotationDegrees(override.rotX));
            }
            if (override.rotY != 0.0F) {
                poseStack.mulPose(Axis.YP.rotationDegrees(override.rotY));
            }
            if (override.rotZ != 0.0F) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(override.rotZ));
            }
            if (override.scale != 1.0F) {
                poseStack.scale(override.scale, override.scale, override.scale);
            }
        }

        private static boolean isEditorSittingPose(LivingEntity entity) {
            return entity != null && (entity.isPassenger() || entity.isCrouching());
        }

        private static boolean renderRelicsFeetHumanoidCurio(
                String slotId,
                SlotContext slotContext,
                ICurioRenderer curioRenderer,
                LivingEntityPatch<LivingEntity> entityPatch,
                LivingEntity livingEntity,
                ItemStack stack,
                PoseStack poseStack,
                MultiBufferSource buffers,
                int packedLight,
                OpenMatrix4f[] poses,
                float partialTicks
        ) {
            if (!SlotRule.isRelicsFeetHumanoidCurio(slotId, stack, curioRenderer)) {
                return false;
            }

            SkinnedMesh skinnedMesh = getOrCreateRelicsCurioMesh(stack, slotContext.index());
            if (skinnedMesh == null) {
                return false;
            }

            ResourceLocation texture = getRelicsCurioTexture(stack);
            if (texture == null) {
                return false;
            }

            poseStack.pushPose();
            boolean rightSide = isRelicsRightFootSlot(slotContext.index());
            double xOffset = rightSide ? RELICS_RIGHT_FOOT_X_OFFSET : RELICS_LEFT_FOOT_X_OFFSET;
            double zOffset = rightSide ? RELICS_RIGHT_FOOT_Z_OFFSET : RELICS_LEFT_FOOT_Z_OFFSET;
            if (rightSide && isRelicsFeetStandingStill(livingEntity, partialTicks)) {
                zOffset += RELICS_RIGHT_FOOT_IDLE_Z_OFFSET;
            }

            poseStack.translate(xOffset, 0.0D, zOffset);
            applyCurioEditorTransform(poseStack, slotContext, stack);
            poseStack.scale(RELICS_FEET_SCALE, RELICS_FEET_SCALE, RELICS_FEET_SCALE);
            skinnedMesh.initialize();
            skinnedMesh.drawPosed(
                    poseStack,
                    buffers.getBuffer(EpicFightRenderTypes.getTriangulated(RenderType.entityCutoutNoCull(texture))),
                    DrawingFunction.NEW_ENTITY,
                    packedLight,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    OverlayTexture.NO_OVERLAY,
                    entityPatch.getArmature(),
                    poses
            );
            poseStack.popPose();
            return true;
        }

        private static boolean renderBodyPartHumanoidCurio(
                String slotId,
                SlotContext slotContext,
                ICurioRenderer curioRenderer,
                LivingEntityPatch<LivingEntity> entityPatch,
                LivingEntity livingEntity,
                ItemStack stack,
                PoseStack poseStack,
                MultiBufferSource buffers,
                int packedLight,
                OpenMatrix4f[] poses
        ) {
            HumanoidCurioBinding binding = HumanoidCurioBinding.fromSlotId(slotId);
            if (binding == null || !(curioRenderer instanceof HumanoidRender humanoidRenderer)) {
                return false;
            }

            SkinnedMesh skinnedMesh = getOrCreatePartialCurioMesh(stack, slotContext, curioRenderer, humanoidRenderer, binding);
            ResourceLocation texture = humanoidRenderer.getModelTexture(stack, slotContext);
            if (skinnedMesh == null || texture == null) {
                return false;
            }

            poseStack.pushPose();
            applyCurioEditorTransform(poseStack, slotContext, stack);
            skinnedMesh.initialize();
            skinnedMesh.drawPosed(
                    poseStack,
                    buffers.getBuffer(EpicFightRenderTypes.getTriangulated(RenderType.entityCutoutNoCull(texture))),
                    DrawingFunction.NEW_ENTITY,
                    packedLight,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    OverlayTexture.NO_OVERLAY,
                    entityPatch.getArmature(),
                    poses
            );
            poseStack.popPose();
            return true;
        }

        @SuppressWarnings("unchecked")
        private static SkinnedMesh getOrCreatePartialCurioMesh(
                ItemStack stack,
                SlotContext slotContext,
                ICurioRenderer curioRenderer,
                HumanoidRender humanoidRenderer,
                HumanoidCurioBinding binding
        ) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId == null) {
                return null;
            }

            String cacheKey = itemId + "#" + binding.cacheKey;
            SkinnedMesh cached = PARTIAL_CURIO_MESHES.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            HumanoidModel<LivingEntity> curioModel = humanoidRenderer.getModel(stack, slotContext);
            if (curioModel == null) {
                return null;
            }

            try {
                PoseStack offscreen = new PoseStack();
                offscreen.translate(10000.0D, 0.0D, 0.0D);
                MultiBufferSource.BufferSource scratch = Minecraft.getInstance().renderBuffers().bufferSource();
                curioRenderer.render(
                        stack,
                        slotContext,
                        offscreen,
                        getParentRenderer(slotContext.entity()),
                        scratch,
                        0,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F
                );
                scratch.endBatch();
            } catch (Throwable ignored) {
            }

            configurePartialHumanoidModel((HumanoidModel<LivingEntity>) curioModel, binding);
            SkinnedMesh mesh = HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel((HumanoidModel<LivingEntity>) curioModel);
            configurePartialHumanoidMesh(mesh, binding);
            PARTIAL_CURIO_MESHES.put(cacheKey, mesh);
            return mesh;
        }

        private static void configurePartialHumanoidModel(HumanoidModel<LivingEntity> model, HumanoidCurioBinding binding) {
            if (model == null) {
                return;
            }

            boolean headVisible = binding == HumanoidCurioBinding.HEAD;
            boolean armsVisible = binding == HumanoidCurioBinding.HANDS;

            model.head.visible = headVisible;
            model.hat.visible = headVisible;
            model.body.visible = false;
            model.leftArm.visible = armsVisible;
            model.rightArm.visible = armsVisible;
            model.leftLeg.visible = false;
            model.rightLeg.visible = false;

            if (model instanceof PlayerModel<?> playerModel) {
                playerModel.jacket.visible = false;
                playerModel.leftSleeve.visible = armsVisible;
                playerModel.rightSleeve.visible = armsVisible;
                playerModel.leftPants.visible = false;
                playerModel.rightPants.visible = false;
            }
        }

        private static void configurePartialHumanoidMesh(SkinnedMesh mesh, HumanoidCurioBinding binding) {
            if (mesh == null) {
                return;
            }

            for (Map.Entry<String, ? extends yesman.epicfight.api.client.model.MeshPart> entry : mesh.getPartEntry()) {
                String partName = entry.getKey().toLowerCase(Locale.ROOT);
                boolean keep;
                if (binding == HumanoidCurioBinding.HEAD) {
                    keep = partName.contains("head") || partName.contains("hat");
                } else {
                    keep = partName.contains("arm")
                            || partName.contains("hand")
                            || partName.contains("sleeve");
                }
                entry.getValue().setHidden(!keep);
            }
        }

        private static boolean isRelicsFeetStandingStill(LivingEntity livingEntity, float partialTicks) {
            return livingEntity.walkAnimation.speed(partialTicks) < 0.01F
                    && livingEntity.getDeltaMovement().horizontalDistanceSqr() < 1.0E-5D;
        }

        private enum HumanoidCurioBinding {
            HEAD("head"),
            HANDS("hands");

            private final String cacheKey;

            HumanoidCurioBinding(String cacheKey) {
                this.cacheKey = cacheKey;
            }

            private static HumanoidCurioBinding fromSlotId(String slotId) {
                String normalized = SlotRule.normalizeSlotId(slotId);
                if (SlotRule.isHeadLikeSlot(normalized)) {
                    return HEAD;
                }
                if (SlotRule.isHandsLikeSlot(normalized)) {
                    return HANDS;
                }
                return null;
            }
        }

        private static ResourceLocation getRelicsCurioTexture(ItemStack stack) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId == null) {
                return null;
            }

            return new ResourceLocation(itemId.getNamespace(), "textures/models/items/" + itemId.getPath() + ".png");
        }

        @SuppressWarnings("unchecked")
        private static SkinnedMesh getOrCreateRelicsCurioMesh(ItemStack stack, int slotIndex) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId == null) {
                return null;
            }

            String cacheKey = itemId + "#" + (slotIndex & 1);
            SkinnedMesh cached = RELICS_CURIO_MESHES.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            try {
                Method getModel = stack.getItem().getClass().getMethod("getModel", ItemStack.class);
                Object modelObject = getModel.invoke(stack.getItem(), stack);

                if (modelObject == null) {
                    return null;
                }

                try {
                    Method setSlot = modelObject.getClass().getMethod("setSlot", int.class);
                    setSlot.invoke(modelObject, slotIndex);
                } catch (NoSuchMethodException ignored) {
                }

                if (!(modelObject instanceof HumanoidModel<?> humanoidModel)) {
                    return null;
                }

                configureRelicsFeetModel((HumanoidModel<LivingEntity>) humanoidModel, slotIndex);
                SkinnedMesh mesh = HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(
                        (HumanoidModel<LivingEntity>) humanoidModel
                );
                configureRelicsFeetMesh(mesh, slotIndex);
                RELICS_CURIO_MESHES.put(cacheKey, mesh);
                return mesh;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static void configureRelicsFeetModel(HumanoidModel<LivingEntity> model, int slotIndex) {
            if (model == null) {
                return;
            }

            model.head.visible = false;
            model.hat.visible = false;
            model.body.visible = false;
            model.leftArm.visible = false;
            model.rightArm.visible = false;

            boolean rightSide = isRelicsRightFootSlot(slotIndex);
            model.leftLeg.visible = !rightSide;
            model.rightLeg.visible = rightSide;
        }

        private static void configureRelicsFeetMesh(SkinnedMesh mesh, int slotIndex) {
            if (mesh == null) {
                return;
            }

            String sideToken = isRelicsRightFootSlot(slotIndex) ? "right" : "left";
            for (Map.Entry<String, ? extends yesman.epicfight.api.client.model.MeshPart> entry : mesh.getPartEntry()) {
                String partName = entry.getKey().toLowerCase(Locale.ROOT);
                boolean keep = (partName.contains("leg") || partName.contains("feet") || partName.contains("foot"))
                        && partName.contains(sideToken);
                entry.getValue().setHidden(!keep);
            }
        }

        private static void setHidden(SkinnedMesh mesh, String partName, boolean hidden) {
            if (mesh.hasPart(partName)) {
                mesh.getPart(partName).setHidden(hidden);
            }
        }

        private static boolean isRelicsRightFootSlot(int slotIndex) {
            return (slotIndex & 1) == 0;
        }
    }

    public static final class SlotRule {
        final String id;
        final String bone;
        final float offX;
        final float offY;
        final float offZ;
        final float rotX;
        final float rotY;
        final float rotZ;
        final float scale;
        final float sitOffX;
        final float sitOffY;
        final float sitOffZ;
        final float sitRotX;
        final float sitRotY;
        final float sitRotZ;

        private static final SlotRule BACK_TRIDENT_ON_BACK =
                new SlotRule("back_trident", "Chest",
                        0.0F, 0.10F, 0.20F,
                        0.0F, 0.0F, 180.0F,
                        1.0F,
                        0.0F, 0.10F, 0.20F,
                        0.0F, 0.0F, 0.0F);
        private static final SlotRule BACK_SHIELD_ON_BACK =
                new SlotRule("back_shield", "Chest",
                        0.12F, 0.04F, 0.08F,
                        0.0F, 180.0F, 0.0F,
                        1.0F,
                        0.12F, 0.04F, 0.08F,
                        0.0F, 180.0F, 0.0F);
        public static final SlotRule BACK_L2_BACKPACK_ON_BACK =
                new SlotRule("l2_backpack", "Chest",
                        0.0F, 0.18F, 0.18F,
                        0.0F, 180.0F, 0.0F,
                        0.75F,
                        0.0F, 0.08F, 0.24F,
                        0.0F, 180.0F, 0.0F);
        private static final SlotRule BACK_RPG_BACKPACK_ON_BACK =
                new SlotRule("rpg_backpack", "Chest",
                        0.0F, 0.20F, -0.04F,
                        0.0F, 0.0F, 180.0F,
                        0.85F,
                        0.0F, 0.30F, 0.10F,
                        45.0F, 0.0F, 180.0F);
        private static final SlotRule BACK_TRAVELERS_BACKPACK_ON_BACK =
                new SlotRule("travelers_backpack", "Chest",
                        0.0F, 0.35F, -0.04F,
                        0.0F, 0.0F, 180.0F,
                        0.95F,
                        0.0F, 0.50F, 0.10F,
                        35.0F, 0.0F, 180.0F);
        private static final SlotRule BACK_SOPHISTICATED_BACKPACK_ON_BACK =
                new SlotRule("sophisticated_backpack", "Chest",
                        0.0F, 0.27F, -0.04F,
                        0.0F, 0.0F, 180.0F,
                        0.75F,
                        0.0F, 0.37F, 0.05F,
                        40.0F, 0.0F, 180.0F);
        private static final SlotRule BACK_SUPPLEMENTARIES_ON_BACK =
                new SlotRule("supplementaries", "Hips",
                        -0.20F, 0.46F, -0.06F,
                        45.0F, 0.0F, 180.0F,
                        1.0F,
                        -0.18F, 0.06F, -0.06F,
                        0.0F, 0.0F, 0.0F);
        public static final SlotRule BACKPACKED_MODEL =
                new SlotRule("backpacked_model", "Chest",
                        0.0F, 0.0F, 0.15F,
                        180.0F, 0.0F, 180.0F,
                        1.0F,
                        Float.NaN, Float.NaN, Float.NaN,
                        Float.NaN, Float.NaN, Float.NaN);
        private static final SlotRule BACK_CHEST_ON_BACK =
                new SlotRule("back_chest", "Chest",
                        0.0F, 0.15F, 0.20F,
                        0.0F, 180.0F, 0.0F,
                        1.0F,
                        0.0F, 0.15F, 0.20F,
                        0.0F, 180.0F, 0.0F);
        private static final SlotRule BOOK_ON_HIP =
                new SlotRule("book_on_hip", "Hips",
                        0.30F, 0.10F, 0.00F,
                        0.35F, 0.0F, 0.0F,
                        0.45F,
                        0.30F, 0.10F, 0.03F,
                        10.0F, 0.0F, 0.0F);
        public static final SlotRule CREATE_GOGGLES =
                new SlotRule("create_goggles", "Head",
                        0.0F, 0.0F, 0.0F,
                        0.0F, 0.0F, 180.0F,
                        1.0F);
        public static final SlotRule BELT_LANTERN =
                new SlotRule("belt_lantern", "Hips",
                        -0.05F, 0.60F, 0.00F,
                        0.0F, 0.0F, 180.0F,
                        0.90F,
                        -0.05F, 0.70F, -0.29F,
                        0.20F, 0.0F, 180.0F);
        private static final SlotRule BACK_WEAPON_ON_BACK =
                new SlotRule("back_weapon", "Chest",
                        0.0F, 0.08F, 0.14F,
                        0.0F, 180.0F, 180.0F,
                        0.75F,
                        0.0F, 0.08F, 0.14F,
                        0.0F, 180.0F, 180.0F);
        private static final SlotRule RELICS_BACK =
                new SlotRule("relics_back", "Chest",
                        0.0F, 0.36F, 0.00F,
                        0.0F, 0.0F, 180.0F,
                        1.10F,
                        0.0F, 0.50F, 0.20F,
                        40.0F, 0.0F, 180.0F);
        private static final SlotRule RELICS_BELT =
                new SlotRule("relics_belt", "Chest",
                        0.0F, 0.02F, 0.10F,
                        0.0F, 180.0F, 0.0F,
                        0.90F,
                        Float.NaN, Float.NaN, Float.NaN,
                        Float.NaN, Float.NaN, Float.NaN);
        private static final SlotRule RELICS_NECKLACE =
                new SlotRule("relics_necklace", "Chest",
                        0.0F, 0.12F, -0.02F,
                        0.0F, 180.0F, 0.0F,
                        0.75F,
                        Float.NaN, Float.NaN, Float.NaN,
                        Float.NaN, Float.NaN, Float.NaN);
        private static final SlotRule RELICS_RING =
                new SlotRule("relics_ring", "Chest",
                        0.15F, 0.05F, 0.02F,
                        0.0F, 180.0F, 0.0F,
                        0.60F,
                        Float.NaN, Float.NaN, Float.NaN,
                        Float.NaN, Float.NaN, Float.NaN);
        private static final SlotRule RELICS_CHARM =
                new SlotRule("relics_charm", "Chest",
                        0.10F, 0.08F, 0.06F,
                        0.0F, 180.0F, 0.0F,
                        0.75F,
                        Float.NaN, Float.NaN, Float.NaN,
                        Float.NaN, Float.NaN, Float.NaN);
        private static final SlotRule RELICS_HANDS =
                new SlotRule("relics_hands", "RightArm",
                        0.05F, 0.25F, 0.00F,
                        0.0F, 0.0F, 0.0F,
                        1.00F,
                        0.05F, 0.25F, 0.00F,
                        0.0F, 0.0F, 0.0F);
        private static final SlotRule RELICS_HANDS_MODEL =
                new SlotRule("relics_hands_model", "Root",
                        0.0F, 0.0F, 0.0F,
                        0.0F, 180.0F, 0.0F,
                        1.0F,
                        0.0F, 0.0F, 0.0F,
                        0.0F, 180.0F, 0.0F);
        private static final SlotRule RELICS_FEET =
                new SlotRule("relics_feet", "RightLeg",
                        0.0F, 0.60F, 0.0F,
                        0.0F, 180.0F, 0.0F,
                        1.0F,
                        0.0F, 0.60F, 0.02F,
                        0.0F, 180.0F, 0.0F);
        private static final SlotRule RELICS_FEET_MODEL =
                new SlotRule("relics_feet_model", "Root",
                        0.0F, 0.0F, 0.0F,
                        0.0F, 180.0F, 0.0F,
                        1.0F,
                        0.0F, 0.0F, 0.0F,
                        0.0F, 180.0F, 0.0F);
        private static final Map<String, String> SLOT_ALIASES = Map.ofEntries(
                Map.entry("waist", "belt"),
                Map.entry("gloves", "hands"),
                Map.entry("aether_gloves", "hands"),
                Map.entry("aether_pendant", "necklace"),
                Map.entry("aether_ring", "ring"),
                Map.entry("aether_cape", "back"),
                Map.entry("aether_accessory", "charm"),
                Map.entry("aether_shield", "back_weapon"),
                Map.entry("all", "curio")
        );

        private static final Map<String, SlotRule> SLOT_RULES = Map.ofEntries(
                Map.entry("back",
                        new SlotRule("back", "Chest",
                                0.0F, 0.27F, -0.04F,
                                0.0F, 0.0F, 180.0F,
                                0.55F,
                                0.0F, 0.40F, 0.00F,
                                30.0F, 0.0F, 180.0F)),
                Map.entry("back_weapon", BACK_WEAPON_ON_BACK),
                Map.entry("belt",
                        new SlotRule("belt", "Hips",
                                0.15F, 0.10F, -0.12F,
                                0.0F, 90.0F, 90.0F,
                                0.75F,
                                0.15F, 0.10F, -0.12F,
                                0.0F, 90.0F, 90.0F)),
                Map.entry("waist",
                        new SlotRule("waist", "Hips",
                                0.15F, 0.10F, -0.12F,
                                0.0F, 90.0F, 90.0F,
                                0.75F,
                                0.15F, 0.10F, -0.12F,
                                0.0F, 90.0F, 90.0F)),
                Map.entry("necklace",
                        new SlotRule("necklace", "Chest",
                                0.0F, 0.55F, -0.05F,
                                0.0F, 180.0F, 0.0F,
                                1.0F,
                                0.0F, 0.55F, -0.05F,
                                0.0F, 180.0F, 0.0F)),
                Map.entry("charm",
                        new SlotRule("charm", "Chest",
                                0.0F, 0.50F, -0.05F,
                                0.0F, 180.0F, 0.0F,
                                1.0F,
                                0.0F, 0.50F, -0.05F,
                                0.0F, 180.0F, 0.0F)),
                Map.entry("head",
                        new SlotRule("head", "Head",
                                0.0F, 0.15F, 0.0F,
                                0.0F, 180.0F, 0.0F,
                                1.0F,
                                0.0F, 0.15F, 0.0F,
                                0.0F, 180.0F, 0.0F)),
                Map.entry("hands",
                        new SlotRule("hands", "RightArm",
                                0.05F, 0.25F, 0.0F,
                                0.0F, 0.0F, 0.0F,
                                1.0F,
                                0.05F, 0.25F, 0.0F,
                                0.0F, 0.0F, 0.0F)),
                Map.entry("feet",
                        new SlotRule("feet", "RightLeg",
                                0.0F, 0.60F, 0.0F,
                                0.0F, 180.0F, 0.0F,
                                1.0F,
                                0.0F, 0.60F, 0.02F,
                                0.0F, 180.0F, 0.0F)),
                Map.entry("ring",
                        new SlotRule("ring", "RightArm",
                                0.05F, 0.25F, 0.0F,
                                0.0F, 0.0F, 0.0F,
                                1.0F,
                                0.05F, 0.25F, 0.0F,
                                0.0F, 0.0F, 0.0F)),
                Map.entry("bracelet",
                        new SlotRule("bracelet", "RightArm",
                                0.05F, 0.25F, 0.0F,
                                0.0F, 0.0F, 0.0F,
                                1.0F,
                                0.05F, 0.25F, 0.0F,
                                0.0F, 0.0F, 0.0F)),
                Map.entry("body",
                        new SlotRule("body", "Chest",
                                0.0F, 0.28F, -0.02F,
                                0.0F, 180.0F, 0.0F,
                                0.9F,
                                0.0F, 0.35F, 0.03F,
                                20.0F, 180.0F, 0.0F)),
                Map.entry("curio",
                        new SlotRule("curio", "Chest",
                                0.0F, 0.32F, 0.0F,
                                0.0F, 180.0F, 0.0F,
                                0.8F,
                                0.0F, 0.38F, 0.04F,
                                18.0F, 180.0F, 0.0F)),
                Map.entry("spellbook",
                        new SlotRule("spellbook", "Hips",
                                0.30F, 0.10F, 0.00F,
                                0.35F, 0.0F, 0.0F,
                                0.45F,
                                0.30F, 0.10F, 0.03F,
                                10.0F, 0.0F, 0.0F)),
                Map.entry("scroll",
                        new SlotRule("scroll", "Hips",
                                0.28F, 0.12F, 0.00F,
                                0.0F, 90.0F, 0.0F,
                                0.55F,
                                0.28F, 0.12F, 0.03F,
                                10.0F, 90.0F, 0.0F)),
                Map.entry("spellstone",
                        new SlotRule("spellstone", "Chest",
                                0.08F, 0.18F, 0.02F,
                                0.0F, 180.0F, 0.0F,
                                0.65F,
                                0.08F, 0.22F, 0.06F,
                                15.0F, 180.0F, 0.0F)),
                Map.entry("talent",
                        new SlotRule("talent", "Chest",
                                0.12F, 0.18F, 0.02F,
                                0.0F, 180.0F, 0.0F,
                                0.65F,
                                0.12F, 0.22F, 0.06F,
                                15.0F, 180.0F, 0.0F)),
                Map.entry("blasphemy",
                        new SlotRule("blasphemy", "Chest",
                                -0.10F, 0.18F, 0.02F,
                                0.0F, 180.0F, 0.0F,
                                0.65F,
                                -0.10F, 0.22F, 0.06F,
                                15.0F, 180.0F, 0.0F)),
                Map.entry("sacrament",
                        new SlotRule("sacrament", "Chest",
                                -0.05F, 0.20F, 0.02F,
                                0.0F, 180.0F, 0.0F,
                                0.7F,
                                -0.05F, 0.24F, 0.06F,
                                15.0F, 180.0F, 0.0F)),
                Map.entry("living_armour_socket",
                        new SlotRule("living_armour_socket", "Chest",
                                0.0F, 0.22F, 0.02F,
                                0.0F, 180.0F, 0.0F,
                                0.6F,
                                0.0F, 0.26F, 0.06F,
                                15.0F, 180.0F, 0.0F))
        );

        private static final SlotRule DEFAULT =
                new SlotRule("default", "Chest",
                        0.0F, 0.35F, 0.0F,
                        0.0F, 180.0F, 0.0F,
                        0.75F,
                        0.0F, 0.35F, 0.0F,
                        0.0F, 180.0F, 0.0F);

        SlotRule(
                String id,
                String bone,
                float offX,
                float offY,
                float offZ,
                float rotX,
                float rotY,
                float rotZ,
                float scale
        ) {
            this(id, bone, offX, offY, offZ, rotX, rotY, rotZ, scale, offX, offY, offZ, rotX, rotY, rotZ);
        }

        SlotRule(
                String id,
                String bone,
                float offX,
                float offY,
                float offZ,
                float rotX,
                float rotY,
                float rotZ,
                float scale,
                float sitOffX,
                float sitOffY,
                float sitOffZ,
                float sitRotX,
                float sitRotY,
                float sitRotZ
        ) {
            this.id = id;
            this.bone = bone;
            this.offX = offX;
            this.offY = offY;
            this.offZ = offZ;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
            this.scale = scale;
            this.sitOffX = sitOffX;
            this.sitOffY = sitOffY;
            this.sitOffZ = sitOffZ;
            this.sitRotX = sitRotX;
            this.sitRotY = sitRotY;
            this.sitRotZ = sitRotZ;
        }

        private static boolean isSitting(LivingEntity entity) {
            return entity != null && (entity.isCrouching() || entity.isPassenger());
        }

        float ox(LivingEntity entity) {
            return isSitting(entity) && !Float.isNaN(this.sitOffX) ? this.sitOffX : this.offX;
        }

        float oy(LivingEntity entity) {
            return isSitting(entity) && !Float.isNaN(this.sitOffY) ? this.sitOffY : this.offY;
        }

        float oz(LivingEntity entity) {
            return isSitting(entity) && !Float.isNaN(this.sitOffZ) ? this.sitOffZ : this.offZ;
        }

        float rx(LivingEntity entity) {
            return isSitting(entity) && !Float.isNaN(this.sitRotX) ? this.sitRotX : this.rotX;
        }

        float ry(LivingEntity entity) {
            return isSitting(entity) && !Float.isNaN(this.sitRotY) ? this.sitRotY : this.rotY;
        }

        float rz(LivingEntity entity) {
            return isSitting(entity) && !Float.isNaN(this.sitRotZ) ? this.sitRotZ : this.rotZ;
        }

        public static SlotRule forSlotAndStack(String slotId, ItemStack stack) {
            return forSlotAndStack(slotId, stack, false);
        }

        public static SlotRule forSlotAndStack(String slotId, ItemStack stack, boolean hasCustomRenderer) {
            if (isCreateGoggles(stack)) {
                return CREATE_GOGGLES;
            }

            if (isBookLike(stack)) {
                return BOOK_ON_HIP;
            }

            if (isLanternLike(stack) && ("belt".equals(slotId) || "waist".equals(slotId))) {
                return BELT_LANTERN;
            }

            if (isRpgBackpack(stack)) {
                return BACK_RPG_BACKPACK_ON_BACK;
            }

            if (isTravelersBackpack(stack)) {
                return BACK_TRAVELERS_BACKPACK_ON_BACK;
            }

            if (isSophisticatedBackpack(stack)) {
                return BACK_SOPHISTICATED_BACKPACK_ON_BACK;
            }

            if (isSupplementaries(stack) && "belt".equals(slotId)) {
                return BACK_SUPPLEMENTARIES_ON_BACK;
            }

            if (isBackpackedBackpack(stack)) {
                return BACKPACKED_MODEL;
            }

            if (isL2Backpack(stack)) {
                return BACK_L2_BACKPACK_ON_BACK;
            }

            if (isRelicsItem(stack)) {
                String normalizedId = normalizeSlotId(slotId);

                switch (normalizedId) {
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
                    case "hands":
                        return hasCustomRenderer ? RELICS_HANDS_MODEL : RELICS_HANDS;
                    case "feet":
                        return hasCustomRenderer ? RELICS_FEET_MODEL : RELICS_FEET;
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

        public static boolean isSupportedFallback(String slotId, ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            if (isBookLike(stack)) {
                return true;
            }
            if (isRpgBackpack(stack)) {
                return true;
            }
            if (isTravelersBackpack(stack)) {
                return true;
            }
            if (isSophisticatedBackpack(stack)) {
                return true;
            }
            if (isSupplementaries(stack)) {
                return true;
            }
            if (isBackpackedBackpack(stack)) {
                return true;
            }
            if (isL2Backpack(stack)) {
                return true;
            }
            if (isRelicsItem(stack)) {
                return true;
            }
            if (isCreateGoggles(stack)) {
                return false;
            }
            if ("back_weapon".equals(slotId)) {
                return true;
            }
            if (isLanternLike(stack) && ("belt".equals(slotId) || "waist".equals(slotId))) {
                return true;
            }

            return true;
        }

        static boolean isSupplementaries(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            Item item = stack.getItem();
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null || !"supplementaries".equals(key.getNamespace())) {
                return false;
            }

            String path = key.getPath();
            return path.equals("quiver") || path.startsWith("quiver_");
        }

        private static boolean isL2Backpack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null || !"l2backpack".equals(key.getNamespace())) {
                return false;
            }

            String path = key.getPath();
            return path.equals("backpack") || path.startsWith("backpack_");
        }

        static boolean isIronsSpellbook(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            Item item = stack.getItem();
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null || !"irons_spellbooks".equals(key.getNamespace())) {
                return false;
            }

            String path = key.getPath();
            if (path.contains("spellbook")
                    || path.contains("spell_book")
                    || path.contains("grimoire")
                    || path.contains("tome")
                    || path.contains("codex")
                    || path.contains("manual")) {
                return true;
            }

            String className = item.getClass().getName().toLowerCase();
            return className.contains("spellbook")
                    || className.contains("spell_book")
                    || className.contains("grimoire");
        }

        private static boolean isSophisticatedBackpack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null || !"sophisticatedbackpacks".equals(key.getNamespace())) {
                return false;
            }

            String path = key.getPath();
            return path.equals("backpack")
                    || path.equals("copper_backpack")
                    || path.equals("iron_backpack")
                    || path.equals("gold_backpack")
                    || path.equals("diamond_backpack")
                    || path.equals("netherite_backpack");
        }

        private static boolean isTravelersBackpack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null || !"travelersbackpack".equals(key.getNamespace())) {
                return false;
            }

            String path = key.getPath();
            if (path.contains("sleeping_bag")) {
                return false;
            }
            if (path.contains("upgrade")) {
                return false;
            }
            return !path.equals("backpack_tank");
        }

        private static boolean isRpgBackpack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null || !"rpg_backpacks".equals(key.getNamespace())) {
                return false;
            }

            String path = key.getPath();
            return path.endsWith("_backpack") || path.contains("backpack");
        }

        private static boolean isBackpackedBackpack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null || !"backpacked".equals(key.getNamespace())) {
                return false;
            }

            return key.getPath().contains("backpack");
        }

        private static boolean isRelicsItem(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return key != null && "relics".equals(key.getNamespace());
        }

        private static boolean isRelicsFeetHumanoidCurio(String slotId, ItemStack stack, ICurioRenderer renderer) {
            if (!isRelicsItem(stack)) {
                return false;
            }

            if (!"feet".equals(normalizeSlotId(slotId))) {
                return false;
            }

            return "it.hurts.sskirillss.relics.client.renderer.items.items.CurioRenderer"
                    .equals(renderer.getClass().getName());
        }

        private static boolean isDivineManuscriptSpellBook(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return key != null
                    && "dacxirons".equals(key.getNamespace())
                    && "divine_manuscript_spell_book".equals(key.getPath());
        }

        public static boolean isBookLike(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            Item item = stack.getItem();
            if (item instanceof EnchantedBookItem
                    || item instanceof WrittenBookItem
                    || item instanceof WritableBookItem
                    || stack.is(Items.BOOK)) {
                return true;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null) {
                return false;
            }

            String namespace = key.getNamespace();
            String path = key.getPath();

            if ("epicfight".equals(namespace) && "skillbook".equals(path)) {
                return true;
            }

            if (("irons_spellbooks".equals(namespace)
                    || "dacxirons".equals(namespace)
                    || "ice_and_fire_spellbooks".equals(namespace)
                    || "ias_spellbooks".equals(namespace)
                    || "traveloptics".equals(namespace)
                    || "darkdoppelganger".equals(namespace)
                    || "dungeons_and_combat".equals(namespace))
                    && (path.contains("spell_book")
                    || path.contains("spellbook")
                    || path.contains("book")
                    || path.contains("tome")
                    || path.contains("grimoire")
                    || path.contains("codex")
                    || path.contains("manual")
                    || path.contains("guide"))) {
                return true;
            }

            return path.contains("book")
                    || path.contains("tome")
                    || path.contains("codex")
                    || path.contains("manual")
                    || path.contains("guide")
                    || path.contains("journal")
                    || path.contains("lexicon")
                    || path.contains("grimoire")
                    || path.contains("compendium")
                    || path.contains("manuscript")
                    || path.contains("spellbook")
                    || path.contains("spell_book")
                    || path.contains("spell");
        }

        public static boolean isLanternLike(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return key != null && key.getPath().contains("lantern");
        }

        public static boolean isCreateGoggles(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return key != null
                    && "create".equals(key.getNamespace())
                    && "goggles".equals(key.getPath());
        }

        private static boolean isTrident(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            Item item = stack.getItem();
            if (item instanceof TridentItem || stack.is(Items.TRIDENT)) {
                return true;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            return key != null && key.getPath().contains("trident");
        }

        private static boolean isShield(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            Item item = stack.getItem();
            if (item instanceof ShieldItem || stack.is(Items.SHIELD)) {
                return true;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            return key != null && key.getPath().contains("shield");
        }

        private static boolean isChestLike(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            if (stack.is(Items.CHEST) || stack.is(Items.TRAPPED_CHEST) || stack.is(Items.BARREL)) {
                return true;
            }

            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key == null) {
                return false;
            }

            String path = key.getPath();
            return path.contains("chest")
                    || path.contains("backpack")
                    || path.contains("rucksack");
        }

        public static SlotRule forId(String id) {
            if (id == null) {
                return DEFAULT;
            }
            return SLOT_RULES.getOrDefault(normalizeSlotId(id), DEFAULT);
        }

        static boolean isHandsLikeSlot(String slotId) {
            String normalized = normalizeSlotId(slotId);
            return "hands".equals(normalized);
        }

        static boolean isHeadLikeSlot(String slotId) {
            String normalized = normalizeSlotId(slotId);
            return "head".equals(normalized);
        }

        static String normalizeSlotId(String slotId) {
            if (slotId == null) {
                return "";
            }

            int colon = slotId.indexOf(':');
            if (colon >= 0 && colon + 1 < slotId.length()) {
                slotId = slotId.substring(colon + 1);
            }

            return SLOT_ALIASES.getOrDefault(slotId, slotId);
        }
    }
}
