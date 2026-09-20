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

        // 线缆面板 part 的能力注册（AE2 19 官方扩展点；2026-09-20 补：
        // AE2 内部把 part 能力转成 registerBlockEntity，provider 的 side = part 所在面、按 part 类精确匹配）
        modEventBus.addListener(AE2Addon::onRegisterPartCapabilities);

        // 巨型订单队列 tick（ServerTickEvent.Post）
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.ae2addon.crafting.BatchedCraftingQueue.class);

        // 注册网络数据包（NeoForge 1.21 payload 网络）
        modEventBus.addListener(AE2Addon::registerPayloads);

        // 集成 CPU 菜单 opener（2026-09-20 补：AE2 19 的 MenuOpener.open 需要先 addOpener 注册 opener，
        // 否则右键集成 CPU 只会打一条 "unknown menu type" warn 并静默无反应）
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(() -> appeng.menu.MenuOpener.addOpener(
                        ModMenuTypes.INTEGRATED_CPU.get(),
                        com.ae2addon.gui.IntegratedCPUMenu::openMenu)));

        // 线缆面板 part 模型注册（2026-09-02 上游同步）：必须早于 AE2 的
        // PartModels.freeze()；此处为客户端最早时机（服务端不加载模型）。
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            try {
                var partModels = appeng.items.parts.PartModelsHelper
                        .createModels(com.ae2addon.part.InfiniteInterfacePart.class);
                appeng.api.parts.PartModels.registerModels(partModels);
                LOGGER.info("[ae2addon] part 模型已注册: {}", partModels.size());
            } catch (Throwable t) {
                LOGGER.warn("[ae2addon] part 模型注册失败(构造期): ", t);
            }
        }

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
        // 「退回网络」按钮（上游 v1.3.0+ 同步）
        registrar.playToServer(com.ae2addon.network.FeederReturnPacket.TYPE,
                com.ae2addon.network.FeederReturnPacket.STREAM_CODEC,
                com.ae2addon.network.FeederReturnPacket::handle);
        // 装配处理器样板翻页（v0.3 M3，上游 v1.3.0 同步）
        registrar.playToServer(com.ae2addon.network.AssemblerPagePacket.TYPE,
                com.ae2addon.network.AssemblerPagePacket.STREAM_CODEC,
                com.ae2addon.network.AssemblerPagePacket::handle);
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
        // AE2 存储能力（存储总线/子网可直连主网存储；上游 v1.3.0 同步：
        // 上游在 getCapability 里对 Capabilities.STORAGE 返回主网 MEStorage，不区分面、无网返空）
        event.registerBlockEntity(appeng.api.AECapabilities.ME_STORAGE,
                ModBlockEntities.INFINITE_INTERFACE.get(),
                (be, side) -> {
                    var grid = ((com.ae2addon.block.InfiniteInterfaceBE) be).getMainNode().getGrid();
                    return grid == null ? null : grid.getStorageService().getInventory();
                });
        // 化学能力（可选集成：Mekanism 存在才注册）
        if (com.ae2addon.compat.MekanismGasCompat.isLoaded()) {
            event.registerBlockEntity(mekanism.common.capabilities.Capabilities.CHEMICAL.block(),
                    ModBlockEntities.INFINITE_INTERFACE.get(),
                    (be, side) -> (mekanism.api.chemical.IChemicalHandler) ((com.ae2addon.block.InfiniteInterfaceBE) be)
                            .getChemHandler(side, mekanism.common.capabilities.Capabilities.CHEMICAL.block()));
        }
    }

    /**
     * 线缆面板 part 的能力（2026-09-20 补，AE2 19 官方扩展点）：
     * 方块版的物品/流体/化学入口在 part 形态下同样对外可见（管道/漏斗可直接塞进网络）。
     */
    private static void onRegisterPartCapabilities(
            appeng.api.parts.RegisterPartCapabilitiesEvent event) {
        event.register(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                (com.ae2addon.part.InfiniteInterfacePart part, net.minecraft.core.Direction side) ->
                        part.getNetworkItemHandler(),
                com.ae2addon.part.InfiniteInterfacePart.class);
        event.register(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                (com.ae2addon.part.InfiniteInterfacePart part, net.minecraft.core.Direction side) ->
                        part.getNetworkFluidHandler(),
                com.ae2addon.part.InfiniteInterfacePart.class);
        if (com.ae2addon.compat.MekanismGasCompat.isLoaded()) {
            // 化学注册在惰性门面里执行：主类不含可选依赖符号（未装 Mekanism 时该类不被加载）
            com.ae2addon.compat.MekanismChemCompat.registerPartCapabilities(event);
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
            // 升级卡注册到宿主（2026-09-02 part 版：面板物品同样注册，方到 Part 阶段补）
            // ⚠️ Upgrades.add(upgrade, supportedBy, max)：卡片在前，机器在后！
            registerInterfaceUpgrades(block);
            var partItem = ModItems.INFINITE_INTERFACE_PANEL_ITEM.get();
            registerInterfaceUpgrades(partItem);
            // 速度卡：感应卡供电倍率（1张×16，2张每轮无上限）+ 喂出预算 ×2/张
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.SPEED_CARD.asItem(), block, 2);
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.SPEED_CARD.asItem(), partItem, 2);
            COMPAT_UPGRADES.set(true); // 全部成功才置位
            LOGGER.info("[ae2addon] 升级卡注册完成（{} 张卡类型）",
                    appeng.api.upgrades.Upgrades.getMaxInstallable(
                            appeng.core.definitions.AEItems.CAPACITY_CARD.asItem(), block.asItem()));
        } catch (Throwable t) {
            // 失败不置位：下次 BE 构造重试，并打堆栈定位
            LOGGER.warn("[ae2addon] 升级卡注册失败（将重试）", t);
        }
    }

    /** 给某机器 item 注册 AE2 自带卡 + 跨 mod 卡（容量/红石/反向/合成/感应/频道/虚拟）。 */
    private static void registerInterfaceUpgrades(net.minecraft.world.level.ItemLike machine) {
        try {
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.CAPACITY_CARD.asItem(), machine, 4);
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.REDSTONE_CARD.asItem(), machine, 1);
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.INVERTER_CARD.asItem(), machine, 1);
            appeng.api.upgrades.Upgrades.add(
                    appeng.core.definitions.AEItems.CRAFTING_CARD.asItem(), machine, 1);
            var inductionCard = com.ae2addon.compat.AppFluxPowerCompat.inductionCard();
            if (inductionCard != null) {
                appeng.api.upgrades.Upgrades.add(inductionCard, machine, 1);
            }
            var channelCard = com.ae2addon.compat.ExtendedAEPlusCompat.channelCard();
            if (channelCard != null) {
                appeng.api.upgrades.Upgrades.add(channelCard, machine, 1);
            }
            var virtualCard = com.ae2addon.compat.ExtendedAEPlusCompat.virtualCraftingCard();
            if (virtualCard != null) {
                appeng.api.upgrades.Upgrades.add(virtualCard, machine, 1);
            }
        } catch (Throwable ignored) {
            // 单宿主失败不阻塞另一个
        }
    }
}
