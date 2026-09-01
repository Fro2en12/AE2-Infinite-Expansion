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
import net.neoforged.fml.ModList;

/**
 * Mekanism + Applied Mekanistics 可选集成（2026-08-28 sensei 需求：气体）。
 * <p>
 * - 标记槽：化学容器（气罐/灌注罐/颜料罐/泥浆罐）→ 内部化学物 AEKey（MekanismKey）
 * - 喂出：MekanismKey（化学物）→ 机器化学槽 insertChemical
 * <p>
 * compileOnly 依赖（mekanism / applied-mekanistics），运行时未装时 isLoaded() 为 false，
 * 所有方法短路返回，不影响主功能。
 * 注意：引用 Mekanism/appmek 类的方法只能在 isLoaded() 为 true 后调用
 * （JVM 按方法体懒加载类，标准可选集成模式）。
 * <p>
 * 待编译确认：Mekanism 10.7.x（NeoForge 1.21.1）已将化学物统一为单一
 * {@link ChemicalStack} + {@link IChemicalHandler}（不再区分 GasStack/InfusionStack/
 * PigmentStack/SlurryStack，也无 GAS/INFUSION/PIGMENT/SLURRY 分形态能力），
 * 能力入口改为 {@link Capabilities#CHEMICAL}（MultiTypeCapability）。
 * 因此 feed / chemicalInContainer 不再按形态分派，改用统一能力；isGas 无法再区分
 * 是否气体（对任意 MekanismKey 返回 true），需确认调用方语义。
 */
public final class MekanismGasCompat {

    private static boolean checked;
    private static boolean loaded;
    /** 诊断日志节流：距上次日志的毫秒数。 */
    private static long lastDiagLog;

    private MekanismGasCompat() {
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

    public static boolean isLoaded() {
        if (!checked) {
            checked = true;
            loaded = ModList.get().isLoaded("mekanism")
                    && ModList.get().isLoaded("appmek");
        }
        return loaded;
    }

    /** 标记槽：化学容器（气罐/灌注罐/颜料罐/泥浆罐）→ 内部化学物 AEKey；非容器返回 null。 */
    public static AEKey chemicalInContainer(ItemStack stack) {
        if (!isLoaded() || stack.isEmpty()) {
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

    /** 该 AEKey 是否为可喂出的化学物（MekanismKey 任意形态）。 */
    public static boolean isFeedable(AEKey key) {
        return isLoaded() && key instanceof MekanismKey;
    }

    /** 是否化学形态（Mekanism 10.7 已统一化学物类型，无 Gas 专属）；供侧面抽取预览用。 */
    public static boolean isGas(AEKey key) {
        // TODO 待编译确认：Mekanism 10.7 已取消 GasStack/形态常量，无法再区分是否气体，
        // 故对任意 MekanismKey 返回 true（即化学物），需确认调用方语义。
        return isLoaded() && key instanceof MekanismKey;
    }

    /** AEKey → MekanismKey（无/非化学返回 null）。 */
    public static MekanismKey mekKeyOf(AEKey key) {
        if (!isLoaded() || !(key instanceof MekanismKey mk)) {
            return null;
        }
        return mk;
    }

    /** 化学物 JEI 拖取 → AEKey（MekanismKey.of，化学物均可）。 */
    public static AEKey keyOfChemical(ChemicalStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) {
            return null;
        }
        return MekanismKey.of(stack);
    }

    /** 喂出：把化学物的 amount 量插入机器化学槽；返回实际喂出量（0=机器满/拒收）。带失败原因诊断（节流）。 */
    public static long feed(BlockEntity target, Direction side, AEKey key, long amount) {
        if (!isFeedable(key) || amount <= 0) {
            // 早退也诊断：key 类型不对或蓄水池里根本没化学物
            diag("isFeedable=false 或 amount<=0: key="
                    + (key == null ? "null" : key.getClass().getSimpleName())
                    + " amount=" + amount + " loaded=" + isLoaded());
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
