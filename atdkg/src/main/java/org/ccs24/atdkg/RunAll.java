package org.ccs24.atdkg;

import com.google.common.primitives.Bytes;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.Ecc;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.EccFactory;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.polynomial.zp.ZpPoly;
import edu.alibaba.mpc4j.common.tool.utils.BigIntegerUtils;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.math.ec.ECPoint;
import org.codehaus.plexus.util.CollectionUtils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RunAll {

    public static final int DEFAULT_PORT = 10000;

    public static Pair<String, Integer> ownPartyLocation;
    // include self & blockchain
    public static Pair<String, Integer> blockchainLocation;

    public static int ownPartyId;
    public static int blockchainPartyId;

    public static UniversalNettyRpcManager rpcManager;


    private static Integer GROUP_SIZE;
    private static Integer AT_SIZE;

    private static Integer DEGREE_T;
    private static SecureRandom SECURE_RANDOM = new SecureRandom();

    private static boolean allowCorruption = false;

    private static final boolean verboseLog = false;

    private static final long DEFAULT_DEAL_TIME = 0;
    private static final long BROADCAST_A_TIMEOUT = 20000;
    private static final long BROADCAST_B_TIMEOUT = 10000;
    //    private static final long DEFAULT_AGREE_TIME = 10000;
    private static final long MULTICAST_A_TIMEOUT = 2000;
    private static final long MULTICAST_B_TIMEOUT = 10000;
//    private static final long MULTICAST_C_TIMEOUT = 5000;

    private static final EccFactory.EccType eccType = EccFactory.EccType.SEC_P256_K1_BC;
    private final static Ecc ecc = EccFactory.createInstance(eccType);

    private final static Integer H2OutputByteLength = 32; // bit length 256
    private final static Hash HASH = HashFactory.createInstance(HashFactory.HashType.JDK_SHA256, H2OutputByteLength);

    private final static ZpPoly zpPoly = EccZpPolyFactory.createInstance(EccZpPolyFactory.ZpPolyType.JDK_ECC_LAGRANGE, ecc.getN());

    private final static boolean compressEncoding = true;

    /**
     * test protocol description
     */
    private static final PtoDesc PTO_DESC = RpcPto.getInstance();

    public static List<ECPoint> VEncPublicKeys = new ArrayList<>();

    public static Map<Integer, VEnc> VEncMap = new HashMap<>();
    public static Map<Integer, ECPoint> VEncPublicKeyMap = new HashMap<>();

    // from one party to blockchain
    // blocking receiving first packet
    // timeout receiving coming packets
    public static long dealBroadcastTaskId = 0;

    // from blockchain to all parties
    // timeout receiving one packet with the header
    public static long dealBlockchainRelayTaskId = 1;

    // from one party to all other parties
    // send one packet
    // do not send if no complaint
    // timeout receiving packet with header
    public static long agreeComplaintMulticastTaskId = 2;

    // from one party to blockchain
    // send one packet even if no complaint
    // do not send if no complaint
    // timeout receiving packets
    public static long agreeComplaintBroadcastTaskId = 3;

    // should wait long enough here

    // from blockchain to all parties
    // send one empty packet even if no complaint
    // timeout receiving packets
    public static long agreeComplaintBlockchainRelayTaskId = 4;

    public static Map<Integer, Set<Integer>> disqualifiedMap;
    public static Map<Integer, Set<Integer>> qualifiedMap;
    public static Map<Integer, Map<Integer, VEncComplaint>> complaintMap;
    public static Map<Integer, List<VEncComplaint>> receivedComplaintsMap;
    public static Map<Integer, VEncComplaint> uniqueComplaintMap;
    public static Map<Integer, VRF> VRFMap;
    public static Map<Integer, ECPoint> VRFPkMap;
    public static Map<Integer, Map<Integer, Candidate>> candidateMap;


    public static void initRpc() throws InterruptedException {
        NettyTimeoutRpc ownPartyRpc = rpcManager.getRpc(ownPartyId);
        System.err.println("initChannels");
        ownPartyRpc.initChannels();
        Thread.sleep(20000L + 100L * GROUP_SIZE * 2);
        System.err.println("connect");
        ownPartyRpc.connect();
        ownPartyRpc.reset();
    }

    public static List<byte[]> deal(byte[] rand, int partyId) {
        return deal(rand, partyId, false);
    }

    public static List<byte[]> deal(byte[] rand, int partyId, boolean corrupted) {

        NettyTimeoutRpc ownRpc = rpcManager.getRpc(partyId);

        VRF vrf = VRFMap.get(partyId);
        VRF.VRFProof proof = vrf.sortition(
                rand,
                "deal".getBytes(),
                BigInteger.valueOf(AT_SIZE),
                BigInteger.valueOf(GROUP_SIZE)
        );

        ownRpc.log("deal-a");

        if (null == proof) {
            return null;
        } else {
            System.err.println("dealer");
        }

        BigInteger[] randZpPolyDegTCoeff = IntStream.range(0, DEGREE_T)
                .mapToObj(index -> BigIntegerUtils.randomNonNegative(ecc.getN(), SECURE_RANDOM))
                .toArray(BigInteger[]::new);
        BigInteger[] zeroToNArray = IntStream.range(0, GROUP_SIZE + 1)
                .mapToObj(BigInteger::valueOf)
                .toArray(BigInteger[]::new);

        BigInteger[] randZpPoly = zpPoly.evaluate(randZpPolyDegTCoeff, zeroToNArray);
        ECPoint g = ecc.getG();
        List<byte[]> CMArrayEncoded = Arrays.stream(randZpPoly).map(f -> ecc.multiply(g, f))
                .map(x -> x.getEncoded(compressEncoding))
                .collect(Collectors.toList());
        List<byte[]> randZpPolyBytes = Arrays.stream(randZpPoly)
                .map(BigInteger::toByteArray)
                .collect(Collectors.toList());

        // remove f(0)
        randZpPolyBytes.remove(0);

        VEnc thisVEnc = VEncMap.get(partyId);
        List<BigInteger> CArray = thisVEnc.multiRecipientEnc(
                randZpPolyBytes, VEncPublicKeys);

        List<byte[]> cm0Proof = SimpleProof.getProof(
                randZpPoly[0] // f(0)
        );

        // corrupt all parties
        if (corrupted) {
            // keep c0
            for (int i = 1; i < CArray.size(); i ++) {
                CArray.set(i, BigInteger.ONE);
            }
        }

        List<byte[]> CArrayEncoded = CArray.stream()
                .map(BigInteger::toByteArray)
                .collect(Collectors.toList());

        // broadcast

        List<byte[]> payload = new ArrayList<>();
        payload.add(// Convert int to byte array
                IntUtils.intToByteArray(partyId) // 1
        );
        payload.addAll(
                proof.serialize() // 6
        );
        payload.addAll(
                CMArrayEncoded // GROUP_SIZE + 1
        );
        payload.addAll(
                CArrayEncoded // GROUP_SIZE + 1
        );
        payload.addAll(
                cm0Proof // 2
        );

    //    System.err.print("size of deal: ");
    //    System.err.println(payload.stream().mapToInt(data -> data.length).sum());

        return payload;
    }

    public static void dealSend(int partyId, List<byte[]> payload) {
        if (null == payload) return;

        NettyTimeoutRpc ownRpc = rpcManager.getRpc(partyId);
        NettyTimeoutRpc blockchainRpc = rpcManager.getRpc(blockchainPartyId);

        DataPacketHeader header = new DataPacketHeader(
                dealBroadcastTaskId,
                PTO_DESC.getPtoId(),
                RpcPto.PtoStep.TAKE_ANY.ordinal(),
                ownRpc.ownParty().getPartyId(),
                blockchainRpc.ownParty().getPartyId()
        );

        DataPacket dataPacket = DataPacket.fromByteArrayList(
                header,
                payload
        );
        ownRpc.send(dataPacket);

        ownRpc.log("deal-b");
    }

    public static void dealBlockchainRelay() {
        NettyTimeoutRpc blockchainRpc = rpcManager.getRpc(blockchainPartyId);

        // catch all payloads
        List<byte[]> payloadAllInOne = new ArrayList<>();

        long timeoutLeft = DEFAULT_DEAL_TIME + BROADCAST_A_TIMEOUT;
        long timeUsed;

        StopWatch broadcastWatch = new StopWatch();
        broadcastWatch.start();
        StopWatch watch = new StopWatch();
        watch.start();
        // blockchain receives (only) one (broadcast) packet
        // from every deal-elected party
        DataPacket dataPacket = blockchainRpc.receiveAny(timeoutLeft);
        watch.stop();
        timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        watch.reset();
        timeoutLeft = (timeUsed == timeoutLeft) ? (-1) : (timeoutLeft - timeUsed);

        System.out.println("blockchain took # ms to receive the first packet dealBlockchainRelay");
        System.out.println(timeUsed);

        while (null != dataPacket) {
            if (dealBroadcastTaskId == dataPacket.getHeader().getEncodeTaskId()) {
                payloadAllInOne.addAll(dataPacket.getPayload());
            } else {
                System.err.format("Party %d: %s packet mismatch:\n" +
                                "received: EncodeTaskId %d, Sender %d\n",
                        blockchainPartyId,
                        "dealBlockchainRelay",
                        dataPacket.getHeader().getEncodeTaskId(),
                        dataPacket.getHeader().getSenderId());
            }
            watch.start();
            dataPacket = blockchainRpc.receiveAny(timeoutLeft);
            watch.stop();
            timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
            watch.reset();
            timeoutLeft = (timeUsed == timeoutLeft) ? (-1) : (timeoutLeft - timeUsed);
        }
        broadcastWatch.stop();
        System.out.println("time to capture dealBlockchain");
        System.out.println(broadcastWatch.getTime(TimeUnit.MILLISECONDS));
        broadcastWatch.reset();
        broadcastWatch.start();

        if (verboseLog) System.out.println("blockchain received # payloads:");
        if (verboseLog) System.out.println(payloadAllInOne.size() / 4);

        for (Party party : blockchainRpc.getPartySet()) {
            if (party.equals(blockchainRpc.ownParty())) {
                // do not send to itself
                continue;
            }

            // should sync here with node!

            DataPacketHeader header = new DataPacketHeader(
                    dealBlockchainRelayTaskId,
                    PTO_DESC.getPtoId(),
                    RpcPto.PtoStep.TAKE_ANY.ordinal(),
                    blockchainRpc.ownParty().getPartyId(),
                    party.getPartyId()
            );
            dataPacket = DataPacket.fromByteArrayList(
                    header,
                    payloadAllInOne
            );
            blockchainRpc.send(dataPacket);
        }

        broadcastWatch.stop();
        System.out.println("time to send dealBlockchain");
        System.out.println(broadcastWatch.getTime(TimeUnit.MILLISECONDS));
        broadcastWatch.reset();
    }

    public static List<byte[]> dealReceive(int partyId, long timeLeft) throws InterruptedException {
        StopWatch watch = new StopWatch();
        watch.start();

        NettyTimeoutRpc ownRpc = rpcManager.getRpc(partyId);

        DataPacketHeader header = new DataPacketHeader(
                dealBlockchainRelayTaskId,
                PTO_DESC.getPtoId(),
                RpcPto.PtoStep.TAKE_ANY.ordinal(),
                blockchainPartyId,
                partyId
        );

        List<byte[]> payloadAllInOne = new ArrayList<>();

        // this party receives (only) one (broadcast) packet
        // from blockchain
        DataPacket dataPacket = ownRpc.receive(header, timeLeft);
        if (null == dataPacket) {
            // timeout
            // lost this packet
            System.err.format("Party %d: %s packet missed:\n",
                    partyId,
                    "dealReceive");
            return null;
        } else if (dealBlockchainRelayTaskId == dataPacket.getHeader().getEncodeTaskId()) {
            payloadAllInOne.addAll(dataPacket.getPayload());
        } else {
            System.err.format("Party %d: %s packet mismatch:\n" +
                            "received: EncodeTaskId %d, Sender %d\n",
                    partyId,
                    "dealReceive",
                    dataPacket.getHeader().getEncodeTaskId(),
                    dataPacket.getHeader().getSenderId());
        }
        watch.stop();
        long timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        timeLeft -= timeUsed;

        System.out.println("time to dealReceive");
        System.out.println(timeUsed);

        if (timeLeft > 0) Thread.sleep(timeLeft);
        return payloadAllInOne;
    }

    public static void agree(int receiverPartyId, List<byte[]> payloadAllInOne, byte[] rand) {
        int packetSize = 2 * GROUP_SIZE + 11;
        if (null == payloadAllInOne) {
            System.err.format("Party %d: payloadAllInOne is empty\n", receiverPartyId);
            return;
        }
        if (payloadAllInOne.size() % packetSize != 0) {
            System.err.format("Party %d: payloadAllInOne wrong size: %d %% %d\n", receiverPartyId,
                    payloadAllInOne.size(),
                    packetSize);
            return;
        }
        List<byte[]> payload;
        for (int index = 0; index < payloadAllInOne.size() / packetSize; index ++) {
            payload = payloadAllInOne.subList(packetSize * index, packetSize * index + packetSize);
            agreeOnePayload(receiverPartyId, payload, rand);
        }
    }

    public static void agreeOnePayload(int receiverPartyId, List<byte[]> payload, byte[] rand) {
        int realSenderPartyId;
        BigInteger[] CArray;
        ECPoint[] CMArray;
        VRF.VRFProof vrfProof;

        // unpack: see deal()
        byte[] partyIdEncoded = payload.get(0);
        List<byte[]> proofEncoded = payload.subList(1, 7);
        List<byte[]> CMArrayEncoded = payload.subList(7, 7 + GROUP_SIZE + 1);
        List<byte[]> CArrayEncoded = payload.subList(7 + GROUP_SIZE + 1, 7 + GROUP_SIZE + 1 + GROUP_SIZE + 1);
        List<byte[]> cm0Proof = payload.subList(7 + GROUP_SIZE + 1 + GROUP_SIZE + 1, 7 + GROUP_SIZE + 1 + GROUP_SIZE + 1 + 2);

        try {
            CArray = CArrayEncoded
                    .stream()
                    .map(BigIntegerUtils::byteArrayToNonNegBigInteger)
                    .toArray(BigInteger[]::new);
            CMArray = CMArrayEncoded
                    .stream()
                    .map(ecc::decode)
                    .toArray(ECPoint[]::new);
            vrfProof = VRF.VRFProof.deserialize(
                    proofEncoded
            );
            realSenderPartyId = IntUtils.byteArrayToInt(partyIdEncoded);
        } catch (Exception e) {
            try {
                realSenderPartyId = IntUtils.byteArrayToInt(partyIdEncoded);
                System.err.format("Exception when parsing payload from sender %d: %s",
                        realSenderPartyId, e.getMessage());
                disqualifiedMap.get(receiverPartyId).add(realSenderPartyId);
            } catch (Exception ee) {
                System.err.println("Exception when parsing realSenderId: " + ee.getMessage());
            }
            return;
        }
        assert VRFPkMap.get(realSenderPartyId) == null;
        if (!VRF.verify(vrfProof, VRFPkMap.get(realSenderPartyId), Bytes.concat(rand, "deal".getBytes()))) {
            // invalid proof
            System.err.format("X VRF proof validation failed for party %d\n", realSenderPartyId);
            disqualifiedMap.get(receiverPartyId).add(realSenderPartyId);
            return;
        }
        if (vrfProof.getBeta().multiply(BigInteger.valueOf(GROUP_SIZE))
                .compareTo(BigInteger.valueOf(AT_SIZE).multiply(BigInteger.valueOf(2).pow(256))) > 0) {
            // valid proof but failed sortition
            System.err.format("X VRF sortition actually failed for party %d\n", realSenderPartyId);
            disqualifiedMap.get(receiverPartyId).add(realSenderPartyId);
            return;
        }

        if (!SimpleProof.verifyProof(cm0Proof, CMArray[0])) {
            // fail CM0 verification
            System.err.format("CM0 verification fail %d\n", realSenderPartyId);
            disqualifiedMap.get(receiverPartyId).add(realSenderPartyId);
            return;
        }

        BigInteger[] randZpPolyDegNMinusTCoeff = IntStream.range(0, GROUP_SIZE - DEGREE_T)
                .mapToObj(index -> BigIntegerUtils.randomNonNegative(ecc.getN(), SECURE_RANDOM))
                .toArray(BigInteger[]::new);
        BigInteger[] zeroToNArray = IntStream.range(0, GROUP_SIZE + 1)
                .mapToObj(BigInteger::valueOf)
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

        BigInteger[] CMDualArray = new BigInteger[GROUP_SIZE + 1];
        Arrays.setAll(CMDualArray, i ->
                numeratorArray[i]
                        .multiply(denominatorArray[i].modInverse(ecc.getN()))
                        .mod(ecc.getN()));

        ECPoint[] PowerArray = new ECPoint[GROUP_SIZE + 1];
        Arrays.setAll(PowerArray, i -> ecc.multiply(CMArray[i], CMDualArray[i]));

        ECPoint result = Arrays.stream(PowerArray).reduce(
                ecc::add).orElse(null);

        if (!ecc.getInfinity().equals(result)) {
            System.err.format("Dual code verification failed for party %d\n", realSenderPartyId);
            disqualifiedMap.get(receiverPartyId).add(realSenderPartyId);
            return;
        }

        VEnc reciverVEnc = VEncMap.get(receiverPartyId);
        byte[] sender_f_i_bytes = reciverVEnc.decrypt(CArray[0], CArray[receiverPartyId + 1]);
        BigInteger sender_f_i = BigIntegerUtils.byteArrayToBigInteger(sender_f_i_bytes);
        if (!CMArray[receiverPartyId + 1].equals(ecc.multiply(ecc.getG(), sender_f_i))) {
            // generate a complaint
            System.err.format("party %d: VEnc verification failed for party %d \n", receiverPartyId, realSenderPartyId);
            VEnc.VEncProof vEncProof = reciverVEnc.getProof(CArray, receiverPartyId);
            VEncComplaint complaint = new VEncComplaint(
                    receiverPartyId, realSenderPartyId, vEncProof
            );
            disqualifiedMap.get(receiverPartyId).add(realSenderPartyId);
            complaintMap.get(receiverPartyId).put(realSenderPartyId, complaint);
            uniqueComplaintMap.putIfAbsent(realSenderPartyId, complaint);
            return;
        }

        Candidate c = new Candidate(receiverPartyId, realSenderPartyId, CArray, CMArray, sender_f_i);
        candidateMap.get(receiverPartyId).put(realSenderPartyId, c);
    }

    public static void multicastComplaints(int plaintiffPartyId, Map<Integer, VEncComplaint> complaintMap) {

        NettyTimeoutRpc ownRpc = rpcManager.getRpc(plaintiffPartyId);

        List<byte[]> payload = new ArrayList<>();
        for (VEncComplaint complaint :
                complaintMap.values()) {
            payload.addAll(
                    complaint.serialize()
            );
        }

        if (payload.isEmpty()) {
            // do not send empty payload if no complaint
            return;
        }

        // multicast
        for (Party receiverParty : ownRpc.getPartySet()) {
            int receiverPartyId = receiverParty.getPartyId();
            if (blockchainPartyId == receiverPartyId) {
                continue;
            }
            if (plaintiffPartyId == receiverPartyId) {
                // directly send to self
                receivedComplaintsMap.get(plaintiffPartyId).addAll(complaintMap.values());
                continue;
            }

            DataPacketHeader header = new DataPacketHeader(
                    agreeComplaintMulticastTaskId,
                    PTO_DESC.getPtoId(),
                    RpcPto.PtoStep.TAKE_ANY.ordinal(),
                    ownRpc.ownParty().getPartyId(),
                    receiverParty.getPartyId()
            );

            DataPacket dataPacket = DataPacket.fromByteArrayList(
                    header,
                    payload
            );
            ownRpc.send(dataPacket);
        }
    }

    public static void complaintMulticastReceive(int receiverPartyId) {

        NettyTimeoutRpc ownRpc = rpcManager.getRpc(receiverPartyId);

        List<byte[]> complaintPayloadList = new ArrayList<>();

        StopWatch watch = new StopWatch();

        long timeoutLeft = MULTICAST_B_TIMEOUT;
        long timeUsed;

        List<Integer> missedSenderPartyIds = new ArrayList<>();

        for (Party senderParty : ownRpc.getPartySet()) {
            int senderPartyId = senderParty.getPartyId();
            if (blockchainPartyId == senderPartyId) {
                continue;
            }
            if (senderPartyId == receiverPartyId) {
                continue;
            }

            DataPacketHeader header = new DataPacketHeader(
                    agreeComplaintMulticastTaskId,
                    PTO_DESC.getPtoId(),
                    RpcPto.PtoStep.TAKE_ANY.ordinal(),
                    senderPartyId,
                    receiverPartyId
            );
            watch.start();
            long thisTimeoutLeft = timeoutLeft;
            DataPacket dataPacket = ownRpc.receive(header, timeoutLeft);
            watch.stop();
            timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
            watch.reset();
            timeoutLeft = (timeUsed == timeoutLeft) ? (-1) : (timeoutLeft - timeUsed);

            if (null != dataPacket) {
                complaintPayloadList.addAll(dataPacket.getPayload());
            } else {
                System.err.format(
                        "miss party %d in %d milisec; actual time used %d; finalTimeLeft %d\n",
                        senderPartyId, thisTimeoutLeft, timeUsed, timeoutLeft
                );
                missedSenderPartyIds.add(senderPartyId);
            }
        }

        List<Integer> completelyMissedSenderPartyIds = new ArrayList<>();

        for (int senderPartyId : missedSenderPartyIds) {
            if (blockchainPartyId == senderPartyId) {
                continue;
            }
            if (senderPartyId == receiverPartyId) {
                continue;
            }

            DataPacketHeader header = new DataPacketHeader(
                    agreeComplaintMulticastTaskId,
                    PTO_DESC.getPtoId(),
                    RpcPto.PtoStep.TAKE_ANY.ordinal(),
                    senderPartyId,
                    receiverPartyId
            );
            // instance attempt
            DataPacket dataPacket = ownRpc.receive(header, -1);

            if (null != dataPacket) {
                complaintPayloadList.addAll(dataPacket.getPayload());
            } else {
                completelyMissedSenderPartyIds.add(senderPartyId);
            }
        }

        if (!completelyMissedSenderPartyIds.isEmpty()) {
            System.err.format("Party %d: %s completely missed # parties: %d\n",
                    receiverPartyId,
                    "complaintMulticastReceive",
                    completelyMissedSenderPartyIds.size()
            );
        }

        for (int i = 0; i < complaintPayloadList.size(); i+=5) {
            VEncComplaint complaint = VEncComplaint.deserialize(complaintPayloadList.subList(i, i + 5));
            if (!receivedComplaintsMap.containsKey(receiverPartyId)) {
                receivedComplaintsMap.put(receiverPartyId, new ArrayList<>());
            }
            receivedComplaintsMap.get(receiverPartyId).add(
                    complaint
            );
        }

        System.out.println("# complaints received in multicast");
        System.out.println(receivedComplaintsMap.get(receiverPartyId).size());

    }

    public static void verifyMulticastComplaints(int receiverPartyId) {

        List<VEncComplaint> complaintsReceivedByReceiver = receivedComplaintsMap.get(receiverPartyId);

        verifyComplaints(receiverPartyId, complaintsReceivedByReceiver);
    }

    private static void verifyComplaints(int receiverPartyId, List<VEncComplaint> complaintsReceivedByReceiver) {
        for (VEncComplaint complaint :
                complaintsReceivedByReceiver) {
            VEnc.VEncProof complaintProof = complaint.complaintProof;
            int plaintiffPartyId = complaint.plaintiffPartyId;
            int defendantPartyId = complaint.defendantPartyId;
            if (disqualifiedMap.get(receiverPartyId).contains(defendantPartyId)) {
                // skip this qualified parties
                uniqueComplaintMap.putIfAbsent(defendantPartyId, complaint);
                continue;
            }
            ECPoint plaintiffPublicKey = VEncPublicKeyMap.get(plaintiffPartyId);
            Candidate defendantCandidate = candidateMap.get(receiverPartyId).get(defendantPartyId);
            // replace the cipher with the receiver's view of cipher
            // verify plaintiff's claim on pk
            if (!VEnc.verifyWithC0(complaintProof, plaintiffPublicKey, defendantCandidate.CArray[0])) {
                // PK^r is invalid, reject the complaint
                System.err.format("party %d: reject complaint from (plaintiff party %d): VEnc verification (a) failed for party %d\n", receiverPartyId, plaintiffPartyId, defendantPartyId);
                continue;
            }
            // PK^r == hPrime is valid
            // validate c1
            BigInteger ci = defendantCandidate.CArray[plaintiffPartyId + 1];
            ECPoint cmi = defendantCandidate.CMArray[plaintiffPartyId + 1];
            ECPoint hPrime = complaintProof.getHPrime();
            if (!ecc.multiply(ecc.getG(), BigIntegerUtils.byteArrayToNonNegBigInteger(
                            HASH.digestToBytes(hPrime.getEncoded(compressEncoding)))
                    .xor(ci)).equals(cmi)) {
                // c1 is invalid, reject the complaint
                System.err.format("party %d: reject complaint from (plaintiff party %d): VEnc verification (b) failed for party %d\n", receiverPartyId, plaintiffPartyId, defendantPartyId);
                continue;
            }
            if (defendantCandidate.CMArray[plaintiffPartyId + 1]
                    .equals(hPrime)) {
                // CM is valid, reject the complaint
                System.err.format("party %d: reject complaint from (plaintiff party %d): CM is valid for party %d\n", receiverPartyId, plaintiffPartyId, defendantPartyId);
                continue;
            }
            // add defendant id to disq
            disqualifiedMap.get(receiverPartyId).add(defendantPartyId);
            // add proof to complaint
            complaintMap.get(receiverPartyId).put(defendantPartyId, complaint);
            uniqueComplaintMap.putIfAbsent(defendantPartyId, complaint);
        }
    }

    public static void broadcastComplaint(byte[] rand, int senderPartyId) {
        NettyTimeoutRpc ownRpc = rpcManager.getRpc(senderPartyId);
        ownRpc.log("broadcastComplaint-a");

        VRF vrf = VRFMap.get(senderPartyId);
        VRF.VRFProof proof = vrf.sortition(
                rand,
                "agree".getBytes(),
                BigInteger.valueOf(AT_SIZE),
                BigInteger.valueOf(GROUP_SIZE)
        );

        if (null == proof) {
            ownRpc.log("non-plaintiff");
            return;
        } else {
            System.err.println("plaintiff");
            ownRpc.log("plaintiff");
        }

        // broadcast
        List<byte[]> payload = new ArrayList<>();

        List<byte[]> complaintBytes = new ArrayList<>();

        System.out.println("# complaints send in broadcast");
        System.out.println(uniqueComplaintMap.size());

        for (VEncComplaint complaint :
                uniqueComplaintMap.values()) {
            complaintBytes.addAll(complaint.serialize()); // 5*uniqueComplaintMap.size()
        }

        if (complaintBytes.isEmpty()) {
            ownRpc.log("broadcastComplaint-b-zero");
            return;
        }

        int complaintBytesSize = complaintBytes.size();
        payload.add(
                IntUtils.intToByteArray(senderPartyId)); // 1
        payload.addAll(
                proof.serialize()); // 6
        payload.add(IntUtils.intToByteArray(complaintBytesSize)); // 1
        payload.addAll(
                complaintBytes); // 5*uniqueComplaintMap.size()

        DataPacketHeader header = new DataPacketHeader(
                agreeComplaintBroadcastTaskId,
                PTO_DESC.getPtoId(),
                RpcPto.PtoStep.TAKE_ANY.ordinal(),
                senderPartyId,
                blockchainPartyId
        );

        DataPacket dataPacket = DataPacket.fromByteArrayList(
                header,
                payload
        );
        rpcManager.getRpc(senderPartyId).send(dataPacket);

        ownRpc.log("broadcastComplaint-c");
    }

    public static void broadcastBlockchainRelay() {
        NettyTimeoutRpc blockchainRpc = rpcManager.getRpc(blockchainPartyId);
        blockchainRpc.log("broadcastBlockchainRelay-a");

        long timeoutLeft = BROADCAST_A_TIMEOUT;
        long timeUsed;

        // catch all payloads
        List<byte[]> payloadAllInOne = new ArrayList<>();

        StopWatch broadcastWatch = new StopWatch();
        broadcastWatch.start();
        StopWatch watch = new StopWatch();
        watch.start();
        // blockchain receives one broadcast packet
        // from every agree-elected party
        DataPacket dataPacket = blockchainRpc.receiveAny(timeoutLeft);
        watch.stop();
        timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        watch.reset();
        timeoutLeft = (timeUsed == timeoutLeft) ? (-1) : (timeoutLeft - timeUsed);

        System.out.println("blockchain took # ms to receive the first packet broadcastBlockchainRelay");
        System.out.println(timeUsed);

        while (null != dataPacket) {
            if (agreeComplaintBroadcastTaskId == dataPacket.getHeader().getEncodeTaskId()) {
                payloadAllInOne.addAll(dataPacket.getPayload());
            } else {
                System.err.format("Party %d: %s packet mismatch:\n" +
                                "received: EncodeTaskId %d, Sender %d\n",
                        blockchainPartyId,
                        "broadcastBlockchainRelay",
                        dataPacket.getHeader().getEncodeTaskId(),
                        dataPacket.getHeader().getSenderId());
            }
            watch.start();
            dataPacket = blockchainRpc.receiveAny(timeoutLeft);
            watch.stop();
            timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
            watch.reset();
            timeoutLeft = (timeUsed == timeoutLeft) ? (-1) : (timeoutLeft - timeUsed);
        }

        broadcastWatch.stop();
        System.out.println("time to capture broadcastBlockchainRelay");
        System.out.println(broadcastWatch.getTime(TimeUnit.MILLISECONDS));
        broadcastWatch.reset();
        broadcastWatch.start();

        for (Party party : blockchainRpc.getPartySet()) {
            if (party.equals(blockchainRpc.ownParty())) {
                continue;
            }

            // should sync here with node!

            DataPacketHeader header = new DataPacketHeader(
                    agreeComplaintBlockchainRelayTaskId,
                    PTO_DESC.getPtoId(),
                    RpcPto.PtoStep.TAKE_ANY.ordinal(),
                    blockchainRpc.ownParty().getPartyId(),
                    party.getPartyId()
            );

            dataPacket = DataPacket.fromByteArrayList(
                    header,
                    payloadAllInOne
            );
            blockchainRpc.send(dataPacket);
        }

        broadcastWatch.stop();
        System.out.println("time to send broadcastBlockchainRelay");
        System.out.println(broadcastWatch.getTime(TimeUnit.MILLISECONDS));
        broadcastWatch.reset();
    }

    public static DataPacket broadcastReceive(int receiverPartyId, long timeLeft) throws InterruptedException {
        StopWatch watch = new StopWatch();
        watch.start();
        NettyTimeoutRpc ownRpc = rpcManager.getRpc(receiverPartyId);

        // (each) party receives one (broadcast) packet
        // contains multiple packets originally sent by other parties
        // relayed by blockchain
        DataPacketHeader header = new DataPacketHeader(
                agreeComplaintBlockchainRelayTaskId,
                PTO_DESC.getPtoId(),
                RpcPto.PtoStep.TAKE_ANY.ordinal(),
                blockchainPartyId,
                receiverPartyId
        );
        DataPacket dataPacket = ownRpc.receive(header, timeLeft);
        watch.stop();
        long timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        timeLeft -= timeUsed;

        System.out.println("time to broadcastReceive");
        System.out.println(timeUsed);

        if (timeLeft > 0) Thread.sleep(timeLeft);
        return dataPacket;
    }

    public static void verifyBroadcastComplaints(int receiverPartyId, DataPacket dataPacket, byte[] rand) {

        if (null == dataPacket) {
            System.err.println("miss broadcast complaint");
            return;
        }

        Map<Integer, Pair<VRF.VRFProof, List<VEncComplaint>>> proofsAndComplaints = new HashMap<>();

        if (agreeComplaintBlockchainRelayTaskId == dataPacket.getHeader().getEncodeTaskId()) {
            List<byte[]> payload = dataPacket.getPayload();
            for (int index = 0; index < dataPacket.getPayload().size();) {

                int senderPartyId = IntUtils.byteArrayToInt(
                        payload.get(index));
                index ++;

                VRF.VRFProof proof = VRF.VRFProof.deserialize(
                        payload.subList(index, index + 6)
                );
                index += 6;

                int complaintBytesSize = IntUtils.byteArrayToInt(payload.get(index));
                index ++;

                List<byte[]> complaintBytes = payload.subList(index, index + complaintBytesSize);
                index += complaintBytesSize;

                List<VEncComplaint> manyComplaints = new ArrayList<>();
                for (int i = 0; i < complaintBytes.size(); i += 5) {
                    manyComplaints.add(
                            VEncComplaint.deserialize(complaintBytes.subList(i, i + 5))
                    );
                }

                proofsAndComplaints.put(senderPartyId, Pair.of(proof, manyComplaints));
            }
        } else {
            System.err.format("Party %d: %s packet mismatch:\n" +
                            "received: EncodeTaskId %d, Sender %d\n",
                    receiverPartyId,
                    "broadcastReceive",
                    dataPacket.getHeader().getEncodeTaskId(),
                    dataPacket.getHeader().getSenderId());
        }

        int broadcastComplaintsCounter = 0;

        for (int realSenderPartyId :
                proofsAndComplaints.keySet()) {
            Pair<VRF.VRFProof, List<VEncComplaint>> proofAndComplaint = proofsAndComplaints.get(realSenderPartyId);
            broadcastComplaintsCounter += proofAndComplaint.getRight().size();
            VRF.VRFProof vrfProof = proofAndComplaint.getLeft();
            if (!VRF.verify(vrfProof, VRFPkMap.get(realSenderPartyId), Bytes.concat(rand, "agree".getBytes()))) {
                // invalid proof
                System.err.format("Y VRF proof validation failed for party %d\n", realSenderPartyId);
                // ignore
                return;
            }
            if (vrfProof.getBeta().multiply(BigInteger.valueOf(GROUP_SIZE))
                    .compareTo(BigInteger.valueOf(AT_SIZE).multiply(BigInteger.valueOf(2).pow(256))) > 0) {
                // valid proof but failed sortition
                System.err.format("Y VRF sortition actually failed for party %d\n", realSenderPartyId);
                // ignore
                return;
            }
            List<VEncComplaint> complaints = proofAndComplaint.getRight();
            verifyComplaints(receiverPartyId, complaints);
        }

        System.out.println("# complaints received in broadcast");
        System.out.println(broadcastComplaintsCounter);
    }

    public static ECPoint generatePK(int partyId) {
        Map<Integer, Candidate> candidateForParty = candidateMap.get(partyId);
        List<ECPoint> CMZeroList = new ArrayList<>();
        for (int candidatePartyId :
                qualifiedMap.get(partyId)) {
            CMZeroList.add(candidateForParty.get(candidatePartyId).CMArray[0]);
        }
        return CMZeroList.stream().reduce(
                ecc::add).orElse(null);
    }

    public static BigInteger generateSKShare(int partyId) {
        Map<Integer, Candidate> candidateForParty = candidateMap.get(partyId);
        List<BigInteger> SKList = new ArrayList<>();
        for (int candidatePartyId :
                qualifiedMap.get(partyId)) {
            SKList.add(candidateForParty.get(candidatePartyId).sk);

        }
        if (SKList.isEmpty()) {
            return null;
        }
        BigInteger result = BigInteger.ONE;
        for (BigInteger sk :
                SKList) {
            result = result.multiply(sk).mod(ecc.getN());
        }
        return result;
    }

    public static ECPoint generatePKShare(int partyId) {
        Map<Integer, Candidate> candidateForParty = candidateMap.get(partyId);
        List<ECPoint> CMList = new ArrayList<>();
        for (int candidatePartyId :
                qualifiedMap.get(partyId)) {
            CMList.add(candidateForParty.get(candidatePartyId).CMArray[partyId + 1]);
        }
        return CMList.stream().reduce(
                ecc::add).orElse(null);
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 4) {
            System.err.println("<index_id> <ip_file.txt> <anytrust_comittee_size> <experiment_prefix> [normal/corrupted]");
            return;
        }

        int index = Integer.parseInt(args[0]);
        String ipFilePath = args[1];
        byte[] experimentPrefix = args[3].getBytes();

        List<String> ipAddresses = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(ipFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                ipAddresses.add(line);
            }
        } catch (FileNotFoundException e) {
            System.err.println("ip file not found, run local tests!");
            int size = Integer.parseInt(ipFilePath);
            for (int i = 0; i < size; i++) {
                ipAddresses.add("127.0.0.1");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<Integer> ports = new ArrayList<>();


        for (int i = 0; i < ipAddresses.size(); i++) {
            ports.add(DEFAULT_PORT + i);
        }

        if (args.length >= 5 && !args[4].equals("normal")) {
            allowCorruption = true;
        }

        if (ipAddresses.size() != ports.size())
        {
            System.err.println("unmatched config");
            return;
        }

        ownPartyLocation = Pair.of(ipAddresses.get(index), ports.get(index));
        // include self & blockchain
        blockchainLocation = Pair.of(ipAddresses.get(ipAddresses.size()-1), ports.get(ports.size()-1));
        rpcManager = new UniversalNettyRpcManager(ipAddresses, ports);

        ownPartyId = rpcManager.getRpc(ownPartyLocation).ownParty().getPartyId();
        blockchainPartyId = rpcManager.getRpc(blockchainLocation).ownParty().getPartyId();

        GROUP_SIZE = rpcManager.getPartyNum() - 1;

        AT_SIZE = Math.min(GROUP_SIZE / 2 + 1, 29);
        try {
            AT_SIZE = Math.min(GROUP_SIZE / 2 + 1, Integer.parseInt(args[2]));
        } catch (NumberFormatException e) {
            System.err.println("invalid at_size:" + args[2]);
        }

        DEGREE_T = GROUP_SIZE / 2 + 1;

        if (ownPartyId == blockchainPartyId) mainBlockchain();
        else mainParty(experimentPrefix);

    }

    public static void mainParty(byte[] rand) throws InterruptedException {

        long totalIdleCost = 0;
        long totalCommunicationCost = 0;
        Map<String, Long> computationCosts = new HashMap<>();

        disqualifiedMap = new HashMap<>(GROUP_SIZE);
        qualifiedMap = new HashMap<>(GROUP_SIZE);
        complaintMap = new HashMap<>(GROUP_SIZE);
        // receiver id -> complaint[])
        receivedComplaintsMap = new HashMap<>(GROUP_SIZE);
        uniqueComplaintMap = new HashMap<>(GROUP_SIZE);
        VRFMap = new HashMap<>(GROUP_SIZE);
        VRFPkMap = new HashMap<>(GROUP_SIZE);
        candidateMap = new HashMap<>(GROUP_SIZE);

        if (verboseLog) System.out.print("Hello and welcome!\n");

        StopWatch watch = new StopWatch();
        watch.start();

        for (int i = 0; i < GROUP_SIZE; i++) {
            // hardcoded vrf sk for now
            VRFMap.put(i, new VRF(BigInteger.valueOf(i)));
            VRFPkMap.put(i, VRFMap.get(i).getPublicKey());
            VEncMap.put(i, new VEnc(BigInteger.valueOf(i)));
            VEncPublicKeys.add(VEncMap.get(i).getPublicKey());
            VEncPublicKeyMap.put(i, VEncMap.get(i).getPublicKey());
        }

        for (int i = 0; i < GROUP_SIZE; i++) {
            disqualifiedMap.put(i, new HashSet<>());
            complaintMap.put(i, new HashMap<>());
            candidateMap.put(i, new HashMap<>());
            receivedComplaintsMap.put(i, new ArrayList<>());
            qualifiedMap.put(i, new HashSet<>());
        }

        watch.stop();
        if (verboseLog) System.out.println("prepare keys");
        watch.reset();

        watch.start();

        initRpc();
        StopWatch totalWatch = new StopWatch();
        totalWatch.start();

        watch.stop();
        if (verboseLog) System.out.println("init rpc");
        watch.reset();

        watch.start();
        List<byte[]> dealPayload;
        if (ownPartyId < (GROUP_SIZE - 1) / 2 && allowCorruption) {
            System.err.println("corrupted");
            dealPayload = deal(rand, ownPartyId, allowCorruption);
        } else {
            dealPayload = deal(rand, ownPartyId);
        }
        watch.stop();
        System.out.println("time to deal");
        long timeLeft = DEFAULT_DEAL_TIME + BROADCAST_A_TIMEOUT + BROADCAST_B_TIMEOUT;
        long timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        System.out.println(timeUsed);
        computationCosts.put("deal", timeUsed);
        timeLeft -= timeUsed;
        watch.reset();

        watch.start();
        dealSend(ownPartyId, dealPayload);
        watch.stop();

        timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        timeLeft -= timeUsed;
        totalCommunicationCost += timeUsed;

        System.out.println("time to dealSend");
        System.out.println(timeUsed);

        watch.reset();

        watch.start();
        // dealBlockchainRelay(); // blockchain only
        watch.stop();
        if (verboseLog) System.out.println("time to relay in blockchain");
        watch.reset();

        watch.start();
        List<byte[]> receivedPayload = dealReceive(ownPartyId, timeLeft);
        watch.stop();

        timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        timeLeft -= timeUsed;
        totalCommunicationCost += timeUsed;

        if (timeLeft > 0) {
            Thread.sleep(timeLeft);
            totalIdleCost += timeLeft;
        }

        watch.reset();

        timeLeft = MULTICAST_A_TIMEOUT + MULTICAST_B_TIMEOUT;
        watch.start();
        agree(ownPartyId, receivedPayload, rand);
        watch.stop();

        timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        timeLeft -= timeUsed;
        computationCosts.put("agree", timeUsed);

        System.out.println("time to agree");
        System.out.println(timeUsed);
        watch.reset();

        int totalComplaintCount = 0;
        for (int i = 0; i < GROUP_SIZE; i++) {
            totalComplaintCount += complaintMap.get(i).size();
        }
        System.err.println("total # complaints to send");
        System.err.println(totalComplaintCount);

        watch.start();
        // multicast complaints
        int plaintiffPartyId = ownPartyId;
        multicastComplaints(plaintiffPartyId, complaintMap.get(plaintiffPartyId));
        watch.stop();

        timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        timeLeft -= timeUsed;
        totalCommunicationCost += timeUsed;

        System.out.println("time to send multicast complaint");
        System.out.println(timeUsed);
        watch.reset();

        watch.start();
        int receiverPartyId = ownPartyId;
        // collect datapacket from others
        complaintMulticastReceive(receiverPartyId);
        // verify later
        watch.stop();

        timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        timeLeft -= timeUsed;
        totalCommunicationCost += timeUsed;

        System.out.println("time to receive multicast complaint");
        System.out.println(timeUsed);
        watch.reset();

        System.out.println("adjust time to finish multicast");
        System.out.println(MULTICAST_A_TIMEOUT + MULTICAST_B_TIMEOUT - timeLeft);

        System.out.println("time to wait after receiving all multicast complaints");
        System.out.println(timeLeft);

        if (timeLeft > 0) {
            Thread.sleep(timeLeft);
            totalIdleCost += timeLeft;
        }

        timeLeft = BROADCAST_A_TIMEOUT + BROADCAST_B_TIMEOUT;

        watch.start();
        receiverPartyId = ownPartyId;
        // collect datapacket from others
        verifyMulticastComplaints(receiverPartyId);

        watch.stop();
        timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        timeLeft -= timeUsed;
        computationCosts.put("verifyMulticast", timeUsed);
        System.out.println("time to verify multicast complaint");
        System.out.println(timeUsed);
        watch.reset();

        watch.start();
        int senderPartyId = ownPartyId;
        // broadcast complaints to blockchain
        broadcastComplaint(rand, senderPartyId);
        watch.stop();

        timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        timeLeft -= timeUsed;
        totalCommunicationCost += timeUsed;

        System.out.println("time to broadcast complaint");
        System.out.println(timeUsed);

        watch.reset();
        watch.start();

        // blockchain only
        // broadcastBlockchainRelay();

        watch.stop();
        if (verboseLog) System.out.println("time to relay broadcast complaints from blockchain");
        watch.reset();
        // allow some time to relay the message
        watch.start();
        receiverPartyId = ownPartyId;
        DataPacket complaintsDataPacket = broadcastReceive(receiverPartyId, timeLeft);
        watch.stop();

        timeUsed += watch.getTime(TimeUnit.MILLISECONDS);
        timeLeft -= timeUsed;
        totalCommunicationCost += timeUsed;

        System.out.println("time to receive relayed complaints from blockchain");
        System.out.println(timeUsed);

        watch.reset();

        if (timeLeft > 0) {
            Thread.sleep(timeLeft);
            totalIdleCost += timeLeft;
        }

        watch.start();
        verifyBroadcastComplaints(receiverPartyId, complaintsDataPacket, rand);
        watch.stop();

        timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        computationCosts.put("verifyBroadcast", timeUsed);

        System.out.println("time to verify complaints from blockchain");
        System.out.println(timeUsed);
        watch.reset();

        watch.start();

        Map<Integer, ECPoint> allPK = new HashMap<>();
        Map<Integer, ECPoint> PKShareList = new HashMap<>();
        Map<Integer, BigInteger> SKShareList = new HashMap<>();

        int partyId = ownPartyId;

        // generate qualify set
        qualifiedMap.put(partyId, new HashSet<Integer>(CollectionUtils.subtract(
                candidateMap.get(partyId).keySet(),
                disqualifiedMap.get(partyId)
        )));

        allPK.put(partyId, generatePK(partyId));
        PKShareList.put(partyId, generatePKShare(partyId));
        SKShareList.put(partyId, generateSKShare(partyId));

        watch.stop();

        timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        computationCosts.put("generateKeys", timeUsed);

        System.out.println("time to compute pk, sk share and pk share");
        System.out.println(timeUsed);
        watch.reset();
        watch.start();

        watch.reset();

        totalWatch.stop();
        timeUsed = totalWatch.getTime(TimeUnit.MILLISECONDS);
        System.out.println("total time:");
        System.out.println(timeUsed);
        System.out.println("total idle cost:");
        System.out.println(totalIdleCost);
        System.out.println("protocol cost:");
        System.out.println(timeUsed - totalIdleCost);
        System.out.println("total communication cost:");
        System.out.println(totalCommunicationCost);
        System.out.println("total computation cost:");
        System.out.println(computationCosts.values().stream().mapToLong(x -> x).sum());
        computationCosts.forEach((key, value) -> System.out.format("%s,%d;", key, value));
        System.out.println();
        System.out.println("total data:");
        System.out.format("%d\n%d\n%d\n",
                rpcManager.getRpc(ownPartyId).getByteLength(),
                rpcManager.getRpc(ownPartyId).getPayloadByteLength(),
                rpcManager.getRpc(ownPartyId).getDataPacketNum()
        );
        System.out.println("total send:");
        System.out.format("%d\n%d\n%d\n",
                rpcManager.getRpc(ownPartyId).getSendByteLength(),
                rpcManager.getRpc(ownPartyId).getSendPayloadByteLength(),
                rpcManager.getRpc(ownPartyId).getSendDataPacketNum()
        );
        System.out.println("total receive:");
        System.out.format("%d\n%d\n%d\n",
                rpcManager.getRpc(ownPartyId).getReceiveByteLength(),
                rpcManager.getRpc(ownPartyId).getReceivePayloadByteLength(),
                rpcManager.getRpc(ownPartyId).getReceiveDataPacketNum()
        );
        Thread.sleep(20000L);
        System.exit(0);
    }

    public static void mainBlockchain() throws InterruptedException {

        System.err.println("blockchain");

        if (verboseLog) System.out.print("Hello and welcome!\n");

        StopWatch watch = new StopWatch();
        watch.start();

        initRpc();
        StopWatch totalWatch = new StopWatch();
        totalWatch.start();

        watch.stop();
        if (verboseLog) System.out.println("init rpc");
        // System.out.println(watch.getTime(TimeUnit.MILLISECONDS));
        watch.reset();
        watch.start();
        watch.stop();
        if (verboseLog) System.out.println("prepare keys");
        watch.reset();
        watch.start();

        watch.stop();
        if (verboseLog) System.out.println("time to deal");
        watch.reset();
        watch.start();

        dealBlockchainRelay();

        watch.stop();
        System.out.println("time to relay in blockchain");
        System.out.println(watch.getTime(TimeUnit.MILLISECONDS));
        long timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        long timeLeft = DEFAULT_DEAL_TIME + BROADCAST_A_TIMEOUT + BROADCAST_B_TIMEOUT - timeUsed;
        if (timeLeft > 0) {
            Thread.sleep(timeLeft);
        }

        watch.reset();

        // sync with parties
        System.out.println("time to sync with multicast + agree");
        Thread.sleep(MULTICAST_A_TIMEOUT + MULTICAST_B_TIMEOUT);

        watch.start();

        // blockchain only
        broadcastBlockchainRelay();

        watch.stop();
        System.out.println("time to relay broadcast complaints from blockchain");
        System.out.println(watch.getTime(TimeUnit.MILLISECONDS));
        timeUsed = watch.getTime(TimeUnit.MILLISECONDS);
        timeLeft = BROADCAST_A_TIMEOUT + BROADCAST_B_TIMEOUT - timeUsed;
        if (timeLeft > 0) {
            Thread.sleep(timeLeft);
        }
        watch.reset();

        totalWatch.stop();
        System.out.println("total time:");
        System.out.println(totalWatch.getTime(TimeUnit.MILLISECONDS));
        System.out.println("total data:");
        System.out.format("%d\n%d\n%d\n",
                rpcManager.getRpc(ownPartyId).getByteLength(),
                rpcManager.getRpc(ownPartyId).getPayloadByteLength(),
                rpcManager.getRpc(ownPartyId).getDataPacketNum()
        );
        System.out.println("total send:");
        System.out.format("%d\n%d\n%d\n",
                rpcManager.getRpc(ownPartyId).getSendByteLength(),
                rpcManager.getRpc(ownPartyId).getSendPayloadByteLength(),
                rpcManager.getRpc(ownPartyId).getSendDataPacketNum()
        );
        System.out.println("total receive:");
        System.out.format("%d\n%d\n%d\n",
                rpcManager.getRpc(ownPartyId).getReceiveByteLength(),
                rpcManager.getRpc(ownPartyId).getReceivePayloadByteLength(),
                rpcManager.getRpc(ownPartyId).getReceiveDataPacketNum()
        );

        Thread.sleep(20000L + 100L * GROUP_SIZE * 2);
        System.exit(0);
    }
}