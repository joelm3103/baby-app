# Release signing and delivery

Phase 16. This is the half of shipping that is not code: the key that makes an APK *BabyLog* on a phone,
and the channel that puts it there without a cable.

## What the key is for

Android installs a build as an **update** only when its signing certificate is byte-identical to the one
already on the device. That certificate is therefore the app's identity, in the only sense the phone
understands — not the package name, not the version, not the label. Everything follows from that:

- **Lose the keystore and the app cannot be updated.** Each installed copy has to be uninstalled first,
  which discards the local database. The log itself survives in Firestore (verified 2026-10-22) and comes
  back on the next sign-in, but the queue does not.
- **Sign with a different key and the install is refused**, with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
  That is the expected result of moving a debug-signed build onto this key, not a fault.
- **The debug key is not a candidate.** `~/.android/debug.keystore` has no password, is per-machine, and is
  regenerated if the folder is wiped — so it can never be the thing two phones update against.

## Where it lives

| File | Path | In git? |
|---|---|---|
| Keystore | `C:\Users\<you>\keystores\babylog-release.jks` | never (`*.jks` is ignored) |
| Gradle's copy of the metadata | `C:\Users\<you>\keystores\keystore.properties` | never (`keystore.properties` is ignored) |
| Fingerprints | `C:\Users\<you>\keystores\babylog-release.fingerprints.txt` | no — public data, kept for reference |

Deliberately **outside the repository and outside `Documents\Syncthing`**. The password lives in the
owner's password manager; `keystore.properties` is a convenience copy that Gradle reads, not the record.

`app/build.gradle.kts` looks for `keystore.properties` at the repo root first, then in
`%USERPROFILE%\keystores`, and creates the `release` signing config **only if one is found**. That is why a
fresh clone still runs `:app:testDebugUnitTest` and `:app:assembleDebug`; `:app:assembleRelease` then emits
`app-release-unsigned.apk` instead of failing, and `apksigner verify` answers `DOES NOT VERIFY` — the
honest outcome for a machine that does not hold the family's key.

## The certificate

| Field | Value |
|---|---|
| Alias | `babylog` |
| Owner | `CN=BabyLog, OU=Family, O=BabyLog, C=TT` |
| Algorithm | RSA 4096, PKCS12 |
| Created | 2026-10-29 |
| Valid until | 2054-03-16 |
| SHA-1 | `07:66:EE:2F:85:C2:ED:2C:8E:2F:C5:B6:B0:5B:90:8D:D4:54:5F:4F` |
| SHA-256 | `50:D9:C8:DE:D8:F7:B5:5E:54:C8:F0:49:BC:9C:0A:3C:57:A0:94:7C:72:5A:8D:1E:5F:80:C7:81:93:32:9C:04` |

The SHA-1 is registered on the **Android app entry in the Firebase console** (Project settings → Your apps
→ `app.babylog` → Add fingerprint). Without it Google Sign-In refuses every release build; email/password
does not care, which is why it stays the primary route. The debug SHA-1 (`24:E3:…`) stays registered
alongside it, because development still happens on debug builds.

## Building and verifying

```bash
export JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
./gradlew :app:testDebugUnitTest :app:assembleRelease
"C:/Users/<you>/AppData/Local/Android/Sdk/build-tools/37.0.0/apksigner.bat" \
    verify --print-certs app/build/outputs/apk/release/app-release.apk
"C:/Users/<you>/AppData/Local/Android/Sdk/build-tools/37.0.0/aapt2.exe" \
    dump badging app/build/outputs/apk/release/app-release.apk
```

`apksigner` must print `certificate DN: CN=BabyLog, OU=Family, O=BabyLog, C=TT` and a SHA-1 digest equal to
the table above; any other digest means the APK signed with a different key, so stop and find out what.
`aapt2` names the version that will land — `versionCode='26' versionName='1.0.0'`.

To recreate the key on a new machine you would run `scripts/create-release-keystore.ps1`, but you cannot:
it refuses to overwrite an existing key, and a new key is a new app identity. Restore the `.jks` and its
password instead.

## Delivering a build

Delivery is a **manual** upload to Firebase App Distribution — no Gradle plugin, no service-account key, no
`firebase-tools`. The app ships once every few weeks, so a repeatable command was not worth a second
long-lived secret to guard.

1. Build and verify as above.
2. Firebase console → **App Distribution** → the `app.babylog` Android app → **Testers & Groups**: both
   accounts are invited once and stay invited.
3. **Distribute** → upload `app/build/outputs/apk/release/app-release.apk`, add release notes, distribute.
4. Each phone opens the App Distribution email or notification and installs over the air. `dumpsys package
   app.babylog` then names what landed.

App Distribution is also what makes `versionCode` mean something beyond history: it compares the number to
decide whether a build is newer, which is why it only ever rises.

**Moving a phone onto the release key**, which happens exactly once per phone: drain the queue (`Sync now`
until 0), **Export** a backup off the device, let the old build be uninstalled (the signature changed, so
the installer refuses to update it in place), install from App Distribution, sign in, and let the log
re-pull. Import the export only if the count comes back short.

## Cutting a tag

`v1.0.0` is cut **on the delivered build**, after a phone reports `versionCode 26` — never before. Tags are
for releases only; a phase ending is not a release, which is why every rung from `0.1.0` to `0.19.0` is
untagged.

```bash
git tag -a v1.0.0 -m "1.0.0 — first release, delivered through App Distribution"
git push origin v1.0.0      # with the merge, and only on approval
```
