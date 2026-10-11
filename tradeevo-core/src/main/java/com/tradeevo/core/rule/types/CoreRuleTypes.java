package com.tradeevo.core.rule.types;

import java.util.List;

import com.tradeevo.core.rule.RuleType;

/** The built-in rule types. */
public final class CoreRuleTypes {

    private CoreRuleTypes() {
    }

    public static List<RuleType> builtIns() {
        return List.of(new CompareRule(), new BetweenRule(), new InRule(), new TimeWindowRule(),
                new AllRule(), new AnyRule(), new NotRule());
    }
}
