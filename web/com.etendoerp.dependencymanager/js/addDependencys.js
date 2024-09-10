OB.ETDEP = OB.ETDEP || {};
OB.ETDEP.AddDependencys = OB.ETDEP.AddDependencys || {};

OB.ETDEP.AddDependencys.onRefreshDep = function(item,view, form, grid){
    var subDependencyField = item.parentElement.parentElement.getField("ETDEP_SubDepenendency");
    var visible = subDependencyField.visible;

    if(!visible){
        item.parentElement.parentElement.getField("ETDEP_SubDepenendency").show();
    }
    var dependencies = [];

    if (item.selection.data.allRows != null) {
        dependencies = item.selection.data.allRows;
    } else {
        dependencies = item.data.localData;
    }

    var dependenciesIdSelect = [];
    var hasSelection = false;

    for (var i = 0; i < dependencies.length; i++) {
        if (dependencies[i].obSelected) {
            dependenciesIdSelect.add(dependencies[i].id);
            hasSelection = true;
        }
    }

    if (!hasSelection) {
        subDependencyField.hide();
    }

    var grid = item.parentElement.parentElement.getField("ETDEP_SubDepenendency").selectionLayout.viewGrid.data;

    OB.RemoteCallManager.call("com.etendoerp.dependencymanager.process.AddSubDependency", {
                        dependencyId: dependenciesIdSelect }, {}, res => {let defaultCriteria = res.data.filter;
                                                                          grid.setCriteria(defaultCriteria);
                                                                          });
};
