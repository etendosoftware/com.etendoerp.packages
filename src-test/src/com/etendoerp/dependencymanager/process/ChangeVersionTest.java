package com.etendoerp.dependencymanager.process;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.util.PackageUtil;

class ChangeVersionTest {
  @Test
  void buildDependencyInfoNewDependency() throws Exception {
    Map<String, PackageDependency> dependenciesCurrent = new HashMap<>();
    Map<String, PackageDependency> dependenciesUpdate = new HashMap<>();
    String key = PackageUtil.GROUP + ":" + PackageUtil.ARTIFACT;
    PackageDependency newDep = new PackageDependency();
    newDep.setVersion("2.0.0");
    dependenciesUpdate.put(key, newDep);

    ChangeVersion changeVersion = new ChangeVersion();

    JSONObject result = changeVersion.buildDependencyInfo(dependenciesCurrent, dependenciesUpdate, key);

    assertEquals(PackageUtil.GROUP, result.getString(PackageUtil.GROUP));
    assertEquals(PackageUtil.ARTIFACT, result.getString(PackageUtil.ARTIFACT));
    assertEquals("", result.getString(PackageUtil.VERSION_V1));
    assertEquals("2.0.0", result.getString(PackageUtil.VERSION_V2));
    assertEquals(PackageUtil.NEW_DEPENDENCY, result.getString(PackageUtil.STATUS));
  }
}