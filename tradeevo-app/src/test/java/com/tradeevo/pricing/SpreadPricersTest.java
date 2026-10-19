package com.tradeevo.pricing;

import static com.tradeevo.AppTestSupport.json;
import static com.tradeevo.pricing.PricingFixtures.SPX;
import static com.tradeevo.pricing.PricingFixtures.TEN;
import static com.tradeevo.pricing.PricingFixtures.VIX;
import static com.tradeevo.pricing.SpreadDirection.BULL_PUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tradeevo.AppTestSupport;
import com.tradeevo.core.error.InvalidConfigException;

class SpreadPricersTest {

    @TempDir
    Path dir;

    private Path file;
    private SpreadPricers pricers;

    @BeforeEach
    void copyShippedFile() throws IOException {
        file = Files.copy(AppTestSupport.PRICING_FILE, dir.resolve("pricing-models.json"));
        pricers = new SpreadPricers(file, AppTestSupport.MAPPER);
    }

    private static double samplePrice(SpreadPricer pricer) {
        return pricer.price(SPX, VIX, TEN, BULL_PUT, 4980, 4970);
    }

    @Test
    void defaultModelIsUsedWhenNoneIsChosen() {
        for (var pricing : new com.fasterxml.jackson.databind.JsonNode[] {null, json("{}"), json("{'overrides': {}}")}) {
            ResolvedPricer resolved = pricers.create(pricing);
            assertThat(resolved.model()).isEqualTo("black-scholes");
            assertThat(resolved.pricer()).isInstanceOf(BlackScholesSpreadPricer.class);
            assertThat(samplePrice(resolved.pricer())).isEqualTo(samplePrice(PricingFixtures.BS_SPREADS));
        }
    }

    @Test
    void choosesTheEmpiricalModel() {
        ResolvedPricer resolved = pricers.create(json("{'model': 'empirical'}"));

        assertThat(resolved.pricer()).isInstanceOf(EmpiricalSpreadPricer.class);
        assertThat(samplePrice(resolved.pricer())).isEqualTo(samplePrice(PricingFixtures.EMPIRICAL_SPREADS));
    }

    @Test
    void appliesOverridesAndReportsTheResolvedParameters() {
        ResolvedPricer resolved = pricers.create(json("{'overrides': {'ivMultiplier': 1.15, 'expiration': '13:00'}}"));

        TimeParams earlyClose = new TimeParams(LocalTime.of(13, 0), 252, 390);
        BlackScholesSpreadPricer expected = new BlackScholesSpreadPricer(earlyClose, new BlackScholesParams(1.15, 0, 0));
        assertThat(samplePrice(resolved.pricer())).isEqualTo(samplePrice(expected));
        assertThat(resolved.pricer().price(SPX, VIX, LocalTime.of(13, 0), BULL_PUT, 5004, 4994)).isEqualTo(4);
        assertThat(resolved.parameters()).isEqualTo(json("""
                {'model': 'black-scholes',
                 'time': {'expiration': '13:00', 'tradingDaysPerYear': 252, 'sessionMinutes': 390},
                 'params': {'ivMultiplier': 1.15, 'rate': 0.0, 'dividend': 0.0}}
                """));
    }

    @Test
    void resolvedParametersCannotBeChangedFromOutside() {
        ResolvedPricer resolved = pricers.create(null);
        resolved.parameters().put("model", "changed");
        assertThat(resolved.parameters().get("model").asText()).isEqualTo("black-scholes");
    }

    @Test
    void reportsOverrideErrorsWithTheStrategyPath() {
        assertThat(pricers.validate(json("{'model': 'empirical', 'extra': 1, "
                + "'overrides': {'decay': -1, 'ivMultiplier': 1.2, 'expiration': '1pm'}}"), "settings.pricing"))
                .containsExactlyInAnyOrder(
                        "settings.pricing.extra: unknown property",
                        "settings.pricing.overrides.decay: must be > 0",
                        "settings.pricing.overrides.ivMultiplier: unknown parameter for model 'empirical'",
                        "settings.pricing.overrides.expiration: must be a time HH:mm");
        assertThat(pricers.validate(json("{'model': 'empirical', 'overrides': {'cMin': 3}}"), "settings.pricing"))
                .containsExactly("settings.pricing.overrides.cMin: cMin must be <= cMax, was 3 > 2.1");
        assertThat(pricers.validate(json("{'model': 'binomial'}"), "settings.pricing"))
                .containsExactly("settings.pricing.model: must be one of [black-scholes, empirical]");
        assertThat(pricers.validate(json("[]"), "settings.pricing")).containsExactly("settings.pricing: must be an object");
        assertThat(pricers.validate(null, "settings.pricing")).isEmpty();

        assertThatThrownBy(() -> pricers.create(json("{'overrides': {'ivMultiplier': 0}}")))
                .isInstanceOfSatisfying(InvalidConfigException.class, e -> assertThat(e.getErrors())
                        .containsExactly("settings.pricing.overrides.ivMultiplier: must be > 0"));
    }

    @Test
    void missingFileFailsOnlyWhenAPricerIsRequested() throws IOException {
        Path missing = dir.resolve("absent.json");
        SpreadPricers lazy = new SpreadPricers(missing, AppTestSupport.MAPPER);

        assertThatThrownBy(() -> lazy.create(null))
                .isInstanceOf(InvalidConfigException.class)
                .hasMessageContaining("file not found");

        Files.copy(AppTestSupport.PRICING_FILE, missing);
        assertThatThrownBy(() -> lazy.create(null)).as("the failure is kept until restart")
                .isInstanceOf(InvalidConfigException.class);
    }

    @Test
    void fileIsReadOnceAndCached() throws IOException {
        assertThat(pricers.create(null).parameters().get("params").get("ivMultiplier").doubleValue()).isEqualTo(1.0);

        Files.writeString(file, Files.readString(file).replace("\"ivMultiplier\": 1.0", "\"ivMultiplier\": 2.0"));
        assertThat(Files.readString(file)).contains("\"ivMultiplier\": 2.0");
        assertThat(pricers.create(null).parameters().get("params").get("ivMultiplier").doubleValue())
                .as("edit not seen until restart").isEqualTo(1.0);
        assertThat(new SpreadPricers(file, AppTestSupport.MAPPER).create(null).parameters().get("params")
                .get("ivMultiplier").doubleValue()).as("a new instance reads the file again").isEqualTo(2.0);

        Files.delete(file);
        assertThat(pricers.create(null).model()).as("still cached after the file is gone").isEqualTo("black-scholes");
    }
}
