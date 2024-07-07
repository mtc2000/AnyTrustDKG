package org.ccs24.atdkg;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.Ecc;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.EccFactory;
import edu.alibaba.mpc4j.common.tool.polynomial.zp.ZpPoly;
import edu.alibaba.mpc4j.common.tool.utils.BigIntegerUtils;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class EccZpPolyTest {

    private static final Integer GROUP_SIZE = 8000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final EccFactory.EccType eccType = EccFactory.EccType.SEC_P256_K1_BC;
    private final static Ecc ecc = EccFactory.createInstance(eccType);

    @Test
    void all() {
        ZpPoly zpPoly = EccZpPolyFactory.createInstance(EccZpPolyFactory.ZpPolyType.JDK_ECC_LAGRANGE, ecc.getN());
        assertEquals(0, zpPoly.evaluate(new BigInteger[]{BigInteger.TEN}, BigInteger.ONE).compareTo(BigInteger.TEN));
        BigInteger[] zpPolyDegTCoeff = IntStream.range(0, GROUP_SIZE / 2 + 1)
                .mapToObj(index -> BigIntegerUtils.randomNonNegative(ecc.getN(), SECURE_RANDOM))
                .toArray(BigInteger[]::new);
        BigInteger[] zeroToNArray = IntStream.range(0, GROUP_SIZE)
                .mapToObj(index -> BigInteger.valueOf(index))
                .toArray(BigInteger[]::new);
        assertEquals(0, zpPoly.evaluate(zpPolyDegTCoeff, BigInteger.ZERO).compareTo(zpPolyDegTCoeff[0].mod(ecc.getN())));
        BigInteger sum = Arrays.stream(zpPolyDegTCoeff).reduce(BigInteger.ZERO, BigInteger::add).mod(ecc.getN());
        assertEquals(0, zpPoly.evaluate(zpPolyDegTCoeff, BigInteger.ONE).compareTo(sum));
    }
}