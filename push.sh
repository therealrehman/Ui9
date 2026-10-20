#!/usr/bin/env bash
# Pushes this project to GitHub so a cloud build (Bitrise) can compile it.
#   First time:  bash push.sh https://github.com/YOUR_NAME/OneUI9Launcher.git
#   Every step:  bash push.sh
# Create the repo first on github.com: New repository, PUBLIC, empty (no README, no .gitignore).
cd "$(dirname "$0")" || exit 1

if ! command -v git >/dev/null 2>&1; then
  echo "git is not installed. Run:  pkg install git"
  exit 1
fi

if [ ! -d .git ]; then
  git init -q
  git symbolic-ref HEAD refs/heads/main
fi
git config user.email >/dev/null 2>&1 || git config user.email "launcher@localhost"
git config user.name  >/dev/null 2>&1 || git config user.name  "OneUI9 Launcher"

if [ -n "$1" ]; then
  git remote remove origin >/dev/null 2>&1
  git remote add origin "$1"
fi
if ! git remote get-url origin >/dev/null 2>&1; then
  echo "No GitHub repo set yet. Run once:"
  echo "  bash push.sh https://github.com/YOUR_NAME/OneUI9Launcher.git"
  exit 1
fi

git add -A
git update-index --chmod=+x gradlew build.sh push.sh release.sh >/dev/null 2>&1
VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -n 1)
if git diff --cached --quiet; then
  echo "Nothing new to commit."
else
  git commit -q -m "Ok Launcher ${VERSION}"
fi
git branch -M main >/dev/null 2>&1

if ! git push -u origin main; then
  echo
  echo "Push failed. Check the repo URL. When asked, log in with your GitHub username + personal access token."
  exit 1
fi
echo
echo "Pushed Ok Launcher ${VERSION}."
