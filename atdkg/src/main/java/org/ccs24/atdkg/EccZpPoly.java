package org.ccs24.atdkg;

import cc.redberry.rings.IntegersZp;
import cc.redberry.rings.Ring;
import cc.redberry.rings.poly.univar.UnivariateInterpolation;
import cc.redberry.rings.poly.univar.UnivariatePolynomial;

import edu.alibaba.mpc4j.common.tool.polynomial.zp.ZpPoly;
import edu.alibaba.mpc4j.common.tool.polynomial.zp.ZpPolyFactory;
import edu.alibaba.mpc4j.common.tool.utils.BigIntegerUtils;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.security.SecureRandom;

/**
 * Based on mpc4j
 * Zp polynoimal interpolation abstract class
 */
public class EccZpPoly implements ZpPoly {

    protected final SecureRandom secureRandom;

    protected final BigInteger p;

    protected final int l;

    /**
     * Zp finite field
     */
    protected final Ring<cc.redberry.rings.bigint.BigInteger> finiteField;

    EccZpPoly(BigInteger p) {
        this.p = p;
        this.l = ((int) BigIntegerUtils.log2(p)) - 1;
        secureRandom = new SecureRandom();
        finiteField = new IntegersZp(new cc.redberry.rings.bigint.BigInteger(p));
    }

    @Override
    public ZpPolyFactory.ZpPolyType getType() {
        return ZpPolyFactory.ZpPolyType.JDK_LAGRANGE;
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
        assert xArray.length == yArray.length
                : "x.length must be equal to y.length, x.length: " + xArray.length + ", y.length: " + yArray.length;
        assert expectNum >= 1 && xArray.length <= expectNum : "x.length must be in range [1, " + expectNum + "]: " + xArray.length;
        for (BigInteger x : xArray) {
            assert validPoint(x);
        }
        for (BigInteger y : yArray) {
            assert validPoint(y);
        }
        // interpolate
        UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> interpolatePolynomial = polynomialInterpolate(xArray, yArray);
        // make more points if there are not enough
        if (xArray.length < expectNum) {
            cc.redberry.rings.bigint.BigInteger[] pointXs = Arrays.stream(xArray)
                    .map(cc.redberry.rings.bigint.BigInteger::new)
                    .toArray(cc.redberry.rings.bigint.BigInteger[]::new);
            // compute (x - x_1) * ... * (x - x_m')
            UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> p1 = UnivariatePolynomial.one(finiteField);
            for (cc.redberry.rings.bigint.BigInteger pointX : pointXs) {
                p1 = p1.multiply(
                        UnivariatePolynomial.zero(finiteField).createLinear(finiteField.negate(pointX), finiteField.getOne())
                );
            }
            // construct a random poly
            cc.redberry.rings.bigint.BigInteger[] prCoefficients
                    = new cc.redberry.rings.bigint.BigInteger[expectNum - pointXs.length];
            for (int index = 0; index < prCoefficients.length; index++) {
                prCoefficients[index] = new cc.redberry.rings.bigint.BigInteger(BigIntegerUtils.randomNonNegative(p, secureRandom));
            }
            UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> pr
                    = UnivariatePolynomial.create(finiteField, prCoefficients);
            // compute P_0(x) + P_1(x) * P_r(x)
            interpolatePolynomial = interpolatePolynomial.add(p1.multiply(pr));
        }
        return polynomialToBigIntegers(xArray.length, expectNum, interpolatePolynomial);
    }

    @Override
    public BigInteger[] rootInterpolate(int expectNum, BigInteger[] xArray, BigInteger y) {
        assert expectNum >= 1 && xArray.length <= expectNum : "num must be in range [0, " + expectNum + "]: " + xArray.length;
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
        cc.redberry.rings.bigint.BigInteger[] pointXs = Arrays.stream(xArray)
                .map(cc.redberry.rings.bigint.BigInteger::new)
                .toArray(cc.redberry.rings.bigint.BigInteger[]::new);
        UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> polynomial = UnivariatePolynomial.one(finiteField);
        // f(x) = (x - x_0) * (x - x_1) * ... * (x - x_m)
        for (cc.redberry.rings.bigint.BigInteger pointX : pointXs) {
            UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> linear = polynomial.createLinear(
                    finiteField.negate(pointX), finiteField.getOne()
            );
            polynomial = polynomial.multiply(linear);
        }
        if (xArray.length < expectNum) {
            // construct a random poly
            cc.redberry.rings.bigint.BigInteger[] prCoefficients = IntStream.range(0, expectNum - xArray.length)
                    .mapToObj(index -> new cc.redberry.rings.bigint.BigInteger(BigIntegerUtils.randomNonNegative(p, secureRandom)))
                    .toArray(cc.redberry.rings.bigint.BigInteger[]::new);
            UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> dummyPolynomial
                    = UnivariatePolynomial.create(finiteField, prCoefficients);
            dummyPolynomial.set(expectNum - xArray.length, finiteField.getOne());
            // compute P_0(x) * P_r(x)
            polynomial = polynomial.multiply(dummyPolynomial);
        }
        cc.redberry.rings.bigint.BigInteger pointY = new cc.redberry.rings.bigint.BigInteger(y);
        polynomial = polynomial.add(UnivariatePolynomial.constant(finiteField, pointY));

        return rootPolynomialToBigIntegers(xArray.length, expectNum, polynomial);
    }

