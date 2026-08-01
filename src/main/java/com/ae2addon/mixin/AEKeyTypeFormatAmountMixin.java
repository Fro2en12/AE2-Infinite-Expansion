package com.ae2addon.mixin;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AmountFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2 数量显示格式化兼容（客户端）。
 * <p>
 * ME 终端/槽位/tooltip 的数量显示最终走 {@link AEKeyType#formatAmount(long, AmountFormat)}
 * （AEKey.formatAmount 内部转调此处）。把无限元件报告的超大数量（≥ 1E）以及负数
 * （多存储累加溢出）显示为 ∞ 符号，与 ReadableNumberConverterMixin 相互补充。
 */
@Mixin(value = AEKeyType.class, remap = false)
public abstract class AEKeyTypeFormatAmountMixin {

    /** 视为"无限"的数量阈值：1e18（1E） */
    private static final long INFINITE_DISPLAY_THRESHOLD = 1_000_000_000_000_000_000L;

    @Inject(method = "formatAmount(JLappeng/api/stacks/AmountFormat;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true)
    private void ae2addon$infiniteDisplay(long amount, AmountFormat format, CallbackInfoReturnable<String> cir) {
        if (amount < 0 || amount >= INFINITE_DISPLAY_THRESHOLD) {
            cir.setReturnValue("∞");
        }
    }
}
