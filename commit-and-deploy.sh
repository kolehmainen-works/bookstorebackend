#!/usr/bin/env bash
# Commit, push, build and deploy bookstorebackend to softala.haaga-helia.fi.
# Logs in with iinak's password (also used for sudo on the server).
# Copied from kolehmainenbookstore/commit-and-deploy.sh.
#
# Usage: ./commit-and-deploy.sh "commit message"
#
# The password is taken from, in this order:
#   1) environment variable DEPLOY_PASSWORD
#   2) file .deploy-password next to this script (keep it out of git)
#   3) a prompt, if run in a terminal
set -euo pipefail

REMOTE_USER="iinak"
REMOTE_HOST="softala.haaga-helia.fi"
REMOTE="${REMOTE_USER}@${REMOTE_HOST}"
APP_NAME="bookstorebackend"
REMOTE_DIR="/opt/prod"
# Check with: systemctl list-units --type=service | grep -i book
# NOTE: this is the bookstore's service on port 8090, so deploying here
# replaces kolehmainenbookstore on the server.
SERVICE_NAME="iinakbookapp.service"
JAR_LOCAL="target/${APP_NAME}-0.0.1-SNAPSHOT.jar"
JAR_REMOTE="${REMOTE_DIR}/iinakbookapp.jar"
# Upload to the user's home first; sudo then copies it to JAR_REMOTE.
JAR_UPLOAD="/home/${REMOTE_USER}/${APP_NAME}.jar.upload"

COMMIT_MSG="${1:-deploy $(date '+%Y-%m-%d %H:%M:%S')}"
SSH_OPTS=(-o ConnectTimeout=15)

cd "$(dirname "$0")"

if [ -z "${DEPLOY_PASSWORD:-}" ] && [ -f .deploy-password ]; then
  DEPLOY_PASSWORD="$(head -n 1 .deploy-password | tr -d '\r')"
fi
if [ -z "${DEPLOY_PASSWORD:-}" ] && [ -t 0 ]; then
  read -r -s -p "Password for ${REMOTE}: " DEPLOY_PASSWORD
  echo
fi
[ -n "${DEPLOY_PASSWORD:-}" ] || { echo "No password: set DEPLOY_PASSWORD or create .deploy-password" >&2; exit 1; }
export DEPLOY_PASSWORD

# ssh/scp read the password from this helper instead of the keyboard
ASKPASS="$(mktemp)"
trap 'rm -f "$ASKPASS"' EXIT
printf '#!/bin/sh\nprintf "%%s\\n" "$DEPLOY_PASSWORD"\n' > "$ASKPASS"
chmod 700 "$ASKPASS"
export SSH_ASKPASS="$ASKPASS" SSH_ASKPASS_REQUIRE=force DISPLAY="${DISPLAY:-:0}"

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "==> 1) git add"
  git add -A

  echo "==> 2) git commit"
  if git diff --cached --quiet; then
    echo "Nothing to commit, continuing."
  else
    git commit -m "$COMMIT_MSG"
  fi

  echo "==> 3) git push"
  git push origin "$(git rev-parse --abbrev-ref HEAD)"
else
  echo "==> 1-3) not a git repository, skipping commit and push"
fi

echo "==> 4) build jar"
# Tests use an in-memory H2 database, so they run here; a failing test stops the deploy.
./mvnw -q clean package
[ -f "$JAR_LOCAL" ] || { echo "Jar not found: $JAR_LOCAL" >&2; exit 1; }

echo "==> 5) scp jar to ${REMOTE}"
if ! scp "${SSH_OPTS[@]}" "$JAR_LOCAL" "${REMOTE}:${JAR_UPLOAD}"; then
  echo "ERROR: could not copy jar to ${REMOTE}:${JAR_UPLOAD}" >&2
  exit 1
fi

echo "==> 6) + 7) sudo copy jar into place and restart ${SERVICE_NAME}"
# sudo -S reads the password from stdin; one sudo runs both steps.
# Exit code 10 = copy failed, 11 = restart failed, 1 = sudo password wrong.
set +e
printf '%s\n' "$DEPLOY_PASSWORD" | ssh "${SSH_OPTS[@]}" "$REMOTE" \
  "sudo -S -p '' sh -c \"cp -f '${JAR_UPLOAD}' '${JAR_REMOTE}' || exit 10; \
   rm -f '${JAR_UPLOAD}'; \
   systemctl restart '${SERVICE_NAME}' || exit 11\""
rc=$?
set -e
case $rc in
  0) ;;
  1) echo "ERROR: sudo failed on ${REMOTE_HOST} (wrong password or no sudo rights?)" >&2; exit 1 ;;
  10) echo "ERROR: could not copy jar to ${JAR_REMOTE} on ${REMOTE_HOST}" >&2; exit 1 ;;
  11) echo "ERROR: could not restart ${SERVICE_NAME} on ${REMOTE_HOST}" >&2; exit 1 ;;
  *)  echo "ERROR: ssh to ${REMOTE} failed (exit code $rc)" >&2; exit 1 ;;
esac
sleep 5
if ! ssh "${SSH_OPTS[@]}" "$REMOTE" "systemctl is-active --quiet '${SERVICE_NAME}'"; then
  echo "ERROR: ${SERVICE_NAME} is not running after restart on ${REMOTE_HOST}" >&2
  ssh "${SSH_OPTS[@]}" "$REMOTE" "systemctl --no-pager status '${SERVICE_NAME}' | head -n 15" || true
  exit 1
fi

echo "==> Deployed. API should be at https://${REMOTE_HOST}:8090/api/auth/login"
