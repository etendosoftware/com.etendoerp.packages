package com.etendoerp.dependencymanager.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.data.FieldProvider;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.ComboTableData;
import org.openbravo.erpCommon.utility.SQLReturnObject;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.service.db.DalConnectionProvider;

public class ChangeFormatUtil {
  private static final String FORMAT_REFERENCE_ID = "02B96BF686064B44BB33C70C43AEFC05";

  private ChangeFormatUtil() {
  }

  public static List<String> getNewFormatList(String currentFormat, String validationRule, VariablesSecureApp vars,
      DalConnectionProvider conn) {
    FieldProvider[] fields = getNewFormatCombo(conn, vars, validationRule, currentFormat);

    List<String> actionList = new ArrayList<>();

    for (FieldProvider field : fields) {
      actionList.add(field.getField("ID"));
    }

    return actionList;
  }

  public static FieldProvider[] getNewFormatCombo(ConnectionProvider conn, VariablesSecureApp vars,
      String validationRule, String currentFormat) {
    FieldProvider[] comboOptions;

    try {
      ComboTableData comboTableData = new ComboTableData(vars, conn, "LIST", "newFormat",
          FORMAT_REFERENCE_ID, validationRule,
          Utility.getContext(conn, vars, "#AccessibleOrgTree", ChangeFormatUtil.class.getName()),
          Utility.getContext(conn, vars, "#User_Client", ChangeFormatUtil.class.getName()), 0);
      Utility.fillSQLParameters(conn, vars, null, comboTableData, ChangeFormatUtil.class.getName(), "");
      comboOptions = comboTableData.select(false);
    } catch (Exception e) {
      return new FieldProvider[0];
    }
    SQLReturnObject[] data = new SQLReturnObject[0];
    if (comboOptions != null) {
      ArrayList<SQLReturnObject> dataArray = new ArrayList<>();
      SQLReturnObject data1 = new SQLReturnObject();
      switch (currentFormat) {
        case DependencyUtil.FORMAT_LOCAL:
          data1.setData("ID", DependencyUtil.FORMAT_SOURCE);
          dataArray.add(data1);
          data1 = new SQLReturnObject();
          data1.setData("ID", DependencyUtil.FORMAT_JAR);
          dataArray.add(data1);
          break;
        case DependencyUtil.FORMAT_SOURCE:
          data1.setData("ID", DependencyUtil.FORMAT_JAR);
          dataArray.add(data1);
          break;
        case DependencyUtil.FORMAT_JAR:
          data1.setData("ID", DependencyUtil.FORMAT_SOURCE);
          dataArray.add(data1);
          break;
        default:
          return new FieldProvider[0];
      }

      data = new SQLReturnObject[dataArray.size()];

      int ind1 = 0;
      int ind2 = 0;
      while (ind1 < comboOptions.length && ind2 < dataArray.size()) {
        for (SQLReturnObject sqlro : dataArray) {
          if (sqlro.getField("ID").equals(comboOptions[ind1].getField("ID"))) {
            data[ind2] = sqlro;
            data[ind2].setData("NAME", comboOptions[ind1].getField("NAME"));
            data[ind2].setData("DESCRIPTION", comboOptions[ind1].getField("DESCRIPTION"));
            ind2++;
            break;
          }
        }
        ind1++;
      }

      // Exclude null values in the array
      data = Arrays.stream(data).filter(Objects::nonNull).toArray(SQLReturnObject[]::new);
    }
    return data;
  }
}
