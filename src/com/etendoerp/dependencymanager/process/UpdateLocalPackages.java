package com.etendoerp.dependencymanager.process;

import com.etendoerp.dependencymanager.util.UpdateLocalPackagesUtil;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

/**
 * This class is responsible for updating local packages.
 */
public class UpdateLocalPackages extends DalBaseProcess {

  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    UpdateLocalPackagesUtil.update();
  }

}
