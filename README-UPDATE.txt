MSA G1 Scanner v1.1 update

Replace the matching files in your GitHub repository with the files in this ZIP.
The GitHub Actions workflow will automatically rebuild the APK after the commit.

Changes:
- Adds Small / Medium / Large facepiece decoding
- Keeps PASS and Pack Frame mappings
- Retries failed NFC-V block reads up to 3 times
- Reads identification-critical blocks first
- Clearly marks partial reads and missing blocks
- Renames the app to MSA G1 Scanner
- Renames the APK to MSA-G1-Scanner-v1.1.apk
- Bumps app version to 1.1
