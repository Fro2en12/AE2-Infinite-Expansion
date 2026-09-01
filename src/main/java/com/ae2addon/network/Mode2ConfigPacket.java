package com.ae2addon.network;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEFluidKey;
import com.ae2addon.cell.UnlimitedCellInventory;
import com.ae2addon.item.MatterBallItem;
import com.ae2addon.item.UniversalStorageCell;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 模式2配置数据包（NeoForge 1.21 payload，双向）：
 * type 0  = 设置阈值
 * type 1  = 加入白名单（传完整ItemStack，自动检测流体容器）
 * type 2  = 移除白名单项
 * type 3  = 请求面板数据（客户端→服务端）
 * type 4  = 面板数据响应（服务端→客户端，支持分包）
 * type 5  = 切换无限状态（客户端→服务端，传完整AEKey NBT）
 * type 6  = 设置工作模式（客户端→服务端）
 * type 7  = 添加 tag/mod 规则（tag=true 传 tag 名，false 传 mod id）
 * type 8  = 移除 tag/mod 规则
 * type 9  = 规则数据响应（服务端→客户端：tags/mods 列表）
 * type 10 = 设置规则生效模式
 * type 11 = 切换黑名单（客户端→服务端，传完整AEKey NBT）
 * type 12 = 黑名单数据响应（服务端→客户端，分包 AEKey 列表）
 * type 13 = 任意 AEKey 直加白名单（JEI 拖取流体/化学物/物品）
 * <p>
 * 注：规则/黑名单的存储读写已切换至 UnlimitedCellInventory 规则 API
 * （addTagRule/removeTagRule/addModRule/removeModRule/setRuleInstant/
 * toggleBlacklist/getTags/getMods/isRuleInstant/getBlacklist），由库存内部
 * 经 CellDataSavedData 持久化；本包不再直接读写元件 NBT。
 */
public class Mode2ConfigPacket implements CustomPacketPayload {

