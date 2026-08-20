package ca.bc.gov.nrs.ilcr.util;

import ca.bc.gov.nrs.ilcr.exception.InvalidSortingFieldException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;

/** Utility class for handling pagination and sorting logic. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaginationUtil {

  /**
   * Resolves the sort order based on the received sort, default sort field, and valid sortable
   * fields.
   *
   * @param receivedSort the requested Sort
   * @param defaultSortField the default sort field
   * @param sortableFields a map of valid sortable fields to their database column names
   * @return the resolved Sort
   */
  public static Sort resolveSort(
      Sort receivedSort, String defaultSortField, Map<String, String> sortableFields) {
    if (receivedSort == null || receivedSort.isUnsorted()) {
      return Sort.by(Direction.ASC, defaultSortField);
    }

    List<Order> resolvedOrders = new ArrayList<>();
    for (Order order : receivedSort) {
      String databaseField = sortableFields.get(order.getProperty());
      if (databaseField == null) {
        throw new InvalidSortingFieldException(order.getProperty());
      }
      resolvedOrders.add(new Order(order.getDirection(), databaseField));
    }

    return resolvedOrders.isEmpty()
        ? Sort.by(Direction.ASC, defaultSortField)
        : Sort.by(resolvedOrders);
  }
}
