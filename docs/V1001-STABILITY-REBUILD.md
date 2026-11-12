# Marcus V100.1 Stability Rebuild

This branch contains a stability and interface pass on the V100 codebase.

## Changes in this pass
- Connect the in-app receiver to the wake service's Hey Marcus and spoken-command broadcasts while the main activity is active.
- Simplify the home header and navigation for narrow Android screens.
- Group floating shortcut and wake-listener controls into a compact panel.
- Correct permission guidance for the overlay shortcut.
- Improve the floating shortcut's visual treatment.
- Add guarded wake-listener restart scheduling and cleanup on service shutdown.
- Increment Android version code to 101 and version name to 100.1.

## Limitations to verify
- A successful CI build is not a substitute for testing on the target phone.
- The wake listener uses Android SpeechRecognizer, not a dedicated low-power wake-word engine; availability, battery use and background behavior vary by device and Android version.
- The floating shortcut opens Marcus; it is not a full independent chat overlay.
- Accessibility actions are limited by Android permissions, app UI semantics and game policies. This app cannot promise human-like autonomous gameplay.
- App crash diagnosis on the physical device still requires the observed crash message or Android crash log.

## Follow-up integration
- The floating shortcut now requests the main activity to enter voice mode when opened; Android still prompts for microphone permission if it has not been granted.
- The in-app receiver processes wake and command broadcasts while the main activity is active.
