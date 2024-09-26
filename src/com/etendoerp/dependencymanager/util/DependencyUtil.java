package com.etendoerp.dependencymanager.util;

import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.dependencymanager.data.Dependency;

public class DependencyUtil {

  public static final String FORMAT_LOCAL = "L";
  public static final String FORMAT_SOURCE = "S";
  public static final String FORMAT_JAR = "J";
  public static final String STATUS_INSTALLED = "INSTALLED";
  public static final String STATUS_PENDING = "PENDING";
  public static final String UNTRACKED_STATUS = "UT";

  private DependencyUtil() {
  }

  /**
   * This method checks if a dependency exists in the database.
   * It creates a criteria query on the Dependency class using the provided group, artifact, version, and externalDependency.
   * The method then executes the query and checks if there is at least one result.
   * If there is at least one result, the method returns true, indicating that the dependency exists.
   * Otherwise, it returns false.
   *
   * @param group The group of the dependency.
   * @param artifact The artifact of the dependency.
   * @param version The version of the dependency.
   * @param externalDependency A boolean indicating whether the dependency is external. If true, the format is set to JAR, otherwise it's set to SOURCE.
   * @return A boolean indicating whether the dependency exists.
   */
  public static boolean existsDependency(String group, String artifact, String version, boolean externalDependency) {
    OBCriteria<Dependency> dependencyCriteria = OBDal.getInstance().createCriteria(Dependency.class);
    dependencyCriteria.add(Restrictions.eq(Dependency.PROPERTY_GROUP, group));
    dependencyCriteria.add(Restrictions.eq(Dependency.PROPERTY_ARTIFACT, artifact));
    dependencyCriteria.add(Restrictions.eq(Dependency.PROPERTY_VERSION, version));
    dependencyCriteria.add(Restrictions.eq(Dependency.PROPERTY_FORMAT, externalDependency ? DependencyUtil.FORMAT_JAR : DependencyUtil.FORMAT_SOURCE));
    return dependencyCriteria.setMaxResults(1).uniqueResult() != null;
  }

  /**
   * Retrieves the installed dependency based on the specified group, artifact, and external dependency flag.
   *
   * This method queries the database for a `Dependency` object that matches the given group, artifact,
   * and whether it is an external dependency. It returns the first matching dependency found.
   *
   * @param group The group identifier of the dependency to be retrieved.
   * @param artifact The artifact identifier of the dependency to be retrieved.
   * @param externalDependency A boolean flag indicating whether the dependency is external.
   * @return The installed `Dependency` object that matches the criteria, or null if no match is found.
   */
  public static Dependency getInstalledDependency(String group, String artifact, boolean externalDependency) {
    OBCriteria<Dependency> dependencyCriteria = OBDal.getInstance().createCriteria(Dependency.class);
    dependencyCriteria.add(Restrictions.eq(Dependency.PROPERTY_GROUP, group));
    dependencyCriteria.add(Restrictions.eq(Dependency.PROPERTY_ARTIFACT, artifact));
    dependencyCriteria.add(Restrictions.eq(Dependency.PROPERTY_EXTERNALDEPENDENCY, externalDependency));
    return (Dependency) dependencyCriteria.setMaxResults(1).uniqueResult();
  }

  /**
   * Retrieves the installed module based on the specified group, artifact, and version.
   *
   * This method queries the database for a `Module` object that matches the provided group and artifact,
   * concatenated into a Java package format. It returns the first matching module found.
   *
   * @param group The group identifier of the module to be retrieved.
   * @param artifact The artifact identifier of the module to be retrieved.
   * @return The installed `Module` object that matches the criteria, or null if no match is found.
   */
  public static Module getInstalledModule(String group, String artifact) {
    OBCriteria<Module> moduleOBCriteria = OBDal.getInstance().createCriteria(Module.class);
    moduleOBCriteria.add(Restrictions.eq(Module.PROPERTY_JAVAPACKAGE, group+"."+artifact));
    return (Module) moduleOBCriteria.setMaxResults(1).uniqueResult();
  }
}
