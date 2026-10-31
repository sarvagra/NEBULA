# Nebula landing page

Static marketing + download page for Nebula. Plain HTML/CSS, no build step, no
JavaScript framework, no connection to the Nebula API. Deploying it cannot affect
the `nebula-api` web service.

```
landing/
  index.html            the page
  styles.css            all styling
  assets/logo.svg       Nebula mark
  download/nebula-1.1.0.apk   the signed release APK
  render.yaml           Render static-site blueprint
```

## Where it is served

The page is served by the Nebula API service itself (`server/src/app.ts` mounts this
directory as static files), so it is live at **https://nebula-73kb.onrender.com** the
moment the backend deploys. `/api/v1/*`, `/health` and `/storage/*` are untouched, and
the APK download is served from the same origin, which is also what
`NEBULA_WEBSITE_URL` in the app's About screen points at.

A separate static site is optional and no longer required. The steps below still work
if you later want the marketing page on its own host or custom domain.

## Deploy as a standalone static site (optional)

1. Render dashboard → **New → Static Site** (not Blueprint, so the existing
   `nebula-api` service is untouched).
2. Connect this repository.
3. Set:
   - **Branch**: `main`
   - **Root Directory**: `landing`
   - **Build Command**: *(empty)*
   - **Publish Directory**: `.`
4. Create the site. The URL is `https://<site-name>.onrender.com`.
5. Set the site name to `nebula-landing` and the app's About screen link matches
   (`NEBULA_WEBSITE_URL` in `NebulaAboutScreen.kt`). If you pick a different
   name, update that constant, rebuild the APK and re-host it.

`render.yaml` in this folder is the same configuration for anyone who prefers
Blueprint deploys; applying it creates only this static site.

## The APK

The signed release APK lives at `download/nebula-1.1.0.apk` and is served by the
same static site, so the download button works the moment the page is live.

A copy is also uploaded to the media bucket:

```
s3://vpc-d026d246-user-data/nebula/apk/nebula-1.1.0.apk
```

That bucket blocks anonymous reads (its policy denies public GET, and per-object
`public-read` ACLs are overridden), so a bare S3 URL returns 403. Only
time-limited presigned URLs work, which is why the page does not link to S3.
To link S3 directly, allow public reads on the `nebula/apk/*` prefix in the
storage provider's dashboard and change the four download links in `index.html`.

## Release checklist

- Build: `./gradlew :app:assembleRelease` with the `NEBULA_RELEASE_*` signing
  values in the environment.
- Copy the artifact over `download/`, bump the two version strings in
  `index.html` (hero meta + release card) and the release notes list.
- Keep the file name version-specific (`nebula-1.1.0.apk`) so a cached older
  download never overwrites a newer one mid-release.
