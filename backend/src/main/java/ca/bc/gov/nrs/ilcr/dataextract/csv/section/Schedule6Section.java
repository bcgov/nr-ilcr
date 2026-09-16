package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NULL_VALUE;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.formula;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.sanitize;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecord;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CodeLists;
import java.util.List;

/**
 * Legacy {@code Schedule6Extract}: one 12-column row per road record. The TSA and Supply Block
 * cells are {@code "<code> - <description>"} resolved from the owner's in-document code lists; a
 * TFL-located record has no TSA in that list and so shows the null marker there, as legacy's cache
 * miss did. The {@code $/m³} is the Excel-formula form.
 */
public final class Schedule6Section implements SectionBuilder {

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "TSA_TFL",
    "TFL_#",
    "S_BLOCK",
    "RMG",
    "VOL_M3",
    "COST_$",
    "CPU_$/M3",
    "COMMENTS"
  };

  @Override
  public String title() {
    return "**** Schedule 6 ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /** The row for one road record. */
  public String[] row(RowContext ctx, RoadRecord record, Schedule6CodeLists codeLists) {
    return ctx.with(
        codeAndDescription(record.areaType(), codeLists == null ? null : codeLists.tsaNumbers()),
        record.tflNumber() != null ? record.tflNumber() : NULL_VALUE,
        codeAndDescription(
            record.supplyBlock(), codeLists == null ? null : codeLists.supplyBlocks()),
        record.rmg() != null ? record.rmg() : NULL_VALUE,
        whole(record.volume()),
        whole(record.cost()),
        formula(record.costPerVolume()),
        record.comments() != null ? sanitize(record.comments()) : NULL_VALUE);
  }

  /** {@code "<code> - <description>"} when the code is in the list, else the null marker. */
  static String codeAndDescription(String code, List<CodeDescriptionDto> options) {
    if (code == null || options == null) {
      return NULL_VALUE;
    }
    for (CodeDescriptionDto option : options) {
      if (code.equals(option.code())) {
        return code + " - " + (option.description() == null ? "" : option.description());
      }
    }
    return NULL_VALUE;
  }
}
