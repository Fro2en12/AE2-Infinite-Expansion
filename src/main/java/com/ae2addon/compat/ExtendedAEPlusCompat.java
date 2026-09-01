package com.ae2addon.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/**
 * ExtendedAE+ 可选集成（2026-08-28 sensei）：
 * - 频道卡（channel_card）：接口无线连网（无线收发器主端），无需接线
 * - 虚拟合成卡（virtual_crafting_card）：最后一次发配瞬间结束任务
 * <p>
 * compileOnly 依赖（extendedae_plus），运行时未装时短路。
 * <p>
 * 待编译确认：1.21.1 版 ExtendedAE+ 的无线链路 API 相比 Forge 1.20.1 有调整 —
 * ChannelCardItem / GenericNodeEndpointImpl / WirelessSlaveLink 包名迁移；
 * getOwnerUUID()、WirelessSlaveLink.setPlacerId()、ChannelCardLinkHelper.disconnect() 已移除。
 * 下方对应位置已按新 API 兼容处理（drop owner 逻辑、disconnect 改用 onUnloadOrRemove()），
 * 需在加入 extendedae_plus 依赖后编译确认。
 */
public final class ExtendedAEPlusCompat {

    private static boolean checked;
    private static boolean loaded;

    private ExtendedAEPlusCompat() {
    }

    public static boolean isLoaded() {
        if (!checked) {
            checked = true;
            loaded = ModList.get().isLoaded("extendedae_plus");
        }
        return loaded;
    }

    /** 频道卡物品（注册表查询，未装返回 null）。 */
    public static Item channelCard() {
        if (!isLoaded()) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("extendedae_plus", "channel_card"));
    }

    /** 虚拟合成卡物品（注册表查询，未装返回 null）。 */
    public static Item virtualCraftingCard() {
        if (!isLoaded()) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("extendedae_plus", "virtual_crafting_card"));
    }

    /**
     * 无线链路持有者：内部用 {@code Object} 惰性持有 WirelessSlaveLink。
     * 2026-09-01 sensei 的实例缺 extendedae_plus 启动崩溃（ModBlockEntities 注册 lambda
     * 加载 BE 类时字段类型直接解析 IWirelessEndpoint → NoClassDefFoundError）。
     * 字段类型改 Object 后，extendedae_plus 类只在方法体内引用 → 惰性加载，缺依赖不崩。
     */
    public static final class ChannelLink {

        /** WirelessSlaveLink（仅 isLoaded() 时非 null）。 */
        private Object link;

        /** 按卡刷新链路（无卡/无效卡自动断开）；卡栈为空也断开。 */
        public void update(com.ae2addon.block.InfiniteInterfaceBE be, net.minecraft.world.item.ItemStack cardStack) {
            if (!isLoaded()) {
                return;
            }
            long channel = -1;
            if (cardStack != null && !cardStack.isEmpty()) {
                channel = com.extendedae_plus.ae.items.ChannelCardItem.getChannel(cardStack);
            }
            try {
                if (channel < 0) {
                    disconnect();
                    return;
                }
                if (link == null) {
                    var endpoint = new com.extendedae_plus.wireless.endpoint.GenericNodeEndpointImpl(
                            () -> be, () -> be.getMainNode().getNode());
                    link = new com.extendedae_plus.wireless.WirelessSlaveLink(endpoint);
                }
                var slave = (com.extendedae_plus.wireless.WirelessSlaveLink) link;
                slave.setFrequency(channel);
                slave.updateStatus();
            } catch (Throwable ignored) {
                // 无线系统异常不影响主功能
            }
        }

        /** 断开（无线系统侧清理）。 */
        public void disconnect() {
            if (!isLoaded() || link == null) {
                link = null;
                return;
            }
            try {
                // TODO 待编译确认：Forge 版 ChannelCardLinkHelper.disconnect() 在 1.21.1 已移除，
                // 现用 WirelessSlaveLink.onUnloadOrRemove() 近似清理，需在加入依赖后确认。
                ((com.extendedae_plus.wireless.WirelessSlaveLink) link).onUnloadOrRemove();
            } catch (Throwable ignored) {
            }
            link = null;
        }

        /** 卸载（方块移除/世界卸载时调用）。 */
        public void unload() {
            if (!isLoaded() || link == null) {
                link = null;
                return;
            }
            try {
                ((com.extendedae_plus.wireless.WirelessSlaveLink) link).onUnloadOrRemove();
            } catch (Throwable ignored) {
            }
            link = null;
        }
    }
}
