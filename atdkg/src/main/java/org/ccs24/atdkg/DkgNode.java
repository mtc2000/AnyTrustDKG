package org.ccs24.atdkg;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.Ecc;
import edu.alibaba.mpc4j.common.tool.polynomial.zp.ZpPoly;
import edu.alibaba.mpc4j.common.tool.utils.BigIntegerUtils;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DkgNode {

    private static int ID = 1;
    private final Integer GROUP_SIZE;
    private Integer AT_SIZE;
    private Integer DEGREE_T;
    private SecureRandom SECURE_RANDOM;

    private Ecc ecc;
    private ZpPoly zpPoly;
    public final List<ECPoint> VEncPublicKeys;

    private final VEnc myVEnc;

    private final VRF myVRF;

    private BigInteger[] zeroToNArray;

    public DkgNode(Integer groupSize, Ecc ecc, ArrayList<ECPoint> publicKeys, VEnc vEnc) {
        GROUP_SIZE = groupSize;
        this.ecc = ecc;
        this.zpPoly = EccZpPolyFactory.createInstance(EccZpPolyFactory.ZpPolyType.JDK_ECC_LAGRANGE, ecc.getN());
        AT_SIZE = Math.min(GROUP_SIZE / 2 + 1, 29);
        DEGREE_T = GROUP_SIZE / 2 + 1;
        SECURE_RANDOM = new SecureRandom();
        this.myVEnc = vEnc;
        this.myVRF = new VRF();
        this.VEncPublicKeys = publicKeys;
        //this.treePoly = treePoly;
        zeroToNArray = IntStream.range(0, GROUP_SIZE + 1)
                .mapToObj(BigInteger::valueOf)
                .toArray(BigInteger[]::new);
    }

    public DealOutput deal(byte[] rand, boolean corrupted) {
        VRF vrf = myVRF;
        VRF.VRFProof proof = vrf.sortition(
                rand,
                "deal".getBytes(),
                BigInteger.valueOf(AT_SIZE),
                BigInteger.valueOf(GROUP_SIZE)
        );

        BigInteger[] randZpPolyDegTCoeff = IntStream.range(0, DEGREE_T)
                .mapToObj(index -> BigIntegerUtils.randomNonNegative(ecc.getN(), SECURE_RANDOM))
                .toArray(BigInteger[]::new);


//        BigInteger[] randZpPoly = (treePoly)? zpTreePoly.evaluate(padTree(randZpPolyDegTCoeff)): zpPoly.evaluate(randZpPolyDegTCoeff, zeroToNArray);
        BigInteger[] randZpPoly = zpPoly.evaluate(randZpPolyDegTCoeff, zeroToNArray);
        ECPoint g = ecc.getG();
        ECPoint[] CMArray = Arrays.stream(randZpPoly).map(f -> ecc.multiply(g, f))
                .toArray(ECPoint[]::new);
        List<byte[]> randZpPolyBytes = Arrays.stream(randZpPoly)
                .map(BigInteger::toByteArray)
                .collect(Collectors.toList());

        randZpPolyBytes.remove(0);

        VEnc thisVEnc = myVEnc;
        BigInteger[] CArray = thisVEnc.multiRecipientEnc(
                        randZpPolyBytes, VEncPublicKeys)
                .toArray(new BigInteger[0]);

        List<byte[]> cm0Proof = SimpleProof.getProof(
                randZpPoly[0] // f(0)
        );

        if (corrupted) {
            for (int i = 1; i < CArray.length; i += 2) {
                CArray[i] = BigInteger.ONE;
            }
        }

        return new DealOutput(proof, CMArray, CArray, cm0Proof);
    }

    public List<VEncComplaint> verifyDeals(List<DealOutput> dealOutputs) {
        ArrayList<VEncComplaint> complaints = new ArrayList<>();
        BigInteger[] CMDualArray = creatDualArray();
        for (DealOutput output : dealOutputs) {
            BigInteger[] CArray = output.cArray;
            VRF.VRFProof vrfProof = output.proof;
            ECPoint[] CMArray = output.cmArrary;
            List<byte[]> cm0Proof = output.cm0Proof;
            ECPoint[] PowerArray = new ECPoint[GROUP_SIZE + 1];
            Arrays.setAll(PowerArray, i -> ecc.multiply(CMArray[i], CMDualArray[i]));
            ECPoint result = Arrays.stream(PowerArray).reduce(
                    ecc::add).orElse(null);
            if (!ecc.getInfinity().equals(result)) {
                System.out.println("wrong transcript");
                continue;
            }
            if (!SimpleProof.verifyProof(cm0Proof, CMArray[0])) {
                // fail CM0 verification
                continue;
            }
            byte[] sender_f_i_bytes = myVEnc.decrypt(CArray[0], CArray[ID + 1]);
            BigInteger sender_f_i = BigIntegerUtils.byteArrayToBigInteger(sender_f_i_bytes);
            if (!CMArray[ID + 1].equals(ecc.multiply(ecc.getG(), sender_f_i))) {    // generate a complaint
                VEnc.VEncProof vEncProof = myVEnc.getProof(CArray, ID);
                VEncComplaint complaint = new VEncComplaint(
                        ID, 0, vEncProof
                );
                complaints.add(complaint);
            }
        }
        return complaints;
    }

    private BigInteger[] creatDualArray() {
        BigInteger[] CMDualArray = new BigInteger[GROUP_SIZE + 1];
        BigInteger[] randZpPolyDegNMinusTCoeff = IntStream.range(0, GROUP_SIZE - DEGREE_T)
                .mapToObj(index -> BigIntegerUtils.randomNonNegative(ecc.getN(), SECURE_RANDOM))
                .toArray(BigInteger[]::new);
        BigInteger[] numeratorArray = zpPoly.evaluate(randZpPolyDegNMinusTCoeff, zeroToNArray);
        BigInteger[] denominatorArray = new BigInteger[GROUP_SIZE + 1];
        for (int i = 0; i < GROUP_SIZE + 1; i++) {
            BigInteger pi = BigInteger.ONE;
            for (int j = 0; j < GROUP_SIZE + 1; j++) {
                if (i != j) {
                    pi = pi.multiply(BigInteger.valueOf(i - j)).mod(ecc.getN());
                }
            }
            denominatorArray[i] = pi;
        }
        Arrays.setAll(CMDualArray, i ->
                numeratorArray[i]
                        .multiply(denominatorArray[i].modInverse(ecc.getN()))
                        .mod(ecc.getN()));
        return CMDualArray;
    }

    private BigInteger[] padTree(BigInteger[] array) {
        BigInteger[] padArray = new BigInteger[GROUP_SIZE + 2];
        for (int i = 0; i < padArray.length; i++) {
            if (i < array.length) {
                padArray[i] = array[i];
            } else {
                padArray[i] = BigInteger.ZERO;
            }
        }
        return padArray;
    }

    public class DealOutput {
        VRF.VRFProof proof;
        ECPoint[] cmArrary;

        BigInteger[] cArray;

        List<byte[]> cm0Proof;

        DealOutput(VRF.VRFProof proof, ECPoint[] cmArrary, BigInteger[] cArray, List<byte[]> cm0Proof) {
            this.proof = proof;
            this.cmArrary = cmArrary;
            this.cArray = cArray;
            this.cm0Proof = cm0Proof;
        }

        public DealOutput copy() {
            VRF.VRFProof proof = this.proof;
            ECPoint[] cmArray = new ECPoint[this.cmArrary.length];
            for (int i = 0; i < this.cmArrary.length; i++) {
                cmArray[i] = this.cmArrary[i].add(ecc.getInfinity());
            }

            BigInteger[] cArray = Arrays.copyOf(this.cArray, this.cArray.length);
            List<byte[]> cm0Proof = new ArrayList<>();
            for (byte[] p:
            this.cm0Proof) {
                cm0Proof.add(p.clone());
            }
            return new DealOutput(proof, cmArray, cArray, cm0Proof);
        }

        public DealOutput alter() {
            VRF.VRFProof proof = this.proof;
            ECPoint[] cmArray = new ECPoint[this.cmArrary.length];
            for (int i = 0; i < this.cmArrary.length; i++) {
                cmArray[i] = this.cmArrary[i].add(ecc.getInfinity());
            }
            BigInteger[] cArray = Arrays.copyOf(this.cArray, this.cArray.length);
            cArray[0] = BigIntegerUtils.byteArrayToBigInteger(ecc.randomPoint(SECURE_RANDOM).getEncoded(true));
            List<byte[]> cm0Proof = new ArrayList<>();
            for (byte[] p:
                    this.cm0Proof) {
                cm0Proof.add(p.clone());
            }
            return new DealOutput(proof, cmArray, cArray, cm0Proof);
        }

    }
}