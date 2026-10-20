# Notifmanager

An Android proof of concept for checking notification-listener access and observing notification lifecycle events.

## What it does

- Shows whether notification access is enabled for the app.
- Opens Android's system settings screen so the user can grant or revoke notification access.
- Receives notification posted and removed events through `NotificationListenerService`.
- Writes safe diagnostic metadata to Logcat while testing.

## Privacy

This prototype does **not** store, transmit, or log notification titles or message text.

The diagnostic logs contain only:

- The originating app package name
- Notification category and state
- Whether title and text fields exist, represented by their character lengths
- Notification posting time

Notification access is a sensitive Android permission. Only enable it on a device you control and only for testing purposes.

## Requirements

- Android Studio
- Android SDK 36 (compile and target SDK)
- Android 8.0 (API 26) or newer device/emulator

## Run the app

1. Clone the repository:

   ```bash
   git clone https://github.com/Mohammad-Zeddd/Notifmanager.git
   ```

2. Open the cloned folder in Android Studio.
3. Allow Android Studio to finish the Gradle sync.
4. Run the app on a device or emulator.
5. Tap **Open notification access settings** and enable notification access for Notifmanager.
6. Send or receive a test notification, then inspect Logcat using the `NotifPoC` tag.

## Project status

This is an early proof of concept. It focuses on confirming that the notification listener connects and receives events safely before any further features are added.

## License

No license has been chosen yet. Until a license is added, the code is not automatically licensed for reuse by others.
