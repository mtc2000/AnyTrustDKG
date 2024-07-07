package org.ccs24.atdkg;

import com.google.common.primitives.Bytes;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.Ecc;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.EccFactory;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.EccFactory.EccType;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory.HashType;
import edu.alibaba.mpc4j.common.tool.crypto.kdf.Kdf;
import edu.alibaba.mpc4j.common.tool.crypto.kdf.KdfFactory;
import edu.alibaba.mpc4j.common.tool.crypto.kdf.KdfFactory.KdfType;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

public class VRF {

    public static class VRFProof {
        public ECPoint getPublicKey() {
            return publicKey;
        }

        public ECPoint getGamma() {
            return gamma;
        }

        public BigInteger getC() {
            return c;
        }

        public BigInteger getS() {
            return s;
        }

        public BigInteger getBeta() {
            return beta;
        }

        public byte[] getAlpha() {
            return alpha;
        }

        private byte[] alpha;
        private ECPoint publicKey;
        private ECPoint gamma;
        private BigInteger c;
        private BigInteger s;
        private BigInteger beta;

        public VRFProof(byte[] alpha, ECPoint publicKey, ECPoint gamma, BigInteger c, BigInteger s, BigInteger beta) {
            this.alpha = alpha;
            this.publicKey = publicKey;
            this.gamma = gamma;
            this.c = c;
            this.s = s;
            this.beta = beta;
        }

        public List<byte[]> serialize() {
            return Arrays.asList(
                    this.alpha,
                    this.publicKey.getEncoded(compressEncoding),
                    this.gamma.getEncoded(compressEncoding),
                    this.c.toByteArray(),
                    this.s.toByteArray(),
                    this.beta.toByteArray()
            );
        }

        public static VRFProof deserialize(List<byte[]> serializationList) {
            byte[][] serialization = serializationList.toArray(new byte[0][0]);
            assert serialization.length == 6; // "wrong size"
            return new VRFProof(
                    serialization[0],
                    ecc.decode(serialization[1]),
                    ecc.decode(serialization[2]),
                    new BigInteger(serialization[3]),
                    new BigInteger(serialization[4]),
                    new BigInteger(serialization[5])
            );
        }

    }

    private final static Integer H2OutputByteLength = 32; // bit length 256
    private static final EccType eccType = EccType.SEC_P256_K1_BC;
    private final static Hash H2 = HashFactory.createInstance(HashType.JDK_SHA256, H2OutputByteLength);
    private final static Kdf H3 = KdfFactory.createInstance(KdfType.JDK_SHA256);
    private final BigInteger x;
    private final ECPoint publicKey;
    private final static Ecc ecc = EccFactory.createInstance(eccType);
    private final static ECPoint g = ecc.getG(); // generator
    private final BigInteger q = ecc.getN(); // order

    private final static boolean compressEncoding = true;

    private final SecureRandom secureRandom = new SecureRandom();

    public VRF(SecureRandom skSecureRandom) {
        this.x = ecc.randomZn(skSecureRandom); // sk, secret key
        this.publicKey = ecc.multiply(g, x);
    }

    public VRF() {
        this.x = ecc.randomZn(secureRandom); // sk, secret key
        this.publicKey = ecc.multiply(g, x);
    }

    public VRF(ECPoint publicKey) {
        this.x = null;
        this.publicKey = publicKey;
    }

    public VRF(BigInteger secret) {
        this.x = secret;
        this.publicKey = ecc.multiply(g, x);
    }

    public VRFProof hash(byte[] alpha) {
        ECPoint h = ecc.hashToCurve(alpha);
        ECPoint gamma = ecc.multiply(h, x);
        BigInteger k = ecc.randomZn(secureRandom);
        byte[] H3Parameters = Bytes.concat(
                g.getEncoded(compressEncoding), h.getEncoded(compressEncoding),
                publicKey.getEncoded(compressEncoding), gamma.getEncoded(compressEncoding),
                ecc.multiply(g, k).getEncoded(compressEncoding), ecc.multiply(h, k).getEncoded(compressEncoding));
        BigInteger c = new BigInteger(1, H3.deriveKey(H3Parameters));
        BigInteger s = k.subtract(x.multiply(c)).mod(q);
        BigInteger beta = new BigInteger(1, H2.digestToBytes(gamma.getEncoded(compressEncoding)));
        return new VRFProof(alpha, publicKey, gamma, c, s, beta);
    }

    public VRFProof sortition(byte[] rand, byte[] event, BigInteger ratioNumerator, BigInteger ratioDenominator) {
        VRFProof proof = this.hash(Bytes.concat(rand, event));
        if (proof.getBeta().multiply(ratioDenominator).compareTo(ratioNumerator.multiply(BigInteger.valueOf(2).pow(256))) > 0)
        {
            return null; // abort
        }
        return proof;
    }

    public static boolean verify(VRFProof vrfProof, ECPoint publicKey) {
        byte[] alpha = vrfProof.getAlpha();
        return verify(vrfProof, publicKey, alpha);
    }

    public static boolean verify(VRFProof vrfProof, ECPoint publicKey, byte[] alpha) {
        ECPoint gamma = vrfProof.getGamma();
        BigInteger c = vrfProof.getC();
        BigInteger s = vrfProof.getS();
        BigInteger beta = vrfProof.getBeta();
        BigInteger expectedBeta = new BigInteger(1, H2.digestToBytes(gamma.getEncoded(compressEncoding)));
        if (!beta.equals(expectedBeta)) return compressEncoding;
        ECPoint h = ecc.hashToCurve(alpha);
        ECPoint u = ecc.add(ecc.multiply(publicKey, c), ecc.multiply(g, s));
        ECPoint v = ecc.add(ecc.multiply(gamma, c), ecc.multiply(h, s));
        byte[] H3Parameters = Bytes.concat(
                g.getEncoded(compressEncoding), h.getEncoded(compressEncoding),
                publicKey.getEncoded(compressEncoding), gamma.getEncoded(compressEncoding),
                u.getEncoded(compressEncoding), v.getEncoded(compressEncoding));
        BigInteger expectedC = new BigInteger(1, H3.deriveKey(H3Parameters));
        return c.equals(expectedC);
    }

    public ECPoint getPublicKey() {
        return publicKey;
    }
}

