package com.ae2addon.mixin;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.ae2addon.block.InfiniteCoProcessingBE;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 移除 AE2 CraftingCPUCluster 的 16 线程上限。
 * <p>
 * 注意：不修改常量 16。整合包中多个模组（bigger_ae2、extendedae_plus 等）都通过
 * {@code @ModifyConstant} 修改同一个常量，谁先改谁生效，其余模组若配置了
 * {@code defaultRequire=1} 会直接崩溃（如 bigger_ae2）。
 * 为避免抢占该常量，这里改为在 {@code addBlockEntity} 返回后直接把
 * {@code accelerator} 字段置为 {@link Integer#MAX_VALUE}，与任何模组都能共存。
 */
@Mixin(CraftingCPUCluster.class)
public abstract class CraftingCPUClusterMixin {

    @Shadow
    private int accelerator;

    @Inject(
            method = "addBlockEntity(Lappeng/blockentity/crafting/CraftingBlockEntity;)V",
            at = @At("RETURN"),
            remap = false
    )
    private void ae2addon$setInfiniteAcceleratorThreads(CraftingBlockEntity blockEntity, CallbackInfo ci) {
        if (blockEntity instanceof InfiniteCoProcessingBE) {
            this.accelerator = Integer.MAX_VALUE;
        }
    }
}
