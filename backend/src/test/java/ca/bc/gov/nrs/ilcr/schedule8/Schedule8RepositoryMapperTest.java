package ca.bc.gov.nrs.ilcr.schedule8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Repository.BecZoneCode;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Repository.CostItemRow;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Repository.ForestRegionCode;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Repository.RateCostTypeCode;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Repository.SkidTypeCode;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Repository.SupportCentreCode;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Repository.TflNumberCode;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Repository.TsaNumberCode;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Repository.TsbNumberCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the {@link Schedule8Repository} {@code default} compose/mapper methods — the
 * sequence-insert / exists-guards / cascade-delete sequences and the code-table → label-map adapters.
 * The {@code @Query} abstract methods are mocked; each default is exercised via
 * {@code thenCallRealMethod}. The SQL itself is proven by the Testcontainers {@code *IT} suite; this
 * locks the in-memory composition/mapping.
 */
@ExtendWith(MockitoExtension.class)
class Schedule8RepositoryMapperTest {

  private static final long MILL = 546L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule8Repository repo;

  @Test
  void insertPage_drawsSequenceThenInsertsRow() {
    when(repo.nextPageId()).thenReturn(500);
    when(repo.insertPage(MILL, YEAR, "SC", "RG", "BZ", "TSA", "TSB", "TFL", "CP", "LIC", "DIV",
        "CON", "PH", "CM", "user")).thenCallRealMethod();

    int id = repo.insertPage(MILL, YEAR, "SC", "RG", "BZ", "TSA", "TSB", "TFL", "CP", "LIC", "DIV",
        "CON", "PH", "CM", "user");

    assertEquals(500, id);
    verify(repo).insertPageRow(500, MILL, YEAR, "SC", "RG", "BZ", "TSA", "TSB", "TFL", "CP", "LIC",
        "DIV", "CON", "PH", "CM", "user");
  }

  @Test
  void pageExists_reflectsCount() {
    when(repo.countPage(8001, MILL, YEAR)).thenReturn(1);
    when(repo.pageExists(8001, MILL, YEAR)).thenCallRealMethod();

    assertTrue(repo.pageExists(8001, MILL, YEAR));
  }

  @Test
  void deletePage_cascadesRateDetailsThenSamplesThenPage() {
    doCallRealMethod().when(repo).deletePage(8001);

    repo.deletePage(8001);

    verify(repo).deletePageRateDetails(8001);
    verify(repo).deletePageSamples(8001);
    verify(repo).deletePageRow(8001);
  }

  @Test
  void insertSample_drawsSequenceThenInsertsRow() {
    when(repo.nextSampleId()).thenReturn(600);
    when(repo.insertSample(eq(8001), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("user")))
        .thenCallRealMethod();

    int id = repo.insertSample(8001, "C", "CB", 10, 0, 0, 0, 0, 0, null, null, null, null, null,
        null, null, null, null, null, null, "user");

    assertEquals(600, id);
    verify(repo).insertSampleRow(eq(600), eq(8001), anyString(), anyString(), anyInt(), anyInt(),
        anyInt(), anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), isNull(), isNull(),
        isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq("user"));
  }

  @Test
  void sampleExists_reflectsCount() {
    when(repo.countSample(9001, 8001)).thenReturn(1);
    when(repo.sampleExists(9001, 8001)).thenCallRealMethod();

    assertTrue(repo.sampleExists(9001, 8001));
  }

  @Test
  void deleteSample_cascadesRateDetailsThenSample() {
    doCallRealMethod().when(repo).deleteSample(9001);

    repo.deleteSample(9001);

    verify(repo).deleteSampleRateDetails(9001);
    verify(repo).deleteSampleRow(9001);
  }

  @Test
  void insertRate_drawsSequenceThenInsertsRow() {
    when(repo.nextRateId()).thenReturn(700);
    when(repo.insertRate(eq(9001), eq("CT"), eq(47), eq("desc"), isNull(), eq("user")))
        .thenCallRealMethod();

    int id = repo.insertRate(9001, "CT", 47, "desc", null, "user");

    assertEquals(700, id);
    verify(repo).insertRateRow(700, 9001, "CT", 47, "desc", null, "user");
  }

  @Test
  void rateExists_reflectsCount() {
    when(repo.countRate(7001, 9001)).thenReturn(0);
    when(repo.rateExists(7001, 9001)).thenCallRealMethod();

    assertFalse(repo.rateExists(7001, 9001));
  }

  @Test
  void sampleInMillYear_reflectsCount() {
    when(repo.countSampleInMillYear(9001, MILL, YEAR)).thenReturn(1);
    when(repo.sampleInMillYear(9001, MILL, YEAR)).thenCallRealMethod();

    assertTrue(repo.sampleInMillYear(9001, MILL, YEAR));
  }

  @Test
  void costItemSubcategories_mapsIdToSubcategory() {
    when(repo.findCategory8CostItems()).thenReturn(List.of(
        new CostItemRow(47, "Truck Barge/Ferry", "1"), new CostItemRow(48, "Crew Barge/Ferry", "3")));
    when(repo.costItemSubcategories()).thenCallRealMethod();

    Map<Integer, String> byId = repo.costItemSubcategories();

    assertEquals("1", byId.get(47));
    assertEquals("3", byId.get(48));
  }

  @Test
  void codeTableLabelMaps_buildCodeToDescription() {
    when(repo.findSupportCentreCodes()).thenReturn(List.of(new SupportCentreCode("SC", "Centre")));
    when(repo.findForestRegionCodes()).thenReturn(List.of(new ForestRegionCode("RG", "Region")));
    when(repo.findBecZoneCodes()).thenReturn(List.of(new BecZoneCode("BZ", "Zone")));
    when(repo.findTsaNumberCodes()).thenReturn(List.of(new TsaNumberCode("TSA", "Tsa")));
    when(repo.findTsbNumberCodes()).thenReturn(List.of(new TsbNumberCode("TSB", "Block")));
    when(repo.findTflNumberCodes()).thenReturn(List.of(new TflNumberCode("TFL", "Tfl")));
    when(repo.findSkidTypeCodes()).thenReturn(List.of(new SkidTypeCode("SK", "Skid")));
    when(repo.findRateCostTypeCodes()).thenReturn(List.of(new RateCostTypeCode("CT", "Cost")));
    when(repo.supportCentreLabels()).thenCallRealMethod();
    when(repo.regionLabels()).thenCallRealMethod();
    when(repo.becZoneLabels()).thenCallRealMethod();
    when(repo.tsaNumberLabels()).thenCallRealMethod();
    when(repo.supplyBlockLabels()).thenCallRealMethod();
    when(repo.tflNumberLabels()).thenCallRealMethod();
    when(repo.skidTypeLabels()).thenCallRealMethod();
    when(repo.costTypeLabels()).thenCallRealMethod();

    assertEquals("Centre", repo.supportCentreLabels().get("SC"));
    assertEquals("Region", repo.regionLabels().get("RG"));
    assertEquals("Zone", repo.becZoneLabels().get("BZ"));
    assertEquals("Tsa", repo.tsaNumberLabels().get("TSA"));
    assertEquals("Block", repo.supplyBlockLabels().get("TSB"));
    assertEquals("Tfl", repo.tflNumberLabels().get("TFL"));
    assertEquals("Skid", repo.skidTypeLabels().get("SK"));
    assertEquals("Cost", repo.costTypeLabels().get("CT"));
  }
}
