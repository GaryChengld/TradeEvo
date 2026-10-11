package com.tradeevo.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeevo.core.data.DataRepository;
import com.tradeevo.store.RunStore;
import com.tradeevo.store.StrategyStore;

@Configuration
public class TradeEvoConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    DataRepository dataRepository(TradeEvoProperties properties, ObjectMapper mapper) {
        return new DataRepository(properties.dataDir(), mapper);
    }

    @Bean
    StrategyStore strategyStore(TradeEvoProperties properties, ObjectMapper mapper) {
        return new StrategyStore(properties.storeDir(), mapper);
    }

    @Bean
    RunStore runStore(TradeEvoProperties properties, ObjectMapper mapper) {
        return new RunStore(properties.storeDir(), mapper);
    }
}
