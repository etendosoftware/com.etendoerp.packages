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

            // Check for core compatibility warning
            if (data.warning) {
                messageType = 'warning';
                messages.push("<li>" + OB.I18N.getLabel("ETDEP_Core_Incompatibility_Warning") + "</li>");
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

            // Categorize dependency messages by NEW, UPDATED, DELETED
            var depsMessages = { 'NEW': [], 'UPDATED': [], 'DELETED': [] };
            if (data.comparison) {
                data.comparison.forEach(function(dep) {
                    var message = `<b>${dep.artifact}</b>`;
                    switch (dep.status) {
                        case '[Deleted]':
                            // Handle deleted dependencies
                            depsMessages['DELETED'].push(message);
                            break;
                        case '[New Dependency]':
                            // Handle new dependencies with version info
                            var newDependencyMessage = OB.I18N.getLabel("ETDEP_New_Dependency_To_Version") + " " + dep.version_v2;
                            message += ` - ${newDependencyMessage}`;
                            depsMessages['NEW'].push(message);
                            break;
                        case '[Updated]':
                            // Handle updated dependencies with version info
                            var updatedMessage = OB.I18N.getLabel("ETDEP_Updated_To_Version") + " " + dep.version_v2;
                            message += ` - ${updatedMessage}`
                            depsMessages['UPDATED'].push(message);
                            break;
                    }
                });
            }

            // Check for any changes to include an intro message
            if (depsMessages['NEW'].length > 0 || depsMessages['UPDATED'].length > 0 || depsMessages['DELETED'].length > 0) {
                versionChangeMessage = `<ul>`;

                // Add each type of change as a subsection
                ['NEW', 'UPDATED', 'DELETED'].forEach(function(key) {
                    if (depsMessages[key].length > 0) {
                        // Use internationalization labels and bold for section titles
                        var translatedKey = `<b>${OB.I18N.getLabel(`ETDEP_${key}`)}</b>`;
                        versionChangeMessage += `<li>${translatedKey}<ul><li>${depsMessages[key].join("</li><li>")}</li></ul></li>`;
                    }
                });

                versionChangeMessage += `</ul>`;
                messages.push(versionChangeMessage);
            }

            // If there are messages, display them on the message bar
            if (messages.length > 0) {
                var finalMessage = `<ul>${messages.join("")}</ul>`;
                view.messageBar.setMessage(messageType, messageType === 'warning' ? 'Warning' : 'Information', finalMessage);
            }
        }
    );
};

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