OB.ETDEP = OB.ETDEP || {};
OB.ETDEP.AddDependencys = OB.ETDEP.AddDependencys || {};

OB.ETDEP.AddDependencys.onRefreshDep = function(item,view){
    var subDependencyField = item.parentElement.parentElement.getField("ETDEP_SubDependency");
    var visible = subDependencyField.visible;

    if(!visible){
        item.parentElement.parentElement.getField("ETDEP_SubDependency").show();
    }
    var dependencies = [];

    if (item.selection.data.allRows != null) {
        dependencies = item.selection.data.allRows;
    } else {
        dependencies = item.data.localData;
    }

    var dependenciesIdSelect = [];
    var hasSelection = false;

    for (let dependency of dependencies) {
        if (dependency.obSelected) {
            dependenciesIdSelect.push(dependency.id);
            hasSelection = true;
        }
    }

    if (!hasSelection) {
        subDependencyField.hide();
    }

    var grid = item.parentElement.parentElement.getField("ETDEP_SubDependency").selectionLayout.viewGrid.data;

    OB.RemoteCallManager.call("com.etendoerp.dependencymanager.process.AddSubDependency", {
                        dependencyId: dependenciesIdSelect }, {}, res => {let defaultCriteria = res.data.filter;
                                                                          grid.setCriteria(defaultCriteria);
                                                                          });
};
