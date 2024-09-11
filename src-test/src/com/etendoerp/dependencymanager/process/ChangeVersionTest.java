package com.etendoerp.dependencymanager.process;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.util.DependencyManagerConstants;
import com.etendoerp.dependencymanager.util.PackageUtil;

class ChangeVersionTest {
  @Test
  void buildDependencyInfoNewDependency() throws Exception {
    Map<String, PackageDependency> dependenciesCurrent = new HashMap<>();
    Map<String, PackageDependency> dependenciesUpdate = new HashMap<>();
    String key = DependencyManagerConstants.GROUP + ":" + DependencyManagerConstants.ARTIFACT;
    PackageDependency newDep = new PackageDependency();
    newDep.setVersion("2.0.0");
    dependenciesUpdate.put(key, newDep);

    ChangeVersion changeVersion = new ChangeVersion();

    JSONObject result = changeVersion.buildDependencyInfo(dependenciesCurrent, dependenciesUpdate, key);

    assertEquals(DependencyManagerConstants.GROUP, result.getString(DependencyManagerConstants.GROUP));
    assertEquals(DependencyManagerConstants.ARTIFACT, result.getString(DependencyManagerConstants.ARTIFACT));
    assertEquals("", result.getString(PackageUtil.VERSION_V1));
    assertEquals("2.0.0", result.getString(PackageUtil.VERSION_V2));
    assertEquals(PackageUtil.NEW_DEPENDENCY, result.getString(PackageUtil.STATUS));
  }
}