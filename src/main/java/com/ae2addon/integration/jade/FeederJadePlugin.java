package com.ae2addon.integration.jade;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import appeng.block.networking.CableBusBlock;
import appeng.blockentity.networking.CableBusBlockEntity;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import com.ae2addon.AE2Addon;
import com.ae2addon.block.FeederHost;
import com.ae2addon.block.InfiniteInterfaceBE;
import com.ae2addon.block.InfiniteInterfaceBlock;
import com.ae2addon.network.FeederHostResolver;

/**
 * 玉（Jade）集成：悬停显示供料站蓄水池全部条目（物品/流体/化学物分组列出），
 * 方块版与线缆面板 part 版都支持。
 * <p>
 * 服务端把 {@link FeederHost#reservoirTooltipLines()} 编成 Component 列表写进 NBT，
 * 客户端反序列化后渲染——translatable 键随客户端语言解析（中/英自动切换）。
 * <p>
 * 1.21.1 移植要点（Jade 15.x / MC 1.21.1）：
 * <ul>
 *   <li>1.20.1 的 {@code Component.Serializer.toJson(Component)} / {@code fromJson(String)}
 *       单参重载在 1.21.1 已不存在（同名的两个方法现在都要 {@code HolderLookup.Provider}），
 *       改用 {@link ComponentSerialization#CODEC} + {@link NbtOps}：translatable/literal
 *       不需要注册表查询，纯 NBT 往返即可。</li>
 *   <li>{@code new ResourceLocation(ns, path)} 已废弃，改用
 *       {@link ResourceLocation#fromNamespaceAndPath(String, String)}。</li>
 *   <li>Jade 15 的插件发现仍走 NeoForge 的 {@code ModList.getAllScanData()} 扫
 *       {@link WailaPlugin} 注解，不需要在 neoforge.mods.toml 里加入口。</li>
 * </ul>
 */
@WailaPlugin(AE2Addon.MODID)
public class FeederJadePlugin implements IWailaPlugin {

    /** 服务端写入的 NBT 键；值是 ListTag，元素为 Component 的 NBT 编码（TAG_COMPOUND）。 */
    private static final String LINES_KEY = "ae2addon:lines";

    /** 蓄水池标题行的 i18n 键（带两个占位：种类数、合计）。 */
    private static final String HEADER_KEY = "ae2addon.jade.header";

    // ── 服务端：蓄水池明细 → Component 列表 → NBT ──

    private static final IServerDataProvider<BlockAccessor> SERVER_PROVIDER = new IServerDataProvider<>() {

        @Override
        public ResourceLocation getUid() {
            return ResourceLocation.fromNamespaceAndPath(AE2Addon.MODID, "feeder_data");
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            // 与网络包同一套定位逻辑：方块 BE 直接命中，线缆 part 从 IPartHost 里找
            FeederHost host = FeederHostResolver.resolve(accessor.getLevel(), accessor.getPosition());
            if (host == null) {
                return;
            }
            List<Component> lines = host.reservoirTooltipLines();
            String[] summary = host.reservoirSummary();
            lines.add(0, Component.translatable(HEADER_KEY, summary[0], summary[1]));

            ListTag list = new ListTag();
            for (Component line : lines) {
                Tag encoded = encodeComponent(line);
                if (encoded != null) {
                    list.add(encoded);
                }
            }
            if (!list.isEmpty()) {
                data.put(LINES_KEY, list);
            }
        }
    };

    // ── 客户端：反序列化渲染 ──

    private static final IBlockComponentProvider CLIENT_PROVIDER = new IBlockComponentProvider() {

        @Override
        public ResourceLocation getUid() {
            return ResourceLocation.fromNamespaceAndPath(AE2Addon.MODID, "feeder_view");
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            // 直接用 instanceof 取 ListTag：getList(key, type) 在元素类型不符时会静默返回空表，
            // 这里不想让一次编码格式变动就丢掉整块提示
            if (data == null || !(data.get(LINES_KEY) instanceof ListTag list)) {
                return;
            }
            for (Tag element : list) {
                ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, element)
                        .result()
                        .ifPresent(component -> tooltip.add(component));
            }
        }
    };

    /**
     * Component → NBT。用 1.21.1 的 {@link ComponentSerialization#CODEC}（NbtOps 版，
     * 不查注册表，translatable 键原样保留交给客户端解析）。
     *
     * @return 编码结果；连纯文本兜底都失败时返回 {@code null}，由调用方跳过该行
     */
    private static Tag encodeComponent(Component component) {
        Tag encoded = ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, component)
                .result()
                .orElse(null);
        if (encoded != null) {
            return encoded;
        }
        // 兜底：万一内容需要注册表（例如带 ItemStack 的 hoverEvent），退化成纯文本，
        // 宁可丢一次本地化，也不要整行消失
        return ComponentSerialization.CODEC
                .encodeStart(NbtOps.INSTANCE, Component.literal(component.getString()))
                .result()
                .orElse(null);
    }

    @Override
    public void register(IWailaCommonRegistration registration) {
        // 方块版：BE 自身实现 FeederHost
        registration.registerBlockDataProvider(SERVER_PROVIDER, InfiniteInterfaceBE.class);
        // 线缆 part 版：宿主 BE 是 CableBusBlockEntity，part 在 appendServerData 里从 IPartHost 取
        registration.registerBlockDataProvider(SERVER_PROVIDER, CableBusBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CLIENT_PROVIDER, InfiniteInterfaceBlock.class);
        registration.registerBlockComponent(CLIENT_PROVIDER, CableBusBlock.class);
    }
}
