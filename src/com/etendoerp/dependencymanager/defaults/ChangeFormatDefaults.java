package com.etendoerp.dependencymanager.defaults;

import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.service.db.DalConnectionProvider;

import com.etendoerp.dependencymanager.util.ChangeFormatUtil;

public class ChangeFormatDefaults extends BaseActionHandler {
  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    final var result = new JSONObject();
    final var jsonNewFormats = new JSONArray();

    try {
      JSONObject jsonData = new JSONObject(content);
      String currentFormat = jsonData.getString("currentFormat");
      VariablesSecureApp vars = RequestContext.get().getVariablesSecureApp();
      DalConnectionProvider conn = new DalConnectionProvider();
      List<String> newFormatList = ChangeFormatUtil.getNewFormatList(currentFormat, "", vars, conn);
      newFormatList.forEach(jsonNewFormats::put);

      result.put("newFormats", jsonNewFormats);
    } catch (Exception e) {
      throw new OBException(e);
    }

    return result;
  }
}
