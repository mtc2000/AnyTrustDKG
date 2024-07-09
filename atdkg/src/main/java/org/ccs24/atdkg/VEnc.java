package org.ccs24.atdkg;

import com.google.common.primitives.Bytes;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.Ecc;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.EccFactory;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.crypto.kdf.Kdf;
import edu.alibaba.mpc4j.common.tool.crypto.kdf.KdfFactory;
import edu.alibaba.mpc4j.common.tool.utils.BigIntegerUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VEnc {

    public static class VEncProof {
        private BigInteger c;
        private BigInteger z;
        private ECPoint hPrime;

        public BigInteger getC() {
            return c;
        }

        public BigInteger getZ() {
            return z;
        }

        public ECPoint getHPrime() {
            return hPrime;
        }

        public VEncProof(BigInteger c, BigInteger z, ECPoint hPrime) {
            this.c = c;
            this.z = z;
            this.hPrime = hPrime;
        }

        public List<byte[]> serialize() {
            return Arrays.asList(
                        this.c.toByteArray(),
                        this.z.toByteArray(),
                        this.hPrime.getEncoded(compressEncoding)
            );
        }

        public static VEncProof deserialize(List<byte[]> serializationList) {
            byte[][] deserialization = serializationList.toArray(new byte[0][0]);
            BigInteger c = BigIntegerUtils.byteArrayToNonNegBigInteger(deserialization[0]);
            BigInteger z = BigIntegerUtils.byteArrayToNonNegBigInteger(deserialization[1]);
            ECPoint hPrime = ecc.decode(deserialization[2]);
            return new VEncProof(
                    c, z, hPrime
            );
        }
    }

    private final static Integer H2OutputByteLength = 32; // bit length 256
    private static final EccFactory.EccType eccType = EccFactory.EccType.SEC_P256_K1_BC;
    private final static Hash HASH = HashFactory.createInstance(HashFactory.HashType.JDK_SHA256, H2OutputByteLength);
    private final static Kdf H3 = KdfFactory.createInstance(KdfFactory.KdfType.JDK_SHA256);
    private final BigInteger p; // order
    private final BigInteger secretKey; // x
    private final ECPoint publicKey; // g^x
    private final static Ecc ecc = EccFactory.createInstance(eccType);
    private static final ECPoint g = ecc.getG(); // generator

    private final static boolean compressEncoding = true;

    private final SecureRandom secureRandom = new SecureRandom();

    public VEnc() {
        this.p = ecc.getN(); // order
        this.secretKey = ecc.randomZn(secureRandom); // sk, secret key
        this.publicKey = ecc.multiply(g, secretKey);
    }

    public VEnc(SecureRandom skSecureRandom) {
        this.p = ecc.getN(); // order
        this.secretKey = ecc.randomZn(skSecureRandom); // sk, secret key
        this.publicKey = ecc.multiply(g, secretKey);
    }

    public VEnc(ECPoint publicKey) {
        this.p = ecc.getN(); // order
        this.secretKey = new BigInteger("-1"); // sk, secret key
        this.publicKey = publicKey;
    }

    public VEnc(BigInteger secretKey) {
        this.p = ecc.getN(); // order
        this.secretKey = secretKey; // sk, secret key
        this.publicKey = ecc.multiply(g, secretKey);
    }

    public ECPoint getPublicKey() {
        return this.publicKey;
    }

    public List<BigInteger> enc(byte[] msg) {
        // sender does not have sk
        // assert this.secretKey.compareTo(BigInteger.ZERO) < 0;
        BigInteger r = ecc.randomZn(secureRandom); // random r
        ECPoint h = ecc.multiply(g, r);
        ECPoint hPrime = ecc.multiply(this.publicKey, r); // h' = pk^r, h'^r == h^sk
        BigInteger c0 = BigIntegerUtils.byteArrayToNonNegBigInteger( h.getEncoded(compressEncoding));
        BigInteger c1 = BigIntegerUtils.byteArrayToNonNegBigInteger(
                HASH.digestToBytes(hPrime.getEncoded(compressEncoding)))
                .xor(BigIntegerUtils.byteArrayToNonNegBigInteger( msg));

        List<BigInteger> cipher = new ArrayList<>();
        cipher.add(c0);
        cipher.add(c1);
        return cipher;
    }

    public List<BigInteger> multiRecipientEnc(List<byte[]> messages, List<ECPoint> publicKeys) {
        // sender does not have others' sk
        // assert this.secretKey.compareTo(BigInteger.ZERO) < 0;

        assert messages.size() == publicKeys.size();
        BigInteger r = ecc.randomZn(secureRandom); // random r
        ECPoint h = ecc.multiply(g, r);

        List<BigInteger> cipher = new ArrayList<>();
        BigInteger c0 = BigIntegerUtils.byteArrayToNonNegBigInteger(h.getEncoded(compressEncoding));
        cipher.add(c0);

        for (int i = 0; i < messages.size(); i++) {
            ECPoint hPrime = ecc.multiply(publicKeys.get(i), r); // h' = pk^r, h'^r == h^sk
            BigInteger ci = BigIntegerUtils.byteArrayToNonNegBigInteger(
                    HASH.digestToBytes(hPrime.getEncoded(compressEncoding)))
                    .xor(BigIntegerUtils.byteArrayToNonNegBigInteger(messages.get(i)));
            cipher.add(ci);
        }

        return cipher;
    }

    public VEncProof getProof(BigInteger c0, BigInteger c1) {
        // receiver has sk
        // assert this.secretKey.compareTo(BigInteger.ZERO) >= 0;
        ECPoint h = ecc.decode(c0.toByteArray()); // h == c0 == g^r
        ECPoint hPrime = ecc.multiply(h, this.secretKey); // h' = pk^r, h'^r == h^sk
        BigInteger a = ecc.randomZn(secureRandom); // random a

        BigInteger c = BigIntegerUtils.byteArrayToNonNegBigInteger(
                HASH.digestToBytes(Bytes.concat(
                        ecc.multiply(h, a).getEncoded(compressEncoding),
                        ecc.multiply(g, a).getEncoded(compressEncoding),
                        hPrime.getEncoded(compressEncoding),
                        this.publicKey.getEncoded(compressEncoding),
                        g.getEncoded(compressEncoding)
                )));

        BigInteger z = (c.multiply(this.secretKey).add(a)).mod(this.p);

        return new VEncProof(c, z, hPrime);
    }

    public VEncProof getProof(List<BigInteger> CArray, int indexFromZero) {
        return this.getProof(CArray.get(0), CArray.get(1 + indexFromZero));
    }

    public VEncProof getProof(BigInteger[] CArray, int indexFromZero) {
        return this.getProof(CArray[0], CArray[1 + indexFromZero]);
    }

    public static boolean verifyWithC0(VEncProof proof, ECPoint proverPublicKey, BigInteger c0){
        // sender does not have sk
        // assert this.secretKey.compareTo(BigInteger.ZERO) < 0;
        BigInteger c = proof.getC();
        BigInteger z = proof.getZ();
        ECPoint h = ecc.decode(c0.toByteArray()); // h == c0 == g^r
        ECPoint hPrime = proof.getHPrime(); // h' = pk^r, h'^r == h^sk == g^(r*sk)
        BigInteger actualC = BigIntegerUtils.byteArrayToNonNegBigInteger(
                HASH.digestToBytes(Bytes.concat(
                        ecc.subtract(ecc.multiply(h, z), ecc.multiply(hPrime, c)).getEncoded(compressEncoding),
                        ecc.subtract(ecc.multiply(g, z), ecc.multiply(proverPublicKey, c)).getEncoded(compressEncoding),
                        hPrime.getEncoded(compressEncoding),
                        proverPublicKey.getEncoded(compressEncoding),
                        g.getEncoded(compressEncoding)
                )));
        return actualC.equals(c);
    }

    public byte[] decrypt(List<BigInteger> cipher, int indexFromZero) {
        // receiver has sk
        // assert this.secretKey.compareTo(BigInteger.ZERO) >= 0;
        BigInteger c0 = cipher.get(0);
        BigInteger c1 = cipher.get(1 + indexFromZero);
        ECPoint h = ecc.decode(c0.toByteArray()); // h == c0 == g^r
        return (BigIntegerUtils.byteArrayToNonNegBigInteger(
                HASH.digestToBytes(ecc.multiply(h, this.secretKey).getEncoded(compressEncoding))))
                .xor(c1).toByteArray();
    }

    public byte[] decrypt(BigInteger c0, BigInteger c1) {
        // receiver has sk
        // assert this.secretKey.compareTo(BigInteger.ZERO) >= 0;
        ECPoint h = ecc.decode(c0.toByteArray()); // h == c0 == g^r

        return (BigIntegerUtils.byteArrayToNonNegBigInteger(
                HASH.digestToBytes(
                        ecc.multiply(h, this.secretKey).getEncoded(compressEncoding))))
                .xor(c1).toByteArray();
    }
}
