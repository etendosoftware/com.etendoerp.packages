#!/bin/bash

source /opt/EtendoERP/modules/com.etendoerp.dependencymanager/.env

function setup(){
    # Check if it is in a Git repository before configuring
    REPO_DIR=$(git rev-parse --show-toplevel 2>/dev/null)

    if [ $? -ne 0 ]; then
        echo "Error: You are not in a Git repository. Check your location."
        exit 1
    fi

    # Navigate to the repository directory
    cd "$REPO_DIR" || { echo "Error: Could not change to directory $REPO_DIR."; exit 1; }

    # Configure Git user and email address
    git config user.name $GITHUB_USER
    git config user.email $GITHUB_MAIL

    echo "Function completed successfully."
}

setup