    /**
     * @param xArray
     * @param yArray
     * @return poly
     */

    protected UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> polynomialInterpolate(java.math.BigInteger[] xArray, java.math.BigInteger[] yArray) {
        cc.redberry.rings.bigint.BigInteger[] points = Arrays.stream(xArray)
                .map(cc.redberry.rings.bigint.BigInteger::new)
                .toArray(cc.redberry.rings.bigint.BigInteger[]::new);
        cc.redberry.rings.bigint.BigInteger[] values = Arrays.stream(yArray)
                .map(cc.redberry.rings.bigint.BigInteger::new)
                .toArray(cc.redberry.rings.bigint.BigInteger[]::new);
        return UnivariateInterpolation.interpolateLagrange(finiteField,
                points,
                values);
    }

    @Override
    public BigInteger evaluate(BigInteger[] coefficients, BigInteger x) {
        assert coefficients.length >= 1 : "coefficient num must be greater than or equal to 1: " + coefficients.length;
        for (BigInteger coefficient : coefficients) {
            assert validPoint(coefficient);
        }

        assert validPoint(x);
        // recover poly
        UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> polynomial = bigIntegersToPolynomial(coefficients);
        // evaluation
        cc.redberry.rings.bigint.BigInteger xRings = new cc.redberry.rings.bigint.BigInteger(x);
        cc.redberry.rings.bigint.BigInteger yRings = polynomial.evaluate(xRings);
        return BigIntegerUtils.byteArrayToBigInteger(yRings.toByteArray());
    }

    @Override
    public BigInteger[] evaluate(BigInteger[] coefficients, BigInteger[] xArray) {
        // recover poly
        UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> polynomial = bigIntegersToPolynomial(coefficients);
        // evaluation
        return polynomialEvaluate(polynomial, xArray);
    }

    protected BigInteger[] polynomialEvaluate
            (UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> polynomial, BigInteger[] xArray) {
        return Arrays.stream(xArray)
                .map(cc.redberry.rings.bigint.BigInteger::new)
                .map(polynomial::evaluate)
                .map(y -> BigIntegerUtils.byteArrayToBigInteger(y.toByteArray()))
                .toArray(BigInteger[]::new);
    }

    protected BigInteger[] polynomialToBigIntegers(int pointNum, int expectNum, UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> polynomial) {
        BigInteger[] coefficients = new BigInteger[coefficientNum(pointNum, expectNum)];
        IntStream.range(0, polynomial.degree() + 1).forEach(degreeIndex -> coefficients[degreeIndex]
                = BigIntegerUtils.byteArrayToBigInteger(polynomial.get(degreeIndex).toByteArray())
        );
        // fill higher degree with zeros
        IntStream.range(polynomial.degree() + 1, coefficients.length).forEach(degreeIndex ->
                coefficients[degreeIndex] = BigInteger.ZERO
        );

        return coefficients;
    }

    private BigInteger[] rootPolynomialToBigIntegers(int pointNum, int expectNum, UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> polynomial) {
        BigInteger[] coefficients = new BigInteger[rootCoefficientNum(pointNum, expectNum)];
        IntStream.range(0, polynomial.degree() + 1).forEach(degreeIndex -> coefficients[degreeIndex]
                = BigIntegerUtils.byteArrayToBigInteger(polynomial.get(degreeIndex).toByteArray())
        );
        // fill higher degree with zeros
        IntStream.range(polynomial.degree() + 1, coefficients.length).forEach(degreeIndex ->
                coefficients[degreeIndex] = BigInteger.ZERO
        );

        return coefficients;
    }

    protected UnivariatePolynomial<cc.redberry.rings.bigint.BigInteger> bigIntegersToPolynomial(
            BigInteger[] coefficients) {
        cc.redberry.rings.bigint.BigInteger[] polyCoefficients = Arrays.stream(coefficients)
                .map(cc.redberry.rings.bigint.BigInteger::new)
                .toArray(cc.redberry.rings.bigint.BigInteger[]::new);

        return UnivariatePolynomial.create(finiteField, polyCoefficients);
    }
}
