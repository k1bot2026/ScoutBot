#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# CoxScout Installer for macOS / Linux
# Clones the repo, builds the JAR, and installs to DreamBot.
# ============================================================

REPO_URL="https://github.com/k1bot2026/ScoutBot.git"
CLONE_DIR="$(mktemp -d)"
DREAMBOT_SCRIPTS="$HOME/DreamBot/Scripts"

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[+]${NC} $1"; }
warn()  { echo -e "${YELLOW}[!]${NC} $1"; }
error() { echo -e "${RED}[x]${NC} $1"; }

cleanup() {
    if [ -d "$CLONE_DIR" ]; then
        rm -rf "$CLONE_DIR"
    fi
}
trap cleanup EXIT

# --- Check Git ---
info "Checking for Git..."
if ! command -v git &>/dev/null; then
    error "Git is not installed."
    echo ""
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "  Install with Homebrew:"
        echo "    brew install git"
    else
        echo "  Install with your package manager, e.g.:"
        echo "    sudo apt install git       # Debian/Ubuntu"
        echo "    sudo dnf install git       # Fedora"
    fi
    exit 1
fi
info "Git found: $(git --version)"

# --- Check Java 11+ ---
info "Checking for Java 11+..."
if ! command -v java &>/dev/null; then
    error "Java is not installed."
    echo ""
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "  Install with Homebrew:"
        echo "    brew install openjdk@11"
    else
        echo "  Install with your package manager, e.g.:"
        echo "    sudo apt install openjdk-11-jdk    # Debian/Ubuntu"
        echo "    sudo dnf install java-11-openjdk   # Fedora"
    fi
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/' | cut -d. -f1)
if [ "$JAVA_VER" -lt 11 ] 2>/dev/null; then
    error "Java 11+ is required, but found Java $JAVA_VER."
    exit 1
fi
info "Java found: $(java -version 2>&1 | head -1)"

# --- Check DreamBot ---
info "Checking for DreamBot..."
if [ ! -d "$DREAMBOT_SCRIPTS" ]; then
    error "DreamBot Scripts folder not found at: $DREAMBOT_SCRIPTS"
    echo ""
    echo "  Make sure DreamBot is installed and has been run at least once."
    exit 1
fi
info "DreamBot Scripts folder found."

# --- Clone repo ---
info "Cloning repository..."
git clone --depth 1 "$REPO_URL" "$CLONE_DIR"

# --- Build ---
info "Building CoxScout..."
cd "$CLONE_DIR"
chmod +x gradlew
./gradlew :scripts:coxscout:jar --no-daemon -q

# --- Find and copy JAR ---
JAR_FILE="$CLONE_DIR/scripts/coxscout/build/libs/CoxScout.jar"
if [ ! -f "$JAR_FILE" ]; then
    error "Build failed — CoxScout.jar not found."
    exit 1
fi

cp "$JAR_FILE" "$DREAMBOT_SCRIPTS/CoxScout.jar"

echo ""
info "CoxScout installed successfully!"
echo "  JAR location: $DREAMBOT_SCRIPTS/CoxScout.jar"
echo "  Open DreamBot and select 'COX Scout' from the script list."
