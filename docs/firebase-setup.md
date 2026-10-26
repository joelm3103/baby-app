# Firebase setup — the Phase 13 half that is not code

Phase 13's code landed with 398 JVM tests and its Firebase setup and two-device acceptance pass were
completed on 2026-10-22 (phone plus second device; see `PLAN.md` → Phase 13). This runbook retains the
setup steps and the pre-setup snapshot below for rebuilding or troubleshooting that configuration.
Phase 14's later sync fixes were checked on the phone, not across both devices.

It changes nothing in the app; it tells you exactly what to click and what each misconfiguration looks
like on screen. The Phase 13 acceptance criteria — log on A, see it on B; three airplane-mode writes on A
arrive exactly once after restore — were verified in that pass.

---

## What was known before setup (historical snapshot)

| Value | Where it goes | Status |
|---|---|---|
| projectId `baby-log-app-119e4` | `firebase.projectId` | known |
| appId `1:705332594857:android:71a9c8f699a0effd7041b8` | `firebase.appId` | known |
| apiKey `AIza…` | `firebase.apiKey` | **you must read it out** — see step 1 |
| webClientId `…apps.googleusercontent.com` | `firebase.webClientId` | **you must read it out** — step 1; only for the Google button |
| uid A, uid B | `firebase.allowedUids` **and** `firebase/firestore.rules` | created in step 4 |

The debug keystore on this machine has SHA-1:

```
24:E3:48:29:BF:8F:A3:DD:44:3F:0F:3B:BB:EA:D4:D4:5F:62:63:21
```

`~/.android/debug.keystore` is **per machine**. A debug APK built on a different computer has a different
SHA-1 and Google Sign-In will refuse it until that certificate is registered too. Email/password does not
care, which is why it is the primary route. The release certificate does not exist until Phase 16.

**A different debug key also means a different app signature, and that blocks the install, not just
Google.** `adb install -r` answers `INSTALL_FAILED_UPDATE_INCOMPATIBLE` when the existing copy was built
on another computer, so the app has to be uninstalled first — which discards the local database and the
outbox. The log itself is safe on the server and comes back on the next sign-in (verified 2026-10-22), but
anything still queued is lost, so drain before moving a phone to a build from a different machine. The
second debug print seen at `C:\HermesHome` on 2026-10-22 is
`F8:27:D1:F3:38:B8:B9:6D:5F:C9:81:54:1D:04:22:0D:81:C6:3C:CE`, and the 2026-10-22 round trip ran on that
one with email/password only.

---

## Step 1 — read the four values out of `google-services.json`

This is the short route, and it is the authoritative one: the console's snippets and the file it hands you
are the same values.

1. Firebase console → the project → **Project settings** (gear) → **General**.
2. Scroll to **Your apps** → the Android app → **`google-services.json`** → download it.
3. Read four fields:

   | Field in the JSON | Fills |
   |---|---|
   | `project_info.project_id` | `firebase.projectId` |
   | `client[0].client_info.mobilesdk_app_id` | `firebase.appId` |
   | `client[0].api_key[0].current_key` | `firebase.apiKey` |
   | `client[0].oauth_client[]` where `client_type` is `3` → `client_id` | `firebase.webClientId` |

   `client_type` `1` is the Android client and is **not** the one the app wants — `3` is the web client
   that Google Sign-In uses as its audience.

Do **not** move this file into `app/`. The build does not read it (`app/build.gradle.kts` deliberately
avoids the `google-services` plugin, because that plugin fails the build when the file is absent and the
file is gitignored). Leave it in Downloads, or anywhere outside the repo.

If the console has no Android app yet, add one first: **Add app** → Android, package name `app.babylog`,
then register the debug SHA-1 from above (**Project settings → Your apps → Add fingerprint**). Without the
app entry there is no `mobilesdk_app_id` and no `oauth_client` entry to read.

---

## Step 2 — turn on the sign-in providers

**Authentication → Get started → Sign-in method.**

- **Email/Password — enable.** This is the primary route and the one that works regardless of what key
  the APK is signed with.
- **Google — enable**, and set the support email. Only needed for the second button; if you skip it,
  leave `firebase.webClientId` blank and the app hides the Google button rather than offering one that
  cannot work (`FirebaseSetup.supportsGoogle`).

## Step 3 — create the two accounts

**Authentication → Users → Add user.** Email and password, two users, one per parent.

**The app has no sign-up.** It signs in to accounts that already exist — deliberately, so a stranger with
the APK cannot mint themselves an account. If you get "account does not exist" on the phone, the account
was never created here.

Copy each user's **uid** (the long string in the UID column). You need both in steps 4 and 5.

## Step 4 — create the Firestore database and paste the rules

1. **Firestore Database → Create database** → production mode → the region closest to you. The region
   cannot be changed later without recreating the database; for two phones in one country it does not
   matter much, but pick it once and keep it.
2. Open `firebase/firestore.rules` in this repo and replace:

   ```
   'REPLACE_WITH_FIRST_UID',
   'REPLACE_WITH_SECOND_UID'
   ```

   with the two uids from step 3, keeping the quotes and the comma.
3. Console → **Firestore Database → Rules** → paste the whole file → **Publish**.

The rules are pasted by hand on purpose — there is no deploy step in this repo, and none is wanted for a
two-person project. `children` and `events` are the only collections; anything else fails closed.

