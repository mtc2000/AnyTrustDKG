package org.ccs24.atdkg;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.polynomial.zp.ZpPoly;
import edu.alibaba.mpc4j.common.tool.polynomial.zp.ZpPolyFactory.ZpPolyType;
import edu.alibaba.mpc4j.common.tool.utils.BigIntegerUtils;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * based on mpc4j

 */
public class EccZpPolyNTL implements ZpPoly {

    protected final SecureRandom secureRandom;
    protected final BigInteger p;
    protected final int l;

    static {
        System.loadLibrary(CommonConstants.MPC4J_NATIVE_TOOL_NAME);
    }

    private final byte[] pByteArray;
    private final int pByteLength;

    public EccZpPolyNTL(BigInteger p) {
        this.p = p;
        this.l = ((int) BigIntegerUtils.log2(p)) - 1;
        secureRandom = new SecureRandom();
        pByteArray = BigIntegerUtils.bigIntegerToByteArray(p);
        pByteLength = pByteArray.length;
    }

    @Override
    public ZpPolyType getType() {
        return ZpPolyType.NTL;
    }

    @Override
    public int getL() {
        return l;
    }

    @Override
    public BigInteger getPrime() {
        return p;
    }

    @Override
    public boolean validPoint(BigInteger point) {
        return BigIntegerUtils.greaterOrEqual(point, BigInteger.ZERO) && BigIntegerUtils.less(point, p);
    }

    @Override
    public BigInteger[] interpolate(int expectNum, BigInteger[] xArray, BigInteger[] yArray) {
        assert xArray.length == yArray.length;
        assert expectNum >= 1 && xArray.length <= expectNum;
        for (BigInteger x : xArray) {
            assert validPoint(x);
        }
        for (BigInteger y : yArray) {
            assert validPoint(y);
        }
        byte[][] xByteArray = BigIntegerUtils.nonNegBigIntegersToByteArrays(xArray, pByteLength);
        byte[][] yByteArray = BigIntegerUtils.nonNegBigIntegersToByteArrays(yArray, pByteLength);

        byte[][] polynomial = nativeInterpolate(pByteArray, expectNum, xByteArray, yByteArray);

        return BigIntegerUtils.byteArraysToNonNegBigIntegers(polynomial);
    }

    @Override
    public BigInteger[] rootInterpolate(int expectNum, BigInteger[] xArray, BigInteger y) {
        assert expectNum >= 1 && xArray.length <= expectNum;
        if (xArray.length == 0) {

            BigInteger[] coefficients = new BigInteger[expectNum + 1];
            for (int index = 0; index < expectNum; index++) {
                coefficients[index] = BigIntegerUtils.randomNonNegative(p, secureRandom);
            }

            coefficients[expectNum] = BigInteger.ONE;
            return coefficients;
        }

        for (BigInteger x : xArray) {
            assert validPoint(x);
        }
        assert validPoint(y);
        byte[][] xByteArray = BigIntegerUtils.nonNegBigIntegersToByteArrays(xArray, pByteLength);
        byte[] yBytes = BigIntegerUtils.nonNegBigIntegerToByteArray(y, pByteLength);

        byte[][] polynomial = nativeRootInterpolate(pByteArray, expectNum, xByteArray, yBytes);

        return BigIntegerUtils.byteArraysToNonNegBigIntegers(polynomial);
    }

    private static native byte[][] nativeRootInterpolate(byte[] primeBytes, int expectNum, byte[][] xArray, byte[] yBytes);
    private static native byte[][] nativeInterpolate(byte[] primeBytes, int expectNum, byte[][] xArray, byte[][] yArray);

    @Override
    public BigInteger evaluate(BigInteger[] coefficients, BigInteger x) {
        assert coefficients.length >= 1;
        for (BigInteger coefficient : coefficients) {
            validPoint(coefficient);
        }

        assert validPoint(x);

        byte[][] coefficientByteArrays = BigIntegerUtils.nonNegBigIntegersToByteArrays(coefficients, pByteLength);
        byte[] xByteArray = BigIntegerUtils.nonNegBigIntegerToByteArray(x, pByteLength);

        byte[] yByteArray = nativeSingleEvaluate(pByteArray, coefficientByteArrays, xByteArray);

        return BigIntegerUtils.byteArrayToNonNegBigInteger(yByteArray);
    }

    private static native byte[] nativeSingleEvaluate(byte[] primeBytes, byte[][] coefficients, byte[] x);

    @Override
    public BigInteger[] evaluate(BigInteger[] coefficients, BigInteger[] xArray) {
        assert coefficients.length >= 1;
        for (BigInteger coefficient : coefficients) {
            assert validPoint(coefficient);
        }

        for (BigInteger x : xArray) {
            assert validPoint(x);
        }

        byte[][] coefficientByteArrays = BigIntegerUtils.nonNegBigIntegersToByteArrays(coefficients, pByteLength);
        byte[][] xByteArrays = BigIntegerUtils.nonNegBigIntegersToByteArrays(xArray, pByteLength);

        byte[][] yByteArrays = nativeEvaluate(pByteArray, coefficientByteArrays, xByteArrays);

        return BigIntegerUtils.byteArraysToNonNegBigIntegers(yByteArrays);
    }

    private static native byte[][] nativeEvaluate(byte[] primeBytes, byte[][] coefficients, byte[][] xArray);
}
