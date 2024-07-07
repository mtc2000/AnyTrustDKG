package org.ccs24.atdkg;

import edu.alibaba.mpc4j.common.tool.polynomial.zp.ZpPoly;

import java.math.BigInteger;

/**
 * based on mpc4j
 * Zp poly interpolation factory class
 */
public class EccZpPolyFactory {
    private EccZpPolyFactory() {
        // empty
    }

    /**
     * Zp poly interpolation type
     */
    public enum ZpPolyType {
        /**
         * JDK implementation
         */
        JDK_ECC_LAGRANGE,
        NTL_ECC_LAGRANGE
    }

    /**
     * @param type interpolation type
     * @param p    finite field degree
     * @return poly instance
     */
    public static ZpPoly createInstance(ZpPolyType type, BigInteger p) {
        switch (type) {
            case JDK_ECC_LAGRANGE:
                return new EccZpPoly(p);
            case NTL_ECC_LAGRANGE:
                return new EccZpPolyNTL(p);
            default:
                throw new IllegalArgumentException("Invalid " + ZpPolyType.class.getSimpleName() + ": " + type.name());
        }
    }
}
