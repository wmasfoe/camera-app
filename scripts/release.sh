#!/bin/bash
set -euo pipefail

# 创建 GitHub Release（打 tag 触发 CI）
# 用法: ./scripts/release.sh v0.2.0-alpha.1

if [ -z "${1:-}" ]; then
    echo "Usage: $0 <version>"
    echo "Example: $0 v0.2.0-alpha.1"
    exit 1
fi

VERSION="$1"

# 验证 tag 格式
if [[ ! "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+ ]]; then
    echo "❌ Invalid version format. Expected: v<major>.<minor>.<patch>[-suffix]"
    exit 1
fi

echo "==> Creating release $VERSION..."

# 确保工作区干净
if [ -n "$(git status --porcelain)" ]; then
    echo "❌ Working directory not clean. Commit or stash changes first."
    exit 1
fi

# 推送最新代码
git push

# 创建并推送 tag
git tag "$VERSION"
git push origin "$VERSION"

echo "✅ Tag $VERSION pushed. CI will build and create the release."
echo "   Track: https://github.com/wmasfoe/camera-app/actions"