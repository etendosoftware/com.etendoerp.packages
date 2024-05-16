package com.etendoerp.dependencymanager.util;

import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

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
}
