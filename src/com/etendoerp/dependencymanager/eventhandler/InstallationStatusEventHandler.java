package com.etendoerp.dependencymanager.eventhandler;

import javax.enterprise.event.Observes;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.util.DependencyUtil;

public class InstallationStatusEventHandler extends EntityPersistenceEventObserver {
  private static final Entity DEPENDENCY_ENTITY = ModelProvider.getInstance().getEntity(Dependency.ENTITY_NAME);
  private static final Property INSTALLATION_STATUS_PROP = DEPENDENCY_ENTITY.getProperty(
      Dependency.PROPERTY_INSTALLATIONSTATUS);
  private static final Property FORMAT_PROP = DEPENDENCY_ENTITY.getProperty(Dependency.PROPERTY_FORMAT);

  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(DEPENDENCY_ENTITY.getName()) };

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    Dependency dependency = (Dependency) event.getTargetInstance();
    String depName = dependency.getGroup() + "." + dependency.getArtifact();
    OBCriteria<Module> moduleCriteria = OBDal.getInstance().createCriteria(Module.class);
    moduleCriteria.add(Restrictions.eq(Module.PROPERTY_JAVAPACKAGE, depName));
    moduleCriteria.add(Restrictions.eq(Module.PROPERTY_VERSION, dependency.getVersion()));
    moduleCriteria.setMaxResults(1);

    Module dependencyModule = (Module) moduleCriteria.uniqueResult();

    if (dependencyModule != null) {
      event.setCurrentState(INSTALLATION_STATUS_PROP, DependencyUtil.STATUS_INSTALLED);
    } else {
      event.setCurrentState(INSTALLATION_STATUS_PROP, DependencyUtil.STATUS_PENDING);
    }
  }

  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    Dependency dependency = (Dependency) event.getTargetInstance();
    String depName = dependency.getGroup() + "." + dependency.getArtifact();
    OBCriteria<Module> moduleCriteria = OBDal.getInstance().createCriteria(Module.class);
    moduleCriteria.add(Restrictions.eq(Module.PROPERTY_JAVAPACKAGE, depName));
    moduleCriteria.add(Restrictions.eq(Module.PROPERTY_VERSION, dependency.getVersion()));
    moduleCriteria.setMaxResults(1);

    Module dependencyModule = (Module) moduleCriteria.uniqueResult();
    boolean formatHasNotChanged = event.getPreviousState(FORMAT_PROP).equals(event.getCurrentState(FORMAT_PROP));

    if (dependencyModule != null && formatHasNotChanged) {
      event.setCurrentState(INSTALLATION_STATUS_PROP, DependencyUtil.STATUS_INSTALLED);
    } else {
      event.setCurrentState(INSTALLATION_STATUS_PROP, DependencyUtil.STATUS_PENDING);
    }
  }
}
