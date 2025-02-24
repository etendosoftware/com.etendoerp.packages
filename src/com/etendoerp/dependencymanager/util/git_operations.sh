#!/bin/bash

source /opt/EtendoERP/modules/com.etendoerp.dependencymanager/.env

function pushAndCommit(){

    # Set the target directory within the repository
    TARGET_DIR="/opt/EtendoERP/modules/com.etendoerp.dependencymanager/"
    cd "$TARGET_DIR" || { echo "Error: Could not change to directory $TARGET_DIR."; exit 1; }

    # Configure Git user and email address
    git config --local user.name "$GITHUB_USER"
    git config --local user.email "$GITHUB_MAIL"

    # Switch to the 'main' branch and perform a pull
    git checkout main > /dev/null 2>&1 || { echo "Error: Could not checkout to branch 'main'."; exit 1; }
    git pull > /dev/null 2>&1 || { echo "Error: Could not perform pull."; exit 1; }

    # Check if there are changes in the specified file
    if git diff --quiet HEAD -- referencedata/standard/Packages_dataset.xml; then
        echo "No changes in the specified file to commit or push."
    else
        echo "There are changes in the specified file. Proceeding with commit and push."
        git add referencedata/standard/Packages_dataset.xml || { echo "Error: Could not add the file to commit."; exit 1; }
        git commit -m "Update packages dataset :package:" || { echo "Error: Could not commit."; exit 1; }
        git push || { echo "Error: Could not push."; exit 1; }
    fi
}

pushAndCommit
