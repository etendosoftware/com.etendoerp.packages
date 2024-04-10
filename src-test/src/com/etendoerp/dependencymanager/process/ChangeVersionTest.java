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
    String key = "group:artifact";
    PackageDependency newDep = new PackageDependency();
    newDep.setVersion("2.0.0");
    dependenciesUpdate.put(key, newDep);

    ChangeVersion changeVersion = new ChangeVersion();

    JSONObject result = changeVersion.buildDependencyInfo(dependenciesCurrent, dependenciesUpdate, key);

    assertEquals("group", result.getString("group"));
    assertEquals("artifact", result.getString("artifact"));
    assertEquals("", result.getString("version_v1"));
    assertEquals("2.0.0", result.getString("version_v2"));
    assertEquals(PackageUtil.NEW_DEPENDENCY, result.getString(PackageUtil.STATUS));
  }
}