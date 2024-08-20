package com.etendoerp.dependencymanager.datasource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.service.datasource.ReadOnlyDataSourceService;

import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.DependencyTreeBuilder;

public class AddDependecyDS extends ReadOnlyDataSourceService {

  @Override
  protected int getCount(Map<String, String> parameters) {
    return getData(parameters, 0, Integer.MAX_VALUE).size();
  }

  @Override
  protected List<Map<String, Object>> getData(Map<String, String> parameters, int startRow, int endRow) {
    final List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    try {
      final String strETDEPPackageVersionId = parameters.get("@ETDEP_Package_Version.id@");
      final PackageVersion packageVersion = OBDal.getInstance().get(PackageVersion.class, strETDEPPackageVersionId);
      List<PackageDependency> dependencyList = DependencyTreeBuilder.createDependencyTree(packageVersion);
      for (PackageDependency dependency : dependencyList) {
        Map<String, Object> map = new HashMap<>();
        map.put("group", dependency.getGroup());
        map.put("artifact", dependency.getArtifact());
        map.put("version", dependency.getVersion());
        result.add(map);
      }
    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }
    return result;
  }
}