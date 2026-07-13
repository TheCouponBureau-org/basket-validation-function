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
}
