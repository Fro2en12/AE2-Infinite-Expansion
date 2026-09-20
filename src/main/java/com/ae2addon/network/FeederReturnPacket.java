package com.ae2addon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 「退回网络」按钮包（客户端 → 服务端；上游 v1.3.0+ 同步 2026-09-06）：
 * 把供料站蓄水池里全部材料（未喂出的推送料 + 待入网缓存产物）插回网络存储。
 * 适用样板发错/任务放弃后材料收不回的场合；插不进的（网络满/拒收）留在蓄水池。
 */
public record FeederReturnPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<FeederReturnPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2addon", "feeder_return"));

    public static final StreamCodec<FriendlyByteBuf, FeederReturnPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FeederReturnPacket decode(FriendlyByteBuf buf) {
            return new FeederReturnPacket(buf.readBlockPos());
        }

        @Override
        public void encode(FriendlyByteBuf buf, FeederReturnPacket msg) {
            buf.writeBlockPos(msg.pos);
        }
    };

    @Override
    public Type<FeederReturnPacket> type() {
        return TYPE;
    }

    public static void handle(FeederReturnPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = context.player().level();
            if (!level.hasChunkAt(msg.pos)) return;
            var fh = FeederHostResolver.resolve(level, msg.pos);
            if (fh != null) {
                fh.returnAllToNetwork();
            }
        });
    }
}
