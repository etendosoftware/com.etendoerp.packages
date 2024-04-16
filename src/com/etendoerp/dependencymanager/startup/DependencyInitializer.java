package com.etendoerp.dependencymanager.startup;

import javax.enterprise.context.ApplicationScoped;

import com.etendoerp.dependencymanager.util.UpdateLocalPackagesUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.client.kernel.ApplicationInitializer;
import org.openbravo.dal.service.OBDal;

@ApplicationScoped
public class DependencyInitializer implements ApplicationInitializer {
  private static final Logger log = LogManager.getLogger();

  public void initialize() {
    new Thread(() -> {
      try {
        UpdateLocalPackagesUtil.update();
        OBDal.getInstance().commitAndClose();
        log.info("Etendo package update completed");
      } catch (Exception e) {
        log.error("Error when updating packages", e);
      }
    }).start();
  }
}
