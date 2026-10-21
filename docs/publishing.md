# Publishing to Maven Central

How `dev.machineminds:jqpdf` gets released. The release itself will run in
GitHub Actions; this page records the credential setup, the secret
inventory, and the local dry-run used to verify a release build without
publishing anything.

## One-time setup (done)

- **Namespace** `dev.machineminds` is verified on the
  [Central Portal](https://central.sonatype.com).
- **Signing key**: RSA 4096, uid
  `Nikolay Kostadinov <n-kostadinov@users.noreply.github.com>`,
  fingerprint `C7B992717875340032903F193655B7C38DF483F4` (long key id
  `3655B7C38DF483F4`), no expiry. The public key is on
  `keyserver.ubuntu.com` so Central can validate signatures.
- **Key backup**: `~/jqpdf-release-secrets/` (outside the repository)
  holds the passphrase-protected armored private key
  (`jqpdf-gpg-private-key.asc`), the passphrase
  (`jqpdf-gpg-passphrase.txt`), and the key id (`jqpdf-gpg-keyid.txt`).
  Without a backup copy of this directory the key is unrecoverable.
  The key also remains in this machine's GnuPG keychain for local
  dry-runs.

## Remaining manual step: the Central token

Portal user tokens cannot be created programmatically. In the Central
Portal, go to **Account → Generate user token**, then store both halves
as repository secrets:

```bash
gh secret set CENTRAL_TOKEN_USERNAME --repo n-kostadinov/jqpdf
gh secret set CENTRAL_TOKEN_PASSWORD --repo n-kostadinov/jqpdf
```

## Secret inventory

| Secret | Set | Used for |
|---|---|---|
| `GPG_PRIVATE_KEY` | yes (armored private key) | workflow imports it into a fresh GnuPG keyring |
| `GPG_PASSPHRASE` | yes | unlocks the signing key during artifact signing |
| `CENTRAL_TOKEN_USERNAME` | **pending** (manual step above) | server `central` in the workflow's settings.xml |
| `CENTRAL_TOKEN_PASSWORD` | **pending** (manual step above) | server `central` in the workflow's settings.xml |

## Release profile

Everything Central requires lives in the `release` Maven profile,
activated only with `-Prelease`: sources jar (native binaries excluded),
javadoc jar, GPG signing (`loopback` pinentry for non-interactive use),
and `central-publishing-maven-plugin` 0.12.0 (`autoPublish=true`,
`waitUntil=published`). Plain `mvn test`/`install` never needs a key or
credentials.

Credentials reach Maven through a `settings.xml` with two servers — the
workflow generates it from secrets:

```xml
<servers>
  <server>
    <id>central</id>
    <username>${env.CENTRAL_TOKEN_USERNAME}</username>
    <password>${env.CENTRAL_TOKEN_PASSWORD}</password>
  </server>
  <server>
    <id>gpg.passphrase</id>
    <passphrase>${env.GPG_PASSPHRASE}</passphrase>
  </server>
</servers>
```

## The release command (as run by the workflow)

[`.github/workflows/release.yml`](../.github/workflows/release.yml) runs
these steps on a tag push; shown here as the manual equivalent:

```bash
# 1. Import the signing key into a fresh keyring
gpg --batch --import <(echo "$GPG_PRIVATE_KEY")   # or: echo ... | gpg --batch --import -

# 2. Prune unsupported platforms and verify the bundle
scripts/release-native-bundle

# 3. Set the release version (pom stays on -SNAPSHOT in git)
mvn versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false

# 4. Build, sign, publish
mvn -Prelease -DskipTests -s settings.xml deploy
```

The `-Dgpg.passphrase=...` property also works (the plugin reads it
directly) but is deprecated in `maven-gpg-plugin` 3.x because it can
leak into logs; the settings.xml server entry above is the supported
route and is what the workflow should use.

The version must not end in `-SNAPSHOT` when publishing a release:
snapshot versions are diverted to the Central snapshot repository
(`distributionManagement` in the pom) and skip validation entirely.

## Release runbook (step by step)

1. **Preconditions** (check once): `gh secret list` shows
   `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `CENTRAL_TOKEN_USERNAME`,
   `CENTRAL_TOKEN_PASSWORD`; CI is green on `main`
   (`gh run list --workflow CI`).
2. **Local prep**: working tree clean and on latest `main`
   (`git status`, `git pull`).
3. **Tag the release**: the tag name is the release version —
   `git tag v1.0.0 && git push origin v1.0.0`.
   The pom stays on its `-SNAPSHOT` version in git; the workflow derives
   `1.0.0` from the tag.
4. **Watch the workflow** (`gh run watch` or the Actions page). It reruns
   the full CI matrix, then the publish job imports the signing key,
   stages the exact natives the tests ran against, prunes unsupported
   platforms (`scripts/release-native-bundle`), sets the release version,
   deploys to Central (`autoPublish`, waits until `published`), and
   creates the GitHub Release with generated notes.
5. **Verify on Central**: `https://repo1.maven.org/maven2/dev/machineminds/jqpdf/<version>/`
   lists the jar, sources, javadoc, and `.asc` signatures.
6. **Edit the GitHub Release** to add the bundled-qpdf line, e.g.
   `Bundles qpdf 12.4.2` (web UI or
   `gh release edit v1.0.0 --notes "Bundles qpdf <qpdf.version> …"`).
7. **Bump the pom** to the next development version
   (`mvn versions:set -DnewVersion=1.1.0-SNAPSHOT` or similar) and push
   to `main`.

If publish fails on missing token secrets, set them (see above) and
re-run the failed job: `gh run rerun <run-id> --failed`. Publishing a
version that is already on Central is not possible — fix forward with a
new patch version instead.

## Local dry-run (nothing uploaded)

```bash
mvn versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
mvn -Prelease -DskipTests -DskipUpload=true \
    -Dgpg.passphrase=$(cat ~/jqpdf-release-secrets/jqpdf-gpg-passphrase.txt) \
    clean deploy
mvn versions:set -DnewVersion=1.0.0-SNAPSHOT -DgenerateBackupPoms=false
```

`-DskipUpload=true` (new in plugin 0.12.0) stops after building
`target/central-publishing/central-bundle.zip`. Check the bundle
contains `jqpdf-<version>.jar`, `-sources.jar`, `-javadoc.jar`, and
`.pom`, each with an `.asc` signature and checksums, and that the main
jar still carries `dev/machineminds/jqpdf/native/` — the natives are
generated by `scripts/fetch-qpdf-binaries` and
`scripts/build-native-shim` and are **not** committed, so run those
first on a fresh checkout.

## Toolchain pin

Build releases with Maven 3.9.x. The `central-publishing-maven-plugin`
is currently broken with Maven 4.
