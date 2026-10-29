package com.oklabs.oneui9launcher

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Lets the launcher lock the screen (double-tap on an empty spot of the Home screen).
 * Android only allows that to a "device admin" with the single policy "lock the screen";
 * it can not read or erase anything. Switch it off any time in Settings > Security > Device admin apps.
 */
class LockAdmin : DeviceAdminReceiver() {

    companion object {
        private fun component(context: Context) = ComponentName(context, LockAdmin::class.java)

        fun isActive(context: Context): Boolean {
            val policy = context.getSystemService(DevicePolicyManager::class.java) ?: return false
            return policy.isAdminActive(component(context))
        }

        /** The system screen that asks the person to allow it. Start it for a result. */
        fun requestIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Lets " + context.getString(R.string.app_name) + " lock the screen when you double-tap an empty spot on the Home screen. It can do nothing else."
                )

        /** Locks the screen now. False when it is not allowed (admin switched off) or the phone refuses. */
        fun lock(context: Context): Boolean {
            val policy = context.getSystemService(DevicePolicyManager::class.java) ?: return false
            if (!policy.isAdminActive(component(context))) return false
            return try {
                policy.lockNow()
                true
            } catch (e: SecurityException) {
                false
            }
        }
    }
}
