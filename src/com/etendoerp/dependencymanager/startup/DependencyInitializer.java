package com.etendoerp.dependencymanager.startup;

import java.util.Properties;

import javax.enterprise.context.ApplicationScoped;

import com.etendoerp.dependencymanager.util.UpdateLocalPackagesUtil;

import org.apache.commons.lang.BooleanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.client.kernel.ApplicationInitializer;
import org.openbravo.dal.service.OBDal;

@ApplicationScoped
public class DependencyInitializer implements ApplicationInitializer {
  private static final Logger log = LogManager.getLogger();
  private static final String UPDATE_LOCAL_PACKAGES_PROPERTY = "no.update.local.packages";

  /**
   * This class is responsible for initializing some operations related to package updates in the application.
   */
  public void initialize() {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String updateLocalPackages = properties.getProperty(UPDATE_LOCAL_PACKAGES_PROPERTY, "");
    if (!BooleanUtils.toBoolean(updateLocalPackages)){
      new Thread(() -> {
        try {
          UpdateLocalPackagesUtil.update();
          OBDal.getInstance().commitAndClose();
          log.info("Etendo package update completed");
        } catch (Exception e) {
          log.error("Error when updating packages", e);
        }
      }).start();
    } else {
      log.info("Etendo local package update skipped");
    }
  }
}
