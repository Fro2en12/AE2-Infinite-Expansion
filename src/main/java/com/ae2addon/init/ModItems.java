package com.ae2addon.init;

import com.ae2addon.AE2Addon;
import com.ae2addon.item.EternalHeartItem;
import com.ae2addon.item.MatterBallItem;
import com.ae2addon.item.UniversalStorageCell;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, AE2Addon.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AE2Addon.MODID);

    // ── 核心基础材料 ──
    public static final DeferredHolder<Item, Item> ETERNAL_HEART = ITEMS.register(
            "eternal_heart",
            EternalHeartItem::new
    );

    // ── 方块物品 ──
    public static final DeferredHolder<Item, Item> INFINITE_CRAFTING_STORAGE_ITEM = ITEMS.register(
            "infinite_crafting_storage",
            () -> new BlockItem(ModBlocks.INFINITE_CRAFTING_STORAGE.get(), new Item.Properties())
    );
    public static final DeferredHolder<Item, Item> INFINITE_CO_PROCESSING_ITEM = ITEMS.register(
            "infinite_co_processing",
            () -> new BlockItem(ModBlocks.INFINITE_CO_PROCESSING.get(), new Item.Properties())
    );

    // ── 万能无限存储元件 ──
    public static final DeferredHolder<Item, Item> UNIVERSAL_STORAGE_CELL = ITEMS.register(
            "universal_storage_cell",
            UniversalStorageCell::new
    );

    // ── 物质球（取消无限时大量物品临时存放） ──
    public static final DeferredHolder<Item, Item> MATTER_BALL = ITEMS.register(
            "matter_ball",
            MatterBallItem::new
    );

    // ── 新增方块物品 ──
    public static final DeferredHolder<Item, Item> INTEGRATED_CPU_ITEM = ITEMS.register(
            "integrated_cpu",
            () -> new BlockItem(ModBlocks.INTEGRATED_CPU.get(), new Item.Properties())
    );

    /** ME接口（无限级）方块物品 */
    public static final DeferredHolder<Item, Item> INFINITE_INTERFACE_ITEM = ITEMS.register(
            "infinite_interface",
            () -> new BlockItem(ModBlocks.INFINITE_INTERFACE.get(), new Item.Properties())
    );

    /** 配置存储卡 */
    public static final DeferredHolder<Item, Item> CONFIG_CARD = ITEMS.register(
            "config_card",
            com.ae2addon.item.ConfigCardItem::new
    );

    // ── 创造模式标签页 ──
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_AE2ADDON = CREATIVE_TABS.register(
            "ae2addon_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2addon"))
                    .icon(() -> new ItemStack(ETERNAL_HEART.get()))
                    .displayItems((params, output) -> {
                        output.accept(ETERNAL_HEART.get());
                        output.accept(UNIVERSAL_STORAGE_CELL.get());
                        output.accept(ModBlocks.INFINITE_CRAFTING_STORAGE.get());
                        output.accept(ModBlocks.INFINITE_CO_PROCESSING.get());
                        output.accept(ModBlocks.INTEGRATED_CPU.get());
                        output.accept(ModBlocks.INFINITE_INTERFACE.get());
                        output.accept(CONFIG_CARD.get());
                        output.accept(MATTER_BALL.get());
                    })
                    .build()
    );
}
