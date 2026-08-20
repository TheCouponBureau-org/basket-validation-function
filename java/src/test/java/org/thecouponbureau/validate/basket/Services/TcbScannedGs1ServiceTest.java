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
        assertEquals(false, parsed.get(0).validated);
    }

    @Test
    void parsesMaxLengthConsumerSerializedGs1Locally() {
        List<TcbScannedGs1Service.SerializedGs1Data> parsed =
                TcbScannedGs1Service.tryParseConsumerSerializedGs1s(
                        "8112069988556677440000019133301677522707");

        assertEquals(1, parsed.size());
        assertEquals("8112069988556677440000019133301677522707", parsed.get(0).gs1);
        assertEquals("811206998855667744000001", parsed.get(0).baseGs1);
        assertEquals(false, parsed.get(0).validated);
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
        assertEquals(false, parsed.get(0).validated);
        assertEquals(false, parsed.get(1).validated);
    }

    @Test
    void parsesConcatenatedVariableLengthConsumerSerializedGs1sLocally() {
        List<TcbScannedGs1Service.SerializedGs1Data> parsed =
                TcbScannedGs1Service.tryParseConsumerSerializedGs1s(
                        "8112209988459000329165266614604064"
                                + "8112069988556677440000019133301677522707");

        assertEquals(2, parsed.size());
        assertEquals("811220998845900032", parsed.get(0).baseGs1);
        assertEquals("811206998855667744000001", parsed.get(1).baseGs1);
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
        assertEquals(true, resolved.get(0).validated);
        assertEquals("8112209988459000349165768322093822", resolved.get(1).gs1);
        assertEquals("811220998845900034", resolved.get(1).baseGs1);
        assertEquals(true, resolved.get(1).validated);
    }

    @Test
    void buildRedeemBatchesUsesOnly16DigitCodesForRedeemCalls() {
        String sixteenDigitCode = "1234567890123456";

        List<TcbScannedGs1Service.PendingRedeemInput> redeemInputs = List.of(
                new TcbScannedGs1Service.PendingRedeemInput(0, sixteenDigitCode),
                new TcbScannedGs1Service.PendingRedeemInput(
                        1,
                        "8112209988459000329165266614604064"),
                new TcbScannedGs1Service.PendingRedeemInput(
                        2,
                        "8112209988459000349165768322093822"));

        List<TcbScannedGs1Service.RedeemChunk> batches = TcbScannedGs1Service.buildRedeemBatches(
                redeemInputs);

        assertEquals(1, batches.size());
        assertEquals(List.of(sixteenDigitCode), batches.get(0).inputs.stream().map(input -> input.gs1).toList());
    }

    @Test
    void buildRedeemBatchesIncludesNonSerializedFsiCodes() {
        List<TcbScannedGs1Service.RedeemChunk> batches = TcbScannedGs1Service.buildRedeemBatches(
                List.of(
                        new TcbScannedGs1Service.PendingRedeemInput(0, "8112209988459000320001"),
                        new TcbScannedGs1Service.PendingRedeemInput(1, "8112209988459000340001")));

        assertEquals(1, batches.size());
        assertEquals(
                List.of(
                        "8112209988459000320001",
                        "8112209988459000340001"),
                batches.get(0).inputs.stream().map(input -> input.gs1).toList());
    }

    @Test
    void buildRedeemBatchesUsesOneRequestPerSixteenDigitCode() {
        List<TcbScannedGs1Service.PendingRedeemInput> scans = new java.util.ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            scans.add(new TcbScannedGs1Service.PendingRedeemInput(
                    index - 1,
                    String.format("%016d", index)));
        }

        List<TcbScannedGs1Service.RedeemChunk> batches = TcbScannedGs1Service.buildRedeemBatches(scans);

        assertEquals(3, batches.size());
        assertEquals(List.of(scans.get(0).gs1), batches.get(0).inputs.stream().map(input -> input.gs1).toList());
        assertEquals(List.of(scans.get(1).gs1), batches.get(1).inputs.stream().map(input -> input.gs1).toList());
        assertEquals(List.of(scans.get(2).gs1), batches.get(2).inputs.stream().map(input -> input.gs1).toList());
    }

    @Test
    void extractResolvedGs1sByInputMaintainsMixedInputOrder() {
        String redeemResponse = "{"
                + "\"status\":\"success\","
                + "\"newly_redeemed\":["
                + "{\"gs1\":\"8112209988459000359165000000000001\",\"master_offer_file\":\"811220998845900035\"}"
                + "]"
                + "}";

        List<List<TcbScannedGs1Service.SerializedGs1Data>> resolvedByInputIndex = new java.util.ArrayList<>();
        resolvedByInputIndex.add(new java.util.ArrayList<>(List.of(serialized("8112009988459000019133220584399722", "811200998845900001", null))));
        resolvedByInputIndex.add(new java.util.ArrayList<>());
        resolvedByInputIndex.add(new java.util.ArrayList<>(List.of(serialized("8112009988459000039133742458606199", "811200998845900003", null))));

        java.util.Map<Integer, List<TcbScannedGs1Service.SerializedGs1Data>> resolvedFromTcb =
                TcbScannedGs1Service.extractResolvedGs1sByInput(
                        redeemResponse,
                        List.of(new TcbScannedGs1Service.PendingRedeemInput(1, "8112209988459000350001")));

        for (java.util.Map.Entry<Integer, List<TcbScannedGs1Service.SerializedGs1Data>> entry : resolvedFromTcb.entrySet()) {
            resolvedByInputIndex.get(entry.getKey()).addAll(entry.getValue());
        }

        List<TcbScannedGs1Service.SerializedGs1Data> flattened =
                invokeFlattenResolvedGs1s(resolvedByInputIndex);

        assertEquals("8112009988459000019133220584399722", flattened.get(0).gs1);
        assertEquals("8112209988459000359165000000000001", flattened.get(1).gs1);
        assertEquals("8112009988459000039133742458606199", flattened.get(2).gs1);
    }

    @Test
    void extractResolvedGs1sByInputReturnsAllCouponsForSingle16DigitFetchCode() {
        String redeemResponse = "{"
                + "\"status\":\"success\","
                + "\"newly_redeemed\":["
                + "{\"gs1\":\"8112209988459000329165266614604064\",\"master_offer_file\":\"811220998845900032\"},"
                + "{\"gs1\":\"8112209988459000349165768322093822\",\"master_offer_file\":\"811220998845900034\"}"
                + "]"
                + "}";

        java.util.Map<Integer, List<TcbScannedGs1Service.SerializedGs1Data>> resolvedFromTcb =
                TcbScannedGs1Service.extractResolvedGs1sByInput(
                        redeemResponse,
                        List.of(new TcbScannedGs1Service.PendingRedeemInput(0, "1234567890123456")));

        assertEquals(1, resolvedFromTcb.size());
        assertEquals(2, resolvedFromTcb.get(0).size());
        assertEquals("8112209988459000329165266614604064", resolvedFromTcb.get(0).get(0).gs1);
        assertEquals(true, resolvedFromTcb.get(0).get(0).validated);
        assertEquals("8112209988459000349165768322093822", resolvedFromTcb.get(0).get(1).gs1);
        assertEquals(true, resolvedFromTcb.get(0).get(1).validated);
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
        assertEquals(true, resolved.get(0).validated);
    }

    private static TcbScannedGs1Service.SerializedGs1Data serialized(
            String gs1,
            String baseGs1,
            Boolean validated) {
        TcbScannedGs1Service.SerializedGs1Data data =
                new TcbScannedGs1Service.SerializedGs1Data();
        data.gs1 = gs1;
        data.baseGs1 = baseGs1;
        data.validated = validated;
        return data;
    }

    @SuppressWarnings("unchecked")
    private static List<TcbScannedGs1Service.SerializedGs1Data> invokeFlattenResolvedGs1s(
            List<List<TcbScannedGs1Service.SerializedGs1Data>> resolvedByInputIndex) {
        try {
            java.lang.reflect.Method method =
                    TcbScannedGs1Service.class.getDeclaredMethod(
                            "flattenResolvedGs1s",
                            List.class);
            method.setAccessible(true);
            return (List<TcbScannedGs1Service.SerializedGs1Data>) method.invoke(
                    null,
                    resolvedByInputIndex);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
