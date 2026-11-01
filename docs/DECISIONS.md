# Decisions

Architecture Decision Records for BabyLog, oldest first. One record per decision that would be expensive
to reverse or non-obvious in hindsight; `PLAN.md` is the plan, `CHANGELOG.md` is when it happened, and
`AGENTS.md` is the working contract. A record is not rewritten when the world moves — a reversal gets a new
record that names what changed.

---

## ADR-0001 — The release key is the app's identity, and delivery is a manual console upload

**Date:** 2026-10-29 · **Phase:** 16 · **Status:** accepted

### Decision

Release builds are signed by a keystore the family owns and no tool can recreate:
`babylog-release.jks` (alias `babylog`, RSA 4096, PKCS12, valid to 2054) with its password in the owner's
password manager. The keystore and the `keystore.properties` Gradle reads live in
`C:\Users\<you>\keystores\` — outside the repository and outside the Syncthing share. Builds reach the
phones through a **manual** upload to Firebase App Distribution, not a Gradle plugin, a service account or
`firebase-tools`.

### Why

Android accepts a build as an update to an installed app only when the signing certificate matches the one
already there. So the key, not the package name or the version, is the app's identity on both phones: sign
with a different one and the phone refuses with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, which means uninstall,
which means the local database goes. The debug key cannot carry that weight — no password, per-machine,
regenerated when the folder is wiped — and it would have to be the same file on both phones forever.

Both secrets are kept out of the repo because the repository is the one artefact that gets cloned, shared
and read by agents; a keystore in git is a key that has been published. The password is typed at a prompt by
the owner (`scripts/create-release-keystore.ps1` hands it to `keytool` through `-storepass:env`) so it never
enters a command line, a shell history or a transcript.

Delivery is manual because the alternative buys automation this project does not need at the price of a
second long-lived secret: the App Distribution Gradle plugin needs a service-account JSON with project
access, and `firebase-tools` needs an interactive login and an npm global. Two releases a year do not
justify either. The cost is accepted deliberately — a release is three console clicks, and the console is
also where the tester list and the upload history live.

### Consequences

- **The keystore is irreplaceable.** Losing the `.jks` or its password ends over-the-air updates for the
  app already installed: every phone must uninstall first. Back it up outside the Syncthing folder; never
  regenerate it, and `create-release-keystore.ps1` refuses to overwrite one that exists.
- **The certificate never changes**, so its SHA-1 is registered once on the Firebase Android app entry and
  every later release keeps Google Sign-In working. Changing the key also means re-registering, on top of
  the uninstall.
- **`versionCode` becomes a contract**, because App Distribution decides "newer" from it. It only rises, and
  a landed rung is never renumbered.
- **A clone without the key still works.** The signing config is created only when `keystore.properties`
  exists, so tests and `assembleDebug` are unaffected and `assembleRelease` degrades to an unsigned APK
  rather than failing the build.
- **No CI can publish.** That is the point: nothing can deliver a build without the owner's machine and the
  owner's key.

### Amendment, 2026-10-29 — the release page also carries the APK

Recorded after the tag was published: **the GitHub release for `v1.0.0` holds the built APK as an asset**
(`baby-log-v1.0.0.apk`, 14 675 571 bytes, `sha256:4dac7cfb823a1c61d532a21a9226783aa1c0941ac5576d319de26c4f23b43bca`,
which is the digest the installing phone reported back). This does not replace the decision above.

- **App Distribution remains the delivery channel for a new build**, because that is where `versionCode` is
  compared, the tester list lives and the phone is told something newer exists. The release asset is a copy of
  an already-released build, not a release process: nothing about "ship 1.0.1" starts on GitHub.
- **It exists for the case the channel cannot serve** — a replacement phone, a tester who was never invited, or
  the console being unreachable — and for that it has to be the *same signed artifact* rather than a rebuild.
  A rebuild would be signed by the same key but would not be byte-identical, so its digest is what must be
  checked before installing: `gh release view v1.0.0 --json assets --jq '.assets[].digest'`.
- **Two hosts, one artifact.** The cost is that the same APK now exists in two places and either could be
  replaced by hand; the digest above is the thing that makes them comparable, so a future release should have
  its own recorded beside it.

---

## ADR-0002 — The food catalogue is published, and parent additions are pending

**Date:** 2026-10-31 · **Phase:** 18 · **Status:** accepted

### Decision

The bundled asset remains the floor. The owner publishes one `reference/food_catalogue` document; its
stable-id entries override the bundled names on both phones. A parent-typed food goes to
`foods_pending/{id}` through the existing outbox so the other phone can show it without an APK release.
Renaming a catalogue entry is local until the owner publishes it, and the phone writes a
`foods_proposals/{id}` suggestion. Hiding and `localName` remain local preferences.

### Why

The observed failure was a name that existed only on one device: a solids event syncs `foodIdsCsv` (ids,
not labels), so the other phone printed the id. Sending a name snapshot with the event would freeze the old
label into meals forever, even after the owner corrected a catalogue typo. Treating every food as an ordinary
synchronized row would require per-row version/deletion conflict rules and an exception for `lastUsedAt`,
which is only recent-order state on one phone. A published list has one owner and one fact about each name;
a pending list is only the bridge for additions that have not yet been promoted.

The first implementation proposal was precisely the rejected row-sync design, and the owner replaced it
before any code landed. This replacement is recorded because otherwise the split between `reference`,
`foods_pending` and `foods_proposals` looks like accidental duplication rather than the boundary the owner
chose.

### Consequences

- A custom food reaches the other phone as soon as its pending write drains; if the phone is offline, it
  stays in the outbox until it can travel.
- A pending addition remains pending until the owner copies its stable id, name and category into the
  catalogue. Promotion makes the catalogue authoritative and removes the pending document.
- A catalogue rename is not a shared rename until published. A local override changes this phone's picker
  and a proposal waits for the owner, so the two parents may see different names in the meantime.
- Hiding an entry is local. Removing a pending addition removes it from the shared pending list, but each
  device retains its local row hidden so an old meal still has its name.
- The catalogue is not checksummed or cached as a growth dataset is. It is a list of words, not clinical
  reference data; a mistaken entry is corrected in the console.
- The bundled asset and published document are both retained. The first supplies offline fallback and
  baseline coverage; the second lets the owner correct names without another signed release.
