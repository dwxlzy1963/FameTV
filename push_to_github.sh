#!/usr/bin/env bash
# push_to_github.sh
# Helper script to push the FameTV project to a new GitHub repository.
#
# Usage:
#   ./push_to_github.sh <your-github-username> <repo-name> [remote-name]
#
# Example:
#   ./push_to_github.sh johnsmith FameTV
#   ./push_to_github.sh johnsmith fametv-fork fork
#
# This script assumes:
#   - You have already created the repo on GitHub (empty, no README/LICENSE)
#   - You have an SSH key configured on GitHub, OR
#   - You will use HTTPS and authenticate via PAT (Personal Access Token)
#
# After pushing, GitHub Actions will auto-build the APK in ~5 minutes
# (see .github/workflows/build.yml).

set -e

USER="${1:?usage: $0 <github-username> <repo-name> [remote-name]}"
REPO="${2:?usage: $0 <github-username> <repo-name> [remote-name]}"
REMOTE="${3:-origin}"

cd "$(dirname "$0")"

# Sanity checks
if [ ! -d .git ]; then
    echo "❌ Not a git repo. Run: git init -b main && git add -A && git commit -m 'initial'"
    exit 1
fi

if ! git remote get-url "$REMOTE" >/dev/null 2>&1; then
    # Try SSH first; if it fails to connect, fall back to HTTPS
    SSH_URL="git@github.com:${USER}/${REPO}.git"
    HTTPS_URL="https://github.com/${USER}/${REPO}.git"

    # Test SSH connectivity (5s timeout)
    if ssh -o ConnectTimeout=5 -o StrictHostKeyChecking=no -T git@github.com 2>&1 | grep -qi "successfully authenticated\|You've successfully authenticated"; then
        echo "✅ SSH auth works, using SSH URL"
        git remote add "$REMOTE" "$SSH_URL"
    else
        echo "⚠️  SSH not configured, using HTTPS URL"
        echo "   You'll need a Personal Access Token (PAT) when prompted for password."
        echo "   Create one at: https://github.com/settings/tokens (scopes: repo, workflow)"
        git remote add "$REMOTE" "$HTTPS_URL"
    fi
fi

echo "📦 Pushing to GitHub..."
git push -u "$REMOTE" main

echo ""
echo "✅ Done!"
echo ""
echo "Next steps:"
echo "  1. Open: https://github.com/${USER}/${REPO}"
echo "  2. Go to Actions tab to watch the APK build (5 min)"
echo "  3. Download the APK artifact when build completes"
echo "  4. (Optional) Enable GitHub Pages to host channels.json:"
echo "     Settings → Pages → Source: main branch → /data folder → Save"
echo "     Your channels.json URL will be:"
echo "     https://${USER}.github.io/${REPO}/channels.json"
