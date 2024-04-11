package com.etendoerp.dependencymanager.filterexpression;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.application.FilterExpression;

import com.etendoerp.dependencymanager.util.DependencyUtil;

public class ChangeFormatDefaultValuesExpression implements FilterExpression {
  @Override
  public String getExpression(Map<String, String> requestMap) {
    String currentParam = requestMap.get("currentParam");
    try {
      JSONObject context = new JSONObject(requestMap.get("context"));
      if (currentParam != null && StringUtils.equals(currentParam, "newFormat")) {
        switch (context.getString("inpformat")) {
          case DependencyUtil.FORMAT_SOURCE:
            return DependencyUtil.FORMAT_JAR;
          case DependencyUtil.FORMAT_JAR:
            return DependencyUtil.FORMAT_SOURCE;
          case DependencyUtil.FORMAT_LOCAL:
          default:
            break;
        }
      }
    } catch (JSONException e) {
      throw new OBException(e);
    }
    return null;
  }
}
