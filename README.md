# SMS Forwarder

An Android app that forwards **every** incoming SMS to another phone number — including the bank, OTP, and short-code messages that other forwarders silently drop.

## Why other forwarders miss messages

Most "SMS forwarder" apps on Android listen to `SMS_RECEIVED`. This broadcast is an **ordered** broadcast — any higher-priority app (the system messaging app, an OEM "smart message" filter, an anti-spam app, or a banking app) can call `abortBroadcast()` and the forwarder never sees the SMS. This is the root cause behind "my bank SMS isn't forwarded but other SMS are."

This app fixes that by becoming the **default SMS app**. The system then delivers messages via `SMS_DELIVER`, which **cannot be aborted**. A `ContentObserver` on `content://sms` runs as a second layer so anything that arrives via OEM-specific paths is still picked up.

## Capture strategy (defence in depth)

| Path | When it fires | Notes |
|------|---------------|-------|
| `SMS_DELIVER_ACTION` | Only sent to the default SMS app | Cannot be aborted. Primary path. |
| `SMS_RECEIVED_ACTION` | Sent to all apps | Fallback when not default. Deduplicated. |
| `ContentObserver` on `content://sms` | Whenever any row is inserted | Catches anything inserted via OEM channels. |

A 2-minute dedup window keyed on `sender + body-hash + second` ensures the same SMS is never forwarded twice across paths.

## Other reliability fixes

- Multipart inbound reassembled with `Telephony.Sms.Intents.getMessagesFromIntent` (handles long bank notifications).
- Outbound uses `SmsManager.divideMessage` + `sendMultipartTextMessage` so long forwards don't get truncated.
- Foreground service with `START_STICKY` keeps the observer alive.
- Boot receiver re-arms after reboot, package update, and HTC/legacy `QUICKBOOT_POWERON`.
- Per-subscription send via `createForSubscriptionId(subId)` on dual-SIM devices.
- Battery optimization opt-out prompt (Doze/App Standby is a big reason FGS gets killed on Xiaomi/Vivo/Realme).
- Loop guard: skips messages whose body starts with `[FWD]` and skips messages whose sender's digits equal the destination.

## Setup

1. Build & install (Android Studio or `./gradlew assembleDebug`).
2. Open the app, then:
   - Tap **Grant runtime permissions** → accept SMS, phone state, notifications.
   - Tap **Set as default SMS app** → confirm.
   - Tap **Disable battery optimization** → Allow.
   - Enter the destination number (with country code, e.g. `+911234567890`) and tap **Save**.
3. Send yourself a test SMS from another phone. You should receive a `[FWD] From <sender>\n<body>` message on the destination number.

## Permissions

| Permission | Why |
|------------|-----|
| `RECEIVE_SMS`, `READ_SMS`, `SEND_SMS` | Receive and forward SMS. |
| `WRITE_SMS` | Default SMS app responsibility — write incoming SMS to the inbox. |
| `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` | Required to qualify as default SMS app (MMS body itself is not forwarded). |
| `READ_PHONE_STATE`, `READ_PHONE_NUMBERS` | Identify subscriptions on dual-SIM devices. |
| `FOREGROUND_SERVICE*` | Keep the `ContentObserver` alive. |
| `RECEIVE_BOOT_COMPLETED` | Restart the service after reboot. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent Doze from killing the service. |
| `POST_NOTIFICATIONS` | Persistent service notification (Android 13+). |

The app sends only to the destination number — it does not make any network calls.

## Limitations

- **MMS bodies are not forwarded.** The WAP-push receiver only exists so the app can hold the default SMS role.
- **Class 0 "flash" SMS** that the system never inserts into the content provider may not be forwarded — these are rare in practice (some carrier emergency notices).
- **OEM aggressive battery managers** (especially MIUI/HyperOS) may still kill background apps. After install, lock the app in recents and disable per-app battery optimizations in the OEM settings menu (not just the AOSP one) for guaranteed reliability.

## Project layout

```
app/src/main/java/com/nkusharora/smsforwarder/
├── App.kt                        # Application + notification channels
├── MainActivity.kt               # Settings UI, role/permission/battery flows
├── data/Prefs.kt                 # SharedPreferences wrapper
├── forward/SmsForwarder.kt       # Multipart send + loop guards
├── receiver/
│   ├── SmsDeliverReceiver.kt     # Default-SMS path (SMS_DELIVER)
│   ├── SmsReceiver.kt            # Fallback path (SMS_RECEIVED)
│   ├── MmsReceiver.kt            # WAP_PUSH_DELIVER stub (role requirement)
│   └── BootReceiver.kt           # Restart after reboot / update
├── service/
│   ├── ForwardingService.kt      # Foreground service host
│   └── SmsContentObserver.kt     # ContentObserver safety-net path
├── ui/
│   ├── ComposeSmsActivity.kt     # ACTION_SENDTO stub (role requirement)
│   └── HeadlessSmsSendService.kt # RESPOND_VIA_MESSAGE stub (role requirement)
└── util/
    ├── Dedup.kt                  # Cross-path dedup window
    └── Logr.kt                   # Tagged logger
```

## Building

The Gradle wrapper JAR is intentionally not committed. To build:

```bash
gradle wrapper --gradle-version 8.7   # generates ./gradlew
./gradlew assembleDebug
```

…or just open the project in Android Studio Iguana+.
