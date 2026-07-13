package org.thecouponbureau.validate.basket.Services;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TcbScannedGs1ServiceTest {

    @Test
    void parsesConsumerSerializedGs1Locally() {
        List<TcbScannedGs1Service.SerializedGs1Data> parsed =
                TcbScannedGs1Service.tryParseConsumerSerializedGs1s(
                        "8112209988459000329165266614604064");

        assertEquals(1, parsed.size());
        assertEquals("8112209988459000329165266614604064", parsed.get(0).gs1);
        assertEquals("811220998845900032", parsed.get(0).baseGs1);
    }

    @Test
    void parsesConcatenatedConsumerSerializedGs1sLocally() {
        List<TcbScannedGs1Service.SerializedGs1Data> parsed =
                TcbScannedGs1Service.tryParseConsumerSerializedGs1s(
                        "8112209988459000329165266614604064"
                                + "8112209988459000349165768322093822");

        assertEquals(2, parsed.size());
        assertEquals("811220998845900032", parsed.get(0).baseGs1);
        assertEquals("811220998845900034", parsed.get(1).baseGs1);
    }

    @Test
    void returnsEmptyForNonConsumerSerializedGs1() {
        List<TcbScannedGs1Service.SerializedGs1Data> parsed =
                TcbScannedGs1Service.tryParseConsumerSerializedGs1s(
                        "8112209988459000320001");

        assertTrue(parsed.isEmpty());
    }

    @Test
    void extractsSerializedGs1AndBaseGs1FromRedeemResponse() {
        String redeemResponse = "{"
                + "\"status\":\"success\","
                + "\"newly_redeemed\":["
                + "{\"gs1\":\"8112209988459000329165266614604064\",\"master_offer_file\":\"811220998845900032\"},"
                + "{\"gs1\":\"8112209988459000349165768322093822\",\"master_offer_file\":\"811220998845900034\"}"
                + "]"
                + "}";

        List<TcbScannedGs1Service.SerializedGs1Data> resolved =
                TcbScannedGs1Service.extractResolvedGs1s(redeemResponse);

        assertEquals(2, resolved.size());
        assertEquals("8112209988459000329165266614604064", resolved.get(0).gs1);
        assertEquals("811220998845900032", resolved.get(0).baseGs1);
        assertEquals("8112209988459000349165768322093822", resolved.get(1).gs1);
        assertEquals("811220998845900034", resolved.get(1).baseGs1);
    }

    @Test
    void buildRedeemBatchesUsesSingleCallsFor16DigitAndLongScans() {
        String longScan =
                "81121099884590002691333214260261938112109988459000269133587761214614";
        String sixteenDigitCode = "1234567890123456";

        List<List<String>> batches = TcbScannedGs1Service.buildRedeemBatches(
                List.of(
                        sixteenDigitCode,
                        longScan,
                        "8112209988459000329165266614604064",
                        "8112209988459000349165768322093822"));

        assertEquals(3, batches.size());
        assertEquals(List.of(sixteenDigitCode), batches.get(0));
        assertEquals(List.of(longScan), batches.get(1));
        assertEquals(
                List.of(
                        "8112209988459000329165266614604064",
                        "8112209988459000349165768322093822"),
                batches.get(2));
    }

    @Test
    void buildRedeemBatchesChunksRemainingScansByFifteen() {
        List<String> scans = new java.util.ArrayList<>();
        for (int index = 1; index <= 31; index++) {
            scans.add("8112209988459000329165266614604" + String.format("%03d", index));
        }

        List<List<String>> batches = TcbScannedGs1Service.buildRedeemBatches(scans);

        assertEquals(3, batches.size());
        assertEquals(15, batches.get(0).size());
        assertEquals(15, batches.get(1).size());
        assertEquals(1, batches.get(2).size());
        assertEquals(scans.get(0), batches.get(0).get(0));
        assertEquals(scans.get(15), batches.get(1).get(0));
        assertEquals(scans.get(30), batches.get(2).get(0));
    }

    @Test
    void returnsOnlyNewlyRedeemedCouponsFromRedeemResponse() {
        String redeemResponse = "{"
                + "\"status\":\"success\","
                + "\"newly_redeemed\":["
                + "{\"gs1\":\"8112209988459000329165266614604064\",\"master_offer_file\":\"811220998845900032\"}"
                + "]"
                + "}";

        List<TcbScannedGs1Service.SerializedGs1Data> resolved =
                TcbScannedGs1Service.extractResolvedGs1s(redeemResponse);

        assertEquals(1, resolved.size());
        assertEquals("8112209988459000329165266614604064", resolved.get(0).gs1);
        assertEquals("811220998845900032", resolved.get(0).baseGs1);
    }
}
