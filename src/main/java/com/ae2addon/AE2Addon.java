package com.ae2addon;

import com.ae2addon.init.ModBlockEntities;
import com.ae2addon.init.ModBlocks;
import com.ae2addon.init.ModItems;
import com.ae2addon.init.ModMenuTypes;
import com.ae2addon.network.Mode2ConfigPacket;
import com.ae2addon.network.SetCellModePacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(AE2Addon.MODID)
public class AE2Addon {
    public static final String MODID = "ae2addon";
    public static final Logger LOGGER = LogManager.getLogger();

    private static final String PROTOCOL_VERSION = "1";

    public AE2Addon(IEventBus modEventBus) {
        // 注册物品
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);

        // 注册方块
        ModBlocks.BLOCKS.register(modEventBus);

        // 注册方块实体
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        // 注册菜单类型
        ModMenuTypes.MENUS.register(modEventBus);

        // 注册网络数据包（NeoForge 1.21 payload 网络）
        modEventBus.addListener(AE2Addon::registerPayloads);

        LOGGER.info("✅ AE2 Addon loaded! Universal Storage Cells ready!");
    }

    private static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(SetCellModePacket.TYPE, SetCellModePacket.STREAM_CODEC, SetCellModePacket::handle);
        registrar.playToServer(Mode2ConfigPacket.TYPE, Mode2ConfigPacket.STREAM_CODEC, Mode2ConfigPacket::handle);
        // type 4（面板数据响应）由服务端发往客户端
        registrar.playToClient(Mode2ConfigPacket.TYPE, Mode2ConfigPacket.STREAM_CODEC, Mode2ConfigPacket::handle);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /**
     * NeoForge 1.21 中 ItemStack 自定义 NBT 改为 DataComponents（CUSTOM_DATA）。
     * 读取副本，供读写自定义标签使用；修改后需调用 {@link #setCellTag} 写回。
     */
    public static CompoundTag cellTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** 将自定义 NBT 写回 ItemStack（配合 {@link #cellTag} 使用） */
    public static void setCellTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
