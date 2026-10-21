#!/bin/sh
set -u

hook=$(cd "$(dirname "$0")" && pwd)/pre-push-denylist.sh
zero=0000000000000000000000000000000000000000
work=$(mktemp -d) || exit 1
trap 'rm -rf "$work"' EXIT HUP INT TERM
fails=0

cd "$work" || exit 1
git init -q . || exit 1
git config user.email t@example.com
git config user.name t
git config commit.gpgsign false

commit() {
    git add -A && git commit -q -m "$1"
    git rev-parse HEAD
}

run_hook() {
    printf 'refs/heads/main %s refs/heads/main %s\n' "$1" "$2" | sh "$hook" origin url >/dev/null 2>&1
}

expect() {
    name=$1 want=$2 got=$3
    if [ "$want" != "$got" ]; then
        echo "FAIL: $name (want exit $want, got $got)" >&2
        fails=$((fails + 1))
    else
        echo "ok: $name"
    fi
}

mkdir -p .git/info
echo 'forbidden' >.git/info/archgate-denylist

echo clean >a.txt
c0=$(commit base)
run_hook "$c0" "$zero"; expect "clean tree passes" 0 $?

echo forbidden >b.txt
c1=$(commit leak)
run_hook "$c1" "$c0"; expect "match in tree blocks" 1 $?

git rm -q b.txt
c2=$(commit cleanup)
run_hook "$c2" "$c0"; expect "match only in history diff blocks" 1 $?
run_hook "$c2" "$c2"; expect "empty range on clean tree passes" 0 $?
run_hook "$c2" 1111111111111111111111111111111111111111
expect "unknown remote sha scans full history and blocks" 1 $?

echo '(' >.git/info/archgate-denylist
run_hook "$c0" "$zero"; expect "invalid regex blocks" 1 $?

rm .git/info/archgate-denylist
run_hook "$c0" "$zero"; expect "missing denylist blocks" 1 $?

[ "$fails" -eq 0 ]
