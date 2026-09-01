package com.ae2addon.init;

import com.ae2addon.AE2Addon;
import com.ae2addon.gui.InfiniteInterfaceMenu;
import com.ae2addon.gui.IntegratedCPUMenu;
import com.ae2addon.gui.Mode2ConfigMenu;
import com.ae2addon.gui.ModeSelectMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 菜单类型注册
 */
public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AE2Addon.MODID);

    // 模式选择界面
    public static final DeferredHolder<MenuType<?>, MenuType<ModeSelectMenu>> MODE_SELECT =
            MENUS.register("mode_select",
                    () -> IMenuTypeExtension.create(ModeSelectMenu::fromNetwork));

    // 模式2配置界面
    public static final DeferredHolder<MenuType<?>, MenuType<Mode2ConfigMenu>> MODE2_CONFIG =
            MENUS.register("mode2_config",
                    () -> IMenuTypeExtension.create(Mode2ConfigMenu::fromNetwork));

    // 集成 CPU 状态界面
    public static final DeferredHolder<MenuType<?>, MenuType<IntegratedCPUMenu>> INTEGRATED_CPU =
            MENUS.register("integrated_cpu",
                    () -> IMenuTypeExtension.create(IntegratedCPUMenu::fromNetwork));

    // ME 接口（无限级）配置界面
    public static final DeferredHolder<MenuType<?>, MenuType<InfiniteInterfaceMenu>> INFINITE_INTERFACE =
            MENUS.register("infinite_interface",
                    () -> IMenuTypeExtension.create(InfiniteInterfaceMenu::fromNetwork));
}
