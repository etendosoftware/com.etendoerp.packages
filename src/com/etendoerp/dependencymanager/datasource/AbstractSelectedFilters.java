package com.etendoerp.dependencymanager.datasource;

public abstract class AbstractSelectedFilters {
  /**
   * The group filter.
   */
  protected String group;

  /**
   * The artifact filter.
   */
  protected String artifact;

  /**
   * The version filter.
   */
  protected String version;

  /**
   * Constructs an instance of {@code AbstractSelectedFilters} with all filters set to {@code null}.
   */
  protected AbstractSelectedFilters() {
    this.group = null;
    this.artifact = null;
    this.version = null;
  }

  /**
   * Returns the group filter.
   *
   * @return the group filter, or {@code null} if not set
   */
  public String getGroup() {
    return group;
  }

  /**
   * Sets the group filter.
   *
   * @param group the new group filter
   */
  public void setGroup(String group) {
    this.group = group;
  }

  /**
   * Returns the artifact filter.
   *
   * @return the artifact filter, or {@code null} if not set
   */
  public String getArtifact() {
    return artifact;
  }

  /**
   * Sets the artifact filter.
   *
   * @param artifact the new artifact filter
   */
  public void setArtifact(String artifact) {
    this.artifact = artifact;
  }

  /**
   * Returns the version filter.
   *
   * @return the version filter, or {@code null} if not set
   */
  public String getVersion() {
    return version;
  }

  /**
   * Sets the version filter.
   *
   * @param version the new version filter
   */
  public void setVersion(String version) {
    this.version = version;
  }
}
