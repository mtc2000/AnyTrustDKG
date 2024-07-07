package org.ccs24.atdkg;

import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class VEncTest {

    public static final Integer GROUP_SIZE = 10;

    @BeforeEach
    void setUp() {
    }

    @Test
    void all() {
        VEnc VEncReceiver = new VEnc();
        ECPoint VEncPK = VEncReceiver.getPublicKey();
        VEnc VEncSender = new VEnc(VEncPK);
        List<BigInteger> cipher = VEncSender.enc("message".getBytes());
        byte[] plaintext = VEncReceiver.decrypt(cipher, 0);
        assertArrayEquals(plaintext, "message".getBytes());

        VEnc.VEncProof proof = VEncReceiver.getProof(cipher.get(0), cipher.get(1));
        boolean result = VEnc.verifyWithC0(proof, VEncPK, cipher.get(0));
        assertTrue(result);

        List<ECPoint> publicKeys = new ArrayList<>();
        Map<Integer, VEnc> VEncMap = new HashMap<>();
        List<byte[]> messages = new ArrayList<>();
        for (int i = 0; i < GROUP_SIZE; i++) {
            VEncMap.put(i, new VEnc());
            publicKeys.add(VEncMap.get(i).getPublicKey());
            messages.add(String.valueOf(i).getBytes());
        }

        int senderId = 0;
        int receiverId = GROUP_SIZE / 2;

        cipher = VEncMap.get(senderId).multiRecipientEnc(
                messages, publicKeys
        );

        plaintext = VEncMap.get(receiverId).decrypt(cipher.get(0), cipher.get(receiverId + 1));

        assertArrayEquals(String.valueOf(receiverId).getBytes(), plaintext);
    }

    @AfterEach
    void tearDown() {
    }
}