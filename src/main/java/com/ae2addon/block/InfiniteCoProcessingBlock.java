package com.ae2addon.block;

import appeng.block.crafting.CraftingUnitBlock;
import appeng.block.crafting.CraftingUnitType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class InfiniteCoProcessingBlock extends CraftingUnitBlock {

    private static boolean CLASS_INIT = false;

    public InfiniteCoProcessingBlock() {
        super(CraftingUnitType.ACCELERATOR);
        if (!CLASS_INIT) {
            CLASS_INIT = true;
            try {
                // NeoForge 1.21 开发环境直接使用 Mojang 官方映射，字段名无需 SRG 转换
                var f1 = appeng.block.AEBaseEntityBlock.class.getDeclaredField("blockEntityClass");
                f1.setAccessible(true);
                f1.set(this, InfiniteCoProcessingBE.class);

                // CraftingBlockEntity does not implement ServerTickingBlockEntity/ClientTickingBlockEntity,
                // so AE2's default ticker for crafting blocks is null. Leave these fields at their default.
                // (Do NOT set empty lambdas here - that would suppress the correct initialization flow.)
            } catch (Exception e) {}
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfiniteCoProcessingBE(pos, state);
    }
}
