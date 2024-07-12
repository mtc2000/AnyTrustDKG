package org.ccs24.atdkg;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.Ecc;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.EccFactory;
import edu.alibaba.mpc4j.common.tool.utils.BigIntegerUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DkgNoteTest {

    private static final Logger LOGGER = Logger.getLogger(DkgNoteTest.class.getName());
    static SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final StopWatch STOP_WATCH = new StopWatch();

    public static void main(String[] args) {
        DkgNoteTest test = new DkgNoteTest();
        if (args.length < 2) {
            System.err.println("usage: <program> <start> <end>. start=9, end=15 on default");
            test.allTest(9, 9);
            return;
        } else {
            test.allTest(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        }
    }

    public void allTest() {
        all(8);
        for (int logN = 8; logN < 9; logN++) {
            all(logN);
        }
    }

    public void allTest(int a, int b) {
        all(8);
        for (int logN = a; logN <= b; logN++) {
            all(logN);
        }
    }

    private void all(int logN) {
        int GROUP_SIZE = 1 << logN;
        int AT_SIZE = Math.min(GROUP_SIZE / 2 + 1, 38);
        // LOGGER.info("===Any-Trust comittee size===" + AT_SIZE + "=====");
        LOGGER.info("===Test start===" + "; Nodes: 2^" + logN + "=====");
        EccFactory.EccType eccType = EccFactory.EccType.SEC_P256_K1_BC;
        Ecc ecc = EccFactory.createInstance(eccType);
        VEnc vEnc = new VEnc();
        ArrayList<ECPoint> publicKeys = fakeKeys(logN, ecc, vEnc.getPublicKey());
        DkgNode dkgNode = new DkgNode(GROUP_SIZE, ecc, publicKeys, vEnc);
        byte[] rand = new byte[32];
        SECURE_RANDOM.nextBytes(rand);
        //LOGGER.info("===Deal start==="+ "Poly: "+ polyType+ "; Nodes: 2^"+ logN+ "=====");
        STOP_WATCH.start();
        DkgNode.DealOutput output = dkgNode.deal(rand, false);
        System.out.println("deal end");
        STOP_WATCH.stop();
        long dealtime = STOP_WATCH.getTime();
        STOP_WATCH.reset();
        // LOGGER.info("===Deal ends==="+ "Poly: "+ polyType+ "; Nodes: 2^"+ logN + "; deal time:" +  dealtime+"ms");
        ArrayList<DkgNode.DealOutput> fakeList = fakeList(AT_SIZE, output);

        //LOGGER.info("===Good-case Verify start==="+ "Poly: "+ polyType+ "; Nodes: 2^"+ logN+ "=====");
        STOP_WATCH.start();
        dkgNode.verifyDeals(fakeList);
        STOP_WATCH.stop();
        long goodVerifyTime = STOP_WATCH.getTime();
        STOP_WATCH.reset();
//        LOGGER.info("===Good-case Verify ends==="+ "Poly: "+ polyType+ "; Nodes: 2^"+ logN + "; verify time:" +  goodVerifyTime+"ms");
//        LOGGER.info("===Worst-case Verify start==="+ "Poly: "+ polyType+ "; Nodes: 2^"+ logN+ "=====");
        DkgNode.DealOutput badOutput = output.alter();
        ArrayList<DkgNode.DealOutput> badList = fakeList(AT_SIZE, badOutput);
        STOP_WATCH.start();
        List<VEncComplaint> complaints = dkgNode.verifyDeals(badList);
        STOP_WATCH.stop();
        long badVerifyTime = STOP_WATCH.getTime();
        STOP_WATCH.reset();
//        LOGGER.info("===Worst-case Verify ends==="+ "Poly: "+ polyType+ "; Nodes: 2^"+ logN+ "; verify time:" +  badVerifyTime+"ms");
//        LOGGER.info("===Worst-case Complaint start==="+ "Poly: "+ polyType+ "; Nodes: 2^"+ logN+ "=====");
        VEncComplaint complaint = complaints.get(1);
        // verify complaint for GROUP-SIZE times
        STOP_WATCH.start();
        BigInteger fakeExponent = ecc.randomZn(SECURE_RANDOM);
        BigInteger badC0 = BigIntegerUtils.byteArrayToNonNegBigInteger(badOutput.cmArrary[0].getEncoded(true));
        for (int i = 0; i < GROUP_SIZE; i++) {
            VEnc.verifyWithC0(complaint.complaintProof, vEnc.getPublicKey(), badC0);
            ecc.getG().multiply(fakeExponent);
        }
        STOP_WATCH.stop();
        long complaintTime = STOP_WATCH.getTime();
        STOP_WATCH.reset();
        LOGGER.info("===END=== 2^" + logN + ": DEAL (" + dealtime + "ms); " +
                "GoodVerify (" + goodVerifyTime + " ms); GoodTotal (" + (dealtime + goodVerifyTime) + "ms);" +
                " BadVerify (" + (badVerifyTime + complaintTime) + "ms); BadTotal (" + (dealtime + badVerifyTime + complaintTime) + " ms)");
    }

    private ArrayList<ECPoint> fakeKeys(int logN, Ecc ecc, ECPoint pk) {
        ArrayList<ECPoint> pks = IntStream.range(0, 1 << logN).parallel()
                .mapToObj(i -> ecc.randomPoint(SECURE_RANDOM))
                .collect(Collectors.toCollection(ArrayList::new));
        pks.set(1, pk);
        return pks;
    }

    private ArrayList<DkgNode.DealOutput> fakeList(int numCopy, DkgNode.DealOutput output) {
        ArrayList<DkgNode.DealOutput> list = new ArrayList<>();
        for (int i = 0; i < numCopy; i++) {
            list.add(output.copy());
        }
        return list;
    }

}
