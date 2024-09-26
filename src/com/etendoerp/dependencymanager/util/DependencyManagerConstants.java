package com.etendoerp.dependencymanager.util;

import org.openbravo.erpCommon.utility.OBMessageUtils;

public class DependencyManagerConstants {

  public static final String ARTIFACT = "artifact";
  public static final String GROUP = "group";
  public static final String VERSION = "version";
  public static final String ID = "id";
  public static final String PARENT = "parent";
  public static final String CRITERIA = "criteria";
  public static final String CONSTRUCTOR = "_constructor";
  public static final String VALUE = "value";
  public static final String LINE = "line";
  public static final String FIELD_NAME = "fieldName";
  public static final String ADVANCED_CRITERIA = "AdvancedCriteria";
  public static final String SORT_BY = "_sortBy";

  private DependencyManagerConstants() {
    throw new UnsupportedOperationException(OBMessageUtils.messageBD("ETDEP_Utility-Class-Cannot-Instantiate"));
  }
}
