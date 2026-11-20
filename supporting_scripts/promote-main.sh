#!/usr/bin/env bash
#
# Promotes develop to main: fast-forwards main to the tip of develop. The push to main triggers the deploy
# workflow, so this IS a production deployment.
#
# main is locked (lock_branch) and requires a linear history, which rules out merge commits and PRs from develop.
# The only way in is a plain fast-forward push while the lock is lifted. This script does exactly that and puts the
# branch protection back exactly as it found it, also when the push fails or the script is interrupted.
#
# Requires: gh (authenticated as a repository admin), git, python3.
# Usage: promote-main.sh [--yes] [--dry-run] [--skip-ci-check]
set -euo pipefail

REPO=${REPO:-ls1intum/Artemis-Benchmarking}
REMOTE=${REMOTE:-origin}
API="repos/$REPO/branches/main/protection"

ASSUME_YES=false
DRY_RUN=false
CHECK_CI=true
for arg in "$@"; do
    case $arg in
        --yes) ASSUME_YES=true ;;
        --dry-run) DRY_RUN=true ;;
        --skip-ci-check) CHECK_CI=false ;;
        *) echo "Unknown argument: $arg" >&2; exit 2 ;;
    esac
done

WORK=$(mktemp -d)
UNLOCKED=false

restore_protection() {
    if [[ $UNLOCKED == true ]]; then
        echo "Restoring branch protection of main ..."
        gh api -X PUT "$API" --input "$WORK/restore.json" > /dev/null
        UNLOCKED=false
    fi
}
cleanup() {
    restore_protection || echo "!! FAILED TO RESTORE THE PROTECTION OF main. Restore it from $WORK/restore.json: gh api -X PUT $API --input $WORK/restore.json" >&2
    # keep the snapshot if something went wrong, otherwise tidy up
    [[ $UNLOCKED == false ]] && rm -rf "$WORK"
}
trap cleanup EXIT

git fetch --quiet "$REMOTE"
MAIN=$(git rev-parse "$REMOTE/main")
DEVELOP=$(git rev-parse "$REMOTE/develop")

if [[ $MAIN == "$DEVELOP" ]]; then
    echo "main is already at the tip of develop ($DEVELOP). Nothing to promote."
    exit 0
fi
if ! git merge-base --is-ancestor "$MAIN" "$DEVELOP"; then
    echo "main is not an ancestor of develop, so this would not be a fast-forward. Refusing." >&2
    exit 1
fi

echo "Commits that will be deployed:"
git log --oneline "$MAIN..$DEVELOP"
echo

if [[ $CHECK_CI == true ]]; then
    # Every check run of the develop tip must have finished successfully (skipped counts as fine).
    BAD=$(gh api "repos/$REPO/commits/$DEVELOP/check-runs" --jq '[.check_runs[] | select(.conclusion != "success" and .conclusion != "skipped" and .conclusion != "neutral") | "\(.name): \(.status)/\(.conclusion)"] | .[]')
    TOTAL=$(gh api "repos/$REPO/commits/$DEVELOP/check-runs" --jq '.total_count')
    if [[ $TOTAL -eq 0 || -n $BAD ]]; then
        echo "CI of ${DEVELOP:0:7} is not green (checks: $TOTAL):" >&2
        echo "${BAD:-no check runs found}" >&2
        echo "Wait for CI or pass --skip-ci-check." >&2
        exit 1
    fi
    echo "CI of ${DEVELOP:0:7} is green ($TOTAL checks)."
fi

gh api "$API" > "$WORK/original.json"

# Turn the GET representation of the protection into the PUT payload that recreates it.
python3 - "$WORK" <<'PY'
import json, sys
work = sys.argv[1]
g = json.load(open(f"{work}/original.json"))

def flag(key):
    value = g.get(key)
    return bool(value.get("enabled")) if isinstance(value, dict) else bool(value)

checks = g.get("required_status_checks")
reviews = g.get("required_pull_request_reviews")
restrictions = g.get("restrictions")
payload = {
    "required_status_checks": {"strict": checks["strict"], "contexts": checks.get("contexts", [])} if checks else None,
    "enforce_admins": flag("enforce_admins"),
    "required_pull_request_reviews": {
        k: reviews[k]
        for k in ("dismiss_stale_reviews", "require_code_owner_reviews", "required_approving_review_count", "require_last_push_approval")
        if k in reviews
    } if reviews else None,
    "restrictions": {
        "users": [u["login"] for u in restrictions["users"]],
        "teams": [t["slug"] for t in restrictions["teams"]],
        "apps": [a["slug"] for a in restrictions.get("apps", [])],
    } if restrictions else None,
    "required_linear_history": flag("required_linear_history"),
    "allow_force_pushes": flag("allow_force_pushes"),
    "allow_deletions": flag("allow_deletions"),
    "block_creations": flag("block_creations"),
    "required_conversation_resolution": flag("required_conversation_resolution"),
    "lock_branch": flag("lock_branch"),
    "allow_fork_syncing": flag("allow_fork_syncing"),
}
json.dump(payload, open(f"{work}/restore.json", "w"))
json.dump({**payload, "lock_branch": False}, open(f"{work}/unlock.json", "w"))
print("Protection snapshot taken (lock_branch=%s, linear_history=%s, force_pushes=%s)" % (payload["lock_branch"], payload["required_linear_history"], payload["allow_force_pushes"]))
PY

if [[ $DRY_RUN == true ]]; then
    echo "Dry run: would fast-forward main ${MAIN:0:7} -> ${DEVELOP:0:7}. Nothing was changed."
    exit 0
fi

if [[ $ASSUME_YES != true ]]; then
    read -r -p "Fast-forward main to ${DEVELOP:0:7} and deploy to production? [y/N] " answer
    [[ $answer == [yY] ]] || { echo "Aborted."; exit 1; }
fi

echo "Lifting the lock on main ..."
gh api -X PUT "$API" --input "$WORK/unlock.json" > /dev/null
UNLOCKED=true

echo "Fast-forwarding main ..."
git push "$REMOTE" "$DEVELOP:refs/heads/main"

restore_protection

git fetch --quiet "$REMOTE"
[[ $(git rev-parse "$REMOTE/main") == "$DEVELOP" ]] || { echo "main does not point at the expected commit" >&2; exit 1; }

# The protection must now be identical to the snapshot.
gh api "$API" > "$WORK/after.json"
python3 - "$WORK" <<'PY'
import json, sys
work = sys.argv[1]
a = json.load(open(f"{work}/original.json")); b = json.load(open(f"{work}/after.json"))
def strip(d):
    if isinstance(d, dict):
        return {k: strip(v) for k, v in d.items() if k not in ("url", "contexts_url")}
    if isinstance(d, list):
        return [strip(x) for x in d]
    return d
if strip(a) != strip(b):
    sys.exit("The branch protection differs from the snapshot after restoring it; inspect it manually")
print("Branch protection restored and verified.")
PY
echo "main is now at ${DEVELOP:0:7}. The deploy workflow was triggered: https://github.com/$REPO/actions/workflows/deploy.yml"
