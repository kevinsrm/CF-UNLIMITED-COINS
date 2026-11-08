# Keep Android components referenced by the manifest when minification is enabled.
-keep class com.cf.kiosk.** extends android.app.Activity { *; }
-keep class com.cf.kiosk.** extends android.app.Service { *; }
-keep class com.cf.kiosk.** extends android.content.BroadcastReceiver { *; }