    public static final Type<Mode2ConfigPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2addon", "mode2_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Mode2ConfigPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> encode(packet, buf),
            Mode2ConfigPacket::decode
    );

    private final int type;
    private final long threshold;
    private final String itemId;           // type 2 用
    private final ItemStack clickedStack;  // type 1 用
    private final List<UnlimitedCellInventory.PanelItem> panelItems; // type 4
    private final CompoundTag keyTag;      // type 5 用
    private final int workMode;            // type 6 用
    /** type 7/8：规则字符串 + 是否 tag（false=mod） */
    private final String rule;
    private final boolean isTag;
    /** type 9：规则列表 */
    private final List<String> ruleList;
    /** type 10：规则生效模式（true=立即全量，false=触碰后） */
    private final boolean ruleInstant;
    /** type 11：切换黑名单用的 key */
    private final CompoundTag blacklistKeyTag;
    /** type 12：黑名单 AEKey 列表（分包） */
    private final List<AEKey> blacklistKeys;
    /** type 13：直加白名单的 AEKey（encode 时才序列化 NBT） */
    private final AEKey directKey;
    /** 分包支持：当前 chunk 索引 / 总 chunk 数，非分包时 (0, 1) */
    private final int chunkIndex;
    private final int totalChunks;

    private static final int MAX_ITEMS_PER_CHUNK = 50;

    // type 3 请求
    public Mode2ConfigPacket(int type) {
        this.type = type;
        this.threshold = 0;
        this.itemId = "";
        this.clickedStack = ItemStack.EMPTY;
        this.panelItems = null;
        this.keyTag = null;
        this.workMode = 0;
        this.rule = "";
        this.isTag = true;
        this.ruleList = null;
        this.ruleInstant = true;
        this.blacklistKeyTag = null;
        this.blacklistKeys = null;
        this.directKey = null;
        this.chunkIndex = 0;
        this.totalChunks = 1;
    }

    // type 0 / 2
    public Mode2ConfigPacket(int type, long threshold, String itemId) {
        this.type = type;
        this.threshold = threshold;
        this.itemId = itemId == null ? "" : itemId;
        this.clickedStack = ItemStack.EMPTY;
        this.panelItems = null;
        this.keyTag = null;
        this.workMode = 0;
        this.rule = "";
        this.isTag = true;
        this.ruleList = null;
        this.ruleInstant = true;
        this.blacklistKeyTag = null;
        this.blacklistKeys = null;
        this.directKey = null;
        this.chunkIndex = 0;
        this.totalChunks = 1;
    }

    // type 6 设置工作模式
    public Mode2ConfigPacket(int type, int workMode) {
        this.type = 6;
        this.threshold = 0;
        this.itemId = "";
        this.clickedStack = ItemStack.EMPTY;
        this.panelItems = null;
        this.keyTag = null;
        this.workMode = workMode;
        this.rule = "";
        this.isTag = true;
        this.ruleList = null;
        this.ruleInstant = true;
        this.blacklistKeyTag = null;
        this.blacklistKeys = null;
        this.directKey = null;
        this.chunkIndex = 0;
        this.totalChunks = 1;
    }

    // type 1 加入白名单（传完整ItemStack）
    public Mode2ConfigPacket(ItemStack clickedStack) {
        this.type = 1;
        this.threshold = 0;
        this.itemId = "";
        this.clickedStack = clickedStack;
        this.panelItems = null;
        this.keyTag = null;
        this.workMode = 0;
        this.rule = "";
        this.isTag = true;
        this.ruleList = null;
        this.ruleInstant = true;
        this.blacklistKeyTag = null;
        this.blacklistKeys = null;
        this.directKey = null;
        this.chunkIndex = 0;
        this.totalChunks = 1;
    }

    /** type 4 面板数据响应（分包） */
    public Mode2ConfigPacket(List<UnlimitedCellInventory.PanelItem> panelItems, int chunkIndex, int totalChunks) {
        this.type = 4;
        this.threshold = 0;
        this.itemId = "";
        this.clickedStack = ItemStack.EMPTY;
        this.panelItems = panelItems;
        this.keyTag = null;
        this.workMode = 0;
        this.rule = "";
        this.isTag = true;
        this.ruleList = null;
        this.ruleInstant = true;
        this.blacklistKeyTag = null;
        this.blacklistKeys = null;
        this.directKey = null;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
    }

    // type 5 切换无限
    public Mode2ConfigPacket(CompoundTag keyTag) {
        this.type = 5;
        this.threshold = 0;
        this.itemId = "";
        this.clickedStack = ItemStack.EMPTY;
        this.panelItems = null;
        this.keyTag = keyTag;
        this.workMode = 0;
        this.rule = "";
        this.isTag = true;
        this.ruleList = null;
        this.ruleInstant = true;
        this.blacklistKeyTag = null;
        this.blacklistKeys = null;
        this.directKey = null;
        this.chunkIndex = 0;
        this.totalChunks = 1;
    }

    // type 7 添加规则 / type 8 移除规则（isTag=true → tag，false → mod）
    public Mode2ConfigPacket(int type, String rule, boolean isTag) {
        this.type = type;
        this.threshold = 0;
        this.itemId = "";
        this.clickedStack = ItemStack.EMPTY;
        this.panelItems = null;
        this.keyTag = null;
        this.workMode = 0;
        this.rule = rule == null ? "" : rule;
        this.isTag = isTag;
        this.ruleList = null;
        this.ruleInstant = true;
        this.blacklistKeyTag = null;
        this.blacklistKeys = null;
        this.directKey = null;
        this.chunkIndex = 0;
        this.totalChunks = 1;
    }

    // type 9 规则数据响应（tags/mods 列表）
    public Mode2ConfigPacket(List<String> ruleList, boolean isTag, boolean ruleInstant) {
        this.type = 9;
        this.threshold = 0;
        this.itemId = "";
        this.clickedStack = ItemStack.EMPTY;
        this.panelItems = null;
        this.keyTag = null;
        this.workMode = 0;
        this.rule = "";
        this.isTag = isTag;
        this.ruleList = ruleList;
        this.ruleInstant = ruleInstant;
        this.blacklistKeyTag = null;
        this.blacklistKeys = null;
        this.directKey = null;
        this.chunkIndex = 0;
        this.totalChunks = 1;
    }

    // type 10 设置规则生效模式（true=立即全量，false=触碰后）
    public Mode2ConfigPacket(boolean ruleInstant) {
        this.type = 10;
        this.threshold = 0;
        this.itemId = "";
        this.clickedStack = ItemStack.EMPTY;
        this.panelItems = null;
        this.keyTag = null;
        this.workMode = 0;
        this.rule = "";
        this.isTag = true;
        this.ruleList = null;
        this.ruleInstant = ruleInstant;
        this.blacklistKeyTag = null;
        this.blacklistKeys = null;
        this.directKey = null;
        this.chunkIndex = 0;
        this.totalChunks = 1;
    }

    // type 11 切换黑名单（传完整 AEKey NBT）
    public Mode2ConfigPacket(CompoundTag blacklistKeyTag, boolean unused) {
        this.type = 11;
        this.threshold = 0;
        this.itemId = "";
        this.clickedStack = ItemStack.EMPTY;
        this.panelItems = null;
        this.keyTag = null;
        this.workMode = 0;
        this.rule = "";
        this.isTag = true;
        this.ruleList = null;
        this.ruleInstant = true;
        this.blacklistKeyTag = blacklistKeyTag;
        this.blacklistKeys = null;
        this.directKey = null;
        this.chunkIndex = 0;
        this.totalChunks = 1;
    }

    // type 12 黑名单数据响应（分包）
    public Mode2ConfigPacket(List<AEKey> blacklistKeys, int chunkIndex, int totalChunks, boolean unused) {
        this.type = 12;
        this.threshold = 0;
        this.itemId = "";
        this.clickedStack = ItemStack.EMPTY;
        this.panelItems = null;
        this.keyTag = null;
        this.workMode = 0;
        this.rule = "";
        this.isTag = true;
        this.ruleList = null;
        this.ruleInstant = true;
        this.blacklistKeyTag = null;
        this.blacklistKeys = blacklistKeys;
        this.directKey = null;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
    }

    // type 13：JEI 拖取流体/化学物、任意 AEKey 直加白名单
    public Mode2ConfigPacket(AEKey key) {
        this.type = 13;
        this.threshold = 0;
        this.itemId = "";
        this.clickedStack = ItemStack.EMPTY;
        this.panelItems = null;
        this.keyTag = null;
        this.workMode = 0;
        this.rule = "";
        this.isTag = true;
        this.ruleList = null;
        this.ruleInstant = true;
        this.blacklistKeyTag = null;
        this.blacklistKeys = null;
        this.directKey = key;
        this.chunkIndex = 0;
        this.totalChunks = 1;
    }

    public static void encode(Mode2ConfigPacket p, RegistryFriendlyByteBuf buf) {
        buf.writeByte(p.type);

        if (p.type == 1) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.clickedStack);
        } else if (p.type == 4 && p.panelItems != null) {
            buf.writeVarInt(p.chunkIndex);
            buf.writeVarInt(p.totalChunks);
            buf.writeVarInt(p.panelItems.size());
            for (var entry : p.panelItems) {
                buf.writeNbt(entry.key.toTagGeneric(buf.registryAccess()));
                buf.writeVarLong(entry.amount);
                buf.writeBoolean(entry.isInfinite);
                buf.writeVarLong(entry.bytes);
            }
        } else if (p.type == 5 && p.keyTag != null) {
            buf.writeNbt(p.keyTag);
        } else if (p.type == 6) {
            buf.writeVarInt(p.workMode);
        } else if (p.type == 7 || p.type == 8) {
            buf.writeBoolean(p.isTag);
            buf.writeUtf(p.rule == null ? "" : p.rule);
        } else if (p.type == 9 && p.ruleList != null) {
            buf.writeBoolean(p.isTag);
            buf.writeBoolean(p.ruleInstant);
            buf.writeVarInt(p.ruleList.size());
            for (String r : p.ruleList) {
                buf.writeUtf(r == null ? "" : r);
            }
        } else if (p.type == 10) {
            buf.writeBoolean(p.ruleInstant);
        } else if (p.type == 11 && p.blacklistKeyTag != null) {
            buf.writeNbt(p.blacklistKeyTag);
        } else if (p.type == 12 && p.blacklistKeys != null) {
            buf.writeVarInt(p.chunkIndex);
            buf.writeVarInt(p.totalChunks);
            buf.writeVarInt(p.blacklistKeys.size());
            for (AEKey k : p.blacklistKeys) {
                buf.writeNbt(k.toTagGeneric(buf.registryAccess()));
            }
        } else if (p.type == 13) {
            buf.writeNbt(p.directKey == null ? null : p.directKey.toTagGeneric(buf.registryAccess()));
        } else {
            buf.writeLong(p.threshold);
            buf.writeUtf(p.itemId == null ? "" : p.itemId);
        }
    }

    public static Mode2ConfigPacket decode(RegistryFriendlyByteBuf buf) {
        int type = buf.readByte();

        if (type == 1) {
            return new Mode2ConfigPacket(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }

        if (type == 13) {
            CompoundTag t = buf.readNbt();
            return new Mode2ConfigPacket(t == null ? null : AEKey.fromTagGeneric(buf.registryAccess(), t));
        }

        if (type == 4) {
            int chunkIndex = buf.readVarInt();
            int totalChunks = buf.readVarInt();
            int count = buf.readVarInt();
            List<UnlimitedCellInventory.PanelItem> items = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                CompoundTag tag = buf.readNbt();
                if (tag == null) continue;
                AEKey key = AEKey.fromTagGeneric(buf.registryAccess(), tag);
                if (key == null) continue;
                long amount = buf.readVarLong();
                boolean isInfinite = buf.readBoolean();
                long bytes = buf.readVarLong();
                items.add(new UnlimitedCellInventory.PanelItem(key, amount, isInfinite, bytes));
            }
            return new Mode2ConfigPacket(items, chunkIndex, totalChunks);
        }

        if (type == 5) {
            CompoundTag tag = buf.readNbt();
            return new Mode2ConfigPacket(tag);
        }

        if (type == 6) {
            return new Mode2ConfigPacket(6, buf.readVarInt());
        }

        if (type == 7 || type == 8) {
            boolean isTag = buf.readBoolean();
            String rule = buf.readUtf();
            return new Mode2ConfigPacket(type, rule, isTag);
        }

        if (type == 9) {
            boolean isTag = buf.readBoolean();
            boolean ruleInstant = buf.readBoolean();
            int count = buf.readVarInt();
            List<String> rules = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                rules.add(buf.readUtf());
            }
            return new Mode2ConfigPacket(rules, isTag, ruleInstant);
        }

        if (type == 10) {
            return new Mode2ConfigPacket(buf.readBoolean());
        }

        if (type == 11) {
            CompoundTag tag = buf.readNbt();
            return new Mode2ConfigPacket(tag, false);
        }

        if (type == 12) {
            int chunkIndex = buf.readVarInt();
            int totalChunks = buf.readVarInt();
            int count = buf.readVarInt();
            List<AEKey> keys = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                CompoundTag tag = buf.readNbt();
                if (tag == null) continue;
                AEKey k = AEKey.fromTagGeneric(buf.registryAccess(), tag);
                if (k != null) keys.add(k);
            }
            return new Mode2ConfigPacket(keys, chunkIndex, totalChunks, false);
        }

        return new Mode2ConfigPacket(type, buf.readLong(), buf.readUtf());
    }

    @Override
    public Type<Mode2ConfigPacket> type() {
        return TYPE;
    }

    public static void handle(final Mode2ConfigPacket p, final IPayloadContext ctx) {
        // 服务端 → 客户端：仅 type 4/9/12（面板/规则/黑名单数据响应）
        if (ctx.flow().isClientbound()) {
            if (p.type == 4) {
                ctx.enqueueWork(() -> com.ae2addon.gui.Mode2ConfigScreen.handlePanelDataChunk(
                        p.panelItems, p.chunkIndex, p.totalChunks));
            } else if (p.type == 9) {
                ctx.enqueueWork(() -> com.ae2addon.gui.Mode2ConfigScreen.handleRuleData(
                        p.isTag, p.ruleList, p.ruleInstant));
            } else if (p.type == 12) {
                ctx.enqueueWork(() -> com.ae2addon.gui.Mode2ConfigScreen.handleBlacklistChunk(
                        p.blacklistKeys, p.chunkIndex, p.totalChunks));
            }
            return;
        }

        // 客户端 → 服务端
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof UniversalStorageCell)) {
                stack = player.getOffhandItem();
                if (!(stack.getItem() instanceof UniversalStorageCell)) return;
            }

            // 通过 UnlimitedCellInventory 操作数据（不再直接读写 NBT）
            UnlimitedCellInventory inv = new UnlimitedCellInventory(stack, null);

            if (p.type == 0) {
                // 设置阈值
                inv.setThreshold(p.threshold);

            } else if (p.type == 1 && !p.clickedStack.isEmpty()) {
                // 加入白名单（自动检测流体容器）
                handleAddToWhitelist(inv, p.clickedStack);

            } else if (p.type == 2 && !p.itemId.isEmpty()) {
                // 移除白名单项
                handleRemoveFromWhitelist(inv, p.itemId, player.level().registryAccess());

            } else if (p.type == 3) {
                sendRuleData(inv, player);
                sendBlacklistData(inv, player);
                sendPanelRefresh(inv, player);
                return;

            } else if (p.type == 5 && p.keyTag != null) {
                handleToggleInfinite(inv, p.keyTag, player);
                return;

            } else if (p.type == 13 && p.directKey != null) {
                // JEI 拖取流体/化学物/物品：AEKey 直加白名单
                inv.addWl(p.directKey);
                sendPanelRefresh(inv, player);
                return;

            } else if (p.type == 6) {
                inv.setWorkMode(p.workMode);
                sendPanelRefresh(inv, player);
                return;

            } else if (p.type == 7 && !p.rule.isEmpty()) {
                // 添加 tag/mod 规则（先验证存在性，再写入库存规则 API）
                boolean added;
                if (p.isTag) {
                    if (!ruleExists(p.rule)) {
                        player.sendSystemMessage(Component.translatable("gui.ae2addon.mode2.tag_not_found", p.rule));
                        return;
                    }
                    added = inv.addTagRule(p.rule);
                } else {
                    if (!modExists(p.rule)) {
                        player.sendSystemMessage(Component.translatable("gui.ae2addon.mode2.mod_not_found", p.rule));
                        return;
                    }
                    added = inv.addModRule(p.rule);
                }
                if (added) {
                    player.sendSystemMessage(Component.translatable(
                            "gui.ae2addon.mode2.rule_added", p.isTag ? "tag" : "mod", p.rule));
                }
                sendRuleData(inv, player);
                sendPanelRefresh(inv, player);
                return;

            } else if (p.type == 8 && !p.rule.isEmpty()) {
                // 移除 tag/mod 规则
                if (p.isTag) {
                    inv.removeTagRule(p.rule);
                } else {
                    inv.removeModRule(p.rule);
                }
                sendRuleData(inv, player);
                sendPanelRefresh(inv, player);
                return;

            } else if (p.type == 10) {
                // 设置规则生效模式（true=立即全量，false=触碰后）
                inv.setRuleInstant(p.ruleInstant);
                sendRuleData(inv, player);
                sendPanelRefresh(inv, player);
                return;

            } else if (p.type == 11 && p.blacklistKeyTag != null) {
                // 切换黑名单
                AEKey key = AEKey.fromTagGeneric(player.level().registryAccess(), p.blacklistKeyTag);
                if (key != null) {
                    boolean nowBlacklisted = inv.toggleBlacklist(key);
                    player.sendSystemMessage(Component.translatable(
                            nowBlacklisted ? "gui.ae2addon.mode2.blacklist_added" : "gui.ae2addon.mode2.blacklist_removed",
                            key.getDisplayName()));
                }
                sendRuleData(inv, player);
                sendBlacklistData(inv, player);
                sendPanelRefresh(inv, player);
                return;
            }

            // type 0/1/2 都重刷面板
            if (p.type == 0 || p.type == 1 || p.type == 2) {
                sendPanelRefresh(inv, player);
            }
        });
    }

    /** 分包发送面板刷新数据：每包最多 MAX_ITEMS_PER_CHUNK 个 item */
    private static void sendPanelRefresh(UnlimitedCellInventory inv, ServerPlayer player) {
        List<UnlimitedCellInventory.PanelItem> allItems = inv.getPanelItems();
        int total = allItems.size();
        int totalChunks = (total + MAX_ITEMS_PER_CHUNK - 1) / MAX_ITEMS_PER_CHUNK;
        if (totalChunks == 0) totalChunks = 1;

        for (int i = 0; i < totalChunks; i++) {
            int from = i * MAX_ITEMS_PER_CHUNK;
            int to = Math.min(from + MAX_ITEMS_PER_CHUNK, total);
            List<UnlimitedCellInventory.PanelItem> chunk = allItems.subList(from, to);
            PacketDistributor.sendToPlayer(player, new Mode2ConfigPacket(chunk, i, totalChunks));
        }
    }

    /** 发送 tags/mods 规则列表（type 9，分两包：tags + mods），数据来自库存规则 API */
    private static void sendRuleData(UnlimitedCellInventory inv, ServerPlayer player) {
        List<String> tags = new ArrayList<>(inv.getTags());
        List<String> mods = new ArrayList<>(inv.getMods());
        boolean instant = inv.isRuleInstant();
        PacketDistributor.sendToPlayer(player, new Mode2ConfigPacket(tags, true, instant));
        PacketDistributor.sendToPlayer(player, new Mode2ConfigPacket(mods, false, instant));
    }

    /** 分包发送黑名单（type 12）：每包最多 MAX_ITEMS_PER_CHUNK 个，数据来自库存规则 API */
    private static void sendBlacklistData(UnlimitedCellInventory inv, ServerPlayer player) {
        List<AEKey> keys = new ArrayList<>(inv.getBlacklist());
        int total = keys.size();
        int totalChunks = (total + MAX_ITEMS_PER_CHUNK - 1) / MAX_ITEMS_PER_CHUNK;
        if (totalChunks == 0) totalChunks = 1;

        for (int i = 0; i < totalChunks; i++) {
            int from = i * MAX_ITEMS_PER_CHUNK;
            int to = Math.min(from + MAX_ITEMS_PER_CHUNK, total);
            List<AEKey> chunk = keys.subList(from, to);
            PacketDistributor.sendToPlayer(player, new Mode2ConfigPacket(chunk, i, totalChunks, false));
        }
    }

    /** tag 是否存在（物品或流体注册表里有该 tag 即可） */
    private static boolean ruleExists(String tag) {
        try {
            ResourceLocation id = ResourceLocation.tryParse(tag);
            if (id == null) return false;
            TagKey<Item> itemTag = TagKey.create(Registries.ITEM, id);
            if (!BuiltInRegistries.ITEM.getTag(itemTag).isEmpty()) {
                return true;
            }
            TagKey<Fluid> fluidTag = TagKey.create(Registries.FLUID, id);
            if (!BuiltInRegistries.FLUID.getTag(fluidTag).isEmpty()) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** mod 是否已加载 */
    private static boolean modExists(String modId) {
        try {
            return ModList.get().isLoaded(modId);
        } catch (Exception e) {
            return false;
        }
    }

    // ── 白名单操作（通过 UnlimitedCellInventory） ──

    /** 将物品加入白名单（自动检测流体容器） */
    private static void handleAddToWhitelist(UnlimitedCellInventory inv, ItemStack itemStack) {
        // 1. 检测是否是流体容器
        FluidStack fluid = getFluidFromContainer(itemStack);
        if (!fluid.isEmpty() && fluid.getFluid() != Fluids.EMPTY) {
            AEFluidKey fluidKey = AEFluidKey.of(fluid.getFluid());
            if (fluidKey != null && !inv.getWl().contains(fluidKey)) {
                inv.addWl(fluidKey);
            }
            return;
        }

        // 2. 不是流体容器 → 加入物品本身（带 NBT）
        if (itemStack.isEmpty()) return;
        AEItemKey itemKey = AEItemKey.of(itemStack);
        if (itemKey == null) return;

        if (!inv.getWl().contains(itemKey)) {
            inv.addWl(itemKey);
        }
    }

    /** 检测物品是否有流体容器能力 */
    private static FluidStack getFluidFromContainer(ItemStack stack) {
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler != null) {
            return handler.getFluidInTank(0);
        }
        return FluidStack.EMPTY;
    }

    /** 移除白名单项 */
    private static void handleRemoveFromWhitelist(UnlimitedCellInventory inv, String itemId,
                                                  net.minecraft.core.HolderLookup.Provider provider) {
        // 遍历白名单，按 id 匹配移除
        for (AEKey key : inv.getWl()) {
            CompoundTag tag = key.toTagGeneric(provider);
            if (itemId.equals(tag.getString("id"))) {
                inv.removeWl(key);
                return;
            }
        }
    }

    // ── 无限状态切换 + 输出 ──

    /**
     * 切换无限状态。取消无限时若数量过大，打包成物质球（防掉落物过多卡死）。
     */
    private static void handleToggleInfinite(UnlimitedCellInventory inv,
                                              CompoundTag keyTag, ServerPlayer player) {
        if (keyTag == null) return;

        AEKey key = AEKey.fromTagGeneric(player.level().registryAccess(), keyTag);
        if (key == null) return;

        boolean wasInfinite = inv.getUl().contains(key) || inv.getWl().contains(key);
        boolean hasCa = inv.hasCommitedAmount(key);
        long committed = hasCa ? inv.getCommitedAmount(key) : 0;

        inv.togglePanelInfinite(key);

        if (wasInfinite && committed > 0 && key instanceof AEItemKey itemKey) {
            // 取消无限 → 输出承诺数量。数量过大时打包成物质球，避免海量掉落物卡死
            outputOrBall(player, itemKey, committed);
        }

        sendPanelRefresh(inv, player);
    }

    /**
     * 输出物品：优先背包；数量过大（超过背包容量）时打包成物质球交给玩家，
     * 右键物质球可展开取回。绝不产生海量掉落物。
     */
    private static void outputOrBall(ServerPlayer player, AEItemKey itemKey, long amount) {
        // 背包容量估算（36 格 × 最大堆叠）
        long capacity = 36L * itemKey.toStack(1).getMaxStackSize();

        if (amount > capacity) {
            // 打包成物质球
            ItemStack ball = MatterBallItem.makeBall(itemKey, amount, player.level().registryAccess());
            boolean placed = player.addItem(ball);
            if (placed) {
                player.sendSystemMessage(Component.translatable(
                        "gui.ae2addon.matter_ball.given", amount, itemKey.getDisplayName()));
            } else {
                // 背包满 → 只掉 1 个球实体，不会卡死
                ItemEntity entity = new ItemEntity(
                        player.level(),
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        ball
                );
                entity.setPickUpDelay(10);
                player.level().addFreshEntity(entity);
                player.sendSystemMessage(Component.translatable(
                        "gui.ae2addon.matter_ball.dropped", amount, itemKey.getDisplayName()));
            }
            return;
        }

        // 数量小 → 原逻辑：优先背包，溢出则掉落
        int maxStackSize = itemKey.toStack(1).getMaxStackSize();
        long remaining = amount;
        while (remaining > 0) {
            int count = (int) Math.min(remaining, maxStackSize);
            ItemStack outStack = itemKey.toStack(count);
            remaining -= count;
            if (!player.addItem(outStack)) {
                if (!outStack.isEmpty()) {
                    ItemEntity entity = new ItemEntity(
                            player.level(),
                            player.getX(), player.getY() + 0.5, player.getZ(),
                            outStack
                    );
                    entity.setPickUpDelay(10);
                    player.level().addFreshEntity(entity);
                }
            }
        }
    }

    /** 输出物品：优先背包，溢出则掉落 */
    private static void outputItems(ServerPlayer player, AEKey key, long amount) {
        if (!(key instanceof AEItemKey itemKey)) return;

        int maxStackSize = itemKey.toStack(1).getMaxStackSize();
        long remaining = amount;

        while (remaining > 0) {
            int count = (int) Math.min(remaining, maxStackSize);
            ItemStack outStack = itemKey.toStack(count);
            remaining -= count;

            if (!player.addItem(outStack)) {
                if (!outStack.isEmpty()) {
                    ItemEntity entity = new ItemEntity(
                            player.level(),
                            player.getX(), player.getY() + 0.5, player.getZ(),
                            outStack
                    );
                    entity.setPickUpDelay(10);
                    player.level().addFreshEntity(entity);
                }
            }
        }
    }
}
