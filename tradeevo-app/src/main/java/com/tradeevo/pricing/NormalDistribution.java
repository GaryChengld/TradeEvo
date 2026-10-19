package com.tradeevo.pricing;

/**
 * Standard normal cumulative distribution function, using Hart's double-precision algorithm as given by
 * G. West, "Better approximations to cumulative normal functions" (2005). Absolute error is about 1e-15.
 * The coefficients are mathematical constants, not model parameters.
 */
public final class NormalDistribution {

    private NormalDistribution() {
    }

    public static double cdf(double x) {
        double abs = Math.abs(x);
        double tail;
        if (abs > 37) {
            tail = 0;
        } else {
            double e = Math.exp(-abs * abs / 2);
            if (abs < 7.07106781186547) {
                double b = 3.52624965998911E-02 * abs + 0.700383064443688;
                b = b * abs + 6.37396220353165;
                b = b * abs + 33.912866078383;
                b = b * abs + 112.079291497871;
                b = b * abs + 221.213596169931;
                b = b * abs + 220.206867912376;
                tail = e * b;
                b = 8.83883476483184E-02 * abs + 1.75566716318264;
                b = b * abs + 16.064177579207;
                b = b * abs + 86.7807322029461;
                b = b * abs + 296.564248779674;
                b = b * abs + 637.333633378831;
                b = b * abs + 793.826512519948;
                b = b * abs + 440.413735824752;
                tail = tail / b;
            } else {
                double b = abs + 0.65;
                b = abs + 4 / b;
                b = abs + 3 / b;
                b = abs + 2 / b;
                b = abs + 1 / b;
                tail = e / b / 2.506628274631;
            }
        }
        return x > 0 ? 1 - tail : tail;
    }
}
