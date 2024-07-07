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

public class SimpleProof {

    private final static Integer H2OutputByteLength = 32; // bit length 256
    private static final EccType eccType = EccType.SEC_P256_K1_BC;
    private final static Hash H2 = HashFactory.createInstance(HashType.JDK_SHA256, H2OutputByteLength);
    private final static Kdf H3 = KdfFactory.createInstance(KdfType.JDK_SHA256);
    private final static Ecc ecc = EccFactory.createInstance(eccType);
    private final static ECPoint g = ecc.getG(); // generator
    private final static BigInteger q = ecc.getN(); // order
    private final static boolean compressEncoding = true;


    public static List<byte[]> getProof (BigInteger secret) {
        SecureRandom secureRandom = new SecureRandom();
        ECPoint publicKey = ecc.multiply(g, secret);
        BigInteger nonce = ecc.randomZn(secureRandom);
        ECPoint gNonce = ecc.multiply(g, nonce);
        BigInteger challenge = new BigInteger(1, H2.digestToBytes(
                Bytes.concat(
                        gNonce.getEncoded(compressEncoding),
                        publicKey.getEncoded(compressEncoding)
                )
        ));
        BigInteger z = (nonce.add(challenge.multiply(secret))).mod(q);
        return Arrays.asList(
                challenge.toByteArray(),
                z.toByteArray()
        );
    }

    public static boolean verifyProof (List<byte[]> proof, ECPoint publicKey) {
        assert proof.size() == 2;
        BigInteger challenge = new BigInteger(proof.get(0));
        BigInteger z = new BigInteger(proof.get(1));
        ECPoint expectedGNonce = ecc.subtract(ecc.multiply(g, z), ecc.multiply(publicKey, challenge));
        BigInteger expectedChallenge = new BigInteger(1, H2.digestToBytes(
                Bytes.concat(
                        expectedGNonce.getEncoded(compressEncoding),
                        publicKey.getEncoded(compressEncoding)
                )
        ));
        return challenge.equals(expectedChallenge);
    }

}

