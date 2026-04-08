package com.oneworldstudio.epicfightcurioscompat;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

@Mod.EventBusSubscriber(
        modid = EpicFightCuriosCompatStandalone.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class CuriosSlotAttachmentFixer {
    private CuriosSlotAttachmentFixer() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(CuriosSlotAttachmentFixer::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (Map.Entry<ResourceLocation, FixedCuriosItems.FixedTarget> entry : FixedCuriosItems.ITEMS.entrySet()) {
                ResourceLocation id = entry.getKey();
                FixedCuriosItems.FixedTarget target = entry.getValue();
                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item == null) {
                    continue;
                }

                Optional<ICurioRenderer> originalOptional = CuriosRendererRegistry.getRenderer(item);
                ICurioRenderer original = originalOptional.orElse(null);
                CuriosRendererRegistry.register(item, () -> new FixedAttachmentRenderer(target, original));
            }
        });
    }

    private static final class FixedAttachmentRenderer implements ICurioRenderer {
        private final FixedCuriosItems.FixedTarget target;
        private final ICurioRenderer original;

        private FixedAttachmentRenderer(FixedCuriosItems.FixedTarget target, ICurioRenderer original) {
            this.target = target;
            this.original = original;
        }

        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(
                ItemStack stack,
                SlotContext slotContext,
                PoseStack poseStack,
                RenderLayerParent<T, M> renderLayerParent,
                MultiBufferSource buffer,
                int light,
                float limbSwing,
                float limbSwingAmount,
                float partialTicks,
                float ageInTicks,
                float netHeadYaw,
                float headPitch
        ) {
            poseStack.pushPose();
            HumanoidModel<?> humanoidModel = resolveHumanoidModel(slotContext, renderLayerParent);
            if (humanoidModel != null) {
                applyAttachmentTransform(humanoidModel, slotContext, poseStack, this.target);
            }

            if (isPreferItemModel(stack)) {
                ItemDisplayContext context = this.target == FixedCuriosItems.FixedTarget.HEAD
                        ? ItemDisplayContext.HEAD
                        : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
                Minecraft.getInstance().getItemRenderer().renderStatic(
                        stack,
                        context,
                        light,
                        OverlayTexture.NO_OVERLAY,
                        poseStack,
                        buffer,
                        slotContext.entity().level(),
                        0
                );
            } else if (this.original != null) {
                this.original.render(
                        stack,
                        slotContext,
                        poseStack,
                        renderLayerParent,
                        buffer,
                        light,
                        limbSwing,
                        limbSwingAmount,
                        partialTicks,
                        ageInTicks,
                        netHeadYaw,
                        headPitch
                );
            } else {
                Minecraft.getInstance().getItemRenderer().renderStatic(
                        stack,
                        ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        light,
                        OverlayTexture.NO_OVERLAY,
                        poseStack,
                        buffer,
                        slotContext.entity().level(),
                        0
                );
            }

            poseStack.popPose();
        }

        private static boolean isPreferItemModel(ItemStack stack) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return key != null
                    && "create".equals(key.getNamespace())
                    && "goggles".equals(key.getPath());
        }

        private static HumanoidModel<?> resolveHumanoidModel(
                SlotContext slotContext,
                RenderLayerParent<?, ?> parent
        ) {
            try {
                if (parent != null) {
                    EntityModel<?> parentModel = parent.getModel();
                    if (parentModel instanceof HumanoidModel<?> humanoidModel) {
                        return humanoidModel;
                    }
                }

                LivingEntity entity = slotContext.entity();
                EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                EntityRenderer<?> renderer = dispatcher.getRenderer(entity);
                if (renderer instanceof LivingEntityRenderer<?, ?> livingRenderer) {
                    EntityModel<?> model = livingRenderer.getModel();
                    if (model instanceof HumanoidModel<?> humanoidModel) {
                        return humanoidModel;
                    }
                }
            } catch (Throwable ignored) {
            }

            return null;
        }

        private static void applyAttachmentTransform(
                HumanoidModel<?> model,
                SlotContext context,
                PoseStack poseStack,
                FixedCuriosItems.FixedTarget target
        ) {
            switch (target) {
                case HEAD:
                    model.head.translateAndRotate(poseStack);
                    break;
                case BODY:
                case BACK:
                    model.body.translateAndRotate(poseStack);
                    break;
                case GLOVES:
                    if (isLeftSide(context)) {
                        model.leftArm.translateAndRotate(poseStack);
                    } else {
                        model.rightArm.translateAndRotate(poseStack);
                    }
                    break;
                case BOOTS:
                    if (isLeftSide(context)) {
                        model.leftLeg.translateAndRotate(poseStack);
                    } else {
                        model.rightLeg.translateAndRotate(poseStack);
                    }
                    break;
                default:
                    break;
            }
        }

        private static boolean isLeftSide(SlotContext context) {
            return getSlotIndex(context) <= 0;
        }

        private static int getSlotIndex(SlotContext context) {
            try {
                Method method = context.getClass().getMethod("index");
                Object result = method.invoke(context);
                if (result instanceof Integer value) {
                    return value;
                }
            } catch (Throwable ignored) {
            }

            try {
                Method method = context.getClass().getMethod("getIndex");
                Object result = method.invoke(context);
                if (result instanceof Integer value) {
                    return value;
                }
            } catch (Throwable ignored) {
            }

            return 0;
        }
    }
}
