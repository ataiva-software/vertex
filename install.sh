#!/bin/bash

# Vertex DevOps Suite Installation Script
# Usage: curl -fsSL https://raw.githubusercontent.com/ataiva-software/vertex/main/install.sh | bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
REPO="ataiva-software/vertex"
BINARY_NAME="vertex"
INSTALL_DIR="/usr/local/bin"

# Print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Detect OS and architecture
detect_platform() {
    local os arch
    
    # Detect OS
    case "$(uname -s)" in
        Linux*)     os="linux" ;;
        Darwin*)    os="darwin" ;;
        CYGWIN*|MINGW*|MSYS*) os="windows" ;;
        *)          print_error "Unsupported operating system: $(uname -s)"; exit 1 ;;
    esac
    
    # Detect architecture
    case "$(uname -m)" in
        x86_64|amd64)   arch="amd64" ;;
        arm64|aarch64)  arch="arm64" ;;
        *)              print_error "Unsupported architecture: $(uname -m)"; exit 1 ;;
    esac
    
    echo "${os}-${arch}"
}

# Get latest release version
get_latest_version() {
    print_status "Fetching latest release information..."
    
    local latest_url="https://api.github.com/repos/${REPO}/releases/latest"
    local version
    
    if command -v curl >/dev/null 2>&1; then
        version=$(curl -fsSL "$latest_url" | grep '"tag_name":' | sed -E 's/.*"tag_name": "([^"]+)".*/\1/')
    elif command -v wget >/dev/null 2>&1; then
        version=$(wget -qO- "$latest_url" | grep '"tag_name":' | sed -E 's/.*"tag_name": "([^"]+)".*/\1/')
    else
        print_error "Neither curl nor wget is available. Please install one of them."
        exit 1
    fi
    
    if [ -z "$version" ]; then
        print_error "Failed to get latest version"
        exit 1
    fi
    
    echo "$version"
}

# Download and install binary
install_vertex() {
    local platform="$1"
    local version="$2"
    local download_url="https://github.com/${REPO}/releases/download/${version}/${BINARY_NAME}-${platform}"
    local temp_file="/tmp/${BINARY_NAME}"
    
    print_status "Downloading Vertex ${version} for ${platform}..."
    
    # Download binary
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$download_url" -o "$temp_file"
    elif command -v wget >/dev/null 2>&1; then
        wget -q "$download_url" -O "$temp_file"
    else
        print_error "Neither curl nor wget is available"
        exit 1
    fi
    
    # Make executable
    chmod +x "$temp_file"
    
    # Install to system
    print_status "Installing to ${INSTALL_DIR}..."
    
    if [ -w "$INSTALL_DIR" ]; then
        mv "$temp_file" "${INSTALL_DIR}/${BINARY_NAME}"
    else
        print_status "Requesting sudo privileges to install to ${INSTALL_DIR}..."
        sudo mv "$temp_file" "${INSTALL_DIR}/${BINARY_NAME}"
    fi
    
    print_success "Vertex installed successfully!"
}

# Verify installation
verify_installation() {
    print_status "Verifying installation..."
    
    if command -v vertex >/dev/null 2>&1; then
        local installed_version
        installed_version=$(vertex --version 2>/dev/null | head -n1 || echo "unknown")
        print_success "Vertex is installed: ${installed_version}"
        return 0
    else
        print_error "Vertex installation failed - binary not found in PATH"
        return 1
    fi
}

# Show next steps
show_next_steps() {
    echo
    print_success "🎉 Vertex DevOps Suite installed successfully!"
    echo
    echo -e "${BLUE}Next steps:${NC}"
    echo "1. Set your master password:"
    echo "   ${YELLOW}export VERTEX_MASTER_PASSWORD=\"your-secure-password\"${NC}"
    echo
    echo "2. Start dependencies (PostgreSQL & Redis):"
    echo "   ${YELLOW}docker run -d --name postgres -e POSTGRES_PASSWORD=secret -p 5432:5432 postgres:15${NC}"
    echo "   ${YELLOW}docker run -d --name redis -p 6379:6379 redis:7${NC}"
    echo
    echo "3. Start all Vertex services:"
    echo "   ${YELLOW}vertex server${NC}"
    echo
    echo "4. Access the web portal:"
    echo "   ${YELLOW}http://localhost:8000${NC}"
    echo
    echo "5. Try CLI commands:"
    echo "   ${YELLOW}vertex status${NC}"
    echo "   ${YELLOW}vertex vault store my-secret \"hello world\"${NC}"
    echo "   ${YELLOW}vertex vault list --format yaml${NC}"
    echo
    echo -e "${BLUE}Documentation:${NC} https://github.com/ataiva-software/vertex/tree/main/docs"
    echo -e "${BLUE}Support:${NC} https://github.com/ataiva-software/vertex/issues"
}

# Main installation flow
main() {
    echo -e "${BLUE}"
    echo "╭─────────────────────────────────────────────────────────────╮"
    echo "│                 Vertex DevOps Suite Installer              │"
    echo "│                                                             │"
    echo "│  Revolutionary Single-Binary DevOps Platform               │"
    echo "│  🔐 Secrets • 🔄 Workflows • ⚡ Tasks • 📊 Monitoring      │"
    echo "╰─────────────────────────────────────────────────────────────╯"
    echo -e "${NC}"
    
    # Check if already installed
    if command -v vertex >/dev/null 2>&1; then
        local current_version
        current_version=$(vertex --version 2>/dev/null | head -n1 || echo "unknown")
        print_warning "Vertex is already installed: ${current_version}"
        echo -n "Do you want to reinstall? [y/N] "
        read -r response
        case "$response" in
            [yY][eS]|[yY]) 
                print_status "Proceeding with reinstallation..."
                ;;
            *)
                print_status "Installation cancelled."
                exit 0
                ;;
        esac
    fi
    
    # Detect platform
    local platform
    platform=$(detect_platform)
    print_status "Detected platform: ${platform}"
    
    # Get latest version
    local version
    version=$(get_latest_version)
    print_status "Latest version: ${version}"
    
    # Install
    install_vertex "$platform" "$version"
    
    # Verify
    if verify_installation; then
        show_next_steps
    else
        exit 1
    fi
}

# Run main function
main "$@"
