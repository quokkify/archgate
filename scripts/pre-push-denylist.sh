#!/bin/sh
# git pre-push hook: args are <remote name> <remote url>; stdin lines are
# "<local ref> <local sha> <remote ref> <remote sha>".
set -u

zero=0000000000000000000000000000000000000000
git_dir=$(git rev-parse --git-dir) || exit 1
list="$git_dir/info/archgate-denylist"

if [ ! -f "$list" ]; then
    echo "denylist missing: $list" >&2
    exit 1
fi

status=0
while read -r local_ref local_sha remote_ref remote_sha; do
    [ "$local_sha" = "$zero" ] && continue

    if git grep -niEf "$list" "$local_sha" >/dev/null 2>&1; then
        echo "denylist match in tree of $local_ref ($local_sha):" >&2
        git grep -nlEif "$list" "$local_sha" >&2
        status=1
    fi

    if [ "$remote_sha" = "$zero" ]; then
        range="$local_sha"
    else
        range="$remote_sha..$local_sha"
    fi
    if git log -p "$range" | grep -niEf "$list" >/dev/null 2>&1; then
        echo "denylist match in commits of $local_ref ($range)" >&2
        status=1
    fi
done

exit "$status"
