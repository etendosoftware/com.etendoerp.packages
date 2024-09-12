OB = OB || {};
OB.ETDEP = OB.ETDEP || {};
OB.ETDEP.AddDependency = OB.ETDEP.AddDependency || {};

OB.ETDEP.AddDependency.onLoad = function(view) {
    var isBundle = view.parentWindow.view.lastRecordSelected.isBundle;
    if (!isBundle) {
        view.theForm.getItem("grid").setCanEdit(false);
    }

    view.theForm.getItem("ETDEP_SubDependency").hide();

    var packageVersionId = view.buttonOwnerView.lastRecordSelected.id;
    var onLoadCallback = function(response, data, request) {
                    if (data && !data.isCompatible) {
                        let currentCoreVersion = data.currentCoreVersion;
                        let coreVersionRange = data.coreVersionRange;
                        let message = OB.I18N.getLabel("ETDEP_Core_Incompatible")
                        message = message.replace("%s", coreVersionRange).replace("%s", currentCoreVersion);
                        view.messageBar.setMessage(isc.OBMessageBar.TYPE_WARNING, null, message);
                    }
                };
    OB.RemoteCallManager.call("com.etendoerp.dependencymanager.process.CheckCoreDependency", {
                    packageVersionId: packageVersionId }, {}, onLoadCallback);
};
