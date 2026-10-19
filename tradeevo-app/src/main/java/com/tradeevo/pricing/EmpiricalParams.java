package com.tradeevo.pricing;

/** Parameters of {@link EmpiricalSpreadPricer}; see its Javadoc for the formula. */
public record EmpiricalParams(
        double base,
        double decay,
        double cScale, double cPivot, double cExponent, double cMin, double cMax,
        double mHeight, double mCenter, double mWidth,
        double aHeight, double aCenter, double aWidth) {

    public EmpiricalParams {
        ParamKind.FINITE.require("base", base);
        ParamKind.POSITIVE.require("decay", decay);
        ParamKind.FINITE.require("cScale", cScale);
        ParamKind.POSITIVE.require("cPivot", cPivot);
        ParamKind.FINITE.require("cExponent", cExponent);
        ParamKind.FINITE.require("cMin", cMin);
        ParamKind.FINITE.require("cMax", cMax);
        ParamKind.FINITE.require("mHeight", mHeight);
        ParamKind.FINITE.require("mCenter", mCenter);
        ParamKind.POSITIVE.require("mWidth", mWidth);
        ParamKind.FINITE.require("aHeight", aHeight);
        ParamKind.FINITE.require("aCenter", aCenter);
        ParamKind.POSITIVE.require("aWidth", aWidth);
        if (cMin > cMax) {
            throw new IllegalArgumentException("cMin: must be <= cMax, was " + cMin + " > " + cMax);
        }
    }
}
