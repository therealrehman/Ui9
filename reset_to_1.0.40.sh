#!/data/data/com.termux/files/usr/bin/bash
# Run in your Ui9 repo after unzipping this zip over it. Deletes every commit above 1.0.40 (603d552), keeps a backup branch.
set -e
rm -rf /tmp/ui9_keep && mkdir /tmp/ui9_keep && cp -r --parents app STEPS.md /tmp/ui9_keep/
git fetch origin
git push -f origin origin/main:refs/heads/backup-before-reset
git reset --hard 603d552
cp -r /tmp/ui9_keep/app /tmp/ui9_keep/STEPS.md .
git add -A && git commit -m "Ok Launcher 1.0.45"
git push --force-with-lease origin HEAD:main
