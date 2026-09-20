package com.ae2addon.part;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.RelativeSide;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.items.misc.WrappedGenericStack;
import appeng.items.parts.PartModels;
import appeng.me.helpers.MachineSource;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;

import com.ae2addon.AE2Addon;
import com.ae2addon.block.FeederHost;
import com.ae2addon.block.InfiniteInterfaceBE;
import com.ae2addon.gui.InfiniteInterfaceMenu;
import com.ae2addon.util.MemoryCardHelper;

/**
 * ME 接口（无限级）· 线缆面板 part（2026-09-02 sensei：面板应能装进机器与线缆之间）。
 * <p>
 * 挂在 AE2 线缆上的部件形态：右键装到线缆上，面板朝 {@link #getSide()} 方向伸出喂机器——
 * 机器贴着线缆即可供料，不额外占格子。核心供料逻辑与方块版 {@link InfiniteInterfaceBE}
 * 对齐（蓄水池 BigInteger、CPU 直灌、按机器容量喂出、网络空间优先 + 待入网缓存、
 * STORAGE 子网直连），GUI/菜单经 {@link FeederHost} 复用方块版界面。
 * <p>
 * 【2026-09-20 1.21.1 NeoForge 移植说明】上游为 Forge 1.20.1 + AE2 15：本文件已按本地
 * AE2 19 Part 体系改写——{@code IPart#onPartActivate/onPartShiftActivate} 已移除，交互统一走
 * {@code onUseItemOn}；NBT 走 {@code writeToNBT/readFromNBT(CompoundTag, HolderLookup.Provider)}；
 * 能力暴露不再有 {@code getCapability(Capability)}(Forge LazyOptional)，改走 getter
 * （NeoForge 1.21 的能力注册只覆盖 BlockEntity，线缆 part 需由 host 侧转发，见文件末尾）。
 */
