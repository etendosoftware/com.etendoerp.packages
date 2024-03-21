package com.etendoerp.dependencymanager;

import org.openbravo.base.session.OBPropertiesProvider;

public class DependencyManagerTestConstants {
  public static final String FIRST_VERSION = "1.0.0";
  public static final String FORMAT_JAR = "J";
  public static final String FORMAT_SOURCE = "S";
  public static final String GROUP_COM_ETENDOERP = "com.etendoerp";
  public static final String MODULE_JAR_PGK_1 = "com.etendoerp.module.jar1";
  public static final String MODULE_SOURCE_PKG_1 = "com.etendoerp.module.source1";
  public static final String AUTHOR_ETENDO_SOFTWARE = "Etendo Software";
  public static final String LICENSE_APACHE = "Apache2.0";
  public static final String TYPE_MODULE = "M";
  public static final String MODULES_PATH = OBPropertiesProvider.getInstance().getOpenbravoProperties().getProperty(
      "source.path") + "/modules/";
  public static final String SUCCESS_MSG_JAR_1 = "The " + MODULE_JAR_PGK_1 + " module was uninstalled successfully";
  public static final String SUCCESS_MSG_SOURCE_1 = "The " + MODULE_SOURCE_PKG_1 + " module was uninstalled successfully";

  private DependencyManagerTestConstants() {
  }
}
