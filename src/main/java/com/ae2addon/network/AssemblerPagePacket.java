package com.ae2addon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 装配处理器样板槽翻页包（客户端 → 服务端，v0.3 M3，2026-09-04 上游同步）。
 * <p>
 * 目标页（0..199），服务端 AssemblerCoreBE.setPage + 菜单槽自动刷新：
 * 页码由 {@code AssemblerMenu} 的 DataSlot 回传客户端，客户端不本地猜页（消除
 * 与服务端页的竞态吞样板）。
 * <p>
 * 1.21.1 移植差异：上游 Forge SimpleChannel（encode/decode/handle(Supplier&lt;NetworkEvent.Context&gt;)）
 * → NeoForge CustomPacketPayload + StreamCodec + static handle(msg, IPayloadContext)
 * （本地 FeederTogglePacket / OrderListPacket 同款写法）。由主 agent 在
 * AE2Addon.registerPayloads 里 playToServer 注册。
 */
public record AssemblerPagePacket(BlockPos pos, int page) implements CustomPacketPayload {

    public static final Type<AssemblerPagePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2addon", "assembler_page"));

    public static final StreamCodec<FriendlyByteBuf, AssemblerPagePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AssemblerPagePacket decode(FriendlyByteBuf buf) {
            return new AssemblerPagePacket(buf.readBlockPos(), buf.readVarInt());
        }

        @Override
        public void encode(FriendlyByteBuf buf, AssemblerPagePacket msg) {
            buf.writeBlockPos(msg.pos);
            buf.writeVarInt(msg.page);
        }
    };

    @Override
    public Type<AssemblerPagePacket> type() {
        return TYPE;
    }

    public static void handle(AssemblerPagePacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = context.player().level();
            if (!level.hasChunkAt(msg.pos)) {
                return;
            }
            if (level.getBlockEntity(msg.pos)
                    instanceof com.ae2addon.block.AssemblerCoreBE core) {
                core.setPage(msg.page);
            }
        });
    }
}
