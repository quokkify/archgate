# Releasing

English · [Русский](releasing.ru.md)

## How it works

```
feature branch ──PR──► main ──► Release Please opens "release PR" ──merge──► tag vX.Y.Z + GitHub Release ──► publish.yml
```

- `main` is the only long-lived branch. Every change reaches it through a pull request.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/). Release Please (single-package mode) reads them and keeps one open **release PR** that updates `version.txt`, its manifest and the changelog.
- The release PR also updates the version in the snippets of `README*.md` and `docs/configuration*.md`: each such code block sits between `<!-- x-release-please-start-version -->` and `<!-- x-release-please-end -->`, which Release Please reads as `extra-files` with the `generic` updater. Wrap a new snippet that names the ArchGate version the same way.
- `version.txt` holds the last released version, `0.0.0` before the first release. Between releases builds are `X.Y.Z-SNAPSHOT`.
- Merging the release PR creates the tag `vX.Y.Z` and the GitHub Release.
- The tag does not trigger publishing directly. A release created with the workflow `GITHUB_TOKEN` does not fire `release` events, so the project-local workflow `publish.yml` is triggered by `workflow_run` of the Release Please workflow instead. It:
  1. checks out the commit the Release Please run built (`workflow_run.head_sha`) and looks for a `vX.Y.Z` tag on it; a run without a release tag, such as an ordinary push to `main`, ends quietly without publishing;
  2. asserts that `v$(cat version.txt)` equals the tag;
  3. builds the release without secrets (`build` without `mavenIT` and the parity test, which CI already ran on `main`);
  4. runs `publishAndReleaseToMavenCentral` for all artifacts, signed, and waits until Central reports the deployment as published;
  5. only then runs `publishPlugins` to the Gradle Plugin Portal, so the Portal never points at artifacts that are not on Central yet.
- The secrets are passed only to the publishing step, and the job runs in the `release` environment.
- A release build (`-Prelease=true`) refuses to publish without the signing key and stops with a message that names the missing `signingInMemoryKey` property.
- To publish by hand, run the workflow with `workflow_dispatch` and the tag, for example `v0.1.0`. A tag that does not exist fails the run.

## First release

The first version is `0.1.0`. It is forced by a commit whose message contains the footer `Release-As: 0.1.0`:

```
git commit --allow-empty -m "chore: release 0.1.0" -m "Release-As: 0.1.0"
```

After that, Release Please derives versions from the commit types: `fix` bumps the patch, `feat` the minor, a breaking change (`!` or a `BREAKING CHANGE` footer) the major.

## Normal release

1. Merge the changes into `main` through pull requests.
2. Open the release PR that Release Please maintains and check the changelog and the new version.
3. Merge it. The tag and the GitHub Release are created.
4. `publish.yml` publishes and releases the artifacts on Maven Central, then publishes the Gradle plugin. Watch the run; when it is green, the release is out.

## Fix something before the release is out

The release PR is only a proposal. Merge the fix into `main` through a normal pull request; Release Please updates the release PR by itself. Nothing is cherry-picked.

## Hotfix an already released version

Release Please releases from `main`, and maintenance branches are not supported. Merge the fix into `main` as a `fix:` pull request and merge the next release PR. If `main` holds unreleased work that must not ship yet, keep that work out of `main` until it is ready, for example in an unmerged pull request.

## Rolling back a bad release

Treat a published version as immutable. Never redeploy, delete or re-tag `X.Y.Z`: consumers and their caches may already hold it. You **roll forward** instead.

| Goal | What to do |
|---|---|
| Unblock consumers right now | They pin the previous version, for example `1.4.1`. |
| Withdraw the change | Merge a `fix:` commit that reverts it into `main`, then merge the release PR. Consumers move to `1.4.3`. |

Do not move or reuse a tag. If a release never published because `publish.yml` failed after the tag was created, fix the cause and publish the same tag again (see the next section); do not create a new tag for the same version.

## When `publish.yml` fails

| Step failed | What to do |
|---|---|
| Tag assertion | `version.txt` and the tag disagree. Do not publish. Fix the release PR flow, then release the next patch. |
| Build the release | Nothing was published. Fix the cause on `main` and release the next patch, or, if the failure was transient, re-run the failed run. |
| `publishAndReleaseToMavenCentral` | If Central did not publish the deployment, nothing was released: fix the cause (credentials, signing key, namespace) and re-run the failed run. If the deployment was published but the wait timed out, check the Central Portal and continue as in the next row. |
| `publishPlugins` | Maven Central already has the release, so re-running the whole run would fail on the existing version. Fix the Plugin Portal credentials, then run the workflow by hand with the tag and `plugin-portal-only` checked. |

If the publish can never succeed and you abandon `X.Y.Z`, release the next patch.

## Prerequisites (one-time, owner actions)

- Verify the `dev.quokkify` namespace in the Central Portal (DNS TXT record).
- Create a Gradle Plugin Portal API key.
- Create the `release` environment and add its secrets: GPG signing key and passphrase (`SIGNING_IN_MEMORY_KEY`, `SIGNING_IN_MEMORY_KEY_PASSWORD`), Central Portal token (`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`), Plugin Portal key and secret (`GRADLE_PUBLISH_KEY`, `GRADLE_PUBLISH_SECRET`).
- Protect `main`: pull requests only, with the required status checks.
