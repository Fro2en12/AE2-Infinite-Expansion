package com.ae2addon.init;

import com.ae2addon.AE2Addon;
import com.ae2addon.block.InfiniteCraftingStorageBE;
import com.ae2addon.block.InfiniteCoProcessingBE;
import com.ae2addon.block.InfiniteInterfaceBE;
import com.ae2addon.block.IntegratedCPUBE;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * AE2 Addon 方块实体注册
 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, AE2Addon.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfiniteCraftingStorageBE>> INFINITE_CRAFTING_STORAGE =
            BLOCK_ENTITIES.register("infinite_crafting_storage",
                    () -> BlockEntityType.Builder.of(
                            InfiniteCraftingStorageBE::new,
                            ModBlocks.INFINITE_CRAFTING_STORAGE.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfiniteCoProcessingBE>> INFINITE_CO_PROCESSING =
            BLOCK_ENTITIES.register("infinite_co_processing",
                    () -> BlockEntityType.Builder.of(
                            InfiniteCoProcessingBE::new,
                            ModBlocks.INFINITE_CO_PROCESSING.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IntegratedCPUBE>> INTEGRATED_CPU =
            BLOCK_ENTITIES.register("integrated_cpu",
                    () -> BlockEntityType.Builder.of(
                            IntegratedCPUBE::new,
                            ModBlocks.INTEGRATED_CPU.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfiniteInterfaceBE>> INFINITE_INTERFACE =
            BLOCK_ENTITIES.register("infinite_interface",
                    () -> BlockEntityType.Builder.of(
                            InfiniteInterfaceBE::new,
                            ModBlocks.INFINITE_INTERFACE.get()
                    ).build(null));
}
