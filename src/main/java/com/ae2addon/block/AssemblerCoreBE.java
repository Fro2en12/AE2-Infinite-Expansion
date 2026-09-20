package com.ae2addon.block;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.ae2addon.init.ModBlockEntities;
import com.ae2addon.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 无限级装配处理器·模块（v0.3 M3，2026-09-04 上游同步）。
 * <p>
 * sensei 决策：不做独立合成单元，作为<b>集成 CPU 的拓展模块</b>——
 * crafting-unit 型方块贴入集成 CPU 簇（与 IntegratedCPUBE 同簇）即生效：
 * <ul>
 *   <li>存储贡献 0（不干扰集成 CPU 的无限存储语义；同簇多块也不会溢出）</li>
 *   <li>样板槽 5×9×200（9000 格）：声明「可虚拟结算的合成样板」白名单</li>
 *   <li>集成 CPU（主簇/虚拟 lane）执行合成时遇白名单合成样板 →
 *       CraftingCpuLogicMixin 虚拟结算（材料销毁、产物瞬时注入）</li>
 *   <li>实现 PatternContainer：样板管理终端可直接访问样板槽</li>
 *   <li>单独放置（无集成 CPU 簇）→ 模块不激活，仅样板槽管理界面可用</li>
 * </ul>
 * <p>
 * 1.21.1 移植差异（2026-09-xx 上游同步，均为 API 形态差异，语义保持上游原文）：
 * <ul>
 *   <li>{@code ItemStack#save(CompoundTag)} → {@code save(HolderLookup.Provider, tag)}，
 *       {@code ItemStack.of} → {@code ItemStack.parseOptional}（本地 InfiniteInterfaceBE 同款）</li>
 *   <li>{@code IPatternDetails#getOutputs()} 返回 {@code List<GenericStack>}（AE2 19）而非数组</li>
 *   <li>样板种类判定用 AE2 19 的类名（appeng.crafting.pattern.AECraftingPattern 等）</li>
 * </ul>
 */
public class AssemblerCoreBE extends CraftingBlockEntity
        implements ICraftingProvider, PatternContainer {

    /** 样板槽规格（上游 sensei 定稿）：5×9 每页 × 200 页。 */
    public static final int SLOT_COLS = 5;
    public static final int SLOT_ROWS = 9;
    public static final int PAGES = 200;
    public static final int PAGE_SIZE = SLOT_COLS * SLOT_ROWS; // 45
    public static final int TOTAL_SLOTS = PAGE_SIZE * PAGES;   // 9000

    /** 样板槽数据（稀疏 List，容量 TOTAL_SLOTS，NBT 只存非空）。 */
    private final List<ItemStack> patterns = new ArrayList<>();

    /** 当前 GUI 页（0..PAGES-1）。 */
    private int page;

    /** 所属集成 CPU（模块贴入集成 CPU 簇后由 updateStatus 记录；null=未激活）。 */
    private IntegratedCPUBE ownerCPU;

    // ── 白名单/样板缓存（槽位变化时失效重建） ──

    private boolean cacheDirty = true;
    private List<IPatternDetails> cachedPatterns = List.of();
    private Set<AEKey> declaredOutputs = Set.of();

    public AssemblerCoreBE(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLER_CORE.get(), pos, state);
    }

    // ── 注册表 ──

    @Override
    public void onReady() {
        super.onReady();
        AssemblerRegistry.register(this);
        refreshOwner();
    }

    /** 簇状态变化（成型/拆毁/重组）时刷新所属集成 CPU。 */
    @Override
    public void updateStatus(CraftingCPUCluster c) {
        super.updateStatus(c);
        refreshOwner();
    }

    /** 记录所属集成 CPU（模块簇的 owner 优先；同网格兜底——上游 2026-09-04 修复：
     *  模块与集成 CPU 不必同 crafting unit 簇，接入同一网络即关联为拓展模块）。 */
    private void refreshOwner() {
        IntegratedCPUBE owner = null;
        var myCluster = getCluster();
        if (myCluster != null && !myCluster.isDestroyed()) {
            owner = IntegratedCPURegistry.ownerOf(myCluster);
        }
        if (owner == null) {
            // 同簇未命中 → 同网格：遍历集成 CPU，找同一 grid 的（一般就一个）
            try {
                var myGrid = getMainNode() == null ? null : getMainNode().getGrid();
                if (myGrid != null) {
                    for (IntegratedCPUBE cpu : IntegratedCPURegistry.all()) {
                        if (cpu.isRemoved()) {
                            continue;
                        }
                        var cpuGrid = cpu.getMainNode() == null ? null
                                : cpu.getMainNode().getGrid();
                        if (cpuGrid == myGrid) {
                            owner = cpu;
                            break;
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // 网格未就绪：保持 null，下次查询再试
            }
        }
        this.ownerCPU = owner;
    }

    /** 外部（AssemblerRegistry.moduleFor）触发的惰性刷新：owner 未建立时重试。 */
    public void refreshOwnerNow() {
        if (ownerCPU == null && isFormed()) {
            refreshOwner();
        }
    }

    public IntegratedCPUBE getOwnerCPU() {
        return ownerCPU;
    }

    @Override
    public void onChunkUnloaded() {
        AssemblerRegistry.unregister(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        AssemblerRegistry.unregister(this);
        super.setRemoved();
    }

    // ── 网格节点：注册为合成 provider（让 CPU 的 provider 循环找到我们）──

    @Override
    protected IManagedGridNode createMainNode() {
        // 基类 CraftingBlockEntity 构造器已设 MULTIBLOCK + REQUIRE_CHANNEL（AE2 19 反编译确认），
        // 这里只补 service 注册
        return super.createMainNode()
                .addService(ICraftingProvider.class, this);
    }

    // ── CraftingBlockEntity 覆写：无限存储由集成 CPU 提供、本模块无加速线程 ──

    @Override
    public long getStorageBytes() {
        // 模块不贡献存储：存储由集成 CPU 提供（返回 0 避免多块累加溢出，
        // 上游 sensei 实测「负数字节 CPU」）。⚠ AE2 的 CraftingCPUCalculator
        // 要求簇内至少一块 getStorageBytes() > 0，本模块单放不成簇属预期行为。
        return 0;
    }

    @Override
    public int getAcceleratorThreads() {
        return 0; // 虚拟结算 N× 一次到位，无需并行线程（时间片限流由 mixin 统一管理）
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        // 模块放置即接网：不依赖 crafting unit 成型（上游 2026-09-04 sensei：贴其他
        // CPU/线缆也要能接入网络——作为集成 CPU 网络内的拓展模块）
        return super.getCableConnectionType(dir);
    }

    @Override
    public Set<Direction> getGridConnectableSides(appeng.api.orientation.BlockOrientation orientation) {
        // 恒 6 面可连（原版按成型裁剪：未成型 noneOf → 无法接网）
        return java.util.EnumSet.allOf(Direction.class);
    }

    // ── 样板槽访问（GUI 用） ──

    public int getPage() {
        return page;
    }

    public void setPage(int p) {
        this.page = Math.max(0, Math.min(PAGES - 1, p));
        setChanged();
    }

    public ItemStack getSlot(int index) {
        return index >= 0 && index < patterns.size() ? patterns.get(index) : ItemStack.EMPTY;
    }

    /**
     * 槽位是否只接受合成族样板（合成/切石机/锻造台，上游 2026-09-04 sensei 指正：
     * 矩阵不只放合成样板）；处理样板/普通物品/空气一律拒绝。
     * <p>
     * AE2 19 判定：{@link PatternDetailsHelper#decodePattern(ItemStack, Level)} 解出
     * {@code appeng.crafting.pattern.AECraftingPattern / AEStonecuttingPattern /
     * AESmithingTablePattern} 接受，{@code AEProcessingPattern} 拒绝。
     * 注：AE2 未提供「是不是合成族样板」的公开 API，只能按解码类名判断（白名单
     * 判定，不认识的新家族一律拒绝——宁可错杀也不让未知样板进虚拟结算白名单）；
     * 若将来 AE2 换类名/加新族（如 AE 版切石机变体），这里需要同步。
     */
    public static boolean isCraftingPatternItem(ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty() || level == null) {
            return false;
        }
        try {
            var details = PatternDetailsHelper.decodePattern(stack, level);
            if (details == null) {
                return false;
            }
            String name = details.getClass().getName();
            if (name.endsWith("AEProcessingPattern")) {
                return false; // 处理样板 → 走 feeder/机器，不进装配处理器
            }
            return name.endsWith("AECraftingPattern")
                    || name.endsWith("AEStonecuttingPattern")
                    || name.endsWith("AESmithingTablePattern")
                    || name.contains("EncodedCraftingPattern");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 放样板（编码后的 pattern 物品）；index 越界/物品非法直接忽略。 */
    public void setSlot(int index, ItemStack stack) {
        if (index < 0 || index >= TOTAL_SLOTS) {
            return;
        }
        if (com.ae2addon.crafting.CraftingCompat.debugLogs) {
            // 诊断：谁在写样板槽（上游 2026-09-04 GUI 吞样板排查）
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            String caller = st.length > 2 ? st[2].toString() : "?";
            String caller2 = st.length > 3 ? st[3].toString() : "?";
            com.ae2addon.AE2Addon.LOGGER.info(
                    "[assembler][core] setSlot idx={} 物品={} 调用者={} <- {}",
                    index,
                    (stack == null || stack.isEmpty()) ? "空气"
                            : stack.getHoverName().getString(),
                    caller, caller2);
        }
        if (stack == null || stack.isEmpty()) {
            if (index < patterns.size()) {
                patterns.set(index, ItemStack.EMPTY);
            }
        } else {
            while (patterns.size() <= index) {
                patterns.add(ItemStack.EMPTY);
            }
            patterns.set(index, stack.copyWithCount(1));
        }
        cacheDirty = true;
        setChanged();
        syncToClient();
    }

    /** 样板槽变化后通知 CraftingService 刷新（provider 声明列表）。 */
    public void onPatternsChanged() {
        cacheDirty = true;
        setChanged();
        var node = getMainNode();
        if (node != null && node.isActive()) {
            ICraftingProvider.requestUpdate(node);
        }
    }

    /**
     * 白名单判定：该合成样板是否被本核心声明（按样板解码后的产物 key 命中样板槽）。
     * <p>
     * 静态查询入口（名字/签名与上游 main 原文一致）：CraftingServiceMixin 移植版
     * 与 CraftingCpuLogicMixin 移植版直接调用 {@code module.declares(pattern)}，
     * 勿改名/改参数/改返回类型。
     */
    public boolean declares(IPatternDetails pattern) {
        if (pattern == null) {
            return false;
        }
        var outs = pattern.getOutputs();
        if (outs == null || outs.isEmpty()) {
            return false;
        }
        AEKey output = outs.get(0).what();
        if (output == null) {
            return false;
        }
        return ensureCache().declared.contains(output);
    }

    // ── ICraftingProvider：报告样板槽内全部样板 ──

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return ensureCache().details;
    }

    /**
     * 防御性实现：虚拟结算由 CPU mixin 在 pushPattern 前拦截（从不真正推送本核心）。
     * 若绕过拦截直接推来（例如处理类样板误入），拒收。
     */
    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs) {
        return false;
    }

    @Override
    public boolean isBusy() {
        return false; // 虚拟结算无真实占用；pushPattern 一律拒收（由 mixin 拦截）
    }

    // ── 缓存 ──

    private Cache ensureCache() {
        if (cacheDirty) {
            cacheDirty = false;
            Level lvl = getLevel();
            List<IPatternDetails> details = new ArrayList<>();
            Set<AEKey> outputs = new LinkedHashSet<>();
            if (lvl != null && !lvl.isClientSide) {
                for (ItemStack stack : patterns) {
                    if (stack == null || stack.isEmpty()) {
                        continue;
                    }
                    try {
                        var decoded = PatternDetailsHelper.decodePattern(stack, lvl);
                        if (decoded == null) {
                            continue;
                        }
                        details.add(decoded);
                        var outs = decoded.getOutputs();
                        if (outs != null) {
                            for (GenericStack out : outs) {
                                if (out != null && out.what() != null) {
                                    outputs.add(out.what());
                                }
                            }
                        }
                    } catch (RuntimeException ignored) {
                        // 槽位物品不是有效样板（如玩家误放普通物品）→ 跳过
                    }
                }
            }
            cachedPatterns = List.copyOf(details);
            declaredOutputs = Set.copyOf(outputs);
        }
        return new Cache(cachedPatterns, declaredOutputs);
    }

    private record Cache(List<IPatternDetails> details, Set<AEKey> declared) {
    }

    // ── PatternContainer（样板管理终端兼容，上游 2026-09-04 sensei：终端可访问样板槽）──
    // 终端全量暴露 9000 格：AE2 PAT 按每行 9 格拆行 + 滚动渲染（反编译确认
    // SlotsRow(container, offset, min(9, ...)) 拆行逻辑）——大容器天然支持。

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
        return new PatternContainerGroup(
                AEItemKey.of(ModBlocks.ASSEMBLER_CORE.get()),
                Component.translatable(getBlockState().getBlock().getDescriptionId()),
                List.of());
    }

    /** 终端适配：直接读写全部 9000 槽（与 GUI 同源 List，无页偏移、不吞样板）。 */
    private final InternalInventory terminalPatternInv = new InternalInventory() {
        @Override
        public int size() {
            return TOTAL_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return getSlot(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            setSlot(slot, stack);
            onPatternsChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isCraftingPatternItem(stack, getLevel());
        }
    };

    // ── 掉落物（2026-09-xx 1.21.1 适配）──

    /**
     * 拆除方块时把样板槽内容作为独立掉落物掉出。
     * <p>
     * 上游 1.20.1 靠战利品函数 {@code minecraft:copy_nbt(source=block_entity)} 把
     * {@code page}/{@code patterns} 抄进掉落的方块物品；1.21.1 移除了 copy_nbt，
     * 等效的 {@code minecraft:copy_components} 只能抄方块实体经
     * {@code BlockEntity#collectImplicitComponents} 导出的数据组件——本 BE 没有任何
     * 组件可导出（{@code collectComponents()} 恒为空 map），那段战利品函数只是空转，
     * 拆掉机器即丢样板。故按本仓库既有范式（{@link InfiniteInterfaceBE#addAdditionalDrops}）
     * 改为掉落独立物品：AE2 19 的 {@code AEBaseEntityBlock#onRemove} 会先调
     * {@code addAdditionalDrops(level, pos, drops)}，再 {@code Platform.spawnDrops} 撒出。
     * <p>
     * 编码样板的数据（输入/输出/替换等）本就存在物品自身的组件里，直接掉原
     * {@link ItemStack} 即可完整保留，不需要额外持久化；方块本体仍由 loot table
     * 的 survives_explosion 分支掉落。
     * <p>
     * 与上游的差异：上游把内容抄进方块物品，掉出来是「带样板的方块」；这里改成
     * 样板作为独立掉落物（与无限接口同范式），GUI 页码 {@code page}（纯视图状态）
     * 不随掉落保留，重新放置回到第 0 页。
     */
    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : patterns) {
            if (stack != null && !stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    // ── NBT ──

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("page", page);
        ListTag list = new ListTag();
        for (int i = 0; i < patterns.size(); i++) {
            ItemStack stack = patterns.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("i", i);
            entry.put("s", stack.save(registries, new CompoundTag()));
            list.add(entry);
        }
        tag.put("patterns", list);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        page = Math.max(0, Math.min(PAGES - 1, tag.getInt("page")));
        patterns.clear();
        ListTag list = tag.getList("patterns", Tag.TAG_COMPOUND);
        for (int k = 0; k < list.size(); k++) {
            CompoundTag entry = list.getCompound(k);
            int i = entry.getInt("i");
            ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("s"));
            if (i >= 0 && i < TOTAL_SLOTS && !stack.isEmpty()) {
                while (patterns.size() <= i) {
                    patterns.add(ItemStack.EMPTY);
                }
                patterns.set(i, stack);
            }
        }
        cacheDirty = true;
    }

    /** 客户端同步（GUI 页刷新用）：服务端变化经 Menu.broadcastChanges 自动下发，这里留空占位。 */
    private void syncToClient() {
        // 容器变化经 Menu.broadcastChanges 自动下发，无需额外包
    }
}
