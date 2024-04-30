package com.etendoerp.dependencymanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.openbravo.client.kernel.BaseComponentProvider;
import org.openbravo.client.kernel.Component;
import org.openbravo.client.kernel.ComponentProvider;


@ApplicationScoped
@ComponentProvider.Qualifier(DependencyManagerComponentProvider.ETDEP_COMPONENT)
public class DependencyManagerComponentProvider extends BaseComponentProvider {
  protected static final String[] JS_FILES = new String[]{
      "uninstallDependencyWarnings.js",
      "changeVersionDropdown.js",
      "changeFormat.js",
      "dependenciesStatusField.js"
  };
  protected static final String ETDEP_COMPONENT = "ETDEP_DependencyManagerComponentProvider";

  @Override
  public Component getComponent(String componentId, Map<String, Object> parameters) {
    throw new IllegalArgumentException("Component id " + componentId + " not supported.");
  }

  @Override
  public List<ComponentResource> getGlobalComponentResources() {
    final GlobalResourcesHelper grhelper = new GlobalResourcesHelper();
    // Add all the javascript source files needed in our module
    for (String file : JS_FILES) {
      grhelper.addERP(file);
    }
    return grhelper.getGlobalResources();
  }

  private class GlobalResourcesHelper {
    private final List<ComponentResource> globalResources = new ArrayList<>();

    public void addERP(String file) {
      String prefix = "web/com.etendoerp.dependencymanager/js/";
      globalResources.add(createStaticResource(prefix + file, false));
    }

    public List<ComponentResource> getGlobalResources() {
      return globalResources;
    }
  }
}
