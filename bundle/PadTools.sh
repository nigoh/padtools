#!/bin/sh

DIR=`dirname "$0"`

# 同梱 Graphviz dot バイナリを検索し、あれば PlantUML に渡す
GRAPHVIZ_PROP=""
OS=$(uname -s 2>/dev/null)
ARCH=$(uname -m 2>/dev/null)
case "$ARCH" in
    aarch64|arm64) ARCH_NORM="aarch64" ;;
    *)             ARCH_NORM="amd64" ;;
esac
case "$OS" in
    Darwin) PLATFORM="mac-$ARCH_NORM" ;;
    *)      PLATFORM="linux-$ARCH_NORM" ;;
esac
BUNDLED_DOT="$DIR/graphviz/$PLATFORM/dot"
if [ ! -x "$BUNDLED_DOT" ]; then
    BUNDLED_DOT="$DIR/graphviz/dot"
fi
if [ -x "$BUNDLED_DOT" ]; then
    GRAPHVIZ_PROP="-Dnet.sourceforge.plantuml.GRAPHVIZ_DOT=$BUNDLED_DOT"
fi

java $GRAPHVIZ_PROP -jar "$DIR/PadTools.jar" "$@"
