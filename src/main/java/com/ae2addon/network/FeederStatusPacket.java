package com.ae2addon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * ME 接口（无限级）蓄水池状态同步包（服务端 → 客户端，变化检测节流）。
 */
public record FeederStatusPacket(List<String> lines) implements CustomPacketPayload {

    public static final Type<FeederStatusPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2addon", "feeder_status"));

    public static final StreamCodec<FriendlyByteBuf, FeederStatusPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FeederStatusPacket decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<String> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) lines.add(buf.readUtf());
            return new FeederStatusPacket(lines);
        }
        @Override
        public void encode(FriendlyByteBuf buf, FeederStatusPacket msg) {
            buf.writeVarInt(msg.lines.size());
            for (String line : msg.lines) buf.writeUtf(line);
        }
    };

    @Override
    public Type<FeederStatusPacket> type() {
        return TYPE;
    }

    public static void handle(FeederStatusPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> com.ae2addon.gui.InfiniteInterfaceScreen.handleStatus(msg.lines));
    }
}
