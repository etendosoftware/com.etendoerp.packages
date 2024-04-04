package com.etendoerp.dependencymanager.process;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.client.application.process.ResponseActionsBuilder;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.dependencymanager.actions.InstallDependency;
import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.data.PackageVersion;

public class ChangeVersion extends BaseProcessActionHandler {
  private static final Logger log = LogManager.getLogger();

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
        throw new JSONException("Dependency not found with ID: " + dependencyId);
      }

      PackageVersion newVersion = OBDal.getInstance().get(PackageVersion.class, newVersionId);
      if (newVersion == null) {
        throw new JSONException("PackageVersion not found with ID: " + newVersionId);
      }

      log.debug("Changing version of dependency: {} to version ID: {}", dependency.getEntityName(), newVersionId);
      String latestVersion = InstallDependency.fetchLatestVersion(dependency.getGroup(), dependency.getArtifact());

      dependency.setVersion(newVersion.getVersion());
      dependency.setInstallationStatus("PENDING");
      dependency.setVersionStatus(InstallDependency.determineVersionStatus(newVersion.getVersion(), latestVersion));

      OBDal.getInstance().save(dependency);
      OBDal.getInstance().flush();

      return getResponseBuilder()
          .refreshGrid()
          .build();
    } catch (JSONException e) {
      log.error("Error processing JSON or updating dependency version", e);
      return getResponseBuilder()
          .showMsgInView(ResponseActionsBuilder.MessageType.ERROR, "Error", OBMessageUtils.messageBD("ETDEP_Error_Updating_Dependency_Version"))
          .build();
    } finally {
      OBContext.restorePreviousMode();
    }
  }
}