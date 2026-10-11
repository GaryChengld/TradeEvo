package com.tradeevo.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param dataDir  root of raw data files: {@code {dataDir}/{dataset}/{yyyy-MM-dd}.json}
 * @param storeDir root of stored strategy versions and run results
 */
@ConfigurationProperties(prefix = "tradeevo")
public record TradeEvoProperties(
        @DefaultValue("./data") Path dataDir,
        @DefaultValue("./store") Path storeDir) {
}
