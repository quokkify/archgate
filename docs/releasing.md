# Releasing

English · [Русский](releasing.ru.md)

## How it works

```
feature branch ──PR──► main ──► Release Please opens "release PR" ──merge──► tag vX.Y.Z + GitHub Release ──► publish.yml
```

- `main` is the only long-lived branch. Every change reaches it through a pull request.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/). Release Please (single-package mode) reads them and keeps one open **release PR** that updates `version.txt`, its manifest and the changelog.
- `version.txt` holds the last released version, `0.0.0` before the first release. Between releases builds are `X.Y.Z-SNAPSHOT`.
- Merging the release PR creates the tag `vX.Y.Z` and the GitHub Release.
- The tag does not trigger publishing directly. A release created with the workflow `GITHUB_TOKEN` does not fire `release` events, so the project-local workflow `publish.yml` is triggered by `workflow_run` of the Release Please workflow instead. It:
  1. checks out the release tag;
  2. asserts that `v$(cat version.txt)` equals the tag;
  3. runs `publishToMavenCentral` for all artifacts, signed;
  4. runs `publishPlugins` to the Gradle Plugin Portal.
- The Maven Central deployment is created with `automaticRelease = false`, so it is released from the Central Portal after a look at the staged files.

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
4. `publish.yml` publishes the artifacts. Watch the run; when it is green, release the staged deployment in the Central Portal.

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

Do not move or reuse a tag. If a release never published because `publish.yml` failed after the tag was created, fix the cause and re-run the failed workflow run for the same tag; do not create a new tag for the same version.

## When `publish.yml` fails

| Step failed | What to do |
|---|---|
| Tag assertion | `version.txt` and the tag disagree. Do not publish. Fix the release PR flow, then release the next patch. |
| `publishToMavenCentral` | Fix the cause (credentials, signing key, namespace), then re-run the workflow. Nothing was released if the deployment was not staged. |
| `publishPlugins` | Maven Central already has the release. Fix the Plugin Portal credentials and re-run only this step through the workflow. |

If the publish can never succeed and you abandon `X.Y.Z`, release the next patch.

## Prerequisites (one-time, owner actions)

- Verify the `dev.quokkify` namespace in the Central Portal (DNS TXT record).
- Create a Gradle Plugin Portal API key.
- Add the repository secrets: GPG signing key and passphrase, Central Portal token (user and password), Plugin Portal key and secret.
- Protect `main`: pull requests only, with the required status checks.