Also record the two uids in `PLAN.md` under Phase 13 once the accounts exist, so the files stay traceable
to the project.

## Step 5 — write `firebase.properties`

Next to `settings.gradle.kts`, beside the repo root (it is gitignored, and must stay that way — it carries
the project's key):

```properties
firebase.projectId=baby-log-app-119e4
firebase.appId=1:705332594857:android:71a9c8f699a0effd7041b8
firebase.apiKey=<current_key from step 1>
firebase.webClientId=<client_type 3 client id from step 1>
firebase.allowedUids=<uidA>,<uidB>
```

`firebase.properties.example` in the repo root is the same template with the comments.

**These values are compiled in.** Gradle reads the file into `BuildConfig`, so editing it and reinstalling
the APK is not enough — the app must be **rebuilt**. A stale build keeps the old values and looks exactly
like a console problem.

## Step 6 — build and install on both phones

```bash
export JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
./gradlew :app:testDebugUnitTest :app:assembleDebug
ADB="C:/Users/<you>/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
```

The app id is `app.babylog`. A build from before the id change installs **beside** this one and keeps its
own database — move the log with Settings → Data → Export, then Import, and never run both against the
same log.

Both phones must be on the same build, or one of them is talking to a different schema of the same
project.

---

## Step 7 — check it, in the order that isolates a failure

**On one phone, before involving the second:**

1. Settings → **Sync** must NOT say "Sync is not set up on this build". If it does, `firebase.properties`
   was missing or empty **at build time**.
2. Sign in with one account. The card then reads "Signed in as …" and "N waiting to sync", or the
   message line explains the refusal.
3. Log one bottle. Then press **Sync now**, or just wait for the post-write drain.
4. In the console, **Firestore Database → Data** must show `events/<id>` with the bottle's fields, and
   the card's "waiting to sync" count must be **0**.

If the document is there and the count is 0, the push half is proven. If the count stays above 0, the
intent remains queued. Read the message line for the failure, check Firestore rules, then press **Sync now**
after fixing the cause (or relaunch to trigger another drain); there is no periodic retry timer.

**Airplane mode, still on one phone:**

5. Turn airplane mode on, sign nothing out, log three bottles.
6. The count reads 3.
7. Airplane mode off. Press **Sync now** (or relaunch; the app drains on start).
8. Exactly three documents, no duplicates, count back to 0.

**Then the second phone — the acceptance criteria from `PLAN.md`:**

9. Sign in on phone B with the **other** account. The bottles logged on A appear.
10. Log on B; it appears on A, without pressing Sync now.
11. Edit the same entry on both phones while both are online — the later edit wins on both, and the two
    copies converge instead of ping-ponging.
12. Delete an entry on A (long-press → delete). It disappears on B **and stays deleted** after B relaunches
    and drains.
13. Delete a child permanently on A (Settings → Children). It must **not** come back on B — that is the
    Phase 7 defect fixed with DELETE intents, and it is the one most likely to regress.

---

## When it does not work

| What you see | What it means |
|---|---|
| Sync card: "Sync is not set up on this build…" | `firebase.properties` absent, or blank values, **at build time**. Rebuild. |
| "Signed in, but Firebase returned no account." | Auth succeeded oddly; usually a wrong `appId` for this project. |
| `CONFIGURATION_NOT_FOUND` on sign-in | The Email/Password provider is not enabled (step 2). |
| `PERMISSION_DENIED` on sync or an empty log on B | Rules not published, a uid typed wrong, or the database was never created (step 4). |
| No "Sign in with Google" button | `firebase.webClientId` is blank — that is the app choosing not to lie, not a bug. |
| Google button signs in then refuses, on a debug build | That machine's debug SHA-1 is not registered on the Android app. |
| `api-key-not-valid` from the console's perspective | An API key restriction that excludes `app.babylog` or the SHA-1. |
| Count stuck above 0, failure message shown | The intent stays queued, not automatically retried on a timer. Read the message, fix the cause, then press **Sync now** or relaunch. |
| Writes appear on B, then revert | Clock skew between the phones: the merge is `updatedAt`-wins, so a phone whose clock is ahead wins every tie. |

## Growth dataset updates (Phase 13.5)

The app can fetch a newer WHO dataset without an APK release. Publish two documents in the
`reference` collection:

- `reference/growth_manifest` with `datasetVersion`, `sha256` and `retrievedAt`.
- `reference/growth_dataset` with a `data` field containing the full GrowthReference JSON.

Clients read both documents and never write them. The rules above allow that for the two accounts.
If either document is missing or fails its checksum, the app keeps the bundled dataset.

**The `reference` rule has to be deployed, not just committed.** The match block arrived in `ba4bbb4`, but
rules reach the console by hand, so the first device pass in Phase 13.5 got `PERMISSION_DENIED` on
`reference/growth_manifest` while `events` synced normally — and the app reports that as `Could not check`,
which reads like a bug in the fetch. If a check fails, paste the file into Firestore → Rules → Publish and
look for that line in `adb logcat | grep Firestore` before touching any code. Publishing the documents is
enough to prove the promise end to end: a copy of the bundled JSON with only `version` and `retrievedAt`
bumped and the SHA-256 recomputed moves the identity line without changing a single percentile.

## What is still Phase 16

The release keystore, its SHA-1 on the Google OAuth client, and over-the-air delivery. Nothing above
depends on them: email/password works on any signing key, which is exactly why it is the primary route.
