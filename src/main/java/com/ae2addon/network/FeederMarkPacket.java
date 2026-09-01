package com.ae2addon.network;

import appeng.api.stacks.AEKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 标记槽标记设置包（客户端 → 服务端）：JEI 拖取等客户端场景设置标记。
 * <p>
 * NeoForge 1.21 payload（CustomPacketPayload + StreamCodec）。
 * 携带标记槽索引 + AEKey（流体/气体/物品均可）。
 * AEKey 序列化：优先使用 AE2 19.x 的 {@link AEKey#OPTIONAL_STREAM_CODEC}（nullable 语义，兼容 key==null 清除标记）。
 */
public record FeederMarkPacket(int markerIndex, AEKey key) implements CustomPacketPayload {

    public static final Type<FeederMarkPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2addon", "feeder_mark"));

    // 字段顺序：markerIndex 1 字节（原 Forge writeByte）+ AEKey（AEKey.OPTIONAL_STREAM_CODEC，写 presence 位）
    public static final StreamCodec<RegistryFriendlyByteBuf, FeederMarkPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FeederMarkPacket decode(RegistryFriendlyByteBuf buf) {
            int index = buf.readByte();
            AEKey key = AEKey.OPTIONAL_STREAM_CODEC.decode(buf);
            return new FeederMarkPacket(index, key);
        }
        @Override
        public void encode(RegistryFriendlyByteBuf buf, FeederMarkPacket msg) {
            buf.writeByte(msg.markerIndex);
            AEKey.OPTIONAL_STREAM_CODEC.encode(buf, msg.key);
        }
    };

    @Override
    public Type<FeederMarkPacket> type() {
        return TYPE;
    }

    public static void handle(final FeederMarkPacket msg, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof com.ae2addon.gui.InfiniteInterfaceMenu menu)) {
                return;
            }
            var feeder = menu.getFeeder();
            if (feeder == null) {
                return;
            }
            if (msg.key == null) {
                feeder.clearMarker(msg.markerIndex);
            } else {
                feeder.markByKey(msg.markerIndex, msg.key);
            }
        });
    }
}
