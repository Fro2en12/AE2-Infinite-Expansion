package com.ae2addon.block;

import appeng.block.AEBaseEntityBlock;
import appeng.block.crafting.CraftingUnitBlock;
import appeng.block.crafting.CraftingUnitType;
import com.ae2addon.gui.AssemblerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 无限级装配处理器·核心方块（v0.3 M3，2026-09-04 上游同步）。
 * <p>
 * 必须继承 CraftingUnitBlock（不能改 Block）：CraftingBlockEntity.onReady 强转
 * block -> AbstractCraftingUnitBlock；AEBaseEntityBlock 依赖 blockEntityClass。
 * 与普通 crafting unit / 集成 CPU 同簇（AE2 原版 CraftingCPUCluster 机制自动完成
 * 相邻 unit 检测成型），无需手搓成型。
 */
public class AssemblerCoreBlock extends CraftingUnitBlock {

    private static boolean BLOCK_ENTITY_CLASS_INIT = false;

    public AssemblerCoreBlock() {
        super(CraftingUnitType.STORAGE_256K);
        // AEBaseEntityBlock 的 blockEntityClass 在 BlockEntityType 构造器中未被设置，
        // 必须反射设为 AssemblerCoreBE.class 防 NPE（同 IntegratedCPUBlock 做法）。
        // 注：上游用 Forge 的 ObfuscationReflectionHelper，1.21.1 不用它。
        if (!BLOCK_ENTITY_CLASS_INIT) {
            BLOCK_ENTITY_CLASS_INIT = true;
            try {
                var f = AEBaseEntityBlock.class.getDeclaredField("blockEntityClass");
                f.setAccessible(true);
                f.set(this, AssemblerCoreBE.class);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 空手右键：已成型 → 打开样板槽界面（声明虚拟结算白名单）。
     * 1.21.1 把 1.20.1 的 use() 拆成 useItemOn（手持物品）/ useWithoutItem（空手），
     * 两者都接上，行为与上游一致。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AssemblerCoreBE core)) {
            return InteractionResult.FAIL;
        }
        if (core.isFormed()) {
            AssemblerMenu.open(player, pos);
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    /** 手持物品右键：已成型同样开界面（避免手持方块时被原版「升级」路径吃掉）。 */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(true);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AssemblerCoreBE core && core.isFormed()) {
            AssemblerMenu.open(player, pos);
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /**
     * 直接 new BE：基类 newBlockEntity 依赖 blockEntityType 字段（AE2 只给自己的
     * 方块注入），第三方方块不 override 会 NPE（同 IntegratedCPUBlock 做法）。
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssemblerCoreBE(pos, state);
    }
}
