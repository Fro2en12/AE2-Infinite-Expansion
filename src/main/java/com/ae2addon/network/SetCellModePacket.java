package com.ae2addon.network;

import com.ae2addon.cell.UnlimitedCellInventory;
import com.ae2addon.item.UniversalStorageCell;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 设置存储元件模式的数据包（NeoForge 1.21 payload）
 * <p>
 * 客户端发送模式编号 → 服务端更新玩家手中物品的模式
 * 通过 UnlimitedCellInventory.setMode() 同步更新摘要标签 _b/_t
 */
public record SetCellModePacket(int mode) implements CustomPacketPayload {

    public static final Type<SetCellModePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2addon", "set_cell_mode"));

    public static final StreamCodec<FriendlyByteBuf, SetCellModePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            SetCellModePacket::mode,
            SetCellModePacket::new
    );

    @Override
    public Type<SetCellModePacket> type() {
        return TYPE;
    }

    public static void handle(final SetCellModePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof UniversalStorageCell)) {
                stack = player.getOffhandItem();
                if (!(stack.getItem() instanceof UniversalStorageCell)) return;
            }

            int newMode = packet.mode;
            if (newMode < 1 || newMode > 3) {
                // mode 0 来自「⇄ 模式」按钮：循环到下一个模式
                int cur = com.ae2addon.AE2Addon.cellTag(stack).getInt("umode");
                if (cur < 1 || cur > 3) cur = 1;
                newMode = (cur % 3) + 1;
            }

            // 通过 inventory 设置模式，同步更新 _b/_t 摘要标签
            UnlimitedCellInventory inv = new UnlimitedCellInventory(stack, null);
            inv.setMode(newMode);
        });
    }
}
