# Step 10d: the launcher uses no libraries and no reflection on its own classes.
# Everything the manifest names is kept automatically; these lines only make that explicit and safe.
-keep class * extends android.appwidget.AppWidgetProvider
-keep class * extends android.app.admin.DeviceAdminReceiver
-keep class * extends android.service.notification.NotificationListenerService

# The only reflection is on a framework class (StatusBarManager) - nothing of ours to keep for it.
-dontwarn android.app.StatusBarManager

# Readable crash traces (file + line) while class names stay shortened.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
