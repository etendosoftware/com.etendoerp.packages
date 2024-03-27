package com.etendoerp.dependencymanager.process;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.data.PackageVersion;

public class ChangeVersion extends BaseActionHandler {
  private static final Logger log = LogManager.getLogger();

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    JSONObject jsonContent;
    JSONObject responseMessage = new JSONObject();
    try {
      jsonContent = new JSONObject(content);
      JSONObject params = jsonContent.getJSONObject("_params");
      String dependencyId = jsonContent.getString("inpetdepDependencyId");
      String newVersion = params.getString("version");
      OBContext.setAdminMode(true);

      Dependency dependency = OBDal.getInstance().get(Dependency.class, dependencyId);
      if (dependency == null) {
        throw new JSONException("Dependency not found with ID: " + dependencyId);
      }

      log.debug("Changing version of dependency: {} to version: {}", dependency.getEntityName(), newVersion);
      dependency.setVersion(OBDal.getInstance().get(PackageVersion.class, newVersion).getVersion());

      OBDal.getInstance().save(dependency);
      OBDal.getInstance().flush();

      responseMessage.put("severity", "success");
      responseMessage.put("title", "Version Changed");
      responseMessage.put("message", "Dependency version successfully updated to " + newVersion);
    } catch (JSONException e) {
      log.error("Error processing JSON or updating dependency version", e);
      try {
        responseMessage.put("severity", "error");
        responseMessage.put("title", "Error");
        responseMessage.put("message", OBMessageUtils.messageBD("Error updating dependency version"));
      } catch (JSONException jsonException) {
        log.error("Error building error message", jsonException);
      }
    } finally {
      OBContext.restorePreviousMode();
    }

    return responseMessage;
  }
}