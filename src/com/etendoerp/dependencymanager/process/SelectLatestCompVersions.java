package com.etendoerp.dependencymanager.process;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.util.PackageUtil;

public class SelectLatestCompVersions extends BaseActionHandler {
  private static void constructDependencyUpdateItem(StringBuilder message, String depName,
      JSONObject compatibilityInfo, boolean warning, String newVersion) throws JSONException {
    message.append("<li>")
        .append(depName)
        .append(" - ");
    if (warning) {
      message.append(String.format(OBMessageUtils.messageBD("ETDEP_Latest_Version_Incompatible"),
          compatibilityInfo.getString(PackageUtil.CORE_VERSION_RANGE), newVersion));
    } else {
      message.append(OBMessageUtils.messageBD("ETDEP_Updating_to_Latest"))
          .append(": ")
          .append(newVersion);
    }
    message.append("</li>");
  }

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    JSONObject jsonResponse = new JSONObject();
    String currentCore = "";
    try {
      JSONObject jsonContent = new JSONObject(content);
      JSONArray selectedRecords = jsonContent.getJSONArray("records");
      boolean warning = false;
      StringBuilder message = new StringBuilder(OBMessageUtils.messageBD("ETDEP_Dependency_Update_Info"));
      String msgWithNoUpdates = OBMessageUtils.messageBD("ETDEP_Dependency_Update_Info") + "<ul></ul>";
      message.append("<ul>");
      for (int i = 0; i < selectedRecords.length(); i++) {
        String group = ((JSONObject) selectedRecords.get(i)).getString("group");
        String artifact = ((JSONObject) selectedRecords.get(i)).getString("artifact");
        String currentVersion = ((JSONObject) selectedRecords.get(i)).getString("version");
        String depName = group + "." + artifact;

        OBCriteria<Package> depPkgCriteria = OBDal.getInstance().createCriteria(Package.class);
        depPkgCriteria.add(Restrictions.eq(Package.PROPERTY_GROUP, group));
        depPkgCriteria.add(Restrictions.eq(Package.PROPERTY_ARTIFACT, artifact));
        depPkgCriteria.setMaxResults(1);
        Package depPkg = (Package) depPkgCriteria.uniqueResult();
        String newVersion = PackageUtil.getCoreCompatibleOrLatestVersion(depPkg);
        JSONObject compatibilityInfo = PackageUtil.checkCoreCompatibility(depPkg, newVersion);
        if (StringUtils.isEmpty(currentCore)) {
          currentCore = compatibilityInfo.getString(PackageUtil.CURRENT_CORE_VERSION);
        }
        if (StringUtils.equals(currentVersion, newVersion)) {
          continue;
        }
        if (!compatibilityInfo.getBoolean(PackageUtil.IS_COMPATIBLE)) {
          warning = true;
        }
        constructDependencyUpdateItem(message, depName, compatibilityInfo, warning, newVersion);
      }
      message.append("</ul>");

      if (warning) {
        message.append("<br>")
            .append(String.format(OBMessageUtils.messageBD("ETDEP_Warning_Incompatible_Deps"), currentCore));
      }
      String strMessage = message.toString();
      if (!StringUtils.equals(strMessage, msgWithNoUpdates)) {
        jsonResponse.put("warning", warning);
        jsonResponse.put("message", strMessage);
      }
    } catch (JSONException e) {
      throw new OBException(e);
    }
    return jsonResponse;
  }
}
