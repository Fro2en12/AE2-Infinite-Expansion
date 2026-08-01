package com.ae2addon.mixin;

import appeng.util.ReadableNumberConverter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2 数量格式化兼容（客户端）。
 * <p>
 * 1. 无限元件向网络报告的超大数量（≥ 1E，即 1e18）显示为 ∞ 符号（无限观感）；
 * 2. 负数不再抛 IllegalArgumentException：多个无限存储的同类型数量在 AE2 网络汇总时
 *    可能累加 Long.MAX_VALUE 溢出为负数，原逻辑会抛异常，导致 AE2WTLib 补货悬浮层
 *    （restockOverlay）渲染崩溃。这里统一返回 ∞ 兜底。
 */
@Mixin(value = ReadableNumberConverter.class, remap = false)
public abstract class ReadableNumberConverterMixin {

    /** 视为"无限"的数量阈值：1e18（1E） */
    private static final long INFINITE_DISPLAY_THRESHOLD = 1_000_000_000_000_000_000L;

    @Inject(method = "format(JI)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void ae2addon$infiniteDisplay(long value, int width, CallbackInfoReturnable<String> cir) {
        if (value < 0 || value >= INFINITE_DISPLAY_THRESHOLD) {
            cir.setReturnValue("∞");
        }
    }
}
