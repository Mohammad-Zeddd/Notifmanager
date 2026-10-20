# Notifmanager

An Android proof of concept for viewing selected notification previews in a local inbox.

## What it does

- Shows whether notification access is enabled for the app.
- Opens Android's system settings screen so the user can grant or revoke notification access.
- Receives notification posted and removed events through `NotificationListenerService`.
- Saves eligible notification previews to a local Room database and displays them in an inbox.
- Lets the user delete all stored history from the app.
- Writes diagnostic metadata to Logcat while testing; it does not log notification titles or bodies.

## Privacy and data handling

Notification access is a sensitive Android permission. Only enable it on a device you control and only for testing purposes.

For each eligible notification, the app stores the following **on the device only**:

- Source app package name and app label
- Posting time and, when available, removal time
- Up to 100 characters of the notification title
- Up to 200 characters of the notification text

The app does not send notification data over the network. It ignores its own notifications, ongoing notifications, and group-summary notifications.

The app attempts to mask 4–8 digit sequences when the notification contains wording such as “OTP”, “verification”, “passcode”, “password”, “code”, or “PIN”. This is a best-effort safeguard, not a guarantee that every sensitive notification will be redacted. Do not use this prototype for sensitive accounts or confidential messages.

The inbox displays the 200 most recent records, but stored history is retained until the user selects **Clear history**. Clearing history deletes all stored notification records from the app database.

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
5. Tap **Notification access** and enable notification access for Notifmanager.
6. Send or receive a test notification. Eligible entries appear in the inbox; diagnostic events use the `NotifPoC` Logcat tag.

## Project status

This is an early proof of concept. It includes local notification-preview storage and is still under active development.

## License

No license has been chosen yet. Until a license is added, the code is not automatically licensed for reuse by others.
