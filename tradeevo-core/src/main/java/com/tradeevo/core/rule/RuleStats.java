package com.tradeevo.core.rule;

/** How often a named rule evaluated to each outcome during one run. */
public record RuleStats(long matched, long notMatched, long unknown) {
}
