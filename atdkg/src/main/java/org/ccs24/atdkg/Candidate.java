package org.ccs24.atdkg;

import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

class Candidate {

    int receiverPartyId; // i
    int senderPartyId; // j
    BigInteger[] CArray; // in j
    ECPoint[] CMArray; // in j
    BigInteger sk; // in j, indexed by i

    public Candidate(int receiverPartyId, int senderPartyId, BigInteger[] CArray, ECPoint[] CMArrary, BigInteger sk) {
        this.receiverPartyId = receiverPartyId;
        this.senderPartyId = senderPartyId;
        this.CArray = CArray;
        this.CMArray = CMArrary;
        this.sk = sk;
    }
}
