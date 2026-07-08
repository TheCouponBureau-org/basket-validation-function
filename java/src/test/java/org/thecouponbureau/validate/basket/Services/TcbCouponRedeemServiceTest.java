package org.thecouponbureau.validate.basket.Services;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TcbCouponRedeemServiceTest {

    @Test
    void chunkGs1sSplitsAtFifteen() {
        List<String> gs1s = new ArrayList<>();
        for (int index = 1; index <= 31; index++) {
            gs1s.add("gs1-" + index);
        }

        List<List<String>> chunks = TcbCouponRedeemService.chunkGs1s(gs1s, 15);

        assertEquals(3, chunks.size());
        assertEquals(15, chunks.get(0).size());
        assertEquals(15, chunks.get(1).size());
        assertEquals(1, chunks.get(2).size());
        assertEquals("gs1-1", chunks.get(0).get(0));
        assertEquals("gs1-16", chunks.get(1).get(0));
        assertEquals("gs1-31", chunks.get(2).get(0));
    }

    @Test
    void mergeRedeemResponsesAggregatesChunkResults() throws Exception {
        String responseOne = "{"
                + "\"status\":\"success\","
                + "\"status_code\":\"FULL_REDEMPTION\","
                + "\"newly_redeemed\":[{\"gs1\":\"a1\",\"master_offer_file\":\"base-a\"}],"
                + "\"total_gs1s_processed\":1,"
                + "\"message\":\"Redeemed 1 gs1(s)\","
                + "\"emailDomain\":\"test.example.org\","
                + "\"master_offer_files\":{\"base-a\":{\"primary_purchase_requirements\":1}},"
                + "\"execution_id\":\"exec-1\","
                + "\"execution_time_in_ms\":25,"
                + "\"execution_start_time\":100,"
                + "\"event_timestamp\":200"
                + "}";

        String responseTwo = "{"
                + "\"status\":\"success\","
                + "\"status_code\":\"FULL_REDEMPTION\","
                + "\"newly_redeemed\":["
                + "{\"gs1\":\"b1\",\"master_offer_file\":\"base-b\"},"
                + "{\"gs1\":\"b2\",\"master_offer_file\":\"base-b\"}],"
                + "\"total_gs1s_processed\":2,"
                + "\"message\":\"Redeemed 2 gs1(s)\","
                + "\"emailDomain\":\"test.example.org\","
                + "\"master_offer_files\":{\"base-b\":{\"primary_purchase_requirements\":2}},"
                + "\"execution_id\":\"exec-2\","
                + "\"execution_time_in_ms\":40,"
                + "\"execution_start_time\":110,"
                + "\"event_timestamp\":250"
                + "}";

        JSONObject merged = new JSONObject(
                TcbCouponRedeemService.mergeRedeemResponses(List.of(responseOne, responseTwo)));

        assertEquals("success", merged.getString("status"));
        assertEquals("FULL_REDEMPTION", merged.getString("status_code"));
        assertEquals(3, merged.getInt("total_gs1s_processed"));
        assertEquals(65, merged.getLong("execution_time_in_ms"));
        assertEquals(100, merged.getLong("execution_start_time"));
        assertEquals(250, merged.getLong("event_timestamp"));
        assertEquals("test.example.org", merged.getString("emailDomain"));

        JSONArray newlyRedeemed = merged.getJSONArray("newly_redeemed");
        assertEquals(3, newlyRedeemed.length());
        assertEquals("a1", newlyRedeemed.getJSONObject(0).getString("gs1"));
        assertEquals("b1", newlyRedeemed.getJSONObject(1).getString("gs1"));
        assertEquals("b2", newlyRedeemed.getJSONObject(2).getString("gs1"));

        JSONObject masterOfferFiles = merged.getJSONObject("master_offer_files");
        assertTrue(masterOfferFiles.has("base-a"));
        assertTrue(masterOfferFiles.has("base-b"));

        assertFalse(merged.has("execution_id"));
        JSONArray executionIds = merged.getJSONArray("execution_ids");
        assertEquals(2, executionIds.length());
        assertEquals("exec-1", executionIds.getString(0));
        assertEquals("exec-2", executionIds.getString(1));
    }
}
