package com.ae2addon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 主动输入/输出开关包（客户端 → 服务端）：
 * which = "extract"（主动抽取）/ "feed"（主动喂出），切换对应开关。
 */
public record FeederTogglePacket(BlockPos pos, String which) implements CustomPacketPayload {

    public static final Type<FeederTogglePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2addon", "feeder_toggle"));

    public static final StreamCodec<FriendlyByteBuf, FeederTogglePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FeederTogglePacket decode(FriendlyByteBuf buf) {
            return new FeederTogglePacket(buf.readBlockPos(), buf.readUtf());
        }
        @Override
        public void encode(FriendlyByteBuf buf, FeederTogglePacket msg) {
            buf.writeBlockPos(msg.pos);
            buf.writeUtf(msg.which);
        }
    };

    @Override
    public Type<FeederTogglePacket> type() {
        return TYPE;
    }

    public static void handle(FeederTogglePacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = context.player().level();
            if (!level.hasChunkAt(msg.pos)) return;
            if (level.getBlockEntity(msg.pos) instanceof com.ae2addon.block.InfiniteInterfaceBE be) {
                be.toggleActive(msg.which);
            }
        });
    }
}
