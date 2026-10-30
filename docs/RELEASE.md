# Nebula — Release Runbook

What the build already enforces, what it needs from you, and what to check before
an internal track upload.

## 1. What the build will not let you do

`app/build.gradle.kts` refuses to package a release build without all four signing
values. The task fails with an explicit message instead of producing an unsigned
artifact that looks shippable:

```
Release signing is not configured. Supply NEBULA_RELEASE_STORE_FILE,
NEBULA_RELEASE_STORE_PASSWORD, NEBULA_RELEASE_KEY_ALIAS, and
NEBULA_RELEASE_KEY_PASSWORD through Gradle properties or environment variables.
```

Values are read from Gradle properties or environment variables, in that order, and
never from a committed file. Passwords must not be written into `gradle.properties`
(they were once, and that is why they are on the rotation list).

## 2. Building the signed artifact

```bash
export NEBULA_RELEASE_STORE_FILE=/absolute/path/to/nebula-release-v2.jks
export NEBULA_RELEASE_STORE_PASSWORD='…'
export NEBULA_RELEASE_KEY_ALIAS='…'
export NEBULA_RELEASE_KEY_PASSWORD='…'

./gradlew :app:bundleRelease        # Play upload
./gradlew :app:assembleRelease      # local install of the exact shipped code
```

The keystore lives outside version control (`*.jks` is gitignored). Keep a copy in
the password manager, not in the repository.

## 3. What R8 must keep

The release build is minified and resource-shrunk. Two sets of rules matter:

| Area | Rule | Why it breaks without it |
|---|---|---|
| Media3 / ExoPlayer | `-keep class androidx.media3.** { *; }` | Renderers and HLS extractors are resolved reflectively. A release build plays YouTube (a WebView) but shows a black surface for every direct stream. |
| Domain/data models | existing `-keep` blocks | JSON parsing and cache restore rely on field names. |

Compose, Coroutines, Socket.IO, OkHttp, Firebase, GMS and Coil keep rules were
already present.

## 4. Before uploading

1. `cd server && npm run build && npm test` — all assertions pass.
2. `./gradlew :app:testDebugUnitTest` — JVM specs pass.
3. Install the **release** APK on a device and exercise the paths that R8 can break:
   Google sign-in, room join, YouTube playback, a direct `.mp4` link, an image
   upload, and a DM send.
4. Confirm the SHA-1 of the signing key is registered on the Firebase Android OAuth
   client, or Google sign-in fails with `This android application is not registered`.
5. `git status` shows no `.env`, `*.jks`, `google-services.json` or service-account
   file.

## 5. Versioning

`versionCode` and `versionName` live in `app/build.gradle.kts`. Increment
`versionCode` for every upload; Play rejects a reused value.

## 6. Internal track

Upload the `.aab` from `:app:bundleRelease` to Play Console → Testing → Internal
testing. The track is where the remaining device checks belong: two-account push
delivery, room queue under two real clients, and the Media3 surface on a real
network.

## 7. Still owned by you

| Item | Why it blocks |
|---|---|
| Keystore passwords | Only you hold them; the build will not sign without them. |
| Play Console account access | Not something a repository can grant. |
| Rotating the exposed credentials | See `docs/SECRETS_ROTATION.md`. A release signed with the current key is still a release built on compromised infrastructure. |
