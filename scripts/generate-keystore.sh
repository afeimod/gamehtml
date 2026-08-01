#!/usr/bin/env bash
#
# 生成 Release 签名 keystore 的便捷脚本
# 用法：./scripts/generate-keystore.sh
#
set -e

KEYSTORE_NAME=${1:-release.keystore}
ALIAS=${2:-game4399}
DNAME="CN=Game4399, OU=App, O=Game4399, L=Beijing, ST=Beijing, C=CN"

if [ -f "app/$KEYSTORE_NAME" ]; then
    echo "已存在 app/$KEYSTORE_NAME，如需重新生成请先删除"
    exit 1
fi

echo "正在生成 keystore: app/$KEYSTORE_NAME (alias=$ALIAS)"
echo "DNAME: $DNAME"
echo ""

keytool -genkey -v \
    -keystore "app/$KEYSTORE_NAME" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 \
    -validity 10000 \
    -dname "$DNAME"

echo ""
echo "✓ 生成成功"
echo ""
echo "下一步：复制 keystore.properties.example 为 keystore.properties 并填写密码"
echo "  cp app/keystore.properties.example app/keystore.properties"
echo ""
echo "注意：keystore.properties 和 *.keystore 已在 .gitignore 中"
