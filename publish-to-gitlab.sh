#!/usr/bin/env bash
#
# qx-hybrid 发布到公司 GitLab Package Registry(本地执行,不走流水线)
#
# 首次使用前,先设置认证(Deploy Token,建议写进 ~/.zshrc 里持久化):
#   export GITLAB_MAVEN_USER='<deploy-token 用户名>'
#   export GITLAB_MAVEN_TOKEN='<deploy-token>'
#   export GITLAB_PROJECT_ID='11853'      # 不设则用下面默认值,请到 GitLab 项目 Settings→General 确认
#
# 用法:
#   ./publish-to-gitlab.sh 0.1.8

set -euo pipefail

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  echo "用法: ./publish-to-gitlab.sh <版本号>   例如: ./publish-to-gitlab.sh 0.1.9"
  exit 1
fi

: "${GITLAB_MAVEN_USER:?请先 export GITLAB_MAVEN_USER=<deploy-token 用户名>}"
: "${GITLAB_MAVEN_TOKEN:?请先 export GITLAB_MAVEN_TOKEN=<deploy-token>}"

PROJECT_ID="${GITLAB_PROJECT_ID:-11853}"
GITLAB_BASE="https://paas-gitlab.mychery.com"
REPO_URL="$GITLAB_BASE/api/v4/projects/$PROJECT_ID/packages/maven"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "==> 发布 qx-hybrid $VERSION 到 GitLab 项目 #$PROJECT_ID"
cd "$PROJECT_DIR"
./gradlew :qx_hybrid:publishReleasePublicationToGitLabRepository \
  -PQX_HYBRID_VERSION="$VERSION" \
  -PMAVEN_REPOSITORY_NAME=GitLab \
  -PMAVEN_REPOSITORY_URL="$REPO_URL" \
  -PMAVEN_USERNAME="$GITLAB_MAVEN_USER" \
  -PMAVEN_PASSWORD="$GITLAB_MAVEN_TOKEN" \
  --console=plain

echo ""
echo "✅ 已发布: com.energy.sdk:qx-hybrid:$VERSION"
echo "   包列表: $GITLAB_BASE/<group>/<project>/-/packages"
