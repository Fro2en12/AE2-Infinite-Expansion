package com.ae2addon.block;

/**
 * 装配处理器模块注册表（v0.3 M3，2026-09-04 上游同步）。
 * <p>
 * sensei 决策：不做独立合成单元，作为集成 CPU 的拓展模块——模块接入哪个网格，
 * 就服务该网格的全部集成 CPU（白名单共享；一般每网一个模块）。
 * <p>
 * 两条查询入口（名字/签名与上游 main 原文一致，CraftingServiceMixin 的移植版
 * 直接静态调用，勿改名）：
 * <ul>
 *   <li>{@link #moduleFor(IntegratedCPUBE)}：按 CPU 反查模块（CraftingCpuLogicMixin
 *       的虚拟结算判定用；owner 为 null 时必须返回 null）</li>
 *   <li>{@link #moduleForGrid(appeng.api.networking.IGrid)}：按网格反查模块
 *       （模拟期即时结算判定用，不需要集成 CPU owner）</li>
 * </ul>
 * 同网格匹配而非 owner 精确匹配（上游 2026-09-04 修复）：多集成 CPU 同网络时，
 * 按 owner 匹配会让其余 CPU 的订单判定 module=null → 走 1× 强制 → 巨型订单卡死。
 */
public final class AssemblerRegistry {

    private static final java.util.Set<AssemblerCoreBE> ACTIVE =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private AssemblerRegistry() {
    }

    public static void register(AssemblerCoreBE blockEntity) {
        ACTIVE.add(blockEntity);
    }

    public static void unregister(AssemblerCoreBE blockEntity) {
        ACTIVE.remove(blockEntity);
    }

    /**
     * 查找服务某集成 CPU 的装配处理器模块。
     * 模块未建立 owner 时惰性重试关联（网格就绪延迟兜底）。
     */
    public static AssemblerCoreBE moduleFor(IntegratedCPUBE owner) {
        if (owner == null) {
            return null;
        }
        var ownerGrid = gridOf(owner);
        if (ownerGrid == null) {
            return null;
        }
        for (AssemblerCoreBE core : ACTIVE) {
            if (core.isRemoved() || !core.isFormed()) {
                continue;
            }
            if (core.getOwnerCPU() == null) {
                core.refreshOwnerNow();
            }
            if (gridOf(core) == ownerGrid) {
                return core;
            }
        }
        return null;
    }

    /**
     * 按网格查找装配处理器模块（上游 2026-09-06：模拟期即时结算判定用——
     * 不需要集成 CPU owner，直接从网格找服务本网的模块）。
     */
    public static AssemblerCoreBE moduleForGrid(appeng.api.networking.IGrid grid) {
        if (grid == null) {
            return null;
        }
        for (AssemblerCoreBE core : ACTIVE) {
            if (core.isRemoved() || !core.isFormed()) {
                continue;
            }
            if (gridOf(core) == grid) {
                return core;
            }
        }
        return null;
    }

    /** 取方块实体的所属网格（未接线/未就绪返回 null）。 */
    private static appeng.api.networking.IGrid gridOf(appeng.blockentity.grid.AENetworkedBlockEntity be) {
        try {
            var node = be.getMainNode();
            return node == null ? null : node.getGrid();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
