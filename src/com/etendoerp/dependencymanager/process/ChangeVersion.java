package com.etendoerp.dependencymanager.process;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.client.application.process.ResponseActionsBuilder;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.dependencymanager.actions.InstallDependency;
import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.PackageUtil;

public class ChangeVersion extends BaseProcessActionHandler {
  private static final Logger log = LogManager.getLogger();
  SelectorChangeVersion selector = new SelectorChangeVersion();

  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {
    JSONObject jsonContent;
    try {
      jsonContent = new JSONObject(content);
      JSONObject params = jsonContent.getJSONObject("_params");
      String dependencyId = jsonContent.getString("inpetdepDependencyId");
      String newVersionId = params.getString("version");
      OBContext.setAdminMode(true);

      Dependency dependency = OBDal.getInstance().get(Dependency.class, dependencyId);
      if (dependency == null) {
        throw new JSONException(OBMessageUtils.messageBD("ETDEP_Package_Version_Not_Found_ID") + dependencyId);
      }

      PackageVersion newVersion = OBDal.getInstance().get(PackageVersion.class, newVersionId);
      if (newVersion == null) {
        throw new JSONException(OBMessageUtils.messageBD("ETDEP_Dependency_Not_Found_ID") + newVersionId);
      }

      log.debug("Changing version of dependency: {} to version ID: {}", dependency.getEntityName(), newVersionId);
      String currentVersion = dependency.getVersion();
      String updateToVersion = newVersion.getVersion();
      String latestVersion = InstallDependency.fetchLatestVersion(dependency.getGroup(), dependency.getArtifact());

      String fromCore = newVersion.getFromCore();
      String latestCore = newVersion.getLatestCore();

      if (fromCore == null || latestCore == null) {
        throw new OBException(OBMessageUtils.messageBD("ETDEP_Invalid_Core_Versions") + newVersionId);
      }

      if (!isCoreVersionCompatible(fromCore, latestCore)) {
        throw new OBException(OBMessageUtils.messageBD("ETDEP_Core_Versions_Incompatible") + newVersionId);
      }

      dependency.setVersion(updateToVersion);
      dependency.setInstallationStatus("PENDING");
      dependency.setVersionStatus(InstallDependency.determineVersionStatus(updateToVersion, latestVersion));

      JSONArray dependenciesComparisonResults = compareDependenciesForChange(dependency.getGroup(),
          dependency.getArtifact(), currentVersion, updateToVersion);

      processDependencyChanges(dependenciesComparisonResults);

      OBDal.getInstance().save(dependency);
      OBDal.getInstance().flush();

      return getResponseBuilder()
          .refreshGrid()
          .build();
    } catch (JSONException e) {
      log.error("Error processing JSON or updating dependency version", e);
      return getResponseBuilder()
          .showMsgInView(ResponseActionsBuilder.MessageType.ERROR, "Error",
              OBMessageUtils.messageBD("ETDEP_Error_Updating_Dependency_Version"))
          .build();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private boolean isCoreVersionCompatible(String fromCore, String latestCore) {
    PackageVersion currentCoreVersion = getCurrentCoreVersion();
    if (currentCoreVersion == null) {
      return false;
    }

    String currentVersion = currentCoreVersion.getVersion();
    return compareVersions(currentVersion, fromCore) >= 0 && compareVersions(currentVersion, latestCore) <= 0;
  }

  private PackageVersion getCurrentCoreVersion() {
      OBCriteria<PackageVersion> packageVersionCriteria = OBDal.getInstance().createCriteria(PackageVersion.class);
      packageVersionCriteria.add(Restrictions.eq(PackageVersion.PROPERTY_PACKAGE, Boolean.TRUE))
                            .addOrder(Order.desc(PackageVersion.PROPERTY_CREATIONDATE));
      return (PackageVersion) packageVersionCriteria.uniqueResult();
  }

  private int compareVersions(String version1, String version2) {
    String[] v1 = version1.split("\\.");
    String[] v2 = version2.split("\\.");

    for (int i = 0; i < Math.max(v1.length, v2.length); i++) {
      int num1 = i < v1.length ? Integer.parseInt(v1[i]) : 0;
      int num2 = i < v2.length ? Integer.parseInt(v2[i]) : 0;
      if (num1 != num2) {
        return num1 - num2;
      }
    }
    return 0;
  }

  private JSONArray compareDependenciesForChange(String depGroup, String artifact, String currentVersion, String updateToVersion) throws JSONException {
    JSONArray result = new JSONArray();

    Package depPackage = selector.fetchPackageByGroupAndArtifact(depGroup, artifact);
    if (depPackage == null) {
      throw new OBException(OBMessageUtils.messageBD("ETDEP_Package_Not_Found") + depGroup + "." + artifact);
    }

    Map<String, PackageDependency> dependenciesCurrent = selector.getDependenciesMap(depPackage, currentVersion);
    Map<String, PackageDependency> dependenciesUpdate = selector.getDependenciesMap(depPackage, updateToVersion);

    Set<String> allKeys = new HashSet<>();
    allKeys.addAll(dependenciesCurrent.keySet());
    allKeys.addAll(dependenciesUpdate.keySet());

    for (String key : allKeys) {
      if (StringUtils.contains(key, PackageUtil.ETENDO_CORE)) continue;
      result.put(buildDependencyInfo(dependenciesCurrent, dependenciesUpdate, key));
    }

    return result;
  }

  public JSONObject buildDependencyInfo(Map<String, PackageDependency> dependenciesCurrent, Map<String, PackageDependency> dependenciesUpdate, String key) throws JSONException {
    JSONObject dependencyInfo = new JSONObject();
    PackageDependency depV1 = dependenciesCurrent.get(key);
    PackageDependency depV2 = dependenciesUpdate.get(key);

    String[] parts = key.split(":");
    dependencyInfo.put(PackageUtil.GROUP, parts[0]);
    dependencyInfo.put(PackageUtil.ARTIFACT, parts[1]);
    dependencyInfo.put(PackageUtil.VERSION_V1, depV1 != null ? depV1.getVersion() : "");
    dependencyInfo.put(PackageUtil.VERSION_V2, depV2 != null ? depV2.getVersion() : "");

    if (depV1 == null && depV2 != null) {
      dependencyInfo.put(PackageUtil.STATUS, PackageUtil.NEW_DEPENDENCY);
    } else if (depV1 != null && depV2 != null && !StringUtils.equals(depV1.getVersion(), depV2.getVersion())) {
      dependencyInfo.put(PackageUtil.STATUS, PackageUtil.UPDATED);
    }

    return dependencyInfo;
  }

  private void processDependencyChanges(JSONArray dependenciesComparisonResults) {
    try {
      for (int i = 0; i < dependenciesComparisonResults.length(); i++) {
        JSONObject dependencyInfo = dependenciesComparisonResults.getJSONObject(i);
        String group = dependencyInfo.getString(PackageUtil.GROUP);
        String artifact = dependencyInfo.getString(PackageUtil.ARTIFACT);
        String status = dependencyInfo.optString(PackageUtil.STATUS, "");

        if (StringUtils.equals(PackageUtil.NEW_DEPENDENCY, status) || StringUtils.equals(PackageUtil.UPDATED, status)) {
          String version = dependencyInfo.getString("version_v2");
          PackageUtil.updateOrCreateDependency(group, artifact, version);
        }
      }
    } catch (JSONException e) {
      log.debug("Error processing dependencies comparison results", e);
    }
  }
}
