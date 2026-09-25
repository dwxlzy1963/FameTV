package com.fametv.app.util;

import android.content.Context;
import android.util.TypedValue;

/** Common UI helpers. */
public final class UiUtil {

    private UiUtil() {}

    public static int dpToPx(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics());
    }
}
