package com.etendoerp.dependencymanager.datasource;

import static com.etendoerp.dependencymanager.util.PackageUtil.compareVersions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.etendoerp.dependencymanager.util.DependencyManagerConstants;

public abstract class AbstractResultComparator implements Comparator<Map<String, Object>> {
  /**
   * List of base string fields used for comparison.
   */
  protected static final List<String> BASE_STRING_FIELD_LIST = List.of(
      DependencyManagerConstants.GROUP,
      DependencyManagerConstants.ARTIFACT
  );

  /**
   * List of version fields used for comparison.
   */
  protected static final List<String> STRING_VERSION_FIELD_LIST = List.of(DependencyManagerConstants.VERSION);

  /**
   * List of string fields available for comparison.
   */
  protected final List<String> stringFieldList;

  /**
   * The field name used for sorting.
   */
  protected String sortByField;

  /**
   * Sorting order: 1 for ascending, -1 for descending.
   */
  protected int ascending;

  /**
   * Constructs an instance of {@code AbstractResultComparator} with
   * the specified field to sort by. The sorting order is determined
   * by whether the field name starts with a '-' character.
   *
   * @param sortByField the field name to sort by
   */
  protected AbstractResultComparator(String sortByField) {
    this.stringFieldList = new ArrayList<>(BASE_STRING_FIELD_LIST);
    this.sortByField = sortByField;
    ascending = 1;
    if (StringUtils.startsWith(sortByField, "-")) {
      ascending = -1;
      this.sortByField = StringUtils.substring(sortByField, 1);
    }
  }

  /**
   * Compares two maps based on the specified field.
   *
   * <p>Uses version comparison for fields in {@link #STRING_VERSION_FIELD_LIST},
   * string comparison for fields in {@link #stringFieldList}, and general
   * comparison otherwise. The result is multiplied by the sorting order.</p>
   *
   * @param map1 the first map to compare
   * @param map2 the second map to compare
   * @return a negative integer, zero, or a positive integer as the first map
   *         is less than, equal to, or greater than the second map
   */
  @Override
  public int compare(Map<String, Object> map1, Map<String, Object> map2) {
    int returnValue = 0;
    if (STRING_VERSION_FIELD_LIST.contains(sortByField)) {
      returnValue = getVersionCompare(map1, map2);
    } else if (stringFieldList.contains(sortByField)) {
      returnValue = getStringCompare(map1, map2);
    } else {
      var val1 = map1.get(sortByField) != null ? map1.get(sortByField).toString() : StringUtils.EMPTY;
      var val2 = map2.get(sortByField) != null ? map2.get(sortByField).toString() : StringUtils.EMPTY;
      returnValue = val1.compareTo(val2);
    }
    return returnValue * ascending;
  }

  /**
   * Compares two maps based on the version field.
   *
   * @param map1 the first map to compare
   * @param map2 the second map to compare
   * @return the comparison result based on version values
   */
  protected int getVersionCompare(Map<String, Object> map1, Map<String, Object> map2) {
    var val1 = map1.get(sortByField) != null ? map1.get(sortByField).toString() : StringUtils.EMPTY;
    var val2 = map2.get(sortByField) != null ? map2.get(sortByField).toString() : StringUtils.EMPTY;
    return compareVersions(val1, val2);
  }

  /**
   * Compares two maps based on the string field.
   *
   * @param map1 the first map to compare
   * @param map2 the second map to compare
   * @return the comparison result based on string values
   */
  protected int getStringCompare(Map<String, Object> map1, Map<String, Object> map2) {
    var val1 = map1.get(sortByField) != null ? map1.get(sortByField).toString() : StringUtils.EMPTY;
    var val2 = map2.get(sortByField) != null ? map2.get(sortByField).toString() : StringUtils.EMPTY;
    return val1.compareTo(val2);
  }
}