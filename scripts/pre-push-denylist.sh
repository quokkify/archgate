#!/bin/sh
# git pre-push hook: args are <remote name> <remote url>; stdin lines are
# "<local ref> <local sha> <remote ref> <remote sha>".
# Install: ln -s ../../scripts/pre-push-denylist.sh "$(git rev-parse --git-common-dir)/hooks/pre-push"
# The denylist lives in the common git dir, so linked worktrees share it.
set -u

zero=0000000000000000000000000000000000000000
common_dir=$(git rev-parse --git-common-dir) || exit 1
list="$common_dir/info/archgate-denylist"

if [ ! -f "$list" ]; then
    echo "denylist missing: $list" >&2
    exit 1
fi

tmp=$(mktemp) || exit 1
trap 'rm -f "$tmp"' EXIT HUP INT TERM

status=0

# rc 0 = match, 1 = clean, anything else = tool error; both match and error block.
verdict() {
    case "$1" in
        0) echo "denylist match in $2" >&2; status=1 ;;
        1) ;;
        *) echo "denylist check failed (exit $1) in $2" >&2; status=1 ;;
    esac
}

while read -r local_ref local_sha remote_ref remote_sha; do
    [ "$local_sha" = "$zero" ] && continue

    git grep -niEf "$list" "$local_sha" >/dev/null 2>&1
    verdict $? "tree of $local_ref"

    range="$local_sha"
    if [ "$remote_sha" != "$zero" ] && git cat-file -e "$remote_sha^{commit}" 2>/dev/null; then
        range="$remote_sha..$local_sha"
    fi

    if git log -p "$range" >"$tmp" 2>/dev/null; then
        grep -niEf "$list" "$tmp" >/dev/null 2>&1
        verdict $? "commits of $local_ref ($range)"
    else
        verdict 128 "git log of $local_ref ($range)"
    fi
done

exit "$status"
