# Privacy

## Data handled by the application

KrKr2 opens game files chosen by the user and stores preferences, caches, and
game-generated data on the device. Games executed by the engine may contain
their own networking or data-handling logic; review and trust game content
before running it.

The optional translation feature processes captured game frames/text on the
device with Google ML Kit. Translation models are downloaded from Google when
first needed, which exposes ordinary network metadata (such as IP address,
requested model, and user agent) to Google under its applicable terms. Captured
frames and recognized game text are not intentionally uploaded by this fork.

The engine contains generic HTTP/download APIs and therefore requests Android
Internet permission. Network access initiated by a game or by a user-selected
URL is outside automatic crash reporting.

## Crash information

Platform crash handling may write memory dumps locally. Memory dumps can
contain game content, paths, text, credentials, or other sensitive process
memory. This fork never automatically uploads crash dumps and has removed the
former Android/Windows plaintext uploader to `avgfun.net`. Do not publish a
dump without inspecting and redacting it. Local dumps can be deleted at any
time.

## Telemetry and support

This fork includes no first-party analytics or telemetry service. GitHub issue
reports are voluntary and public; remove personal information and attach logs
only when safe. Privacy questions belong in the fork's issue tracker, not an
upstream project.

