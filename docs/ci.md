# Continuous integration

Every pull request and every push to `main` runs the `CI` workflow ([`.github/workflows/ci.yml`](../.github/workflows/ci.yml)). It has two independent jobs. **Keep the job names `verify` and `docker-tests` stable**: branch protection and the README badge refer to them.

| Check | What it does | Typical time |
|---|---|---|
| `verify` | JDK 21, Maven cache, `mvn verify` over all modules. Runs every unit test; the Docker-backed tests are skipped. | about 2 min |
| `docker-tests` | Builds the sandbox image from `runner/docker` as `javarpg-runner:1`, then runs `mvn verify -Ddocker.tests=true` (unit tests plus the escape suite, timing, happy path and harness tests against the real container). | about 3 min |

A newer commit on a pull request cancels that pull request's older run. Runs on `main` are never cancelled. Jobs have timeouts (15 and 30 minutes), and a cancelled, timed-out or failed job never counts as a pass.

## Reading a failure
Open the failed check on the pull request, then **Summary > Artifacts**. A failed run uploads `verify-test-reports` or `docker-tests-test-reports` (the `target/surefire-reports` folders). Reports are kept for **3 days** and are only uploaded on failure.

To re-run: open the run and choose **Re-run failed jobs**. A test that passes only after a re-run is a defect to fix, not a pass.

## Safety of the workflow
- Triggered by `pull_request`, never `pull_request_target`; the token is `contents: read` and no secrets are used anywhere.
- Only GitHub-maintained actions (`checkout`, `setup-java`, `upload-artifact`), pinned to a major version.
- Hostile test fixtures run only inside the sandbox container on a throwaway GitHub-hosted VM.

## Branch protection (current state: not enforceable)
The repository is **private on the GitHub Free plan**, and GitHub does not offer branch protection or rulesets there (the API answers "Upgrade to GitHub Pro or make this repository public"). Until that changes the two checks are **advisory**: they report on every pull request, but nothing stops a merge when they are red. Merge only when both are green.

When the repository becomes public, or the account moves to GitHub Pro or higher, apply these settings (**Settings > Branches > Add branch protection rule**, or **Settings > Rules > Rulesets**):

1. Branch name pattern: `main`.
2. Require a pull request before merging.
3. Require status checks to pass before merging: add **`verify`** and **`docker-tests`** (they appear in the search box after they have run once).
4. Require branches to be up to date before merging.
5. Leave **Do not allow bypassing the above settings** unticked, so the owner can bypass in an emergency. A bypassed merge should be followed by a fix of the broken check.
6. Settings > Actions > General > **Fork pull request workflows**: keep *Require approval for first-time contributors* on.

The same with the GitHub CLI (classic branch protection; `enforce_admins: false` keeps the owner bypass):

```bash
gh api -X PUT repos/Xyrsto/JavaRPG/branches/main/protection --input - <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["verify", "docker-tests"] },
  "enforce_admins": false,
  "required_pull_request_reviews": null,
  "restrictions": null
}
JSON
```

## Build status badge
The README badge shows the latest `CI` run on `main`. The repository is private, so the badge image only renders for people signed in to GitHub with access.
