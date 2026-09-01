package com.ae2addon.block;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import com.ae2addon.gui.InfiniteInterfaceMenu;
import com.ae2addon.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * ME 接口（无限级）— 机器供料站方块。
 * 方向性方块（水平 4 朝向）：FACING = 正面 = 相邻机器所在侧。
 * - 正面：喂出物品给机器 + 机器/漏斗可抽
 * - 其余面：接 AE 网络
 */
public class InfiniteInterfaceBlock extends AEBaseEntityBlock<InfiniteInterfaceBE> {

    public InfiniteInterfaceBlock() {
        super(stoneProps().strength(3.0f).requiresCorrectToolForDrops());
        setBlockEntity(InfiniteInterfaceBE.class, null, null, null);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    @Override
    public net.minecraft.world.item.Item asItem() {
        var ro = com.ae2addon.init.ModItems.INFINITE_INTERFACE_ITEM;
        if (ro != null && ro.isBound()) {
            return ro.get();
        }
        return super.asItem();
    }

    /** 带物品交互：配置卡/内存卡复制粘贴（shift+右键粘贴，右键复制）。1.21.1 签名：useItemOn。 */
    @Override
    public ItemInteractionResult useItemOn(
            ItemStack heldStack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (heldStack.getItem() instanceof com.ae2addon.item.ConfigCardItem
                || heldStack.getItem() instanceof appeng.items.tools.MemoryCardItem) {
            var be = getBlockEntity(level, pos);
            if (be != null) {
                boolean paste = player.isShiftKeyDown();
                boolean handled = paste
                        ? com.ae2addon.util.MemoryCardHelper.handlePaste(be, player, heldStack)
                        : com.ae2addon.util.MemoryCardHelper.handleCopy(be, player, heldStack);
                if (handled) {
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }
        // 非卡片物品：交给 useWithoutItem 打开 GUI（或允许物品自身行为）
        if (player instanceof ServerPlayer serverPlayer) {
            openInterfaceGui(serverPlayer, level, pos);
            return ItemInteractionResult.sidedSuccess(true);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 空手/非特殊物品交互：打开接口 GUI。 */
    @Override
    public InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            openInterfaceGui(serverPlayer, level, pos);
            return InteractionResult.sidedSuccess(true);
        }
        return InteractionResult.PASS;
    }

    private void openInterfaceGui(ServerPlayer serverPlayer, Level level, BlockPos pos) {
        var be = getBlockEntity(level, pos);
        if (be == null) {
            return;
        }
        serverPlayer.openMenu(
                new SimpleMenuProvider((containerId, inventory, ignored) ->
                        new InfiniteInterfaceMenu(containerId, inventory, be),
                        Component.translatable("gui.ae2addon.infinite_interface.title")),
                buffer -> MenuLocators.writeToPacket(buffer, MenuLocators.forBlockEntity(be)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfiniteInterfaceBE(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof InfiniteInterfaceBE feeder) {
                feeder.serverTick();
            }
        };
    }
}
