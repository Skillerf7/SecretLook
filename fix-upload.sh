#!/data/data/com.termux/files/usr/bin/bash
# Legt den Workflow an (falls der versteckte Ordner .github fehlt) und pusht alles.
cd "$(dirname "$0")" || exit 1

mkdir -p .github/workflows
cp -f workflow-kopie/build-apk.yml .github/workflows/build-apk.yml

[ -d .git ] || git init -b main
git config user.name  >/dev/null || git config user.name  "Skillerf7"
git config user.email >/dev/null || git config user.email "skillerf7@users.noreply.github.com"
git remote get-url origin >/dev/null 2>&1 \
  && git remote set-url origin https://github.com/Skillerf7/SecretLook.git \
  || git remote add origin https://github.com/Skillerf7/SecretLook.git

chmod +x gradlew
git add -A
git update-index --chmod=+x gradlew
git commit -m "Add build workflow and gitignore" || echo "Nichts Neues zu committen"
git branch -M main

git pull --rebase origin main --allow-unrelated-histories || { echo "Pull-Konflikt, bitte melden"; exit 1; }
git push -u origin main
echo "FERTIG: GitHub -> Actions -> Build Debug APK"
