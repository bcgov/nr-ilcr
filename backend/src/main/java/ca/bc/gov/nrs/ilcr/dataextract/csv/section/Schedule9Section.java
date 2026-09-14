package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NULL_VALUE;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.textTrimmed;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.twoDecimals;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule9.dto.ContractualWorkRecord;

/**
 * Legacy {@code Schedule9Extract}: one 17-column row per contractual work record. {@code
 * CONTRACTUAL_ITEM} is {@code "<item name> - <cost item id>"}; the cost and {@code $/UNIT} cells
 * are dashed when the UNIT count is absent — legacy guarded both on the unit, not the cost, and
 * that is kept.
 */
public final class Schedule9Section implements SectionBuilder {

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "COMPANY_ID",
    "CONTRACTUAL_ITEM",
    "OTH_ITEM_DESC",
    "SIDE_SLOPE_%",
    "NO_OF_UNITS",
    "UNIT_TYPE",
    "OTHER_DESC",
    "BIOGEO_ZONE",
    "COST_$",
    "$/UNIT",
    "SOURCE",
    "SOURCE_OTHER",
    "COMMENTS"
  };

  @Override
  public String title() {
    return "**** Schedule 9 ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /** The row for one record. */
  public String[] row(RowContext ctx, ContractualWorkRecord record) {
    boolean hasUnits = record.numberOfUnits() != null;
    return ctx.with(
        blankToNull(record.contractorId()),
        contractualItem(record.contractualItem()),
        textTrimmed(record.itemDescription()),
        record.sideSlopePct() == null ? NULL_VALUE : String.valueOf(record.sideSlopePct()),
        hasUnits ? whole(record.numberOfUnits()) : NULL_VALUE,
        codeOf(record.unitType()),
        textTrimmed(record.unitDescription()),
        codeOf(record.biogeoclimaticZone()),
        hasUnits ? whole(record.cost()) : NULL_VALUE,
        hasUnits ? twoDecimals(record.costPerUnit()) : NULL_VALUE,
        codeOf(record.source()),
        textTrimmed(record.sourceDescription()),
        textTrimmed(record.comments()));
  }

  private static String contractualItem(CodeDescriptionDto item) {
    if (item == null) {
      return NULL_VALUE;
    }
    return item.description() + " - " + item.code();
  }

  private static String codeOf(CodeDescriptionDto dto) {
    return dto == null || dto.code() == null || dto.code().trim().isEmpty()
        ? NULL_VALUE
        : dto.code();
  }

  private static String blankToNull(String value) {
    return value == null || value.trim().isEmpty() ? NULL_VALUE : value;
  }
}
