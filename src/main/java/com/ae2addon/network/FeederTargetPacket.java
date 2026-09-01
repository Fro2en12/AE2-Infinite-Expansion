package com.ae2addon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 每标记缓存目标设置包（客户端 → 服务端，中键弹框输入）：
 * 设置指定接口方块上某个标记槽的独立补货目标（-1 = 清除独立值回退全局）。
 */
public record FeederTargetPacket(BlockPos pos, int markerIndex, long target) implements CustomPacketPayload {

    public static final Type<FeederTargetPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2addon", "feeder_target"));

    public static final StreamCodec<FriendlyByteBuf, FeederTargetPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FeederTargetPacket decode(FriendlyByteBuf buf) {
            return new FeederTargetPacket(buf.readBlockPos(), buf.readInt(), buf.readLong());
        }
        @Override
        public void encode(FriendlyByteBuf buf, FeederTargetPacket msg) {
            buf.writeBlockPos(msg.pos);
            buf.writeInt(msg.markerIndex);
            buf.writeLong(msg.target);
        }
    };

    @Override
    public Type<FeederTargetPacket> type() {
        return TYPE;
    }

    public static void handle(FeederTargetPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = context.player().level();
            if (!level.hasChunkAt(msg.pos)) return;
            if (level.getBlockEntity(msg.pos) instanceof com.ae2addon.block.InfiniteInterfaceBE be) {
                be.setMarkerTarget(msg.markerIndex, msg.target);
            }
        });
    }
}
