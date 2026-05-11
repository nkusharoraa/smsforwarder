# Keep BroadcastReceiver/Service entry points referenced from AndroidManifest.
-keep class com.nkusharora.smsforwarder.receiver.** { *; }
-keep class com.nkusharora.smsforwarder.service.** { *; }
-keep class com.nkusharora.smsforwarder.ui.** { *; }
