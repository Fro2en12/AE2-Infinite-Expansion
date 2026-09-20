package com.ae2addon.compat;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Mekanism + Applied Mekanistics 化学物实际操作（惰性类，随上游 v1.3.0 同步）。
 * <p>
 * 本类允许自由引用 mekanism / appmek 类：只有 {@link MekanismGasCompat#isLoaded()}
 * 为 true 时调用方才会加载它。门面 {@link MekanismGasCompat} 一律不引用可选依赖类
 * （2026-09-04 崩溃教训：签名或方法体引用可选依赖 → 类加载验证期 NoClassDefFoundError）。
 * <p>
 * Mekanism 10.7.x（NeoForge 1.21.1）已把化学物统一为单一 {@link ChemicalStack} +
 * {@link IChemicalHandler}（不再区分 Gas/Infusion/Pigment/Slurry 与分形态能力），
 * 能力入口为 {@link Capabilities#CHEMICAL}（MultiTypeCapability）。
 */
public final class MekanismChemCompat {

    /** 诊断日志节流：距上次日志的毫秒数。 */
    private static long lastDiagLog;

    private MekanismChemCompat() {
    }

    /** 节流诊断日志（每 5 秒最多一条）。 */
    private static void diag(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastDiagLog < 5000) {
            return;
        }
        lastDiagLog = now;
        com.ae2addon.AE2Addon.LOGGER.warn("[ae2addon][feeder] 化学物喂出失败: {}", msg);
    }

    /** 标记槽：化学容器（气罐/灌注罐/颜料罐/泥浆罐）→ 内部化学物 AEKey；非容器返回 null。 */
    public static AEKey chemicalInContainer(ItemStack stack) {
        if (!MekanismGasCompat.isLoaded() || stack.isEmpty()) {
            return null;
        }
        try {
            IChemicalHandler ch = Capabilities.CHEMICAL.getCapability(stack);
            if (ch == null || ch.getChemicalTanks() <= 0) {
                return null;
            }
            ChemicalStack chemical = ch.getChemicalInTank(0);
            if (chemical == null || chemical.isEmpty()) {
                return null;
            }
            return MekanismKey.of(chemical);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 是否化学形态（Mekanism 10.7 已统一化学物类型，无 Gas 专属）；供侧面抽取预览用。 */
    public static boolean isGas(AEKey key) {
        return MekanismGasCompat.isLoaded() && key instanceof MekanismKey;
    }

    /** AEKey → MekanismKey（无/非化学返回 null）。 */
    public static MekanismKey mekKeyOf(AEKey key) {
        if (!MekanismGasCompat.isLoaded() || !(key instanceof MekanismKey mk)) {
            return null;
        }
        return mk;
    }

    /** 化学物 JEI 拖取 → AEKey（MekanismKey.of，化学物均可）。 */
    public static AEKey keyOfChemical(ChemicalStack stack) {
        if (!MekanismGasCompat.isLoaded() || stack == null || stack.isEmpty()) {
            return null;
        }
        return MekanismKey.of(stack);
    }

    /** 喂出：把化学物的 amount 量插入机器化学槽；返回实际喂出量（0=机器满/拒收）。带失败原因诊断（节流）。 */
    public static long feed(BlockEntity target, Direction side, AEKey key, long amount) {
        if (!MekanismGasCompat.isFeedable(key) || amount <= 0) {
            // 早退也诊断：key 类型不对或蓄水池里根本没化学物
            diag("isFeedable=false 或 amount<=0: key="
                    + (key == null ? "null" : key.getClass().getSimpleName())
                    + " amount=" + amount + " loaded=" + MekanismGasCompat.isLoaded());
            return 0;
        }
        try {
            MekanismKey mekKey = (MekanismKey) key;
            ChemicalStack stack = mekKey.getStack();
            if (stack == null || stack.isEmpty()) {
                diag("key 内部化学物为空");
                return 0;
            }
            long insertAmount = Math.min(amount, Integer.MAX_VALUE);
            IChemicalHandler handler = findHandler(target, side);
            if (handler == null) {
                diag("机器无化学槽（" + machineName(target) + "）");
                return 0;
            }
            ChemicalStack leftover = handler.insertChemical(
                    stack.copyWithAmount(insertAmount), Action.EXECUTE);
            long fed = insertAmount - leftover.getAmount();
            if (fed <= 0) {
                diag("机器拒收化学物（尝试 " + insertAmount + "；槽满或不吃）");
            }
            return Math.max(0, fed);
        } catch (RuntimeException e) {
            diag("异常: " + e);
            return 0;
        }
    }

    private static String machineName(BlockEntity target) {
        try {
            return target.getBlockState().getBlock().getName().getString();
        } catch (RuntimeException e) {
            return "?";
        }
    }

    /** 查找机器化学 handler：指定面优先，找不到遍历其余面。 */
    private static IChemicalHandler findHandler(BlockEntity target, Direction primary) {
        if (target == null) {
            return null;
        }
        Level level = target.getLevel();
        if (level == null) {
            return null;
        }
        BlockPos pos = target.getBlockPos();
        IChemicalHandler h = Capabilities.CHEMICAL.getCapabilityIfLoaded(level, pos, primary);
        if (h != null) {
            return h;
        }
        for (Direction side : Direction.values()) {
            if (side == primary) {
                continue;
            }
            h = Capabilities.CHEMICAL.getCapabilityIfLoaded(level, pos, side);
            if (h != null) {
                return h;
            }
        }
        return null;
    }
}
