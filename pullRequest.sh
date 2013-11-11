#!/bin/sh

OAUTH_TOKEN=$1
REPO_OWNER=$2
PROJECT_NAME=$3
TITLE=$(git log -1 --pretty=%B)
CURRENT_BRANCH=$(git symbolic-ref HEAD | sed -e 's,.*/\(.*\),\1,')

curl \
    -X POST \
    -H "Authorization: token $OAUTH_TOKEN" \
    -d@- \
    "https://api.github.com/repos/$REPO_OWNER/$PROJECT_NAME/pulls" <<EOF
{
  "title": "$TITLE",
  "head": "$CURRENT_BRANCH",
  "base": "master"
}
EOF
