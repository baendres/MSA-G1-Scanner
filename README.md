# MSA G1 Tag Reader

A small Android app for directly reading the ISO 15693 / NFC-V RFID tags embedded in MSA G1 components.

## What v1 does

- Uses the Android phone's built-in NFC hardware; no external reader is required.
- Reads NXP ICODE SLIX / ISO 15693 tags through `NfcV`.
- Shows the MSA RFID Serial Number (the ISO-15693 UID, normalized to the same format seen on the MSA labels).
- Reads blocks `00` through `1B` and decodes the component fields mapped from the samples collected so far.
- Recognizes the observed:
  - G1 Facepiece — component code `51019546`, type code `00000340`
  - G1 PASS Device — component code `52017200`, type code `00000540`
  - G1 Pack Frame — component code `42018936`, type code `00000640`
- Provides an optional raw-memory view for gathering more samples.

## Important decoder status

The component names above are based on repeated observed samples. The RFID Serial Number/UID relationship is confirmed from an MSA facepiece label. Production number, manufacture date, and other factory fields are not yet reliably decoded, so the app intentionally does not invent labels for them.

## Build

This is a standard Android Gradle project. It targets Android 15 API 35 and requires Android 4.4/API 19 or newer.

Build command:

```bash
gradle assembleDebug
```

The debug APK will be written to:

`app/build/outputs/apk/debug/app-debug.apk`

## Testing

Open the app, enable NFC, and hold the upper/back area of the phone against the MSA RFID location. The app uses Android Reader Mode, so it only captures NFC-V tags while the app is open.