public class InfiniteInterfacePart extends AEBasePart
        implements FeederHost, ICraftingProvider, IGridTickable, ICraftingRequester,
        appeng.helpers.patternprovider.PatternContainer, IUpgradeableObject {

    /** 面板模型基名（模型 json/贴图由 ClientSetup 侧注册，本文件不注册资源）。 */
    public static final ResourceLocation MODEL_BASE =
            ResourceLocation.fromNamespaceAndPath(AE2Addon.MODID, "part/infinite_interface_panel");

    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE);

    /** 活跃 part 注册表（供 CPU mixin / 方块版静态取消回退遍历：面板形态也要能收推送并回退）。 */
    public static final Set<InfiniteInterfacePart> ACTIVE_PARTS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ── 状态（与方块版对齐；数值口径引用方块版 public static 配置常量） ──

    private final IUpgradeInventory upgrades;

    private final Map<AEKey, BigInteger> reservoir = new LinkedHashMap<>();
    /** 蓄水池中当前含样板推送材料的 key（标记喂出关闭时只喂这些）。 */
    private final Set<AEKey> patternKeys = new HashSet<>();
    /** 待入网缓存（网络拒收/空间不足的抽取产物，网络恢复后自动补送）。 */
    private final Set<AEKey> pendingNetworkKeys = new HashSet<>();

    private final SimpleContainer patternInv = new SimpleContainer(45) {
        @Override
        public void setChanged() {
            super.setChanged();
            onPatternsChanged();
        }

        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            // 页授权：未插容量卡只有第 1 页（9 格），每张卡 +9 格
            if (index >= 9 + capacityCards() * 9) {
                return false;
            }
            return PatternDetailsHelper.isEncodedPattern(stack);
        }
    };

    private final SimpleContainer markerInv = new SimpleContainer(45) {
        @Override
        public void setChanged() {
            super.setChanged();
            onMarkersChanged(); // 标记变更 → 退缓存检查（消失的标记退回网络）
        }

        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            if (index >= 9 + capacityCards() * 9) {
                return false;
            }
            // 标记槽只收虚拟标记（WGS）：真实物品一律拒绝（物品留在手上，标记请用左键/右键/JEI）
            if (stack.isEmpty()) {
                return true;
            }
            return stack.getItem() instanceof WrappedGenericStack;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            // 防御（对齐方块版 2026-08-28）：任何路径（拖拽/管道/代码）放入真实物品 →
            // 自动转虚拟标记，绝不吞物品（容器只存 WrappedGenericStack）
            if (!stack.isEmpty() && !(stack.getItem() instanceof WrappedGenericStack)) {
                AEKey key = keyOfStack(stack);
                if (key != null) {
                    super.setItem(index, WrappedGenericStack.wrap(key, 1));
                    return;
                }
                super.setItem(index, ItemStack.EMPTY);
                return;
            }
            super.setItem(index, stack);
        }
    };

    private final IActionSource actionSource = new MachineSource(this::getActionableNode);

    // ── 每接口独立参数（-1 = 用全局配置；内存卡可复制） ──

    private long pStockTarget = -1;
    private int pRestockInterval = -1;
    private int pFeedBudget = -1;

    // ── 开关/方向（默认全开；GUI 内切换） ──

    private boolean activeExtract = true;
    private boolean activeFeed = true;
    /** 标记喂出（标记补货缓存 → 机器；关闭时只喂样板推送材料）。 */
    private boolean activeMarkerFeed = true;
    /** 主动抽取方向（相对面；FRONT = 面板伸出方向）。 */
    private RelativeSide extractSide = RelativeSide.FRONT;

    // ── 样板/统计 ──

    private boolean patternDirty = true;
    private List<IPatternDetails> patterns = List.of();
    private boolean removed;
    private long rateWindowFed;
    private long currentFeedRate;
    private long rejectWindow;
    private long currentRejectRate;
    /** 喂出失败诊断限流：同一卡住状态最多每 6000 tick 打一次（防刷屏）。2026-09-20 缺口修复（审查发现）。 */
    private long lastFeederFailLogTick;
    private BigInteger totalFed = BigInteger.ZERO;

    /** 每标记独立缓存目标（中键循环切换；缺省用全局 STOCK_TARGET）。 */
    private final Map<AEKey, Long> markerTargets = new LinkedHashMap<>();

    /** 中键循环档位：1K → 10K → 100K → 1M → MAX → 1K…（与方块版一致）。 */
    private static final long[] TARGET_STEPS = {1_000L, 10_000L, 100_000L, 1_000_000L, Long.MAX_VALUE};

    /** 上次标记的 key 集合（变化检测：消失的标记 → 退回蓄水池缓存）。 */
    private Set<AEKey> lastMarkedKeys = java.util.Collections.emptySet();

    // ── CPU 推送归属（取消回退用） ──

    /** CPU 任务推送到本面板的材料归属：CPU簇 → 物品 → 数量。 */
    private final Map<Object, Map<AEKey, BigInteger>> pushedByCluster = new HashMap<>();

    // ── 虚拟合成卡 ──

    /** key → 上次发起合成请求的 gameTime（防重复请求节流）。 */
    private final Map<AEKey, Long> craftingRequests = new HashMap<>();
    /** 同 key 合成请求冷却（tick；5 秒，与方块版一致）。 */
    private static final long CRAFT_COOLDOWN = 100;

    /** 频道卡无线从端链路（ExtendedAE+；惰性持有——缺依赖不崩类加载）。 */
    private final com.ae2addon.compat.ExtendedAEPlusCompat.ChannelLink channelLink =
            new com.ae2addon.compat.ExtendedAEPlusCompat.ChannelLink();

    public InfiniteInterfacePart(IPartItem<?> partItem) {
        super(partItem);
        // 升级槽 9 格：容量卡（页数）/速度卡（喂出预算×2、供电倍率）/红石/反向/合成/
        // 感应（AppFlux 供电）/频道（ExtendedAE+ 无线）全部共用同一库存
        this.upgrades = UpgradeInventories.forMachine(partItem.asItem(), 9, this::onUpgradesChanged);
        // 2026-09-20 缺口修复（审查发现）：世界加载时触发跨 mod 升级卡懒注册
        // （AppFlux/ExtendedAE+ 卡 + 容量/红石/合成等卡注册到 panel 物品）。纯面板存档
        // （从未放过方块版）此前没有任何触发点，插卡会被升级注册表拒绝；本行与方块版
        // InfiniteInterfaceBE 构造器里的同一行等价（幂等，失败下次重试）。
        AE2Addon.ensureCompatUpgrades();
    }

    // ── 网格节点：注册为合成 provider + tick 服务（CPU 才能找到我们推样板） ──

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(ICraftingProvider.class, this)
                .addService(IGridTickable.class, this);
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        removed = false;
        ACTIVE_PARTS.add(this);
    }

    @Override
    public void removeFromWorld() {
        channelLink.unload(); // 频道卡无线链路断开（与方块版生命周期一致）
        super.removeFromWorld();
        removed = true;
        ACTIVE_PARTS.remove(this);
    }

    /** 任意状态变更（GUI/网络包/标记）→ 标记存档，防重启丢配置（AEBasePart 无 setChanged）。 */
    @Override
    public void setChanged() {
        var host = getHost();
        if (host != null) {
            host.markForSave();
        }
    }

    // ── 模型/碰撞 ──

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        // 面板盒：贴线缆面伸出（side 方向），2/16 厚视觉 + 少许连接段
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS_OFF;
    }

    @Override
    public float getCableConnectionLength(appeng.api.util.AECableType cable) {
        return 4;
    }

    // ── 方向：正面 = side（机器所在侧） ──

    @Override
    @Nullable
    public Direction getFront() {
        return getSide();
    }

    /** 相对面 → 世界方向（面板所在 side 为基准）。 */
    @Nullable
    private Direction resolveDir(RelativeSide rel) {
        Direction side = getSide();
        if (side == null) {
            return null;
        }
        return switch (rel) {
            case FRONT -> side;
            case BACK -> side.getOpposite();
            case LEFT -> side.getClockWise();
            case RIGHT -> side.getCounterClockWise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }

    // ── tick（IGridTickable；每 tick 由网格调度） ──

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, false, 1);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (removed || isClientSide()) {
            return TickRateModulation.IDLE;
        }
        Level lvl = getLevel();
        if (lvl == null || getBlockEntity() == null) {
            return TickRateModulation.SLEEP; // 尚未挂到线缆 host（防御：getBlockPos 依赖它）
        }
        long t = lvl.getGameTime();
        if (patternDirty) {
            rebuildPatterns();
        }
        if ((t & 0x3F) == 0) {
            updateChannelLink(); // 每 3 秒刷新无线链路（主端变动/延迟连接）
        }
        if ((t & 19) == 0) {
            currentFeedRate = rateWindowFed;
            rateWindowFed = 0;
            currentRejectRate = rejectWindow;
            rejectWindow = 0;
        }
        if ((t % Math.max(1, restockIntervalValue())) == 0) {
            restockFromNetwork();
        }
        if ((t % Math.max(1, InfiniteInterfaceBE.EXTRACT_INTERVAL)) == 0) {
            extractFromMachine();
        }
        if ((t % 10) == 0) {
            pushPendingToNetwork();
        }
        if ((t % 20) == 0) {
            retryPendingReturns(); // 取消回退滞留重试（断网/拒收恢复后自动补退）
        }
        feedMachinePower(); // 感应卡供电独立于喂出（蓄水池空也供电）
        feedMachine();
        return TickRateModulation.IDLE;
    }

    // ── ICraftingProvider：无条件接收 CPU 直灌 N× ──

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return patterns;
    }

    /**
     * 无条件收下 CPU 推送的样板输入（含 ScaledPattern N× 缩放后的 KeyCounter）。
     * 输入全部进蓄水池，CPU 推完即走，不阻塞。
     */
    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs) {
        Object pusher = com.ae2addon.crafting.CraftingCompat.currentPushingCluster;
        Map<AEKey, BigInteger> perCluster = null;
        if (pusher != null) {
            perCluster = pushedByCluster.computeIfAbsent(pusher, k -> new HashMap<>());
        }
        long inputTypes = 0;
        long inputCount = 0;
        if (inputs != null) {
            for (KeyCounter kc : inputs) {
                if (kc == null) {
                    continue;
                }
                for (var entry : kc) {
                    AEKey key = entry.getKey();
                    long amount = entry.getLongValue();
                    if (key != null && amount > 0) {
                        addReservoir(key, BigInteger.valueOf(amount));
                        patternKeys.add(key);
                        inputTypes++;
                        inputCount += amount;
                        if (perCluster != null) {
                            perCluster.merge(key, BigInteger.valueOf(amount), BigInteger::add);
                        }
                    }
                }
            }
        }
        // 2026-09-20 缺口修复（审查发现）：推送接收诊断（照方块版 pushPattern 的
        // [ae2addon][feeder] 前缀风格）——面板收不到 CPU 推送时用它定位
        String[] summary = reservoirSummary();
        AE2Addon.LOGGER.info(
                "[ae2addon][feeder] pushPattern 接收(part) pattern={} 本次{}种/{}个 → 蓄水池={}种/合计{}（推送源={}）",
                patternDetails == null ? "null" : patternDetails.getClass().getSimpleName(),
                inputTypes, inputCount, summary[0], summary[1],
                pusher == null ? "外部/未知" : pusher.getClass().getSimpleName());
        setChanged();
        return true; // 蓄水池无限，永不拒收
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    // ── CPU 任务取消回退（与方块版 BE 同一触发链） ──

    /** 取消回退：把指定 CPU 簇推送、尚未喂出的材料插回网络（不丢料加固）。 */
    private void returnPushedForCluster(Object cluster) {
        Map<AEKey, BigInteger> pushed = pushedByCluster.get(cluster);
        if (pushed == null || pushed.isEmpty()) {
            return;
        }
        IGrid grid = getMainNode().getGrid();
        MEStorage storage = grid == null ? null : grid.getStorageService().getInventory();
        BigInteger returned = BigInteger.ZERO;
        java.util.Iterator<Map.Entry<AEKey, BigInteger>> it = pushed.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            AEKey key = entry.getKey();
            long have = reservoirAmount(key);
            long back = entry.getValue().min(BigInteger.valueOf(have))
                    .min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            if (back <= 0) {
                it.remove(); // 池里已无该料：清记账防滞留重试空转
                continue;
            }
            if (storage == null) {
                // 断网：不退不扣，记账保留 → 周期重试（不丢料）
                break;
            }
            long inserted = storage.insert(key, back, Actionable.MODULATE, actionSource);
            if (inserted > 0) {
                subtractReservoir(key, inserted);
                returned = returned.add(BigInteger.valueOf(inserted));
                long remain = entry.getValue().subtract(BigInteger.valueOf(inserted)).longValue();
                if (remain <= 0) {
                    it.remove(); // 该 key 全部退完
                } else {
                    entry.setValue(BigInteger.valueOf(remain)); // 部分退：记账保留余量
                }
            }
            // inserted == 0：网络拒收（满/瞬态），记账保留 → 周期重试
        }
        if (pushed.isEmpty()) {
            pushedByCluster.remove(cluster); // 全部退完才删簇账
        }
        if (returned.signum() > 0) {
            setChanged();
        }
    }

    /** 滞留回退重试：断网/拒收时没退完的簇记账，网格恢复后自动补退（每 20 tick）。 */
    private void retryPendingReturns() {
        if (pushedByCluster.isEmpty()) {
            return;
        }
        if (getMainNode().getGrid() == null) {
            return; // 仍断网，等下次
        }
        for (Object cluster : new ArrayList<>(pushedByCluster.keySet())) {
            returnPushedForCluster(cluster); // 保留的记账会重试，退完自动删
        }
    }

    /** 新任务开始：清空该簇归属记录（材料保留在面板，正常交付语义）。 */
    private void resetPushedForCluster(Object cluster) {
        pushedByCluster.remove(cluster);
    }

    /** 供方块版静态取消回退遍历调用（面板形态与方块形态共用同一 CPU 取消链）。 */
    public void returnPushedForClusterPublic(Object cluster) {
        returnPushedForCluster(cluster);
    }

    /** 供方块版静态重置遍历调用。 */
    public void resetPushedForClusterPublic(Object cluster) {
        resetPushedForCluster(cluster);
    }

    /** 手动「退回网络」（2026-09-06 sensei）：蓄水池全部材料插回网络（同方块版）。 */
    @Override
    public boolean returnAllToNetwork() {
        IGrid grid = getMainNode().getGrid();
        MEStorage storage = grid == null ? null : grid.getStorageService().getInventory();
        if (storage == null) {
            return false; // 未连网退不回（料留在蓄水池，不丢）
        }
        BigInteger returned = BigInteger.ZERO;
        int types = 0;
        int stuck = 0;
        for (AEKey key : new ArrayList<>(reservoir.keySet())) {
            long amt = reservoirAmount(key);
            if (amt <= 0) {
                continue;
            }
            long inserted = storage.insert(key, amt, Actionable.MODULATE, actionSource);
            if (inserted > 0) {
                subtractReservoir(key, inserted);
                returned = returned.add(BigInteger.valueOf(inserted));
                if (reservoirAmount(key) <= 0) {
                    types++;
                }
            } else {
                stuck++; // 网络拒收（满/不可存）→ 留在蓄水池待下次
            }
        }
        pushedByCluster.clear(); // 手动全退：簇推送记账作废，防 CPU cancel 二次回退
        if (returned.signum() > 0) {
            setChanged();
            AE2Addon.LOGGER.info(
                    "[ae2addon][feeder] 手动退回网络 {} 个（{}种清空，{}种拒收留池）→ 面板蓄水池剩余{}/合计{}",
                    InfiniteInterfaceBE.fmt(returned), types, stuck,
                    reservoirSummary()[0], InfiniteInterfaceBE.fmt(reservoirTotal()));
            return true;
        }
        return false;
    }

    // ── 蓄水池 ──

    private void addReservoir(AEKey key, BigInteger amount) {
        if (amount.signum() <= 0) {
            return;
        }
        reservoir.merge(key, amount, BigInteger::add);
    }

    private void subtractReservoir(AEKey key, long amount) {
        if (amount <= 0) {
            return;
        }
        reservoir.computeIfPresent(key, (k, v) -> {
            BigInteger next = v.subtract(BigInteger.valueOf(amount));
            if (next.signum() <= 0) {
                patternKeys.remove(key);
                pendingNetworkKeys.remove(key);
                return null;
            }
            return next;
        });
    }

    private long reservoirAmount(AEKey key) {
        BigInteger amt = reservoir.get(key);
        return amt == null ? 0 : amt.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    /** 蓄水池合计（BigInteger）。 */
    private BigInteger reservoirTotal() {
        BigInteger total = BigInteger.ZERO;
        for (BigInteger v : reservoir.values()) {
            if (v.signum() > 0) {
                total = total.add(v);
            }
        }
        return total;
    }

    /** 网络收不下/断网的料先记在待入网缓存，网络恢复后自动补送。 */
    private void cacheForNetwork(AEKey key, long amount) {
        if (amount <= 0) {
            return;
        }
        addReservoir(key, BigInteger.valueOf(amount));
        pendingNetworkKeys.add(key);
        setChanged();
    }

    /** 待入网缓存自动补送（每 10 tick）。 */
    private void pushPendingToNetwork() {
        if (pendingNetworkKeys.isEmpty()) {
            return;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        MEStorage storage = grid.getStorageService().getInventory();
        for (AEKey key : new ArrayList<>(pendingNetworkKeys)) {
            BigInteger amt = reservoir.get(key);
            if (amt == null || amt.signum() <= 0) {
                pendingNetworkKeys.remove(key);
                continue;
            }
            long want = amt.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            long inserted;
            try {
                inserted = storage.insert(key, want, Actionable.MODULATE, actionSource);
            } catch (RuntimeException e) {
                continue;
            }
            if (inserted > 0) {
                subtractReservoir(key, inserted);
                setChanged();
            }
        }
    }

    // ── 补货：网络 → 蓄水池（按标记目标） ──

    private void restockFromNetwork() {
        cleanupStrayItems(); // 标记槽误存的真实物品退回网络（防吞材料）
        if (stockTargetValue() <= 0) {
            return; // 配置关闭自动补货（只收 CPU 推送）
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        MEStorage storage = grid.getStorageService().getInventory();
        for (AEKey key : wantedKeys()) {
            long have = reservoirAmount(key);
            long target = targetFor(key);
            long want = target - have;
            if (want < 0) {
                // 目标调小：超出部分退回网络（只补不减 bug 修复，对齐方块版）
                long excess = -want;
                try {
                    long back = storage.insert(key, excess, Actionable.MODULATE, actionSource);
                    if (back > 0) {
                        subtractReservoir(key, back);
                        setChanged();
                    }
                } catch (RuntimeException ignored) {
                }
                continue;
            }
            if (want == 0) {
                continue;
            }
            long got = storage.extract(key, want, Actionable.MODULATE, actionSource);
            if (got > 0) {
                addReservoir(key, BigInteger.valueOf(got));
                setChanged();
            } else if (hasCraftingCard() && isCraftable(key)) {
                // 虚拟合成卡：网络没有 → 请求 CPU 合成
                requestCrafting(key, want);
            }
        }
    }

    /**
     * 待补物品 = 标记槽里的虚拟标记（样板输入不自动补货——定量语义）。
     * <p>
     * 2026-09-20 缺口修复（审查发现）：补齐方块版 wantedKeys 的「含容器兜底」三级判定
     * （WGS → 流体容器内含流体 → 化学物容器内含气体 → 物品本体）。面板侧标记槽正常路径
     * 只存 WGS（真实物品会被容器 setItem 虚拟化 / cleanupStrayItems 清掉），兜底用于
     * 旧存档与异常写入路径；另比方块版多加 null 守卫（AEFluidKey.of 可能返回 null，
     * 直接进集合会让补货循环对 null key 取网络存储）。
     */
    private Set<AEKey> wantedKeys() {
        Set<AEKey> keys = new HashSet<>();
        for (int i = 0; i < markerInv.getContainerSize(); i++) {
            ItemStack stack = markerInv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            // ① 右键标记的 WrappedGenericStack（流体/气体等任意 key）→ 直接取 key
            if (stack.getItem() instanceof WrappedGenericStack wgs) {
                AEKey wrapped = wgs.unwrapWhat(stack);
                if (wrapped != null) {
                    keys.add(wrapped);
                    continue;
                }
            }
            // ② 旧方式兜底：流体容器（桶/罐/蓄液罐）→ 标记内部流体
            var contained = FluidUtil.getFluidContained(stack);
            if (contained.isPresent() && !contained.get().isEmpty()) {
                AEFluidKey fluidKey = AEFluidKey.of(contained.get().getFluid());
                if (fluidKey != null) {
                    keys.add(fluidKey);
                    continue;
                }
            }
            // ③ 化学物容器（气罐/气桶）→ 标记内部化学物（Mekanism 可选集成）
            AEKey chemKey = com.ae2addon.compat.MekanismChemCompat.chemicalInContainer(stack);
            if (chemKey != null) {
                keys.add(chemKey);
                continue;
            }
            // ④ 普通物品本体
            AEItemKey itemKey = AEItemKey.of(stack);
            if (itemKey != null) {
                keys.add(itemKey);
            }
        }
        return keys;
    }

    /** 槽内堆叠 → AEKey（WrappedGenericStack 解包；普通物品转 itemKey）。 */
    @Nullable
    private static AEKey keyOfStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof WrappedGenericStack wgs) {
            return wgs.unwrapWhat(stack);
        }
        return AEItemKey.of(stack);
    }

    /**
     * 清理标记槽里的真实物品（非 WrappedGenericStack）：shift 快捷移动可能绕过虚拟化
     * 拦截直接写入容器导致材料被吞（对齐方块版 2026-08-28 BUG 修复）。
     */
    private void cleanupStrayItems() {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        MEStorage storage = grid.getStorageService().getInventory();
        boolean changed = false;
        for (int i = 0; i < markerInv.getContainerSize(); i++) {
            ItemStack stack = markerInv.getItem(i);
            if (stack.isEmpty() || stack.getItem() instanceof WrappedGenericStack) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key != null && storage != null) {
                long inserted = storage.insert(key, stack.getCount(), Actionable.MODULATE, actionSource);
                AE2Addon.LOGGER.warn("[ae2addon][feeder] 清理标记槽误存物品(part): {} x{} 已退回网络",
                        key, inserted);
            }
            markerInv.setItem(i, ItemStack.EMPTY);
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    // ── 喂出：蓄水池 → 机器（正面 side 方向） ──

    private void feedMachine() {
        if (!activeFeed) {
            return; // GUI 开关：主动喂出关闭
        }
        if (!redstoneAllowsFeed()) {
            return; // 感应卡（红石卡）：信号不允许时暂停喂出
        }
        Level lvl = getLevel();
        Direction front = getFront();
        if (lvl == null || front == null) {
            return;
        }
        BlockEntity target = lvl.getBlockEntity(getBlockPos().relative(front));
        if (target == null) {
            return;
        }
        Direction machineSide = front.getOpposite();
        IItemHandler handler = findItemHandler(target, machineSide);
        IFluidHandler fluidHandler = findFluidHandler(target, machineSide);
        int slots = handler == null ? 0 : handler.getSlots();
        if (slots <= 0 && fluidHandler == null) {
            return; // 机器无物品/流体入口（化学物路径单独尝试）
        }
        // 可喂种类数（物品/流体/化学物并行轮转基数）：预算均分，所有种类同时推进
        int feedable = 0;
        for (var entry : reservoir.entrySet()) {
            if ((entry.getKey() instanceof AEItemKey || entry.getKey() instanceof AEFluidKey
                    || com.ae2addon.compat.MekanismGasCompat.isFeedable(entry.getKey()))
                    && entry.getValue().signum() > 0) {
                if (pendingNetworkKeys.contains(entry.getKey())) {
                    continue; // 待入网缓存不喂出
                }
                if (!activeMarkerFeed && !patternKeys.contains(entry.getKey())) {
                    continue; // 标记喂出关闭：只喂样板推送材料
                }
                feedable++;
            }
        }
        if (feedable <= 0) {
            return;
        }
        // 速度卡：每张喂出预算 ×2（最高 ×4；与方块版一致）
        int mult = 1 << Math.min(speedCards(), 2);
        int budget = (int) Math.min((long) Math.max(1, feedBudgetValue()) * mult, 1_000_000);
        int perItemBudget = Math.max(1, budget / feedable);
        int totalBudget = budget;
        long fedAll = 0;
        for (var it = reservoir.entrySet().iterator(); it.hasNext() && totalBudget > 0; ) {
            var entry = it.next();
            AEKey key = entry.getKey();
            if (entry.getValue().signum() <= 0) {
                it.remove();
                continue;
            }
            if (pendingNetworkKeys.contains(key)) {
                continue;
            }
            if (!activeMarkerFeed && !patternKeys.contains(key)) {
                continue;
            }
            long amount = entry.getValue().min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            long fed = 0;
            int itemBudget = perItemBudget;
            if (key instanceof AEItemKey itemKey && handler != null && slots > 0) {
                while (amount > 0 && itemBudget > 0 && totalBudget > 0) {
                    int chunk = (int) Math.min(InfiniteInterfaceBE.FEED_STACK, amount);
                    ItemStack stack = itemKey.toStack(chunk);
                    ItemStack leftover = stack;
                    // 一轮 = 依次尝试所有输入槽（多槽机器并行填满）
                    for (int slot = 0; slot < slots && !leftover.isEmpty(); slot++) {
                        leftover = handler.insertItem(slot, leftover, false);
                    }
                    int inserted = chunk - leftover.getCount();
                    if (inserted <= 0) {
                        break; // 该物品本轮拒收 → 换下一种
                    }
                    fed += inserted;
                    amount -= inserted;
                    itemBudget--;
                    totalBudget--;
                }
            } else if (key instanceof AEFluidKey fluidKey && fluidHandler != null) {
                // 流体喂出：fill 到机器液体槽
                while (amount > 0 && itemBudget > 0 && totalBudget > 0) {
                    int chunk = (int) Math.min(Integer.MAX_VALUE, amount);
                    FluidStack fs = fluidKey.toStack(chunk);
                    int filled = fluidHandler.fill(fs, FluidAction.EXECUTE);
                    if (filled <= 0) {
                        break; // 机器液体槽满/不吃该流体
                    }
                    fed += filled;
                    amount -= filled;
                    itemBudget--;
                    totalBudget--;
                }
            } else if (com.ae2addon.compat.MekanismGasCompat.isFeedable(key)) {
                // 化学物喂出：insertChemical 到机器化学槽（Mekanism 可选集成）
                while (amount > 0 && itemBudget > 0 && totalBudget > 0) {
                    long fedOnce = com.ae2addon.compat.MekanismChemCompat.feed(
                            target, machineSide, key, amount);
                    if (fedOnce <= 0) {
                        break; // 机器化学槽满/不吃该化学物
                    }
                    fed += fedOnce;
                    amount -= fedOnce;
                    itemBudget--;
                    totalBudget--;
                }
            }
            if (fed > 0) {
                // ⚠️ 用 entry.setValue/it.remove 而非 computeIfPresent(null)：
                // 迭代中通过 map 删除会 ConcurrentModificationException（潜在崩溃）
                BigInteger next = entry.getValue().subtract(BigInteger.valueOf(fed));
                if (next.signum() > 0) {
                    entry.setValue(next);
                } else {
                    patternKeys.remove(key);
                    it.remove();
                }
                fedAll += fed;
            }
        }
        if (fedAll > 0) {
            totalFed = totalFed.add(BigInteger.valueOf(fedAll));
            rateWindowFed += fedAll;
            setChanged();
        } else {
            rejectWindow++; // 有货但整 tick 零喂出 → 机器满/拒收（诊断瓶颈）
            // 2026-09-20 缺口修复（审查发现）：喂出失败诊断（照方块版 [ae2addon][feeder]
            // 前缀风格；限流每 6000 tick 一条，防刷屏）
            long nowTick = lvl.getGameTime();
            if (nowTick - lastFeederFailLogTick >= 6000) {
                lastFeederFailLogTick = nowTick;
                AE2Addon.LOGGER.info(
                        "[ae2addon][feeder] 喂出失败(part) 整 tick 零喂出：可喂{}种，机器物品槽={} 流体槽={}，蓄水池={}种/合计{}（5分钟内不再重复）",
                        feedable, handler != null, fluidHandler != null,
                        reservoirSummary()[0], InfiniteInterfaceBE.fmt(totalAmount()));
            }
        }
    }

    // ── 主动抽取：机器 → 网络/待入网缓存 ──

    /**
     * 主动抽取（对齐方块版 2026-08-28 sensei）：从指定方向机器抽物品/流体/化学物 →
     * 网络；网络收不下的进待入网缓存。防回流：标记材料（标记列表/蓄水池中）跳过。
     */
    private void extractFromMachine() {
        if (!activeExtract) {
            return; // GUI 开关：主动抽取关闭
        }
        Level lvl = getLevel();
        Direction dir = resolveDir(extractSide);
        if (lvl == null || dir == null) {
            return;
        }
        BlockEntity target = lvl.getBlockEntity(getBlockPos().relative(dir));
        if (target == null) {
            return;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        MEStorage storage = grid.getStorageService().getInventory();
        Direction side = dir.getOpposite();
        try {
            // 物品：遍历所有槽找产物（标记材料跳过）；全部槽轮流抽
            // 2026-09-20 缺口修复（审查发现）：旧写法先 extractItem(SIMULATE) 累出总量 →
            // 按虚报量 storage.insert → 再真抽且忽略差额：容器 simulate 虚报时网络凭空多出
            // 材料（复制物品）。现照方块版形态：先 getStackInSlot 探真实内容（不 simulate）→
            // 循环 extractItem(..., false) 真抽累计（受 EXTRACT_LOOP_CAP 约束）→ 按实得量
            // storage.insert → 插不进的 cacheForNetwork；槽内容变化时把异物塞回/缓存。
            IItemHandler handler = findItemHandler(target, side);
            if (handler != null && handler.getSlots() > 0) {
                int loopCap = com.ae2addon.config.AE2AddonConfig.feederExtractLoopCap();
                boolean looping = loopCap > 0; // 0 = 关循环，仅单次钳制量
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    // 防回流 probe：直接看槽内容（巨型容器的 simulate 会虚报可抽量）
                    ItemStack probe = handler.getStackInSlot(slot);
                    if (probe.isEmpty()) {
                        continue;
                    }
                    AEItemKey key = AEItemKey.of(probe);
                    if (key == null || isMarkedMaterial(key)) {
                        continue;
                    }
                    // ⚠️ 部分机器的 IItemHandler 把单次 extractItem 钳制在物品最大堆叠
                    // （Mekanism 箱柜 = min(槽内数量, maxStackSize)），配置的大抽取量一次拿不完
                    // → 真抽循环累计（对齐方块版 2026-08-29 修复）。循环上限可配置（0=关循环）。
                    long remaining = Math.min(probe.getCount(), (long) InfiniteInterfaceBE.EXTRACT_STACK);
                    long gotTotal = 0;
                    int guard = 0;
                    do {
                        int want = (int) Math.min(remaining,
                                looping ? (long) loopCap - gotTotal : remaining);
                        if (want <= 0) {
                            break;
                        }
                        ItemStack part = handler.extractItem(slot, want, false); // 真抽（EXECUTE）
                        if (part.isEmpty()) {
                            break; // 槽被抽空/拒给
                        }
                        AEItemKey partKey = AEItemKey.of(part);
                        if (partKey == null || !partKey.equals(key)) {
                            // 槽内容变化（防御）：抽出的异物塞回，塞不回则缓存（不丢）
                            try {
                                ItemStack rest = handler.insertItem(slot, part, false);
                                if (!rest.isEmpty()) {
                                    cacheForNetwork(partKey != null ? partKey : key, rest.getCount());
                                }
                            } catch (RuntimeException ignored) {
                            }
                            break;
                        }
                        int n = part.getCount();
                        gotTotal += n;
                        remaining -= n;
                    } while (looping && remaining > 0 && gotTotal < loopCap && guard++ < 65536);
                    if (gotTotal <= 0) {
                        continue;
                    }
                    // 按真实抽到的量入网；插不进的走待入网缓存（网络满不卡机器）
                    long inserted = storage.insert(key, gotTotal, Actionable.MODULATE, actionSource);
                    long cached = gotTotal - inserted;
                    if (inserted > 0 || cached > 0) {
                        if (cached > 0) {
                            cacheForNetwork(key, cached);
                        }
                        AE2Addon.LOGGER.info("[ae2addon][feeder] 主动抽取(part): {} x{} → 网络{}（槽{}）",
                                key, inserted, cached > 0 ? " + 缓存" + cached : "", slot);
                    }
                }
            }
            // 流体：逐罐先真实 drain 再入网（产物跳过标记材料；多罐机器全罐抽）
            // 2026-09-20 缺口修复（审查发现）：旧写法 drain(SIMULATE) → 按虚报量入网 → 再按
            // inserted+cached EXECUTE 抽，simulate 虚报时差额凭空复制。现与方块版一致：
            // drain(..., EXECUTE) 先真抽 → 按实得量入网 → 余量 cacheForNetwork。
            IFluidHandler fluidHandler = findFluidHandler(target, side);
            if (fluidHandler != null && fluidHandler.getTanks() > 0) {
                for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                    FluidStack inTank = fluidHandler.getFluidInTank(tank);
                    if (inTank.isEmpty()) {
                        continue;
                    }
                    AEFluidKey key = AEFluidKey.of(inTank.getFluid());
                    if (key == null || isMarkedMaterial(key)) {
                        continue;
                    }
                    int want = (int) Math.min(inTank.getAmount(), (long) InfiniteInterfaceBE.EXTRACT_FLUID);
                    if (want <= 0) {
                        continue;
                    }
                    FluidStack drained = fluidHandler.drain(
                            new FluidStack(inTank.getFluid(), want), FluidAction.EXECUTE);
                    if (drained.isEmpty()) {
                        continue;
                    }
                    long got = drained.getAmount();
                    long inserted = storage.insert(key, got, Actionable.MODULATE, actionSource);
                    long cached = got - inserted;
                    if (inserted > 0 || cached > 0) {
                        if (cached > 0) {
                            cacheForNetwork(key, cached);
                        }
                        AE2Addon.LOGGER.info("[ae2addon][feeder] 主动抽取(part): {} {}mB → 网络{}（罐{}）",
                                key, inserted, cached > 0 ? " + 缓存" + cached : "", tank);
                    }
                }
            }
            // 化学物（Mekanism 10.7 统一 CHEMICAL 能力，只抽一次）
            if (com.ae2addon.compat.MekanismGasCompat.isLoaded()) {
                extractChemicalsFrom(target, side, storage);
            }
        } catch (RuntimeException ignored) {
        }
    }

    /** 化学物抽取（Mekanism 10.7 统一 CHEMICAL：气体/灌注/颜料/浆液同一能力）。 */
    private void extractChemicalsFrom(BlockEntity target, Direction side, MEStorage storage) {
        Level tl = target.getLevel();
        mekanism.api.chemical.IChemicalHandler ch = tl == null ? null
                : mekanism.common.capabilities.Capabilities.CHEMICAL.getCapabilityIfLoaded(
                        tl, target.getBlockPos(), side);
        if (ch == null || ch.getChemicalTanks() <= 0) {
            return;
        }
        for (int tank = 0; tank < ch.getChemicalTanks(); tank++) {
            var inTank = ch.getChemicalInTank(tank);
            if (inTank == null || inTank.isEmpty()) {
                continue;
            }
            AEKey key = com.ae2addon.compat.MekanismChemCompat.keyOfChemical(inTank);
            if (key == null || isMarkedMaterial(key)) {
                continue;
            }
            long want = Math.min(inTank.getAmount(), InfiniteInterfaceBE.EXTRACT_GAS);
            if (want <= 0) {
                continue;
            }
            // 2026-09-20 缺口修复（审查发现）：先真实 EXECUTE 抽取再入网（simulate 虚报会导致
            // 差额复制，与物品/流体段同理）——与方块版一致
            var real = ch.extractChemical(tank, want, mekanism.api.Action.EXECUTE);
            if (real == null || real.isEmpty()) {
                continue;
            }
            long got = Math.min(real.getAmount(), want);
            if (got <= 0) {
                continue;
            }
            long inserted = storage.insert(key, got, Actionable.MODULATE, actionSource);
            long cached = got - inserted;
            if (inserted > 0 || cached > 0) {
                // 余量进待入网缓存（网络收不下的不丢）
                if (cached > 0) {
                    cacheForNetwork(key, cached);
                }
                AE2Addon.LOGGER.info("[ae2addon][feeder] 主动抽取(part): {} {}单位 → 网络{}（罐{}）",
                        key, inserted, cached > 0 ? " + 缓存" + cached : "", tank);
            }
        }
    }

    /** 机器物品 handler：指定面优先，找不到遍历其余面（对齐方块版）。 */
    @Nullable
    private static IItemHandler findItemHandler(BlockEntity target, Direction primary) {
        Level tl = target == null ? null : target.getLevel();
        if (tl == null) {
            return null;
        }
        IItemHandler handler = tl.getCapability(Capabilities.ItemHandler.BLOCK,
                target.getBlockPos(), target.getBlockState(), target, primary);
        if (handler != null) {
            return handler;
        }
        for (Direction d : Direction.values()) {
            if (d == primary) {
                continue;
            }
            handler = tl.getCapability(Capabilities.ItemHandler.BLOCK,
                    target.getBlockPos(), target.getBlockState(), target, d);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    /** 机器流体 handler：指定面优先，找不到遍历其余面（对齐方块版）。 */
    @Nullable
    private static IFluidHandler findFluidHandler(BlockEntity target, Direction primary) {
        Level tl = target == null ? null : target.getLevel();
        if (tl == null) {
            return null;
        }
        IFluidHandler handler = tl.getCapability(Capabilities.FluidHandler.BLOCK,
                target.getBlockPos(), target.getBlockState(), target, primary);
        if (handler != null) {
            return handler;
        }
        for (Direction d : Direction.values()) {
            if (d == primary) {
                continue;
            }
            handler = tl.getCapability(Capabilities.FluidHandler.BLOCK,
                    target.getBlockPos(), target.getBlockState(), target, d);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    /**
     * 是否为标记材料（标记列表或蓄水池缓存中）——是则跳过抽取（防回流死循环）。
     * <p>
     * 2026-09-20 缺口修复（审查发现）：① 标记判定改与方块版一致——用 {@link #wantedKeys()}
     * （含容器兜底）而不是只认 WGS 的旧 markerContains，标记集合与补货集合同源；
     * ② 补「待入网缓存不防回流」例外：缓存未送完期间，容器里新产的同种产物不能被
     * 误当「喂入材料」而永不抽取（方块版 2026-09-06 上游同步的同款修复）。
     */
    private boolean isMarkedMaterial(AEKey key) {
        if (wantedKeys().contains(key)) {
            return true;
        }
        // 待入网缓存（从容器抽出的产物，网络满暂存）不防回流
        if (pendingNetworkKeys.contains(key)) {
            return false;
        }
        BigInteger have = reservoir.get(key);
        return have != null && have.signum() > 0;
    }

    /** 感应卡供电：网络 FE → 正面机器能量槽（独立于喂出；蓄水池空也供电）。 */
    private void feedMachinePower() {
        if (!hasInductionCard()) {
            return;
        }
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        try {
            Direction front = getFront();
            if (front == null) {
                return;
            }
            BlockEntity target = lvl.getBlockEntity(getBlockPos().relative(front));
            if (target == null) {
                return;
            }
            long cap = com.ae2addon.config.AE2AddonConfig.feederPowerEffectiveFeCap(speedCards());
            int passes = com.ae2addon.config.AE2AddonConfig.feederPowerPasses();
            long fe = com.ae2addon.compat.AppFluxPowerCompat.feedEnergy(
                    target, front.getOpposite(), getMainNode().getGrid(), actionSource, passes, cap);
            if (fe > 0 && (lvl.getGameTime() & 0x3F) == 0) {
                int accel = speedCards();
                String mode = accel >= 2 ? "无上限" : accel == 1 ? "×16" : "config";
                AE2Addon.LOGGER.info("[ae2addon][feeder] 供电(part) {} FE/tick（感应卡，{}轮，{}速度卡={}）",
                        fe, passes, accel, mode);
            }
        } catch (RuntimeException ignored) {
        }
    }

    // ── 虚拟合成卡（CRAFTING_CARD）：补货提取失败且可合成时请求 CPU 合成 ──

    /** 网络是否可合成该 key（虚拟合成卡）。 */
    private boolean isCraftable(AEKey key) {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        try {
            return grid.getCraftingService().isCraftable(key);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 发起 CPU 合成请求（异步；AE2-VM 兼容——beginCraftingCalculation 由 VM 接管）。 */
    private void requestCrafting(AEKey key, long amount) {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide) {
            return;
        }
        long now = lvl.getGameTime();
        Long last = craftingRequests.get(key);
        if (last != null && now - last < CRAFT_COOLDOWN) {
            return; // 冷却中，防刷屏
        }
        craftingRequests.put(key, now);
        try {
            var service = grid.getCraftingService();
            // 匿名模拟请求者（part 不能直接 implements：getGridNode default 与父类冲突）
            var simulationRequester = new appeng.api.networking.crafting.ICraftingSimulationRequester() {
                @Override
                public IActionSource getActionSource() {
                    return actionSource;
                }
            };
            java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> future =
                    service.beginCraftingCalculation(lvl, simulationRequester, key, amount,
                            appeng.api.networking.crafting.CalculationStrategy.CRAFT_LESS);
            // Future（非 CompletableFuture）：后台线程等待计算结果，完成后切主线程提交
            java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return future.get(15, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception e) {
                            AE2Addon.LOGGER.warn("[ae2addon][feeder] 虚拟合成计算失败(part): {} {}", key, e);
                            return null;
                        }
                    })
                    .thenAccept(plan -> {
                        if (plan == null || plan.simulation() || plan.bytes() <= 0) {
                            return;
                        }
                        if (lvl.getServer() != null) {
                            lvl.getServer().execute(() -> {
                                try {
                                    var result = service.submitJob(plan, this, null, false, actionSource);
                                    if (result != null && result.successful()) {
                                        AE2Addon.LOGGER.info(
                                                "[ae2addon][feeder] 虚拟合成卡(part): 提交合成 {} x{}",
                                                key, plan.bytes());
                                    } else {
                                        AE2Addon.LOGGER.warn(
                                                "[ae2addon][feeder] 虚拟合成卡(part)提交未成功: {} 错误={}",
                                                key, result == null ? "null" : result.errorCode());
                                    }
                                } catch (RuntimeException e) {
                                    AE2Addon.LOGGER.warn(
                                            "[ae2addon][feeder] 虚拟合成卡(part)提交失败: {} {}", key, e);
                                }
                            });
                        }
                    });
        } catch (RuntimeException e) {
            craftingRequests.remove(key);
            AE2Addon.LOGGER.warn("[ae2addon][feeder] 虚拟合成请求异常(part): {} {}", key, e);
        }
    }

    // ── ICraftingRequester（虚拟合成卡：CPU 产物直接进蓄水池） ──

    @Override
    public com.google.common.collect.ImmutableSet<appeng.api.networking.crafting.ICraftingLink> getRequestedJobs() {
        return com.google.common.collect.ImmutableSet.of();
    }

    @Override
    public long insertCraftedItems(appeng.api.networking.crafting.ICraftingLink link,
            AEKey what, long amount, Actionable actionable) {
        if (actionable == Actionable.MODULATE && amount > 0 && what != null) {
            addReservoir(what, BigInteger.valueOf(amount));
            setChanged();
        }
        return amount; // 全收（进蓄水池，随后按机器容量喂出）
    }

    @Override
    public void jobStateChange(appeng.api.networking.crafting.ICraftingLink link) {
        // 合成结束/取消：清冷却，允许稍后重试（与方块版一致）
        if (link != null && link.getCraftingID() != null) {
            craftingRequests.entrySet().removeIf(e ->
                    e.getKey().toString().equals(link.getCraftingID().toString()));
        } else {
            craftingRequests.clear();
        }
    }

    // ── 频道卡无线链路（ExtendedAE+，惰性持有） ──

    /** 按卡刷新无线链路（每 64 tick；无卡自动断开）。 */
    private void updateChannelLink() {
        var card = com.ae2addon.compat.ExtendedAEPlusCompat.channelCard();
        ItemStack cardStack = ItemStack.EMPTY;
        if (card != null) {
            for (int i = 0; i < upgrades.size(); i++) {
                ItemStack stack = upgrades.getStackInSlot(i);
                if (stack.getItem() == card) {
                    cardStack = stack;
                    break;
                }
            }
        }
        channelLink.update(this::getBlockEntity, () -> getMainNode().getNode(), cardStack);
    }

    // ── 升级卡 ──

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    private void onUpgradesChanged() {
        // 2026-09-20 缺口修复（审查发现）：容量卡强制上限 4（双保险；容器过滤失效时兜底）
        // ——与方块版 onUpgradesChanged 同款处理
        var capCard = appeng.core.definitions.AEItems.CAPACITY_CARD.asItem();
        if (upgrades.getInstalledUpgrades(capCard) > 4) {
            for (int i = 0; i < upgrades.size(); i++) {
                if (upgrades.getStackInSlot(i).getItem() == capCard) {
                    upgrades.setItemDirect(i, ItemStack.EMPTY);
                    break;
                }
            }
        }
        setChanged();
        updateChannelLink();
    }

    /** 容量卡数量（0-4）：每张样板槽 + 标记槽各 +9 格。 */
    @Override
    public int capacityCards() {
        return upgrades.getInstalledUpgrades(appeng.core.definitions.AEItems.CAPACITY_CARD.asItem());
    }

    /** 速度卡数量（0-2）：每张喂出预算 ×2；感应卡供电倍率（1 张 ×16，2 张每轮无上限）。 */
    public int speedCards() {
        return upgrades.getInstalledUpgrades(appeng.core.definitions.AEItems.SPEED_CARD.asItem());
    }

    /** 当前活动的样板槽数（9 + 容量卡×9，分页显示）。 */
    public int activePatternSlots() {
        return Math.min(patternInv.getContainerSize(), 9 + capacityCards() * 9);
    }

    /** 当前活动的标记槽数（9 + 容量卡×9）。 */
    public int activeMarkerSlots() {
        return Math.min(markerInv.getContainerSize(), 9 + capacityCards() * 9);
    }

    /** 最大页数（0 基）：容量卡数（基础页 + 每卡一页）。 */
    @Override
    public int maxPage() {
        return capacityCards();
    }

    /** 感应卡（红石门控喂出；无卡恒放行）。 */
    public boolean hasRedstoneCard() {
        return upgrades.getInstalledUpgrades(appeng.core.definitions.AEItems.REDSTONE_CARD.asItem()) > 0;
    }

    /** 反向卡（反转红石信号；无感应卡时无效）。 */
    public boolean hasInverterCard() {
        return upgrades.getInstalledUpgrades(appeng.core.definitions.AEItems.INVERTER_CARD.asItem()) > 0;
    }

    /** 虚拟合成卡（补货不足时请求合成）。 */
    public boolean hasCraftingCard() {
        return upgrades.getInstalledUpgrades(appeng.core.definitions.AEItems.CRAFTING_CARD.asItem()) > 0;
    }

    /** AppFlux 感应卡（给正面机器供电）。 */
    public boolean hasInductionCard() {
        var card = com.ae2addon.compat.AppFluxPowerCompat.inductionCard();
        return card != null && upgrades.getInstalledUpgrades(card) > 0;
    }

    /** ExtendedAE+ 频道卡（无线连网）。 */
    public boolean hasChannelCard() {
        var card = com.ae2addon.compat.ExtendedAEPlusCompat.channelCard();
        return card != null && upgrades.getInstalledUpgrades(card) > 0;
    }

    /** 红石门控：红石卡安装时，信号高=喂出（反向卡则反转；无红石卡恒放行）。 */
    private boolean redstoneAllowsFeed() {
        if (!hasRedstoneCard()) {
            return true;
        }
        Level lvl = getLevel();
        boolean powered = lvl != null && lvl.hasNeighborSignal(getBlockPos());
        return hasInverterCard() ? !powered : powered;
    }

    // ── PatternContainer（样板管理终端兼容） ──

    private final InternalInventory terminalPatternInv = new InternalInventory() {
        @Override
        public int size() {
            // 按容量卡裁剪：无卡 9 格，每张卡 +9 格（终端显示格数）
            return activePatternSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= activePatternSlots()) {
                return ItemStack.EMPTY;
            }
            return patternInv.getItem(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            if (slot < 0 || slot >= activePatternSlots()) {
                return;
            }
            patternInv.setItem(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= activePatternSlots()) {
                return false;
            }
            return stack.isEmpty() || PatternDetailsHelper.isEncodedPattern(stack);
        }
    };

    @Override
    public IGrid getGrid() {
        return getMainNode().getGrid();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return terminalPatternInv;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        // 优先显示正面贴着的机器
        Direction front = getFront();
        Level lvl = getLevel();
        if (front != null && lvl != null) {
            BlockEntity machine = lvl.getBlockEntity(getBlockPos().relative(front));
            if (machine != null) {
                // ICraftingMachine 机器 → 官方信息；否则取方块图标+名称
                PatternContainerGroup group = PatternContainerGroup.fromMachine(
                        lvl, machine.getBlockPos(), front.getOpposite());
                if (group != null) {
                    return group;
                }
                net.minecraft.world.level.block.Block machineBlock = machine.getBlockState().getBlock();
                return new PatternContainerGroup(AEItemKey.of(machineBlock),
                        machineBlock.getName(), List.of());
            }
        }
        // 兜底：面板本体
        return new PatternContainerGroup(AEItemKey.of(getPartItem().asItem()),
                getPartItem().asItem().getDescription(), List.of());
    }

    // ── 样板管理 ──

    private void onPatternsChanged() {
        patternDirty = true; // 下个 tick 重建 patterns 并通知 CPU
        patterns = List.of();
        if (isClientSide()) {
            return;
        }
        if (getMainNode().isReady()) {
            ICraftingProvider.requestUpdate(getMainNode());
        }
        setChanged();
    }

    private void rebuildPatterns() {
        patternDirty = false;
        Level lvl = getLevel();
        List<IPatternDetails> list = new ArrayList<>();
        if (lvl != null) {
            int limit = activePatternSlots();
            for (int i = 0; i < limit; i++) {
                ItemStack stack = patternInv.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                IPatternDetails details = PatternDetailsHelper.decodePattern(stack, lvl);
                // 兼容（对齐方块版 2026-08-28）：接口不接合成样板（AECraftingPattern）——
                // 合成需分子装配室/样板供应器执行，接口只喂处理机器
                if (details != null && !details.getClass().getName().endsWith("AECraftingPattern")) {
                    list.add(details);
                }
            }
        }
        patterns = List.copyOf(list);
        if (getMainNode().isReady()) {
            ICraftingProvider.requestUpdate(getMainNode());
        }
    }

    @Override
    public SimpleContainer getPatternInventory() {
        return patternInv;
    }

    @Override
    public SimpleContainer getMarkerInventory() {
        return markerInv;
    }

    /** 标记槽中已放置的物品种数（GUI 状态用）。 */
    public int markerCount() {
        int count = 0;
        for (int i = 0; i < markerInv.getContainerSize(); i++) {
            if (!markerInv.getItem(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    // ── 标记槽点击/标记管理（FeederHost） ──

    /**
     * 标记槽点击：content=true（右键）= 容器内容物优先（流体/化学物容器 → 内容物）；
     * content=false（左键）= 一律标记本体。空手 = 清空标记。
     */
    @Override
    public boolean handleMarkerClick(int markerIndex, ItemStack carried, boolean content) {
        if (markerIndex < 0 || markerIndex >= markerInv.getContainerSize()) {
            return false;
        }
        if (carried.isEmpty()) {
            markByKey(markerIndex, null);
            return true;
        }
        if (content) {
            // 右键：容器内容物优先（流体/化学物容器 → 内容物）
            var contained = FluidUtil.getFluidContained(carried);
            if (contained.isPresent() && !contained.get().isEmpty()) {
                markByKey(markerIndex, AEFluidKey.of(contained.get()));
                return true;
            }
            AEKey chemKey = com.ae2addon.compat.MekanismChemCompat.chemicalInContainer(carried);
            if (chemKey != null) {
                markByKey(markerIndex, chemKey);
                return true;
            }
        }
        // 普通物品 / 左键：一律标记本体（WGS 虚拟标记，不占用真实物品）
        AEItemKey itemKey = AEItemKey.of(carried);
        if (itemKey != null) {
            markByKey(markerIndex, itemKey);
            return true;
        }
        return false;
    }

    @Override
    public void markByKey(int markerIndex, AEKey key) {
        if (markerIndex < 0 || markerIndex >= markerInv.getContainerSize()) {
            return;
        }
        if (key == null) {
            markerInv.setItem(markerIndex, ItemStack.EMPTY);
        } else {
            markerInv.setItem(markerIndex, WrappedGenericStack.wrap(key, 1));
        }
        setChanged();
        onMarkersChanged(); // 消失的标记 → 退回蓄水池缓存
    }

    @Override
    public void clearMarker(int markerIndex) {
        markByKey(markerIndex, null);
    }

    /** 中键点击标记槽：循环切换该标记的缓存目标（1K → 10K → 100K → 1M → MAX → 回退全局）。 */
    @Override
    public void cycleMarkerTarget(int markerIndex) {
        if (markerIndex < 0 || markerIndex >= markerInv.getContainerSize()) {
            return;
        }
        AEKey key = keyOfStack(markerInv.getItem(markerIndex));
        if (key == null) {
            return;
        }
        long cur = markerTargets.getOrDefault(key, 0L);
        long next = 0;
        for (long step : TARGET_STEPS) {
            if (step > cur) {
                next = step;
                break;
            }
        }
        if (next > 0) {
            markerTargets.put(key, next);
        } else {
            markerTargets.remove(key);
        }
        setChanged();
    }

    /** 中键弹框输入：设置标记槽的独立缓存目标（target<=0 清除独立值回退全局）。 */
    @Override
    public void setMarkerTarget(int markerIndex, long target) {
        if (markerIndex < 0 || markerIndex >= markerInv.getContainerSize()) {
            return;
        }
        AEKey key = keyOfStack(markerInv.getItem(markerIndex));
        if (key == null) {
            return;
        }
        if (target > 0) {
            markerTargets.put(key, target);
        } else {
            markerTargets.remove(key);
        }
        setChanged();
    }

    /**
     * 标记变化（setChanged 之后由标记容器调用）：
     * 标记区不占真实存储——标记取消 → 对应蓄水池缓存退回网络；网络收不下的部分放回蓄水池，不丢。
     */
    private void onMarkersChanged() {
        setChanged();
        Set<AEKey> now = wantedKeys();
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide || now.equals(lastMarkedKeys)) {
            lastMarkedKeys = now;
            return;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid != null) {
            MEStorage storage = grid.getStorageService().getInventory();
            for (AEKey gone : lastMarkedKeys) {
                if (now.contains(gone)) {
                    continue;
                }
                if (patternKeys.contains(gone)) {
                    continue; // 样板喂料中的材料不退（防取消标记误退喂料致机器断料）
                }
                BigInteger amount = reservoir.remove(gone);
                pendingNetworkKeys.remove(gone); // 缓存标记一并清（料已整笔处理）
                if (amount != null && amount.signum() > 0 && storage != null) {
                    try {
                        long back = amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
                        long inserted = storage.insert(gone, back, Actionable.MODULATE, actionSource);
                        if (inserted < back) {
                            // 网络空间不足：未退回部分放回蓄水池，不丢
                            reservoir.merge(gone, BigInteger.valueOf(back - inserted), BigInteger::add);
                        }
                    } catch (RuntimeException ignored) {
                        reservoir.merge(gone, amount, BigInteger::add); // 回滚防丢
                    }
                }
            }
        }
        lastMarkedKeys = now;
    }

    // ── 每接口参数 / 开关（FeederHost） ──

    @Override
    public long stockTargetValue() {
        return pStockTarget >= 0 ? pStockTarget : InfiniteInterfaceBE.STOCK_TARGET;
    }

    @Override
    public int restockIntervalValue() {
        return pRestockInterval > 0 ? pRestockInterval : InfiniteInterfaceBE.RESTOCK_INTERVAL;
    }

    @Override
    public int feedBudgetValue() {
        return pFeedBudget > 0 ? pFeedBudget : InfiniteInterfaceBE.FEED_BUDGET;
    }

    @Override
    public long pStockTarget() {
        return pStockTarget;
    }

    @Override
    public void pStockTarget(long v) {
        pStockTarget = v;
        setChanged();
    }

    @Override
    public int pRestockInterval() {
        return pRestockInterval;
    }

    @Override
    public void pRestockInterval(int v) {
        pRestockInterval = v;
        setChanged();
    }

    @Override
    public int pFeedBudget() {
        return pFeedBudget;
    }

    @Override
    public void pFeedBudget(int v) {
        pFeedBudget = v;
        setChanged();
    }

    @Override
    public void setPerBlockParam(String key, long value) {
        switch (key) {
            case "stockTarget" -> pStockTarget = Math.max(0, value);
            case "restockInterval" -> pRestockInterval = (int) Math.max(0, Math.min(10000, value));
            case "feedBudget" -> pFeedBudget = (int) Math.max(0, Math.min(1_000_000, value));
            default -> {
                return;
            }
        }
        setChanged();
    }

    @Override
    public long targetFor(AEKey key) {
        Long v = markerTargets.get(key);
        if (v != null && v > 0) {
            return v;
        }
        return stockTargetValue();
    }

    @Override
    public Map<AEKey, Long> markerTargetsSnapshot() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(markerTargets));
    }

    @Override
    public void markerTargetsClear() {
        markerTargets.clear();
        setChanged();
    }

    @Override
    public void markerTargetsPut(AEKey key, long target) {
        if (target > 0) {
            markerTargets.put(key, target);
        } else {
            markerTargets.remove(key);
        }
        setChanged();
    }

    @Override
    public boolean activeExtract() {
        return activeExtract;
    }

    @Override
    public boolean activeFeed() {
        return activeFeed;
    }

    @Override
    public boolean activeMarkerFeed() {
        return activeMarkerFeed;
    }

    @Override
    public RelativeSide extractSide() {
        return extractSide;
    }

    /** GUI 开关切换（"extract"/"feed"/"markerFeed"/"dir"）。 */
    @Override
    public void toggleActive(String which) {
        if ("extract".equals(which)) {
            activeExtract = !activeExtract;
        } else if ("feed".equals(which)) {
            activeFeed = !activeFeed;
        } else if ("markerFeed".equals(which)) {
            activeMarkerFeed = !activeMarkerFeed;
        } else if ("dir".equals(which)) {
            cycleExtractSide();
            return; // 已在 cycle 内 setChanged
        }
        setChanged();
    }

    @Override
    public void setActiveExtract(boolean v) {
        activeExtract = v;
        setChanged();
    }

    @Override
    public void setActiveFeed(boolean v) {
        activeFeed = v;
        setChanged();
    }

    @Override
    public void setExtractSide(RelativeSide side) {
        extractSide = side;
        setChanged();
    }

    /** 循环切换抽取方向（正→后→上→下→左→右）。 */
    @Override
    public void cycleExtractSide() {
        RelativeSide[] all = RelativeSide.values();
        int idx = java.util.Arrays.asList(all).indexOf(extractSide);
        extractSide = all[(idx + 1) % all.length];
        setChanged();
    }

    // ── 蓄水池概览（FeederHost / GUI 状态） ──

    @Override
    public String[] reservoirSummary() {
        int types = 0;
        BigInteger total = BigInteger.ZERO;
        for (var entry : reservoir.entrySet()) {
            if (entry.getValue().signum() > 0) {
                types++;
                total = total.add(entry.getValue());
            }
        }
        return new String[]{String.valueOf(types), InfiniteInterfaceBE.fmt(total)};
    }

    @Override
    public BigInteger totalFed() {
        return totalFed;
    }

    @Override
    public long feedRatePerSecond() {
        return currentFeedRate;
    }

    @Override
    public long rejectRatePerSecond() {
        return currentRejectRate;
    }

    @Override
    public List<net.minecraft.network.chat.Component> reservoirTooltipLines() {
        return FeederHost.buildReservoirLines(reservoir);
    }

    /** 蓄水池 top N 条目描述（GUI 状态用）。 */
    public List<String> topItems(int limit) {
        List<Map.Entry<AEKey, BigInteger>> list = new ArrayList<>();
        for (var entry : reservoir.entrySet()) {
            if (entry.getValue().signum() > 0) {
                list.add(entry);
            }
        }
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            var entry = list.get(i);
            String name;
            try {
                name = entry.getKey().getDisplayName().getString();
            } catch (RuntimeException e) {
                name = entry.getKey().toString();
            }
            out.add(name + " × " + InfiniteInterfaceBE.fmt(entry.getValue()));
        }
        return out;
    }

    /** 蓄水池内物品合计（BigInteger，GUI 显示）。 */
    public BigInteger totalAmount() {
        return reservoirTotal();
    }

    // ── 位置/生命周期 ──

    @Override
    public BlockPos getBlockPos() {
        return getBlockEntity().getBlockPos();
    }

    @Override
    public boolean isRemoved() {
        return removed;
    }

    // ── 交互：GUI / 插卡 / 配置卡（2026-09-20 由上游 onPartActivate /
    //    onPartShiftActivate 合并到 1.21.1 的 onUseItemOn） ──

    /**
     * AE2 19 里 {@code AEBasePart.onUseItemOn} 会把标准内存卡（IMemoryCard）先吃掉，
     * 只留 {@code useStandardMemoryCard()} 这个开关。本面板的配置卡/内存卡复制粘贴要自己
     * 处理（与方块版语义一致），故返回 false 让父类让路。
     */
    @Override
    public boolean useStandardMemoryCard() {
        return false;
    }

    @Override
    public boolean onUseItemOn(ItemStack held, Player p, InteractionHand hand, Vec3 pos) {
        if (p.level().isClientSide) {
            return true; // 客户端只挡下手（服务端为准）
        }
        boolean sneaking = p.isShiftKeyDown();
        // ① 手持升级卡：shift = 全插直到满，否则插一张（AE2 玩法）
        if (!held.isEmpty() && insertUpgradeCards(held, sneaking ? 0 : 1)) {
            return true;
        }
        // ② 配置卡 / 内存卡：shift = 粘贴，否则 = 复制
        if (held.getItem() instanceof com.ae2addon.item.ConfigCardItem
                || held.getItem() instanceof appeng.items.tools.MemoryCardItem) {
            boolean handled = sneaking
                    ? MemoryCardHelper.handlePaste(this, p, held)
                    : MemoryCardHelper.handleCopy(this, p, held);
            if (handled) {
                return true;
            }
        }
        // ③ 打开本面板 GUI（菜单按 FeederHost 工作，方块版/面板版共用）
        if (p instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) ->
                            new InfiniteInterfaceMenu(containerId, inventory, this),
                            net.minecraft.network.chat.Component.translatable(
                                    "gui.ae2addon.infinite_interface.title")),
                    buffer -> MenuLocators.writeToPacket(buffer, MenuLocators.forPart(this)));
        }
        return true;
    }

    // ── 掉落：part item 由 AE2 自动掉；这里补样板 + 升级卡（蓄水池属于网络/CPU 不退防刷） ──

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (int i = 0; i < patternInv.getContainerSize(); i++) {
            ItemStack st = patternInv.getItem(i);
            if (!st.isEmpty()) {
                drops.add(st);
            }
        }
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack st = upgrades.getStackInSlot(i);
            if (!st.isEmpty()) {
                drops.add(st);
            }
        }
        // 2026-09-20 缺口修复（审查发现）：拆机丢配置——把标记槽/每标记目标/开关/抽取方向/
        // 每接口参数写进掉落的 panel 物品 CUSTOM_DATA（键名与方块版 writeConfigToStack 一致，
        // 读写共用 writeConfigToTag/readConfigFromTag）。拆线缆时 AE2 先 addPartDrop（本体物品
        // 已入列表，且已由 exportSettings 带上配置）再调本方法，这里就地再写一遍做兜底；
        // 列表里找不到本体时「不新建」——整块破坏路径的本体物品由 CableBusBlock.getDrops 掉，
        // 此处新建会凭空多掉一个面板。
        net.minecraft.world.item.Item panelItem =
                com.ae2addon.init.ModItems.INFINITE_INTERFACE_PANEL_ITEM.get();
        Level dropLevel = getLevel();
        if (dropLevel != null) {
            for (ItemStack drop : drops) {
                if (!drop.isEmpty() && drop.getItem() == panelItem) {
                    writeConfigToStack(drop, dropLevel.registryAccess());
                    break;
                }
            }
        }
    }

    // ── 配置随掉落物品走（拆机重放自动恢复） ──
    //
    // 2026-09-20 缺口修复（审查发现）：方块版 BE 的 writeConfigToStack 会把标记槽/每标记目标/
    // 开关/方向写进掉落的方块物品，重放自动恢复；面板版此前全丢。
    // AE2 的两条链路（已按 AE2 19.2.17 字节码核对）：
    //   掉落：IPart.addPartDrop → new ItemStack(partItem) + exportSettings(DISMANTLE_ITEM)；
    //         扳手拆机 CableBusBlockEntity.disassembleWithWrench 先 addPartDrop 再 addAdditionalDrops；
    //         整块破坏 CableBusBlock.getDrops → CableBusContainer.addPartDrops → addPartDrop。
    //   放置：PartPlacement.place → part.importSettings(DISMANTLE_ITEM, stack.getComponents(), player)。
    // 因此覆写这两个钩子即可覆盖两条掉落路径 + 自动恢复，无需另造 NBT 体系。

    @Override
    public void exportSettings(appeng.util.SettingsFrom from, DataComponentMap.Builder builder) {
        super.exportSettings(from, builder);
        if (from != appeng.util.SettingsFrom.DISMANTLE_ITEM) {
            return; // 内存卡等其它来源照旧（面板走自己的 MemoryCardHelper）
        }
        Level lvl = getLevel();
        if (lvl == null) {
            return; // 拿不到 registryAccess 就不写配置（宁缺不崩）
        }
        CompoundTag tag = new CompoundTag();
        writeConfigToTag(tag, lvl.registryAccess());
        builder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom from, DataComponentMap input, @Nullable Player player) {
        super.importSettings(from, input, player);
        if (from != appeng.util.SettingsFrom.DISMANTLE_ITEM) {
            return;
        }
        CustomData custom = input.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return; // 普通放置（无配置）：不改动，保持默认全开
        }
        CompoundTag tag = custom.copyTag();
        if (tag.isEmpty()) {
            return;
        }
        // registryAccess：优先 part 所在 level；放置瞬间 level 尚未挂上时退回玩家 level（放置必经玩家）
        Level lvl = getLevel();
        net.minecraft.core.HolderLookup.Provider registries = lvl != null
                ? lvl.registryAccess()
                : (player != null ? player.level().registryAccess() : null);
        if (registries == null) {
            AE2Addon.LOGGER.warn("[ae2addon][feeder] 放置配置未恢复(part)：拿不到 registryAccess，CUSTOM_DATA={}", tag);
            return;
        }
        readConfigFromTag(tag, registries);
        setChanged();
        AE2Addon.LOGGER.info("[ae2addon][feeder] 面板放置恢复配置(part)：标记{}个 开关[抽取{} 喂出{} 标记喂出{}] 抽取方向{}",
                markerTargets.size(), activeExtract ? "开" : "关", activeFeed ? "开" : "关",
                activeMarkerFeed ? "开" : "关", extractSide.name());
    }

    /** 把 config 段落写进物品 CUSTOM_DATA（掉落的 panel 物品；读侧 importSettings 同键恢复）。 */
    private void writeConfigToStack(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        writeConfigToTag(tag, registries);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * config 段落写入：键名与方块版 {@code InfiniteInterfaceBE.writeConfigToStack} 逐键一致
     * （markers / markerTargets / activeExtract / activeFeed / activeMarkerFeed / extractSide /
     * pStockTarget / pRestockInterval / pFeedBudget），世界存档（writeToNBT）与掉落物品
     * CUSTOM_DATA（exportSettings）共用同一份写入逻辑。
     * 蓄水池缓存不进 NBT（属于网络/CPU，防刷）。
     */
    private void writeConfigToTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        tag.putBoolean("activeExtract", activeExtract);
        tag.putBoolean("activeFeed", activeFeed);
        tag.putBoolean("activeMarkerFeed", activeMarkerFeed);
        tag.putString("extractSide", extractSide.name());
        tag.putLong("pStockTarget", pStockTarget);
        tag.putInt("pRestockInterval", pRestockInterval);
        tag.putInt("pFeedBudget", pFeedBudget);
        // 每标记独立缓存目标
        ListTag targetList = new ListTag();
        for (var e : markerTargets.entrySet()) {
            CompoundTag ent = new CompoundTag();
            ent.put("Key", WrappedGenericStack.wrap(e.getKey(), 1).save(registries, new CompoundTag()));
            ent.putLong("Target", e.getValue());
            targetList.add(ent);
        }
        tag.put("markerTargets", targetList);
        // 标记槽（带槽位；空槽不写）
        ListTag markerList = new ListTag();
        for (int i = 0; i < markerInv.getContainerSize(); i++) {
            ItemStack st = markerInv.getItem(i);
            if (!st.isEmpty()) {
                CompoundTag ent = new CompoundTag();
                ent.putInt("Slot", i);
                st.save(registries, ent);
                markerList.add(ent);
            }
        }
        tag.put("markers", markerList);
    }

    /**
     * config 段落读回（与 writeConfigToTag 同键；世界存档与物品 CUSTOM_DATA 共用）。
     * 只读配置段落，不碰蓄水池/样板（防刷）。
     * 开关类按「键存在才覆盖」处理：本方法现在也读来历不明的物品 CUSTOM_DATA，
     * 缺失键时保持字段默认（true），绝不因无关 NBT 静默关掉喂出/抽取。
     */
    private void readConfigFromTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        if (tag.contains("activeExtract")) {
            activeExtract = tag.getBoolean("activeExtract");
        }
        if (tag.contains("activeFeed")) {
            activeFeed = tag.getBoolean("activeFeed");
        }
        if (tag.contains("activeMarkerFeed")) {
            activeMarkerFeed = tag.getBoolean("activeMarkerFeed");
        }
        try {
            extractSide = RelativeSide.valueOf(tag.getString("extractSide"));
        } catch (RuntimeException ignored) {
            extractSide = RelativeSide.FRONT;
        }
        pStockTarget = tag.contains("pStockTarget") ? tag.getLong("pStockTarget") : -1;
        pRestockInterval = tag.contains("pRestockInterval") ? tag.getInt("pRestockInterval") : -1;
        pFeedBudget = tag.contains("pFeedBudget") ? tag.getInt("pFeedBudget") : -1;
        // 每标记独立缓存目标
        markerTargets.clear();
        ListTag targetList = tag.getList("markerTargets", Tag.TAG_COMPOUND);
        for (int i = 0; i < targetList.size(); i++) {
            CompoundTag entry = targetList.getCompound(i);
            try {
                ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("Key"));
                AEKey wrapped = null;
                if (stack.getItem() instanceof WrappedGenericStack wgs) {
                    wrapped = wgs.unwrapWhat(stack);
                }
                if (wrapped != null) {
                    markerTargets.put(wrapped, entry.getLong("Target"));
                }
            } catch (RuntimeException ignored) {
            }
        }
        // 标记槽
        ListTag markerList = tag.getList("markers", Tag.TAG_COMPOUND);
        markerInv.clearContent();
        for (int i = 0; i < markerList.size(); i++) {
            CompoundTag entry = markerList.getCompound(i);
            ItemStack st = ItemStack.parseOptional(registries, entry);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < markerInv.getContainerSize() && !st.isEmpty()) {
                markerInv.setItem(slot, st);
            }
        }
    }

    // ── NBT ──

    @Override
    public void writeToNBT(CompoundTag data, net.minecraft.core.HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        // 2026-09-20 缺口修复（审查发现）：配置段落抽成 writeConfigToTag——世界存档与掉落
        // 物品 CUSTOM_DATA（exportSettings）同一份写入逻辑，键名不变（旧存档兼容）
        writeConfigToTag(data, registries);
        // 蓄水池（BigInteger 走字符串，防溢出）
        ListTag reservoirList = new ListTag();
        for (var entry : reservoir.entrySet()) {
            CompoundTag ent = new CompoundTag();
            ent.put("key", entry.getKey().toTagGeneric(registries));
            ent.putString("amount", entry.getValue().toString());
            reservoirList.add(ent);
        }
        data.put("reservoir", reservoirList);
        // 样板槽（带槽位，缺省空槽不写）
        ListTag patternList = new ListTag();
        for (int i = 0; i < patternInv.getContainerSize(); i++) {
            ItemStack st = patternInv.getItem(i);
            if (!st.isEmpty()) {
                CompoundTag ent = new CompoundTag();
                ent.putInt("Slot", i);
                st.save(registries, ent);
                patternList.add(ent);
            }
        }
        data.put("patterns", patternList);
        // 标记槽由 writeConfigToTag 写入（同一份配置段落）
        // 待入网缓存标记
        ListTag pendingList = new ListTag();
        for (AEKey key : pendingNetworkKeys) {
            CompoundTag ent = new CompoundTag();
            ent.put("key", key.toTagGeneric(registries));
            pendingList.add(ent);
        }
        data.put("pendingNetwork", pendingList);
        upgrades.writeToNBT(data, "upgrades", registries);
        data.putString("totalFed", totalFed.toString());
    }

    @Override
    public void readFromNBT(CompoundTag data, net.minecraft.core.HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        // 2026-09-20 缺口修复（审查发现）：配置段落抽成 readConfigFromTag（与掉落物品
        // CUSTOM_DATA 读回同一份逻辑，键名不变）
        readConfigFromTag(data, registries);
        reservoir.clear();
        patternKeys.clear();
        pendingNetworkKeys.clear();
        ListTag reservoirList = data.getList("reservoir", Tag.TAG_COMPOUND);
        for (int i = 0; i < reservoirList.size(); i++) {
            CompoundTag entry = reservoirList.getCompound(i);
            try {
                AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound("key"));
                BigInteger amount = new BigInteger(entry.getString("amount"));
                if (key != null && amount.signum() > 0) {
                    reservoir.put(key, amount);
                }
            } catch (RuntimeException ignored) {
                // 单条损坏不影响整体
            }
        }
        ListTag patternList = data.getList("patterns", Tag.TAG_COMPOUND);
        patternInv.clearContent();
        for (int i = 0; i < patternList.size(); i++) {
            CompoundTag entry = patternList.getCompound(i);
            ItemStack st = ItemStack.parseOptional(registries, entry);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < patternInv.getContainerSize() && !st.isEmpty()) {
                patternInv.setItem(slot, st);
            }
        }
        patternDirty = true; // level 可能为 null，解码延迟到首个 tick
        // 标记槽已由 readConfigFromTag 读回
        ListTag pendingList = data.getList("pendingNetwork", Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingList.size(); i++) {
            try {
                AEKey key = AEKey.fromTagGeneric(registries, pendingList.getCompound(i).getCompound("key"));
                if (key != null && reservoir.containsKey(key)) {
                    pendingNetworkKeys.add(key);
                }
            } catch (RuntimeException ignored) {
            }
        }
        try {
            totalFed = new BigInteger(data.getString("totalFed"));
        } catch (RuntimeException ignored) {
            totalFed = BigInteger.ZERO;
        }
        upgrades.readFromNBT(data, "upgrades", registries);
        updateChannelLink();
    }

    // ── 能力暴露（NeoForge 1.21.1） ──
    //
    // ⚠️【移植说明】Forge 1.20.1 的 IPart#getCapability(Capability) 在 AE2 19 / NeoForge 1.21
    // 已不存在（能力注册只覆盖 BlockEntity）。本类改为暴露 handler getter，由线缆 host 侧
    // （或后续 mixin）转发；未接线时外部管道暂时看不到面板的 IO 面。
    // TODO(2026-09-20): 若需要管道/漏斗直接对接面板，需在线缆方块能力注册处按
    //   FeederHostResolver 同款方式取到 IPartHost 上的本 part，再返回下面的 handler。

    /** 网络入口物品 handler（管道/漏斗塞进来的物品直接进网络，收不下的进待入网缓存）。 */
    public IItemHandler getNetworkItemHandler() {
        return networkItemHandler;
    }

    private final IItemHandler networkItemHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            IGrid grid = getMainNode().getGrid();
            if (grid == null) {
                return stack;
            }
            try {
                AEItemKey key = AEItemKey.of(stack);
                if (key == null) {
                    return stack;
                }
                long inserted = grid.getStorageService().getInventory().insert(
                        key, stack.getCount(),
                        simulate ? Actionable.SIMULATE : Actionable.MODULATE, actionSource);
                long rest = stack.getCount() - inserted;
                if (rest > 0 && !simulate) {
                    cacheForNetwork(key, rest);
                }
                return ItemStack.EMPTY; // 全接收（入网 + 待入网缓存）
            } catch (RuntimeException e) {
                return stack;
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0) {
                return ItemStack.EMPTY;
            }
            for (var entry : reservoir.entrySet()) {
                if (entry.getValue().signum() <= 0
                        || !(entry.getKey() instanceof AEItemKey itemKey)) {
                    continue;
                }
                long take = entry.getValue()
                        .min(BigInteger.valueOf(amount))
                        .min(BigInteger.valueOf(InfiniteInterfaceBE.FEED_STACK)).longValue();
                if (take <= 0) {
                    continue;
                }
                if (!simulate) {
                    subtractReservoir(itemKey, take);
                    totalFed = totalFed.add(BigInteger.valueOf(take));
                    setChanged();
                }
                return itemKey.toStack((int) take);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            // 反映真实缓存上限（补货目标），Jade/管道显示不误导
            return (int) Math.min(Math.max(InfiniteInterfaceBE.STOCK_TARGET, 1), Integer.MAX_VALUE);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty();
        }
    };

    /**
     * 网络入口流体 handler（语义与方块版 BE 的 networkFluidHandler 一致）：
     * 外部塞进来的流体直接进网络，网络收不下的余量进待入网缓存（不拒收、不丢）；
     * 抽取侧是蓄水池里数量最多的那种流体（Jade/管道能看到真实流体与数量）。
     * 2026-09-20 缺口修复（审查发现）：面板此前只暴露物品入口。
     */
    public IFluidHandler getNetworkFluidHandler() {
        return networkFluidHandler;
    }

    /** 蓄水池中数量最多的流体（预览/通用抽取用；对齐方块版 largestFluid）。 */
    @Nullable
    private Map.Entry<AEKey, BigInteger> largestFluid() {
        Map.Entry<AEKey, BigInteger> best = null;
        for (var entry : reservoir.entrySet()) {
            if (!(entry.getKey() instanceof AEFluidKey) || entry.getValue().signum() <= 0) {
                continue;
            }
            if (best == null || entry.getValue().compareTo(best.getValue()) > 0) {
                best = entry;
            }
        }
        return best;
    }

    private final IFluidHandler networkFluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank != 0) {
                return FluidStack.EMPTY;
            }
            var best = largestFluid();
            if (best == null) {
                return FluidStack.EMPTY;
            }
            long amount = best.getValue().min(BigInteger.valueOf(Integer.MAX_VALUE)).longValue();
            return new FluidStack(((AEFluidKey) best.getKey()).getFluid(), (int) Math.max(1, amount));
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return !stack.isEmpty();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            IGrid grid = getMainNode().getGrid();
            if (grid == null) {
                return 0;
            }
            try {
                AEFluidKey key = AEFluidKey.of(resource.getFluid());
                if (key == null) {
                    return 0;
                }
                long inserted = grid.getStorageService().getInventory().insert(
                        key, resource.getAmount(),
                        action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE, actionSource);
                // 网络空间不足的余量：收进蓄水池待入网缓存（等空间自动补送），不拒收
                long rest = resource.getAmount() - inserted;
                if (rest > 0 && !action.simulate()) {
                    cacheForNetwork(key, rest);
                }
                return resource.getAmount(); // 全接收（入网 + 缓存）
            } catch (RuntimeException e) {
                return 0;
            }
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            AEFluidKey key = AEFluidKey.of(resource.getFluid());
            if (key == null) {
                return FluidStack.EMPTY;
            }
            // 从蓄水池扣对应流体（管道从侧面抽流体缓存）
            long take = Math.min(reservoirAmount(key), resource.getAmount());
            if (take <= 0) {
                return FluidStack.EMPTY;
            }
            if (!action.simulate()) {
                subtractReservoir(key, take);
                setChanged();
            }
            return new FluidStack(resource.getFluid(), (int) take);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            // 蓄水池最多流体（管道无指定时的通用抽取）
            var best = largestFluid();
            if (best == null) {
                return FluidStack.EMPTY;
            }
            long take = Math.min(best.getValue().longValue(), maxDrain);
            if (take <= 0) {
                return FluidStack.EMPTY;
            }
            if (!action.simulate()) {
                subtractReservoir(best.getKey(), take);
                setChanged();
            }
            return new FluidStack(((AEFluidKey) best.getKey()).getFluid(), (int) take);
        }
    };

    // ── 化学物入口（Mekanism 10.7 统一 IChemicalHandler） ──

    /**
     * 化学能力入口（仅 Mekanism 加载时由调用方使用；未加载时安全返回 null）。
     * <p>
     * 2026-09-20 缺口修复（审查发现）：面板此前没有化学入口，语义对齐方块版
     * {@code InfiniteInterfaceBE.getChemHandler}——塞进来的化学物直接进网络、网络满走
     * 待入网缓存；抽取侧从蓄水池取。惰性创建：实例只在首次调用且 Mekanism 已加载时构造，
     * mekanism 符号全部关在静态内部类 {@link ChemHandlerHolder} 里，类加载与构造期都不解析
     * mekanism 类型（对齐方块版审计补漏 P0-6，避免无 Mekanism 环境 NoClassDefFoundError）。
     */
    @Nullable
    public mekanism.api.chemical.IChemicalHandler getChemHandler() {
        if (!com.ae2addon.compat.MekanismGasCompat.isLoaded()) {
            return null; // 未加载 Mekanism：无化学入口（也不触碰 mekanism 符号）
        }
        try {
            if (networkChemicalHandler == null) {
                networkChemicalHandler = ChemHandlerHolder.create(this);
            }
            return ChemHandlerHolder.asHandler(networkChemicalHandler);
        } catch (RuntimeException | LinkageError e) {
            return null; // 依赖缺失/环境异常：安全返回 null，不崩
        }
    }

    /** 化学能力实例惰性持有（Object 字段：类加载期不解析 mekanism 符号）。 */
    @Nullable
    private Object networkChemicalHandler;

    /** 蓄水池中数量最多的化学物（预览/通用抽取用；对齐方块版 largestChemical）。 */
    @Nullable
    private Map.Entry<AEKey, BigInteger> largestChemical() {
        Map.Entry<AEKey, BigInteger> best = null;
        for (var entry : reservoir.entrySet()) {
            if (entry.getValue().signum() <= 0
                    || com.ae2addon.compat.MekanismChemCompat.mekKeyOf(entry.getKey()) == null) {
                continue;
            }
            if (best == null || entry.getValue().compareTo(best.getValue()) > 0) {
                best = entry;
            }
        }
        return best;
    }

    /**
     * 化学能力实现（静态内部类 = 惰性持有；所有 mekanism 类型引用集中在此，不创建即不解析）。
     * 语义与方块版 ChemHandlerHolder 一致：罐视图 = 蓄水池里最多的那种化学物，
     * 塞入 → 网络（余量缓存），抽出 → 蓄水池。
     */
    private static final class ChemHandlerHolder {

        static mekanism.api.chemical.IChemicalHandler create(InfiniteInterfacePart host) {
            return new Impl(host);
        }

        static mekanism.api.chemical.IChemicalHandler asHandler(Object holder) {
            return (mekanism.api.chemical.IChemicalHandler) holder;
        }

        private static final class Impl implements mekanism.api.chemical.IChemicalHandler {

            private final InfiniteInterfacePart host;

            Impl(InfiniteInterfacePart host) {
                this.host = host;
            }

            @Override
            public int getChemicalTanks() {
                return 1;
            }

            @Override
            public mekanism.api.chemical.ChemicalStack getChemicalInTank(int tank) {
                if (tank != 0) {
                    return mekanism.api.chemical.ChemicalStack.EMPTY;
                }
                var best = this.host.largestChemical();
                if (best == null) {
                    return mekanism.api.chemical.ChemicalStack.EMPTY;
                }
                var mk = com.ae2addon.compat.MekanismChemCompat.mekKeyOf(best.getKey());
                if (mk == null) {
                    return mekanism.api.chemical.ChemicalStack.EMPTY;
                }
                long amount = best.getValue().min(BigInteger.valueOf(Integer.MAX_VALUE)).longValue();
                return mk.getStack().copyWithAmount(Math.max(1, amount));
            }

            @Override
            public void setChemicalInTank(int tank, mekanism.api.chemical.ChemicalStack stack) {
                // 只读（蓄水池视图），忽略写入
            }

            @Override
            public long getChemicalTankCapacity(int tank) {
                return Integer.MAX_VALUE;
            }

            @Override
            public boolean isValid(int tank, mekanism.api.chemical.ChemicalStack stack) {
                return !stack.isEmpty();
            }

            @Override
            public mekanism.api.chemical.ChemicalStack insertChemical(int tank,
                    mekanism.api.chemical.ChemicalStack resource, mekanism.api.Action action) {
                return insertChemical(resource, action);
            }

            @Override
            public mekanism.api.chemical.ChemicalStack insertChemical(
                    mekanism.api.chemical.ChemicalStack resource, mekanism.api.Action action) {
                if (resource.isEmpty()) {
                    return resource;
                }
                IGrid grid = this.host.getMainNode().getGrid();
                if (grid == null) {
                    return resource;
                }
                try {
                    AEKey key = com.ae2addon.compat.MekanismChemCompat.keyOfChemical(resource);
                    if (key == null) {
                        return resource;
                    }
                    long inserted = grid.getStorageService().getInventory().insert(
                            key, resource.getAmount(),
                            action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE,
                            this.host.actionSource);
                    // 网络空间不足的余量：收进待入网缓存（等空间自动补送），不拒收
                    long rest = resource.getAmount() - inserted;
                    if (rest > 0 && !action.simulate()) {
                        this.host.cacheForNetwork(key, rest);
                    }
                    return mekanism.api.chemical.ChemicalStack.EMPTY; // 全接收（入网 + 缓存）
                } catch (RuntimeException e) {
                    return resource;
                }
            }

            @Override
            public mekanism.api.chemical.ChemicalStack extractChemical(int tank, long amount,
                    mekanism.api.Action action) {
                return extractChemical(amount, action);
            }

            @Override
            public mekanism.api.chemical.ChemicalStack extractChemical(long amount,
                    mekanism.api.Action action) {
                if (amount <= 0) {
                    return mekanism.api.chemical.ChemicalStack.EMPTY;
                }
                // 蓄水池中数量最多的化学物（管道无指定时的通用抽取）
                var best = this.host.largestChemical();
                if (best == null) {
                    return mekanism.api.chemical.ChemicalStack.EMPTY;
                }
                var mk = com.ae2addon.compat.MekanismChemCompat.mekKeyOf(best.getKey());
                if (mk == null) {
                    return mekanism.api.chemical.ChemicalStack.EMPTY;
                }
                long take = best.getValue().min(BigInteger.valueOf(amount))
                        .min(BigInteger.valueOf(Integer.MAX_VALUE)).longValue();
                if (take <= 0) {
                    return mekanism.api.chemical.ChemicalStack.EMPTY;
                }
                if (!action.simulate()) {
                    this.host.subtractReservoir(best.getKey(), take);
                    this.host.setChanged();
                }
                return mk.getStack().copyWithAmount(take);
            }

            @Override
            public mekanism.api.chemical.ChemicalStack extractChemical(
                    mekanism.api.chemical.ChemicalStack stack, mekanism.api.Action action) {
                if (stack.isEmpty()) {
                    return stack;
                }
                AEKey key = com.ae2addon.compat.MekanismChemCompat.keyOfChemical(stack);
                if (key == null) {
                    return stack;
                }
                long take = Math.min(this.host.reservoirAmount(key), stack.getAmount());
                if (take <= 0) {
                    return mekanism.api.chemical.ChemicalStack.EMPTY;
                }
                if (!action.simulate()) {
                    this.host.subtractReservoir(key, take);
                    this.host.setChanged();
                }
                return stack.copyWithAmount(take);
            }
        }
    }

    // ── 杂项 ──

    /** 外部（方块版静态方法/命令）触发存档。 */
    public void saveChanges() {
        setChanged();
    }

    /** 供外部把面板缓存里的某 key 记入待入网（对齐方块版的缓存入口）。 */
    public void cacheEntryForNetwork(AEKey key, long amount) {
        cacheForNetwork(key, amount);
    }

}
