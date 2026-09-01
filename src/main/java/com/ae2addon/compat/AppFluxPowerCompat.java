package com.ae2addon.compat;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import com.glodblock.github.appflux.common.caps.NetworkFEPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * AppFlux 可选集成（2026-08-28 sensei：感应卡 = AppFlux 对机器的供电卡）。
 * <p>
 * 感应卡（appflux:induction_card）插入接口后：每 tick 从网络 FE 存储
 * （NetworkFEPower）给正面机器的 IEnergyStorage 充能。
 * <p>
 * compileOnly 依赖（appflux），运行时未装 AppFlux 时短路。
 * <p>
 * NeoForge 1.21：AppFlux 的 NetworkFEPower 已改为 record（实现
 * {@link IEnergyStorage}），用构造器 {@code new NetworkFEPower(storage, source)} 取代
 * 旧版静态 {@code of()} 工厂；能量能力用 {@link Capabilities.EnergyStorage#BLOCK}，
 * 经 {@code level.getCapability(BlockCapability, BlockPos, Direction)} 查询并直接返回可空值
 * （NeoForge 1.21 无 BlockEntity.getCapability，也不再用 LazyOptional）。
 */
public final class AppFluxPowerCompat {

    private static boolean checked;
    private static boolean loaded;
    /** 每 tick 供电上限（FE；防单 tick 卡顿，可再调）。 */
    private static final long MAX_FE_PER_TICK = 100_000_000L;

    private AppFluxPowerCompat() {
    }

    public static boolean isLoaded() {
        if (!checked) {
            checked = true;
            loaded = ModList.get().isLoaded("appflux");
        }
        return loaded;
    }

    /** AppFlux 感应卡物品（注册表查询，未装返回 null）。 */
    public static net.minecraft.world.item.Item inductionCard() {
        if (!isLoaded()) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("appflux", "induction_card"));
    }

    /** 给机器充能：网络 FE → 机器能量槽；返回本次实际传输 FE。 */
    public static long feedEnergy(BlockEntity target, Direction side,
            appeng.api.networking.IGrid grid, IActionSource source) {
        if (!isLoaded() || target == null || grid == null) {
            return 0;
        }
        try {
            IStorageService storage = grid.getStorageService();
            if (storage == null) {
                return 0;
            }
            IEnergyStorage networkEnergy = new NetworkFEPower(storage, source);
            if (networkEnergy == null || !networkEnergy.canExtract()) {
                return 0;
            }
            Level level = target.getLevel();
            if (level == null) {
                return 0;
            }
            BlockPos pos = target.getBlockPos();
            IEnergyStorage machine = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
            if (machine == null || !machine.canReceive()) {
                if (System.getProperty("ae2addon.debugPower") != null) {
                    com.ae2addon.AE2Addon.LOGGER.info(
                            "[ae2addon][feeder] 供电诊断: 机器能量槽不可接收 (machine={})",
                            machine);
                }
                return 0;
            }
            if (System.getProperty("ae2addon.debugPower") != null) {
                com.ae2addon.AE2Addon.LOGGER.info(
                        "[ae2addon][feeder] 供电诊断: networkEnergy={} canExtract={} stored={}/{} 上限={}FE/t",
                        networkEnergy, networkEnergy.canExtract(),
                        machine.getEnergyStored(), machine.getMaxEnergyStored(), MAX_FE_PER_TICK);
            }
            int need = Math.min((int) MAX_FE_PER_TICK,
                    machine.getMaxEnergyStored() - machine.getEnergyStored());
            if (need <= 0) {
                return 0;
            }
            // 先模拟确认机器能收多少，再按量从网络扣，避免多扣
            int accepted = machine.receiveEnergy(need, true);
            if (accepted <= 0) {
                return 0;
            }
            int extracted = networkEnergy.extractEnergy(accepted, false);
            if (extracted <= 0) {
                return 0;
            }
            machine.receiveEnergy(extracted, false);
            return extracted;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
