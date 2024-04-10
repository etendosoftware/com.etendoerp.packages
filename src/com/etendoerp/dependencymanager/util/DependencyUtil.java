package com.etendoerp.dependencymanager.util;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.openbravo.base.session.OBPropertiesProvider;

public class DependencyUtil {

  public static final String FORMAT_LOCAL = "L";
  public static final String FORMAT_SOURCE = "S";
  public static final String FORMAT_JAR = "J";
  private DependencyUtil() {
  }

  public static void deleteSourceDependencyDir(String dependencyName) throws IOException {
    String sourceRootPath = OBPropertiesProvider.getInstance().getOpenbravoProperties().getProperty("source.path");
    String sourceDepFilePath = sourceRootPath + "/modules/" + dependencyName;
    File sourceDepDir = new File(sourceDepFilePath);
    FileUtils.deleteDirectory(sourceDepDir);
  }
}
