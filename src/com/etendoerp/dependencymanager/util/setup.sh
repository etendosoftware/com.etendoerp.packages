#!/bin/bash

source /opt/EtendoERP/modules/com.etendoerp.dependencymanager/.env

function setup(){

    # Configure Git user and email address
    git config user.name $GITHUB_USER
    git config user.email $GITHUB_MAIL

    echo "Function completed successfully."
}

setup
