package com.etendoerp.dependencymanager.actions;

import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import java.util.List;

public class InstallDependency extends Action {
  private static final String PLATFORM_GROUP = "com.etendoerp.platform";
  private static final String CORE_ARTIFACT = "etendo-core";

  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    try {
      List<PackageVersion> packageVersions = getInputContents(getInputClass());
      for (PackageVersion version : packageVersions) {
        processPackageVersion(version);
      }
      return buildSuccessResult();
    } catch (Exception e) {
      return buildErrorResult(e);
    }
  }

  private void processPackageVersion(PackageVersion version) {
    updateOrCreateDependency(version.getPackage().getGroup(),
        version.getPackage().getArtifact(), version.getVersion());

    for (PackageDependency dependency : version.getETDEPPackageDependencyList()) {
      if (shouldSkipDependency(dependency)) {
        continue;
      }
      updateOrCreateDependency(dependency.getGroup(), dependency.getArtifact(),
          dependency.getVersion());
    }
  }

  private boolean shouldSkipDependency(PackageDependency dependency) {
    return StringUtils.equals(dependency.getGroup(), PLATFORM_GROUP) && StringUtils.equals(
        dependency.getArtifact(), CORE_ARTIFACT);
  }

  private void updateOrCreateDependency(String group, String artifact, String version) {
    Dependency existingDependency = OBDal.getInstance()
        .createQuery(Dependency.class, "as pv where pv.group = :group and pv.artifact = :artifact")
        .setNamedParameter("group", group)
        .setNamedParameter("artifact", artifact)
        .uniqueResult();

    if (existingDependency != null) {
      existingDependency.setVersion(version);
    } else {
      Dependency newDependency = new Dependency();
      newDependency.setGroup(group);
      newDependency.setArtifact(artifact);
      newDependency.setVersion(version);
      existingDependency = newDependency;
    }
    OBDal.getInstance().save(existingDependency);
  }

  private ActionResult buildSuccessResult() {
    ActionResult result = new ActionResult();
    result.setType(Result.Type.SUCCESS);
    result.setMessage(OBMessageUtils.getI18NMessage("Success"));
    return result;
  }

  private ActionResult buildErrorResult(Exception e) {
    // Log the exception e
    ActionResult result = new ActionResult();
    result.setType(Result.Type.ERROR);
    result.setMessage(OBMessageUtils.getI18NMessage("Error: " + e.getMessage()));
    return result;
  }

  @Override
  protected Class<PackageVersion> getInputClass() {
    return PackageVersion.class;
  }
}
