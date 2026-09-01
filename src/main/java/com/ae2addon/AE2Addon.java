package com.ae2addon;

import com.ae2addon.init.ModBlockEntities;
import com.ae2addon.init.ModBlocks;
import com.ae2addon.init.ModItems;
import com.ae2addon.init.ModMenuTypes;
import com.ae2addon.network.FeederMarkPacket;
import com.ae2addon.network.FeederSettingPacket;
import com.ae2addon.network.FeederStatusPacket;
import com.ae2addon.network.FeederTargetPacket;
import com.ae2addon.network.FeederTogglePacket;
import com.ae2addon.network.LaneListPacket;
import com.ae2addon.network.Mode2ConfigPacket;
import com.ae2addon.network.OrderListPacket;
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

        // 配置文件注册 + 热加载监听
        com.ae2addon.config.AE2AddonConfig.register();
        modEventBus.addListener(com.ae2addon.config.AE2AddonConfig::onConfigEvent);

        // 方块能力注册（NeoForge 1.21.1：能力经 RegisterCapabilitiesEvent 提供）
        modEventBus.addListener(AE2Addon::onRegisterCapabilities);

        // 巨型订单队列 tick（ServerTickEvent.Post）
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.ae2addon.crafting.BatchedCraftingQueue.class);

        // 注册网络数据包（NeoForge 1.21 payload 网络）
        modEventBus.addListener(AE2Addon::registerPayloads);

        LOGGER.info("✅ AE2 Addon loaded! Universal Storage Cells ready!");
    }

    private static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(SetCellModePacket.TYPE, SetCellModePacket.STREAM_CODEC, SetCellModePacket::handle);
        // Mode2ConfigPacket 双向使用（客户端→服务端指令 + 服务端→客户端面板数据），
        // 同一 TYPE 只能注册一次，必须用 playBidirectional
        registrar.playBidirectional(Mode2ConfigPacket.TYPE, Mode2ConfigPacket.STREAM_CODEC, Mode2ConfigPacket::handle);
        // ME接口/集成CPU 数据包
        registrar.playToServer(FeederSettingPacket.TYPE, FeederSettingPacket.STREAM_CODEC, FeederSettingPacket::handle);
        registrar.playToServer(FeederTargetPacket.TYPE, FeederTargetPacket.STREAM_CODEC, FeederTargetPacket::handle);
        registrar.playToServer(FeederTogglePacket.TYPE, FeederTogglePacket.STREAM_CODEC, FeederTogglePacket::handle);
        registrar.playToServer(FeederMarkPacket.TYPE, FeederMarkPacket.STREAM_CODEC, FeederMarkPacket::handle);
        registrar.playToClient(FeederStatusPacket.TYPE, FeederStatusPacket.STREAM_CODEC, FeederStatusPacket::handle);
        registrar.playToClient(LaneListPacket.TYPE, LaneListPacket.STREAM_CODEC, LaneListPacket::handle);
        registrar.playToClient(OrderListPacket.TYPE, OrderListPacket.STREAM_CODEC, OrderListPacket::handle);
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

    /** NeoForge 1.21.1 能力注册：ME接口（无限级）的 item/fluid/化学 handler。 */
    private static void onRegisterCapabilities(
            final net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        // 物品能力（正面=FrontItemHandler，其余面=网络入口）
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.INFINITE_INTERFACE.get(),
                (be, side) -> ((com.ae2addon.block.InfiniteInterfaceBE) be).getItemHandler(side));
        // 流体能力（网络入口）
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.INFINITE_INTERFACE.get(),
                (be, side) -> ((com.ae2addon.block.InfiniteInterfaceBE) be).getNetworkFluidHandler());
        // 化学能力（可选集成：Mekanism 存在才注册）
        if (com.ae2addon.compat.MekanismGasCompat.isLoaded()) {
            event.registerBlockEntity(mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                    ModBlockEntities.INFINITE_INTERFACE.get(),
                    (be, side) -> (mekanism.api.chemical.IChemicalHandler) ((com.ae2addon.block.InfiniteInterfaceBE) be)
                            .getChemHandler(side, mekanism.common.capabilities.Capabilities.CHEMICAL.block()));
        }
    }

    /** 升级卡统一懒注册（BE 构造时触发；所有注册表已就绪）。 */
    private static final java.util.concurrent.atomic.AtomicBoolean COMPAT_UPGRADES =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** 世界加载时（BE 构造）调用；此时所有 mod 的注册表已就绪。 */
    public static void ensureCompatUpgrades() {
        if (COMPAT_UPGRADES.get()) {
            return; // 已成功注册
        }
        try {
            var block = ModBlocks.INFINITE_INTERFACE.get();
            // AE2 自带卡片（容量/红石/反向/合成）
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.CAPACITY_CARD.asItem(), block, 4);
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.REDSTONE_CARD.asItem(), block, 1);
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.INVERTER_CARD.asItem(), block, 1);
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.CRAFTING_CARD.asItem(), block, 1);
            // AppFlux 感应卡（供电卡）
            var inductionCard = com.ae2addon.compat.AppFluxPowerCompat.inductionCard();
            if (inductionCard != null) {
                appeng.api.upgrades.Upgrades.add(inductionCard, block, 1);
            }
            // ExtendedAE+ 频道卡 + 虚拟合成卡
            var channelCard = com.ae2addon.compat.ExtendedAEPlusCompat.channelCard();
            if (channelCard != null) {
                appeng.api.upgrades.Upgrades.add(channelCard, block, 1);
            }
            var virtualCard = com.ae2addon.compat.ExtendedAEPlusCompat.virtualCraftingCard();
            if (virtualCard != null) {
                appeng.api.upgrades.Upgrades.add(virtualCard, block, 1);
            }
            COMPAT_UPGRADES.set(true); // 全部成功才置位
            LOGGER.info("[ae2addon] 升级卡注册完成");
        } catch (Throwable t) {
            LOGGER.warn("[ae2addon] 升级卡注册失败（将重试）", t);
        }
    }
}
