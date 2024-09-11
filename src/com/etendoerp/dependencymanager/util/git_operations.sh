#!/bin/bash

function pushAndCommit(){

    # Get the root directory of the Git repository
    REPO_DIR=$(git rev-parse --show-toplevel 2>/dev/null)

    if [ $? -ne 0 ]; then
        echo "Error: You are not in a Git repository. Check your location."
        exit 1
    fi

    # Set the target directory within the repository
    TARGET_DIR="$REPO_DIR/modules/com.etendoerp.dependencymanager/"
    cd "$TARGET_DIR" || { echo "Error: Could not change to directory $TARGET_DIR."; exit 1; }

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
