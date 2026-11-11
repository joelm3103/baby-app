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
    verify --print-certs app/build/outputs/apk/release/BabyLog-1.7.0.apk
"C:/Users/<you>/AppData/Local/Android/Sdk/build-tools/37.0.0/aapt2.exe" \
    dump badging app/build/outputs/apk/release/BabyLog-1.7.0.apk
```

The artifact is named `BabyLog-<versionName>.apk` (`%appname%-%appversion%`, owner's request 2026-11-05), so
the file says what it is and two builds cannot overwrite each other. `app/build.gradle.kts` derives the name
from the same `appVersionName` the About screen reads; the build type decides the directory, not the filename.
Dated `CHANGELOG.md` and `PLAN.md` records still say `app-release.apk`, because they record what happened.

`apksigner` must print `certificate DN: CN=BabyLog, OU=Family, O=BabyLog, C=TT` and a SHA-1 digest equal to
the table above; any other digest means the APK signed with a different key, so stop and find out what.
`aapt2` names the version that will land — `versionCode='26' versionName='1.0.0'`.

To recreate the key on a new machine you would run `scripts/create-release-keystore.ps1`, but you cannot:
it refuses to overwrite an existing key, and a new key is a new app identity. Restore the `.jks` and its
password instead.

## Delivering a build

**The GitHub release IS the delivery** (ADR-0001's 2026-11-04 amendment, which retired the Firebase App
Distribution upload). A tag carrying the signed APK is what a phone installs from, so there is no console
trip, no CLI plugin and no second long-lived secret to guard for a build that ships once every few weeks.
Nothing here is a rebuild: the APK attached to the release is the file `apksigner` verified, byte for byte,
because a second build signed by the same key is still a different file and its digest would not match the
one recorded below.

1. Build and verify as above (`:app:testDebugUnitTest :app:assembleRelease`, then `apksigner verify`).
2. **Install it on the phone first** (`adb install -r`) and let the app report its version — a tag is cut on
   a delivered build, never on a green compile.
3. Cut the tag and create the release with the artifact attached:

   ```bash
   sha256sum app/build/outputs/apk/release/BabyLog-1.8.1.apk
   git tag -a v1.8.1 -m "1.8.1 — <one line on what shipped>"
   git push origin v1.8.1
   gh release create v1.8.1 app/build/outputs/apk/release/BabyLog-1.8.1.apk \
       --title 1.8.1 --notes-file /tmp/notes.md
   ```

   The notes say what changed and **quote the digest**, so a phone's copy can be compared against the page
   before installing:

   ```bash
   gh release view v1.8.1 --json assets --jq '.assets[] | "\(.name) \(.size) \(.digest)"'
   ```
4. Each phone updates in place from the release page (`adb install -r`, or the download plus the system
   installer). Uninstalling first is what discards the log — never do it to make a build fit.

### Released artifacts

Recorded so a phone's copy can be checked against the page. Every file is signed by the certificate above.

| Tag | Asset | Bytes | sha256 |
|---|---|---|---|
| `v1.0.0` | `baby-log-v1.0.0.apk` | 14 675 571 | `4dac7cfb823a1c61d532a21a9226783aa1c0941ac5576d319de26c4f23b43bca` |
| `v1.7.1` | `BabyLog-1.7.1.apk` | 14 781 151 | `65fbd2f3257b036afe74bbfbca1450c98cc50f3aa5501f3bb3410c862f21db2a` |
| `v1.7.2` | `BabyLog-1.7.2.apk` | 14 781 151 | `d41b0c2d8936022904233803d68140b33c6460bdbd084c5cd0c0dc56db6cffda` |
| `v1.8.0` | — never released; superseded the same day it was built | 14 781 151 | `7dca5480bbab07bb3af4c9efbfa81f93279ef45276d6dbc7571335ecb0f72be0` |
| `v1.8.1` | `BabyLog-1.8.1.apk` | 14 781 151 | `95496bbb98417bbe41fd2e8bdfae4712fd6c19eef4e6fe151a863ccae11004c1` |

### App Distribution is retired

Uploading to Firebase App Distribution is no longer the channel, so "a build that was not uploaded through
the console has not been delivered" is retired with it: a release page with the artifact and its digest is
the delivery. `versionCode` still only ever rises, and nothing automates the comparison any more — which is
exactly why the number must not be handled loosely.

The app's own update check (ADR-0003) reads the newest release from GitHub once a day and can offer its
page; nothing is downloaded or installed by the app. The repository is **public**, verified 2026-11-10 by an
unauthenticated `curl` (`releases/latest` answers `200` with `tag_name v1.7.2`), so the check can see a
release — which is the premise ADR-0003 has always assumed and the one thing a release must not break by
turning the repository private again.

## Cutting a tag

A tag is cut **on a delivered build**, after a phone reports its version — never when a phase ends. That is
why every rung from `0.1.0` to `1.7.0` is untagged while `v1.0.0`, `v1.7.1`, `v1.7.2` and `v1.8.1` exist:
the difference is a release page holding a file a phone has run, not the size of the work. A build that was
delivered but never tagged leaves a gap in the ladder above, and a build tagged before it reached a phone
puts a version on the page that nobody has executed.

