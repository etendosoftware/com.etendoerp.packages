package com.etendoerp.dependencymanager.process;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.service.db.DbUtility;


public class UpdatePagackesManual extends BaseProcessActionHandler {
  private static final Logger log = LogManager.getLogger();


  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String data) {
    try {
      new GetPackagesFromRepositories().doExecute(null);
      // Success Message
      return getSuccessMessage(OBMessageUtils.messageBD("ProcessOK"));

    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      log.error("Error when updating packages", e);

      try {
        Throwable ex = DbUtility.getUnderlyingSQLException(e);
        String message = OBMessageUtils.translateError(ex.getMessage()).getMessage();
        return getErrorMessage(message);
      } catch (Exception ignore) {
        log.error("Error when updating packages", ignore);
      }
    }

    return new JSONObject();
  }


  /**
   * Returns a JSONObject with the success message to be printed
   */
  private static JSONObject getSuccessMessage(final String msgText) {
    final JSONObject result = new JSONObject();
    try {
      final JSONArray actions = new JSONArray();
      final JSONObject msgInBPTab = new JSONObject();
      msgInBPTab.put("msgType", "success");
      msgInBPTab.put("msgTitle", OBMessageUtils.messageBD("success"));
      msgInBPTab.put("msgText", msgText);
      final JSONObject msgInBPTabAction = new JSONObject();
      msgInBPTabAction.put("showMsgInProcessView", msgInBPTab);
      actions.put(msgInBPTabAction);
      actions.put(new JSONObject().put("refreshGrid", new JSONObject()));
      result.put("responseActions", actions);
    } catch (Exception e) {
      log.error(e);
    }

    return result;
  }

  /**
   * Returns a JSONObject with the error message to be printed and retry execution
   */
  private static JSONObject getErrorMessage(final String msgText) {
    final JSONObject result = new JSONObject();
    try {
      final JSONObject msg = new JSONObject();
      msg.put("severity", "error");
      msg.put("text", msgText);
      result.put("message", msg);
      result.put("retryExecution", true);
    } catch (Exception e) {
      log.error(e);
    }
    return result;
  }

}