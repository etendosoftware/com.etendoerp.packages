OB.ETDEP = OB.ETDEP || {};
OB.ETDEP.ChangeVersion = {};

// Function to handle version changes for dependencies
OB.ETDEP.ChangeVersion.onChangeVersion = function(item, view, form, grid) {
    var selectedVersion = item.form.getValues().version$version;
    var currentVersion = view.parentWindow.view.viewGrid.getSelectedRecord().version;
    var depGroup = view.parentWindow.view.viewGrid.getSelectedRecord().group;
    var artifact = view.parentWindow.view.viewGrid.getSelectedRecord().artifact;

    // Perform a remote call to check and manage version changes
    OB.RemoteCallManager.call(
        'com.etendoerp.dependencymanager.process.SelectorChangeVersion',
        {
            depGroup: depGroup,
            artifact: artifact,
            updateToVersion: selectedVersion,
            currentVersion: currentVersion
        },
        null,
        function(response) {
            var data = response.data;
            var messages = []; // Messages to display
            var messageType = 'info'; // Default message type

            if (data.warning) {
                var currentCoreVersion = data.compatibilityDetails.currentCoreVersion;
                var coreVersionRange = data.compatibilityDetails.coreVersionRange;
                var message = OB.I18N.getLabel("ETDEP_Core_Incompatibility_Warning")
                message = message.replace("%s", coreVersionRange).replace("%s", currentCoreVersion);
                messageType = 'warning';
                messages.push("<li>" + message + "</li>");
            }

            // Determine if it's a version upgrade or downgrade
            var isUpgrade = compareVersions(selectedVersion, currentVersion) > 0;
            var versionChangeMessage = isUpgrade ?
                "<li>" + OB.I18N.getLabel("ETDEP_Version_Upgrade") + "</li>" :
                "<li>" + OB.I18N.getLabel("ETDEP_Version_Downgrade") + "</li>";

            // Add version change message to the list
            messages.push(versionChangeMessage);

            // Prepare messages for dependency changes
            if (data.comparison && data.comparison.length > 0) {
                messages.push("<li>" + OB.I18N.getLabel("ETDEP_Dependency_Changes") + "</li>");
            }

            // Categorize dependency messages by NEW or UPDATED
            var depsMessages = processDependencyMessages(data.comparison);
            if (depsMessages) {
                messages.push(depsMessages);
            }

            if (messages.length > 0) {
                var finalMessage = `<ul>${messages.join("")}</ul>`;
                view.messageBar.setMessage(messageType, messageType === 'warning' ? 'Warning' : 'Information', finalMessage);
            }
        }
    );
};

// Function to process dependency messages
function processDependencyMessages(comparisonData) {
    if (!comparisonData) return "";

    var depsMessages = { 'NEW': [], 'UPDATED': [] };
    comparisonData.forEach(function(dep) {
        var message = `<b>${dep.artifact}</b>`;
        switch (dep.status) {
            case '[New Dependency]':
                var newDependencyMessage = `${OB.I18N.getLabel("ETDEP_New_Dependency_With_Version")} ${dep.version_v2}`;
                message += ` - ${newDependencyMessage}`;
                depsMessages['NEW'].push(message);
                break;
            case '[Updated]':
                var updatedMessage = dep.version_v1 && dep.version_v2 ?
                    `${OB.I18N.getLabel("ETDEP_Change_From_Version")} ${dep.version_v1} ${OB.I18N.getLabel("ETDEP_To")} ${dep.version_v2}` :
                    `${OB.I18N.getLabel("ETDEP_Change_To_Version")} ${dep.version_v2}`;
                message += ` - ${updatedMessage}`;
                depsMessages['UPDATED'].push(message);
                break;
        }
    });

    if (depsMessages['NEW'].length > 0 || depsMessages['UPDATED'].length > 0) {
        var versionChangeMessage = "<ul>";
        ['NEW', 'UPDATED'].forEach(function(key) {
            if (depsMessages[key].length > 0) {
                var translatedLabel = OB.I18N.getLabel("ETDEP_" + key);
                var translatedKey = "<b><u>" + translatedLabel + "</u></b>";
                var joinedMessages = depsMessages[key].join("</li><li>");
                versionChangeMessage += "<li>" + translatedKey + "<ul><li>" + joinedMessages + "</li></ul></li>";
            }
        });
        versionChangeMessage += "</ul>";
        return versionChangeMessage;
    }

    return "";
}

// Function to compare two version strings
function compareVersions(version1, version2) {
    const v1 = version1.split('.').map(Number);
    const v2 = version2.split('.').map(Number);

    for (let i = 0; i < Math.max(v1.length, v2.length); i++) {
        const num1 = v1[i] || 0;
        const num2 = v2[i] || 0;

        if (num1 > num2) return 1; // version1 is greater
        if (num1 < num2) return -1; // version2 is greater
    }

    return 0; // versions are equal
}