package com.ae2addon.init;

import com.ae2addon.AE2Addon;
import com.ae2addon.block.InfiniteCraftingStorageBlock;
import com.ae2addon.block.InfiniteCoProcessingBlock;
import com.ae2addon.block.InfiniteInterfaceBlock;
import com.ae2addon.block.IntegratedCPUBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * AE2 Addon 方块注册
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, AE2Addon.MODID);

    /** 无限合成存储器 — 合成 CPU 扩容，永不爆仓 */
    public static final DeferredHolder<Block, Block> INFINITE_CRAFTING_STORAGE = BLOCKS.register(
            "infinite_crafting_storage",
            InfiniteCraftingStorageBlock::new
    );

    /** 无限并行处理单元 — 合成队列无限并发 */
    public static final DeferredHolder<Block, Block> INFINITE_CO_PROCESSING = BLOCKS.register(
            "infinite_co_processing",
            InfiniteCoProcessingBlock::new
    );

    /** 集成型CPU（无限级）— 3×5×3 多方块，提供无限合成能力 */
    public static final DeferredHolder<Block, Block> INTEGRATED_CPU = BLOCKS.register(
            "integrated_cpu",
            IntegratedCPUBlock::new
    );

    /** ME接口（无限级）— 机器供料站 */
    public static final DeferredHolder<Block, Block> INFINITE_INTERFACE = BLOCKS.register(
            "infinite_interface",
            InfiniteInterfaceBlock::new
    );
}
