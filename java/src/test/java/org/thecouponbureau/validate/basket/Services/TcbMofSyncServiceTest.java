package org.thecouponbureau.validate.basket.Services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

public class TcbMofSyncServiceTest {

    @Test
    void initialModeUsesLastSixMonthsThroughTomorrow() {
        TcbMofSyncService.DateRange dateRange =
                TcbMofSyncService.resolveDateRange(
                        "initial",
                        LocalDate.of(2026, 8, 18));

        assertEquals(LocalDate.of(2026, 2, 18), dateRange.fromDate);
        assertEquals(LocalDate.of(2026, 8, 19), dateRange.toDate);
    }

    @Test
    void incrementalModeUsesYesterdayThroughTomorrow() {
        TcbMofSyncService.DateRange dateRange =
                TcbMofSyncService.resolveDateRange(
                        "incremental",
                        LocalDate.of(2026, 8, 18));

        assertEquals(LocalDate.of(2026, 8, 17), dateRange.fromDate);
        assertEquals(LocalDate.of(2026, 8, 19), dateRange.toDate);
    }

    @Test
    void mapsApiRecordIntoSdkPurchaseRequirementShape() {
        TcbMofSyncService.ApiRecord apiRecord = new TcbMofSyncService.ApiRecord();
        apiRecord.baseGs1 = "811200998845900001";
        apiRecord.primaryPurchaseGtins = List.of("037000930396", "037000934677");
        apiRecord.primaryPurchaseSaveValue = 100L;
        apiRecord.primaryPurchaseRequirements = 2L;
        apiRecord.primaryPurchaseReqCode = 0;
        apiRecord.saveValueCode = 0;

        List<TcbMofSyncService.MasterOfferFileRecord> records =
                TcbMofSyncService.mapRecords(List.of(apiRecord));

        assertEquals(1, records.size());
        assertEquals("811200998845900001", records.get(0).baseGs1);
        assertEquals(
                List.of("037000930396", "037000934677"),
                records.get(0).purchaseRequirement.primaryPurchaseGtins);
        assertEquals(Long.valueOf(100), records.get(0).purchaseRequirement.primaryPurchaseSaveValue);
        assertEquals(Long.valueOf(2), records.get(0).purchaseRequirement.primaryPurchaseRequirements);
        assertEquals(Integer.valueOf(0), records.get(0).purchaseRequirement.primaryPurchaseReqCode);
        assertEquals(Integer.valueOf(0), records.get(0).purchaseRequirement.saveValueCode);
    }

    @Test
    void deserializesCamelCaseNextPageNoFromApiResponse() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        String json = "{"
                + "\"status\":\"success\","
                + "\"data\":[],"
                + "\"nextPageNo\":\"abc123\","
                + "\"execution_id\":\"exec-1\","
                + "\"execution_time_in_ms\":10,"
                + "\"execution_start_time\":20"
                + "}";

        TcbMofSyncService.ApiResponse response =
                mapper.readValue(json, TcbMofSyncService.ApiResponse.class);

        assertEquals("abc123", response.nextPageNo);
    }
}
