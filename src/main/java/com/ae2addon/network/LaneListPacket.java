package com.ae2addon.network;

import com.ae2addon.gui.IntegratedCPUMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 量子分裂线程完整列表同步包（服务端 → 客户端）。
 * 一次性同步完整 lane 列表，客户端滚动面板渲染。
 */
public record LaneListPacket(List<String> lanes, int laneCount, int activeJobs,
                             boolean formed, int selectedLaneIndex) implements CustomPacketPayload {

    public static final Type<LaneListPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2addon", "lane_list"));

    public static final StreamCodec<FriendlyByteBuf, LaneListPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LaneListPacket decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<String> lanes = new ArrayList<>(size);
            for (int i = 0; i < size; i++) lanes.add(buf.readUtf());
            return new LaneListPacket(lanes, buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readVarInt());
        }
        @Override
        public void encode(FriendlyByteBuf buf, LaneListPacket msg) {
            buf.writeVarInt(msg.lanes.size());
            for (String s : msg.lanes) buf.writeUtf(s);
            buf.writeVarInt(msg.laneCount);
            buf.writeVarInt(msg.activeJobs);
            buf.writeBoolean(msg.formed);
            buf.writeVarInt(msg.selectedLaneIndex);
        }
    };

    @Override
    public Type<LaneListPacket> type() {
        return TYPE;
    }

    public static void handle(LaneListPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu instanceof IntegratedCPUMenu menu) {
                menu.fullLanes = msg.lanes;
                menu.laneCount = msg.laneCount;
                menu.activeJobs = msg.activeJobs;
                menu.formed = msg.formed;
                menu.selectedLaneIndex = msg.selectedLaneIndex;
            }
        });
    }
}
