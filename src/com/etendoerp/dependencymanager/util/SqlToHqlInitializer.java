package com.etendoerp.dependencymanager.util;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.dialect.function.SQLFunction;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.type.StandardBasicTypes;
import org.openbravo.dal.core.SQLFunctionRegister;

public class SqlToHqlInitializer implements SQLFunctionRegister {

  @Override
  public Map<String, SQLFunction> getSQLFunctions() {
    Map<String, SQLFunction> sqlFunctions = new HashMap<>();
    sqlFunctions.put("etdep_split_string",
        new StandardSQLFunction("etdep_split_string", StandardBasicTypes.BIG_DECIMAL));
    sqlFunctions.put("etdep_split_string1", new StandardSQLFunction("etdep_split_string1",
        StandardBasicTypes.BIG_DECIMAL));
    sqlFunctions.put("etdep_split_string2", new StandardSQLFunction("etdep_split_string2",
        StandardBasicTypes.BIG_DECIMAL));
    sqlFunctions.put("etdep_split_string3", new StandardSQLFunction("etdep_split_string3",
        StandardBasicTypes.BIG_DECIMAL));
    return sqlFunctions;
  }
}
