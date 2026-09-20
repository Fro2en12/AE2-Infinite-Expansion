package com.ae2addon.gui;

import com.ae2addon.block.AssemblerCoreBE;
import com.ae2addon.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 装配处理器样板槽菜单（v0.3 M3，2026-09-04 上游同步）：
 * 每页 9×5 = 45 格，200 页翻页（5×9×200 规格）。
 * <p>
 * 槽写入经 pageContainer（当前页窗口容器）转手，服务端 broadcastChanges 负责
 * 与核心的 9000 格样板槽对账。上游踩过的三个坑（原文保留在方法注释里）：
 * ① 首帧只加载不写回（空窗口清库）② 仅同页才写回（翻页后窗口是旧页残留）
 * ③ 先写回后重载。
 * <p>
 * 1.21.1 移植差异：上游 {@code NetworkHooks.openScreen} → {@code ServerPlayer#openMenu}
 * （本地 IntegratedCPUMenu 同款）；{@code fromNetwork} 收
 * {@link RegistryFriendlyByteBuf}（NeoForge IMenuTypeExtension 工厂签名）。
 */
public class AssemblerMenu extends AbstractContainerMenu {

    private final AssemblerCoreBE core;

    /** 客户端侧标记（playerInventory 的 level 判断）。 */
    private final boolean clientSide;

    /** 当前页窗口容器（45 槽）：服务端广播前从 core 重载，客户端渲染/拖拽走它。
     *  直连 core 的槽在客户端会读 stale 页 + 广播回写错位（上游 2026-09-04）。 */
    private final SimpleContainer pageContainer = new SimpleContainer(AssemblerCoreBE.PAGE_SIZE);

    /** 首次同步标志：打开瞬间窗口为空，必须先加载再允许写回
     *  （否则空窗口会把 core 里已有样板清掉——上游 2026-09-04 吞样板真凶）。 */
    private boolean windowInitialized;

    /** 窗口内容所属页（写回仅限同页——翻页后窗口是旧页残留，写回会把旧页样板
     *  复制进新页 = 每页样板同步，上游 2026-09-04 23:05 sensei 反馈）。 */
    private int windowPage = -1;

    // 槽区几何
    private static final int COLS = 9;
    private static final int ROWS = 5;
    private static final int SLOT_X0 = 8;
    private static final int SLOT_Y0 = 30;
    private static final int PLAYER_Y = 118;

    /** 样板槽 slotId 区间 [0, PAGE_SIZE)。 */
    private static final int PATTERN_SLOTS = AssemblerCoreBE.PAGE_SIZE;
    /** 玩家背包 slotId 区间 [PAGE_SIZE, PAGE_SIZE + 36)。 */
    private static final int PLAYER_SLOTS = 36;

    /** 当前页（服务端权威，DataSlot 同步——上游 2026-09-04：翻页包异步会导致
     *  客户端页码与服务端页竞态，放样板被写进旧页 → 吞样板）。 */
    private final DataSlot pageData = addDataSlot(new DataSlot() {
        @Override
        public int get() {
            return core.getPage();
        }

        @Override
        public void set(int value) {
            core.setPage(value);
        }
    });

    /**
     * 样板槽：容器恒为「当前页窗口」（{@link AssemblerCoreBE#PAGE_SIZE}=45 格），
     * index 就是页内下标 0..44。翻页由服务端 {@link #broadcastChanges()} 把新页内容
     * 灌进 pageContainer 完成——这 45 个槽<b>永远</b>代表当前显示页，所以本类
     * <b>不重写 isActive()</b>（继承 vanilla {@code Slot#isActive()} 的恒真，
     * 与上游裸 Slot 语义一致）。
     * <p>
     * 2026-09-20 分页渲染修复：本地移植时把 InfiniteInterfaceMenu 的
     * 「45 格 = 5 页 × 9 格，按页显隐」模式搬了过来，写成
     * {@code page = i / AssemblerCoreBE.PAGE_SIZE}（PAGE_SIZE=45 → 恒 0），
     * isActive() 退化成 {@code page == currentPage()}：第 1 页侥幸为真，
     * 第 2 页起因 0 != currentPage 而全 45 格 inactive。
     * 1.21.1 {@code AbstractContainerScreen} 渲染（render L105-115）与命中
     * （findSlot L289-296）双重过滤 isActive()，症状即「翻到第 2 页起只剩
     * Screen 自绘的空格底、放不进也点不中」。
     */
    private class PatternSlot extends Slot {
        PatternSlot(int index, int x, int y) {
            super(pageContainer, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // 只接受合成族样板（合成/切石机/锻造台）；处理样板与普通物品拒绝
            return AssemblerCoreBE.isCraftingPatternItem(stack, core.getLevel());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    public AssemblerMenu(int id, Inventory playerInventory, AssemblerCoreBE core) {
        super(ModMenuTypes.ASSEMBLER.get(), id);
        this.core = core;
        this.clientSide = playerInventory.player.level().isClientSide;

        // 样板槽：每页 9 列 × 5 行，窗口按当前页显示（服务端由 broadcastChanges 维护）。
        // 2026-09-20 分页渲染修复：ROWS*COLS == PAGE_SIZE，这 45 个槽就是「当前页」的
        // 全部 45 格，槽上不存在页号——原先传的 i/PAGE_SIZE 恒为 0，会把第 2 页起
        // 的槽全判成 isActive()==false（不渲染、不可点）。
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int i = row * COLS + col;
                addSlot(new PatternSlot(i, SLOT_X0 + col * 18, SLOT_Y0 + row * 18));
            }
        }
        // 玩家背包 9×3 + 快捷栏（固定位置）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, PLAYER_Y + 58));
        }
    }

    /** 客户端构造（NeoForge IMenuTypeExtension 工厂）：从网络包读 pos 定位 BE。 */
    public static AssemblerMenu fromNetwork(int id, Inventory playerInventory,
            RegistryFriendlyByteBuf buffer) {
        var pos = buffer.readBlockPos();
        if (!playerInventory.player.level().hasChunkAt(pos)) {
            throw new IllegalStateException("装配处理器核心所在的区块未加载: " + pos);
        }
        if (playerInventory.player.level().getBlockEntity(pos)
                instanceof AssemblerCoreBE core) {
            return new AssemblerMenu(id, playerInventory, core);
        }
        throw new IllegalStateException("无法定位装配处理器核心: " + pos);
    }

    /**
     * 打开界面（服务端调用；1.21.1 用 ServerPlayer#openMenu，
     * 与本地 IntegratedCPUMenu.openMenu 同款写法）。
     */
    public static void open(Player player, BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(player.level().getBlockEntity(pos) instanceof AssemblerCoreBE core)) {
            return;
        }
        var provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new AssemblerMenu(containerId, inventory, core),
                Component.translatable("gui.ae2addon.assembler.title"));
        serverPlayer.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
    }

    public AssemblerCoreBE getCore() {
        return core;
    }

    /**
     * 当前页（客户端显示用：服务端权威值，经 DataSlot 同步）。
     */
    public int currentPage() {
        return Math.max(0, Math.min(AssemblerCoreBE.PAGES - 1, pageData.get()));
    }

    /**
     * 客户端请求翻页（delta = ±1，循环）。只发包，目标页由服务端计算并写入
     * DataSlot（客户端不本地猜页 → 消除与服务端页的竞态吞样板，上游 2026-09-04）。
     */
    public void changePage(int delta) {
        if (clientSide) {
            int current = currentPage();
            int next = Math.floorMod(current + delta, AssemblerCoreBE.PAGES);
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.ae2addon.network.AssemblerPagePacket(core.getBlockPos(), next));
        }
    }

    /**
     * 服务端权威同步（上游 2026-09-04 三个防错原则）：
     * ① 首帧只加载不写回（空窗口清库）② 仅同页才写回（翻页后窗口是旧页残留，
     * 写回会把旧页样板复制进新页 = 每页同步）③ 先写回后重载。
     */
    @Override
    public void broadcastChanges() {
        if (!clientSide) {
            int page = core.getPage();
            int base = page * AssemblerCoreBE.PAGE_SIZE;
            if (windowInitialized && page == windowPage) {
                boolean dirty = false;
                for (int i = 0; i < AssemblerCoreBE.PAGE_SIZE; i++) {
                    if (!ItemStack.matches(core.getSlot(base + i), pageContainer.getItem(i))) {
                        dirty = true;
                        core.setSlot(base + i, pageContainer.getItem(i));
                    }
                }
                if (dirty) {
                    core.onPatternsChanged();
                    if (com.ae2addon.crafting.CraftingCompat.debugLogs) {
                        com.ae2addon.AE2Addon.LOGGER.info(
                                "[assembler][menu] broadcastChanges 写回: page={} 槽0={} core槽0={}",
                                page,
                                pageContainer.getItem(0).getHoverName().getString(),
                                core.getSlot(base).getHoverName().getString());
                    }
                }
            }
            windowInitialized = true;
            windowPage = page;
            for (int i = 0; i < AssemblerCoreBE.PAGE_SIZE; i++) {
                pageContainer.setItem(i, core.getSlot(base + i));
            }
        }
        super.broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (!clientSide && com.ae2addon.crafting.CraftingCompat.debugLogs) {
            com.ae2addon.AE2Addon.LOGGER.info(
                    "[assembler][menu] clicked slotId={} dragType={} type={} page={} carried={} 槽内={}",
                    slotId, dragType, clickType, core.getPage(),
                    getCarried().getHoverName().getString(),
                    (slotId >= 0 && slotId < slots.size())
                            ? slots.get(slotId).getItem().getHoverName().getString() : "-");
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    /**
     * 关闭前落盘：窗口内容写回 core（玩家最后改动）。仅当窗口已初始化且
     * 未翻页（窗口内容属于当前页）才写回——否则跳过，防空窗口/旧页残留清库。
     */
    @Override
    public void removed(Player player) {
        if (!clientSide && windowInitialized && core.getPage() == windowPage) {
            int base = core.getPage() * AssemblerCoreBE.PAGE_SIZE;
            boolean dirty = false;
            for (int i = 0; i < AssemblerCoreBE.PAGE_SIZE; i++) {
                if (!ItemStack.matches(core.getSlot(base + i), pageContainer.getItem(i))) {
                    dirty = true;
                    core.setSlot(base + i, pageContainer.getItem(i));
                }
            }
            if (dirty) {
                core.onPatternsChanged();
            }
        }
        super.removed(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack inSlot = slot.getItem();
            copy = inSlot.copy();
            if (index < PATTERN_SLOTS) {
                // 样板槽 → 背包
                if (!moveItemStackTo(inSlot, PATTERN_SLOTS, PATTERN_SLOTS + PLAYER_SLOTS, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 背包 → 样板槽（仅有效样板可放）
                if (!moveItemStackTo(inSlot, 0, PATTERN_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (inSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return core != null && !core.isRemoved()
                && player.level().getBlockEntity(core.getBlockPos()) == core
                && player.distanceToSqr(core.getBlockPos().getX() + 0.5,
                        core.getBlockPos().getY() + 0.5,
                        core.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
