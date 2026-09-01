package com.ae2addon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 接口 GUI 内联设置包（客户端 → 服务端）：修改接口运行参数并热加载。
 * key: stockTarget / restockInterval / feedBudget；value 已由客户端解析为 long。
 */
public record FeederSettingPacket(BlockPos pos, String key, long value) implements CustomPacketPayload {

    public static final Type<FeederSettingPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2addon", "feeder_setting"));

    public static final StreamCodec<FriendlyByteBuf, FeederSettingPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FeederSettingPacket decode(FriendlyByteBuf buf) {
            return new FeederSettingPacket(buf.readBlockPos(), buf.readUtf(), buf.readLong());
        }
        @Override
        public void encode(FriendlyByteBuf buf, FeederSettingPacket msg) {
            buf.writeBlockPos(msg.pos);
            buf.writeUtf(msg.key);
            buf.writeLong(msg.value);
        }
    };

    @Override
    public Type<FeederSettingPacket> type() {
        return TYPE;
    }

    public static void handle(FeederSettingPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = context.player().level();
            if (!level.hasChunkAt(msg.pos)) return;
            if (level.getBlockEntity(msg.pos) instanceof com.ae2addon.block.InfiniteInterfaceBE be) {
                be.setPerBlockParam(msg.key, msg.value);
            }
        });
    }
}
