# Marcus V100 — Unified Release Scope

This document is the acceptance checklist for the single integrated Marcus Android release. A successful Gradle build is not sufficient to mark a feature complete; each item needs implementation evidence and, where device-dependent, on-device verification.

## Core application
- [ ] One cohesive Compose interface with responsive layout, clear navigation, accessible controls, and consistent robotic identity.
- [ ] Chat connected to the configured Marcus backend, with visible loading, timeout, retry, and offline/error states.
- [ ] Local conversation history controls and clear-chat confirmation.
- [ ] Local memory notes with edit/delete/clear controls and clear disclosure that local notes are not cloud memory.

## Voice
- [ ] In-app speech-to-text with lifecycle-safe recognizer creation, one active session at a time, permission checks, and recoverable errors.
- [ ] Text-to-speech with stop control and saved voice settings.
- [ ] Optional wake phrase mode using a foreground microphone service and persistent Android notification.
- [ ] Explicit start/stop controls; no hidden or silent microphone activation.
- [ ] Verify on Redmi A3 / Android 16 before claiming stability.

## Phone assistance
- [ ] App launcher using Android's supported package visibility.
- [ ] Accessibility service for supported navigation, reading visible UI, and text entry only after the user manually enables it in Android Settings.
- [ ] Floating assistant shortcut with overlay permission; disclose that it opens Marcus and is not a full independent overlay unless that is separately implemented.
- [ ] Confirmation before consequential actions; never bypass Android security, Play Protect, permission dialogs, or lock screens.

## Gaming co-pilot
- [ ] Game launcher and session timer.
- [ ] User-entered notes, calculations, and strategy assistance.
- [ ] Optional supported accessibility interactions only where Android and the game permit them.
- [ ] Do not claim autonomous human-like gameplay, anti-cheat evasion, or guaranteed control of game characters.

## Build and verification
- [ ] CI compilation succeeds.
- [ ] APK artifact is produced and downloadable.
- [ ] Install/launch test on target Android device.
- [ ] Smoke-test chat, memory persistence, TTS, microphone, wake service, floating shortcut, accessibility settings handoff, and game launcher.
- [ ] Record known limitations and test results in the release notes.

## Release rule
Do not label the APK "complete" until all applicable items above are implemented and the target-device tests have been performed. Features that depend on user-granted Android permissions must be described as permission-gated.
