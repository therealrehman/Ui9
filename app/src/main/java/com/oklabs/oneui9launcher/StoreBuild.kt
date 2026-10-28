package com.oklabs.oneui9launcher

import android.content.Context

/**
 * Huawei plan, step 8: one switch for "this is the store build". In the release build type (app/src/release) the Accessibility service is
 * not even in the manifest and `store_no_accessibility` is true, so every Accessibility row, hint and policy line is hidden and the code
 * never looks for the service. The debug build keeps the optional Accessibility way.
 */
object StoreBuild {
    fun noAccessibility(context: Context): Boolean = context.resources.getBoolean(R.bool.store_no_accessibility)
}
