package com.ae2addon.init;

import com.ae2addon.AE2Addon;
import com.ae2addon.gui.AssemblerScreen;
import com.ae2addon.gui.InfiniteInterfaceScreen;
import com.ae2addon.gui.IntegratedCPUScreen;
import com.ae2addon.gui.Mode2ConfigScreen;
import com.ae2addon.gui.ModeSelectScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 客户端设置：注册GUI界面
 */
@EventBusSubscriber(modid = AE2Addon.MODID, value = Dist.CLIENT)
public class ClientSetup {

    /** 线缆面板 part 模型注册（必须早于 AE2 的 PartModels.freeze()；2026-09-02 上游同步）。 */
    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                var models = appeng.items.parts.PartModelsHelper
                        .createModels(com.ae2addon.part.InfiniteInterfacePart.class);
                appeng.api.parts.PartModels.registerModels(models);
            } catch (Throwable e) {
                // NoClassDefFoundError 等 Error 也要吞掉：缺可选依赖时不能让客户端启不来
                AE2Addon.LOGGER.warn("[ae2addon] part 模型注册失败: ", e);
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.MODE_SELECT.get(), ModeSelectScreen::new);
        event.register(ModMenuTypes.MODE2_CONFIG.get(), Mode2ConfigScreen::new);
        event.register(ModMenuTypes.INTEGRATED_CPU.get(), IntegratedCPUScreen::new);
        event.register(ModMenuTypes.INFINITE_INTERFACE.get(), InfiniteInterfaceScreen::new);
        event.register(ModMenuTypes.ASSEMBLER.get(), AssemblerScreen::new);
    }
}
