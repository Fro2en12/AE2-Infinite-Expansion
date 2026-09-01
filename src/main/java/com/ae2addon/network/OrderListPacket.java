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
 * 巨型订单列表同步包（服务端 → 客户端）。
 * 集成 CPU 界面「巨型订单」管理面板的数据源。
 */
public record OrderListPacket(List<String> orders) implements CustomPacketPayload {

    public static final Type<OrderListPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2addon", "order_list"));

    public static final StreamCodec<FriendlyByteBuf, OrderListPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OrderListPacket decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<String> orders = new ArrayList<>(size);
            for (int i = 0; i < size; i++) orders.add(buf.readUtf());
            return new OrderListPacket(orders);
        }
        @Override
        public void encode(FriendlyByteBuf buf, OrderListPacket msg) {
            buf.writeVarInt(msg.orders.size());
            for (String s : msg.orders) buf.writeUtf(s);
        }
    };

    @Override
    public Type<OrderListPacket> type() {
        return TYPE;
    }

    public static void handle(OrderListPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu instanceof IntegratedCPUMenu menu) {
                menu.fullOrders = msg.orders;
            }
        });
    }
}
