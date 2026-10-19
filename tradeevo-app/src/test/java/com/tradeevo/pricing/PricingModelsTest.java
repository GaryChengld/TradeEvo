package com.tradeevo.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradeevo.AppTestSupport;
import com.tradeevo.core.error.InvalidConfigException;

class PricingModelsTest {

    @TempDir
    Path dir;

    private static ObjectNode shipped() {
        try {
            return (ObjectNode) AppTestSupport.MAPPER.readTree(AppTestSupport.PRICING_FILE.toFile());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static ObjectNode model(ObjectNode root, String name) {
        return (ObjectNode) root.get("models").get(name);
    }

    /** Applies the edit to the shipped file and returns the errors it causes. */
    private static java.util.List<String> errorsAfter(Consumer<ObjectNode> edit) {
        ObjectNode root = shipped();
        edit.accept(root);
        try {
            PricingModels.parse(root);
        } catch (InvalidConfigException e) {
            return e.getErrors();
        }
        throw new AssertionError("expected the edited file to be invalid");
    }

    @Test
    void shippedFileLoadsAndMatchesTheTestFixtures() {
        PricingModels models = PricingModels.load(AppTestSupport.PRICING_FILE, AppTestSupport.MAPPER);

        assertThat(models.defaultModel()).isEqualTo(PricingModels.BLACK_SCHOLES);
        assertThat(models.timeParams()).isEqualTo(PricingFixtures.TIME);
        assertThat(models.blackScholesParams()).isEqualTo(PricingFixtures.BS);
        assertThat(models.empiricalParams()).isEqualTo(PricingFixtures.EMPIRICAL);
    }

    @Test
    void reportsEachProblemWithItsPath() {
        assertThat(errorsAfter(r -> r.put("defaultModel", "binomial")))
                .containsExactly("defaultModel: must be one of [black-scholes, empirical]");
        assertThat(errorsAfter(r -> model(r, "black-scholes").put("ivMultiplier", 0)))
                .containsExactly("models.black-scholes.ivMultiplier: must be > 0");
        assertThat(errorsAfter(r -> model(r, "empirical").remove("decay")))
                .containsExactly("models.empirical.decay: is required");
        assertThat(errorsAfter(r -> r.put("extra", 1))).containsExactly("extra: unknown property");
        assertThat(errorsAfter(r -> ((ObjectNode) r.get("time")).put("timezone", "ET")))
                .containsExactly("time.timezone: unknown parameter");
        assertThat(errorsAfter(r -> model(r, "empirical").put("deacy", 3.8)))
                .containsExactly("models.empirical.deacy: unknown parameter");
        assertThat(errorsAfter(r -> ((ObjectNode) r.get("models")).putObject("binomial")))
                .containsExactly("models.binomial: unknown model, must be one of [black-scholes, empirical]");
        assertThat(errorsAfter(r -> model(r, "empirical").put("decay", Double.NaN)))
                .containsExactly("models.empirical.decay: must be a finite number");
        assertThat(errorsAfter(r -> model(r, "empirical").put("base", Double.POSITIVE_INFINITY)))
                .containsExactly("models.empirical.base: must be a finite number");
        assertThat(errorsAfter(r -> model(r, "empirical").put("decay", "3.8")))
                .containsExactly("models.empirical.decay: must be a number");
        assertThat(errorsAfter(r -> model(r, "empirical").put("cMin", 2.5)))
                .containsExactly("models.empirical.cMin: must be <= cMax, was 2.5 > 2.1");
        assertThat(errorsAfter(r -> ((ObjectNode) r.get("time")).put("expiration", "4pm")))
                .containsExactly("time.expiration: must be a time HH:mm");
        assertThat(errorsAfter(r -> ((ObjectNode) r.get("time")).put("expiration", "25:00")))
                .containsExactly("time.expiration: must be a valid time HH:mm");
        assertThat(errorsAfter(r -> ((ObjectNode) r.get("time")).put("tradingDaysPerYear", 0)))
                .containsExactly("time.tradingDaysPerYear: must be a positive integer");
        assertThat(errorsAfter(r -> ((ObjectNode) r.get("time")).put("sessionMinutes", 390.5)))
                .containsExactly("time.sessionMinutes: must be a positive integer");
        assertThat(errorsAfter(r -> r.remove("time"))).containsExactly("time: is required");
    }

    @Test
    void reportsSeveralErrorsTogether() {
        assertThat(errorsAfter(r -> {
            r.remove("defaultModel");
            model(r, "black-scholes").put("ivMultiplier", -1);
            model(r, "empirical").put("mWidth", 0).put("cMin", 3);
            ((ObjectNode) r.get("time")).put("expiration", "16:00:00");
        })).containsExactlyInAnyOrder(
                "defaultModel: is required",
                "models.black-scholes.ivMultiplier: must be > 0",
                "models.empirical.mWidth: must be > 0",
                "models.empirical.cMin: must be <= cMax, was 3 > 2.1",
                "time.expiration: must be a time HH:mm");
    }

    @Test
    void missingOrMalformedFileFails() throws IOException {
        Path missing = dir.resolve("missing.json");
        assertThatThrownBy(() -> PricingModels.load(missing, AppTestSupport.MAPPER))
                .isInstanceOfSatisfying(InvalidConfigException.class,
                        e -> assertThat(e.getErrors()).singleElement().asString().contains("file not found"));

        Path malformed = Files.writeString(dir.resolve("bad.json"), "{\"defaultModel\": ");
        assertThatThrownBy(() -> PricingModels.load(malformed, AppTestSupport.MAPPER))
                .isInstanceOf(InvalidConfigException.class)
                .hasMessageStartingWith("Invalid pricing file " + malformed);
    }
}
