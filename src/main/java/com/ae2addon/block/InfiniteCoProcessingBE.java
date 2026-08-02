package com.ae2addon.block;

import appeng.blockentity.crafting.CraftingBlockEntity;
import com.ae2addon.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class InfiniteCoProcessingBE extends CraftingBlockEntity {

    public InfiniteCoProcessingBE(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFINITE_CO_PROCESSING.get(), pos, state);
    }

    @Override
    public long getStorageBytes() {
        return 0;
    }

    @Override
    public int getAcceleratorThreads() {
        // 返回安全值：AE2 addBlockEntity 中若线程数超过上限（原版 16，其他模组可能改为
        // 1024 等）会抛 IllegalArgumentException。真正的"无限"由 CraftingCPUClusterMixin
        // 在加入后把 accelerator 字段直接置为 Integer.MAX_VALUE 实现。
        return 1;
    }
}
