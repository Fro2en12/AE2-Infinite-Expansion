package com.ae2addon.init;

import com.ae2addon.AE2Addon;
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

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.MODE_SELECT.get(), ModeSelectScreen::new);
        event.register(ModMenuTypes.MODE2_CONFIG.get(), Mode2ConfigScreen::new);
        event.register(ModMenuTypes.INTEGRATED_CPU.get(), IntegratedCPUScreen::new);
        event.register(ModMenuTypes.INFINITE_INTERFACE.get(), InfiniteInterfaceScreen::new);
    }
}
