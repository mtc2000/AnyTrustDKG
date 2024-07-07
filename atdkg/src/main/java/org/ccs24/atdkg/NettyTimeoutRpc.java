package org.ccs24.atdkg;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.impl.netty.DataReceiveThread;
import edu.alibaba.mpc4j.common.rpc.impl.netty.DataSendManager;
import edu.alibaba.mpc4j.common.rpc.impl.netty.NettyParty;
import edu.alibaba.mpc4j.common.rpc.impl.netty.protobuf.NettyRpcProtobuf;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.utils.BigIntegerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.Collectors;

/**
 * based on mpc4j
 * RPC implementation based on Netty
 */
public class NettyTimeoutRpc implements Rpc {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyTimeoutRpc.class);

    private final HashMap<Integer, NettyParty> partyIdHashMap;

    private final NettyParty ownParty;

    private final int ownPartyId;

    private final DataPacketTimeoutBuffer dataPacketBuffer;

    private final CyclicBarrier cyclicBarrier;

    private DataReceiveThread dataReceiveThread;

    private DataSendManager dataSendManager;

    private long sendDataPacketNum;

    private long sendPayloadByteLength;

    private long sendByteLength;

    private long receiveDataPacketNum;

    private long receivePayloadByteLength;

    private long receiveByteLength;

    public NettyTimeoutRpc(NettyParty ownParty, Set<NettyParty> partySet) {

        Preconditions.checkArgument(partySet.size() > 1, "Party set size must be greater than 1");

        Preconditions.checkArgument(partySet.contains(ownParty), "Party set must contain own party");
        this.ownParty = ownParty;
        ownPartyId = ownParty.getPartyId();

        partyIdHashMap = new HashMap<>();
        partySet.forEach(party -> partyIdHashMap.put(party.getPartyId(), party));
        sendDataPacketNum = 0;
        sendPayloadByteLength = 0;
        sendByteLength = 0;
        receiveDataPacketNum = 0;
        receivePayloadByteLength = 0;
        receiveByteLength = 0;
        dataReceiveThread = null;

        cyclicBarrier = new CyclicBarrier(2);
        dataPacketBuffer = new DataPacketTimeoutBuffer();
    }

    @Override
    public Party ownParty() {
        return ownParty;
    }

    @Override
    public Set<Party> getPartySet() {
        return partyIdHashMap.keySet().stream().map(partyIdHashMap::get).collect(Collectors.toSet());
    }

    @Override
    public Party getParty(int partyId) {
        assert (partyIdHashMap.containsKey(partyId));
        return partyIdHashMap.get(partyId);
    }

    public void initChannels() {

        dataReceiveThread = new DataReceiveThread(ownParty, cyclicBarrier, dataPacketBuffer);
        dataReceiveThread.start();

        dataSendManager = new DataSendManager();
    }

    public void log(String msg) {
        LOGGER.info("{}: {}",
                ownParty,
                msg
        );
    }

    public static void waitUntil(long timestamp) {
        long millis = timestamp - System.currentTimeMillis();
        // return immediately if time is already in the past
        if (millis <= 0)
            return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void connect() {
        partyIdHashMap.keySet().stream().sorted().forEach(otherPartyId -> {
            if (otherPartyId < ownPartyId) {

                DataPacketHeader clientConnectHeader = new DataPacketHeader(
                        Long.MAX_VALUE - ownPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.CLIENT_CONNECT.ordinal(),
                        ownPartyId, otherPartyId
                );
                send(DataPacket.fromByteArrayList(clientConnectHeader, new LinkedList<>()));
                LOGGER.debug(
                        "{} requests connection with {}",
                        partyIdHashMap.get(ownPartyId), partyIdHashMap.get(otherPartyId)
                );

                DataPacketHeader serverConnectHeader = new DataPacketHeader(
                        Long.MAX_VALUE - otherPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.SERVER_CONNECT.ordinal(),
                        otherPartyId, ownPartyId
                );
                receive(serverConnectHeader);
                LOGGER.debug(
                        "{} successfully make connection with {}",
                        partyIdHashMap.get(ownPartyId), partyIdHashMap.get(otherPartyId)
                );
            } else if (otherPartyId > ownPartyId) {

                DataPacketHeader clientConnectHeader = new DataPacketHeader(
                        Long.MAX_VALUE - otherPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.CLIENT_CONNECT.ordinal(),
                        otherPartyId, ownPartyId
                );
                LOGGER.debug(
                        "{} receives connection request from {}",
                        partyIdHashMap.get(ownPartyId), partyIdHashMap.get(otherPartyId)
                );
                receive(clientConnectHeader);

                DataPacketHeader serverConnectHeader = new DataPacketHeader(
                        Long.MAX_VALUE - ownPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.SERVER_CONNECT.ordinal(),
                        ownPartyId, otherPartyId
                );
                send(DataPacket.fromByteArrayList(serverConnectHeader, new LinkedList<>()));
                LOGGER.debug(
                        "{} successfully make connection with {}",
                        partyIdHashMap.get(ownPartyId), partyIdHashMap.get(otherPartyId)
                );
            }
        });
        LOGGER.info("{} connected", ownParty);
        int lastPartyId = partyIdHashMap.size() - 1;
        if (ownPartyId == lastPartyId) {
            // client
            List<byte[]> payload = new LinkedList<>();
            long timestamp = System.currentTimeMillis() + 5000L + 100L * partyIdHashMap.size();
            byte[] timestampBytes = BigInteger.valueOf(timestamp).toByteArray();
            payload.add(timestampBytes);
            for (int otherPartyId:
                 partyIdHashMap.keySet()) {
                if (ownPartyId == otherPartyId) continue;
                DataPacketHeader clientConnectHeader = new DataPacketHeader(
                        Long.MAX_VALUE - otherPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.CLIENT_TRUE_SYNC.ordinal(),
                        ownPartyId, otherPartyId
                );
                send(DataPacket.fromByteArrayList(clientConnectHeader, payload));
            }
            LOGGER.info("{} waits for {} milliseconds", ownParty, timestamp - System.currentTimeMillis());
            waitUntil(timestamp);
        } else {
            // server
            DataPacketHeader clientConnectHeader = new DataPacketHeader(
                    Long.MAX_VALUE - ownPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.CLIENT_TRUE_SYNC.ordinal(),
                    lastPartyId, ownPartyId
            );
            DataPacket dataPacket = receive(clientConnectHeader);
            List<byte[]> payload = dataPacket.getPayload();
            long timestamp = BigIntegerUtils.byteArrayToNonNegBigInteger(payload.remove(0)).longValue();
            LOGGER.info("{} waits for {} milliseconds", ownParty, timestamp - System.currentTimeMillis());
            waitUntil(timestamp);
        }
        LOGGER.info("{} synced connected", ownParty);
    }

    private void updateReceiveStat(DataPacket dataPacket) {
        if (null == dataPacket) {
            return;
        }

        DataPacketHeader header = dataPacket.getHeader();
        NettyRpcProtobuf.DataPacketProto.HeaderProto headerProto = NettyRpcProtobuf.DataPacketProto.HeaderProto
                .newBuilder()
                .setEncodeTaskId(header.getEncodeTaskId())
                .setPtoId(header.getPtoId())
                .setStepId(header.getStepId())
                .setExtraInfo(header.getExtraInfo())
                .setSenderId(header.getSenderId())
                .setReceiverId(header.getReceiverId())
                .build();

        List<ByteString> payloadByteStringList = dataPacket.getPayload().stream()
                .map(ByteString::copyFrom)
                .collect(Collectors.toList());
        NettyRpcProtobuf.DataPacketProto.PayloadProto payloadProto = NettyRpcProtobuf.DataPacketProto.PayloadProto
                .newBuilder()
                .addAllPayloadBytes(payloadByteStringList)
                .build();

        NettyRpcProtobuf.DataPacketProto dataPacketProto = NettyRpcProtobuf.DataPacketProto
                .newBuilder()
                .setHeaderProto(headerProto)
                .setPayloadProto(payloadProto)
                .build();
        int thisPayloadByteLength = dataPacket.getPayload().stream().mapToInt(data -> data.length).sum();
        receivePayloadByteLength += thisPayloadByteLength;
        int thisPacketByteLength = dataPacketProto.getSerializedSize();
        receiveByteLength += thisPacketByteLength;
        receiveDataPacketNum++;
        LOGGER.info("{} receives from {}, payload {}, packet {}",
                ownParty,
                partyIdHashMap.get(dataPacket.getHeader().getReceiverId()),
                thisPayloadByteLength,
                thisPacketByteLength
        );
    }

    @Override
    public void send(DataPacket dataPacket) {
        DataPacketHeader header = dataPacket.getHeader();
        Preconditions.checkArgument(
                ownPartyId == header.getSenderId(), "Sender ID must be %s", ownPartyId
        );
        Preconditions.checkArgument(
                partyIdHashMap.containsKey(header.getReceiverId()),
                "Party set does not contain Receiver ID = %s", header.getReceiverId()
        );


        NettyRpcProtobuf.DataPacketProto.HeaderProto headerProto = NettyRpcProtobuf.DataPacketProto.HeaderProto
                .newBuilder()
                .setEncodeTaskId(header.getEncodeTaskId())
                .setPtoId(header.getPtoId())
                .setStepId(header.getStepId())
                .setExtraInfo(header.getExtraInfo())
                .setSenderId(header.getSenderId())
                .setReceiverId(header.getReceiverId())
                .build();

        List<ByteString> payloadByteStringList = dataPacket.getPayload().stream()
                .map(ByteString::copyFrom)
                .collect(Collectors.toList());
        NettyRpcProtobuf.DataPacketProto.PayloadProto payloadProto = NettyRpcProtobuf.DataPacketProto.PayloadProto
                .newBuilder()
                .addAllPayloadBytes(payloadByteStringList)
                .build();

        NettyRpcProtobuf.DataPacketProto dataPacketProto = NettyRpcProtobuf.DataPacketProto
                .newBuilder()
                .setHeaderProto(headerProto)
                .setPayloadProto(payloadProto)
                .build();
        int thisPayloadByteLength = dataPacket.getPayload().stream().mapToInt(data -> data.length).sum();
        sendPayloadByteLength += thisPayloadByteLength;
        int thisPacketByteLength = dataPacketProto.getSerializedSize();
        sendByteLength += thisPacketByteLength;
        sendDataPacketNum++;
        dataSendManager.sendData(partyIdHashMap.get(header.getReceiverId()), dataPacketProto);
        LOGGER.info("{} send to {}, payload {}, packet {}",
                ownParty,
                partyIdHashMap.get(dataPacket.getHeader().getReceiverId()),
                thisPayloadByteLength,
                thisPacketByteLength
        );
//        System.err.format("%d:", dataPacket.getPayload().stream().mapToInt(data -> data.length).sum());
//        dataPacket.getPayload().stream().mapToInt(data -> data.length).forEach(x -> System.err.format("%d,", x));
//        System.err.println();
    }

    public DataPacket receive(DataPacketHeader header, long timeout) {
        Preconditions.checkArgument(
                ownPartyId == header.getReceiverId(), "Receiver ID must be %s", ownPartyId
        );
        Preconditions.checkArgument(
                partyIdHashMap.containsKey(header.getSenderId()),
                "Party set does not contain Sender ID = %s", header.getSenderId()
        );
        try {

            DataPacket dataPacket = dataPacketBuffer.takeTimeout(header, timeout);
            if (null == dataPacket) {
                LOGGER.info("{} timeout receives from {}",
                        ownParty,
                        partyIdHashMap.get(header.getSenderId())
                );
                return null;
            }

            updateReceiveStat(dataPacket);
            return dataPacket;
        } catch (InterruptedException e) {

            return null;
        }
    }

    public DataPacket receiveAny(long timeout) {
        try {

            DataPacket dataPacket = dataPacketBuffer.takeTimeout(ownPartyId, timeout);
            if (null == dataPacket) {
                LOGGER.info("{} timeout receives from Any",
                        ownParty
                );
                return null;
            }
            updateReceiveStat(dataPacket);
            return dataPacket;
        } catch (InterruptedException e) {
            return null;
        }
    }

    @Override
    public DataPacket receive(DataPacketHeader header) {
        Preconditions.checkArgument(
                ownPartyId == header.getReceiverId(), "Receiver ID must be %s", ownPartyId
        );
        Preconditions.checkArgument(
                partyIdHashMap.containsKey(header.getSenderId()),
                "Party set does not contain Sender ID = %s", header.getSenderId()
        );
        try {

            DataPacket dataPacket = dataPacketBuffer.take(header);
            updateReceiveStat(dataPacket);
            return dataPacket;
        } catch (InterruptedException e) {

            return null;
        }
    }

    @Override
    public DataPacket receiveAny() {
        try {

            DataPacket dataPacket = dataPacketBuffer.take(ownPartyId);
            updateReceiveStat(dataPacket);
            return dataPacket;
        } catch (InterruptedException e) {
            return null;
        }
    }

    public long getSendPayloadByteLength() {
        return sendPayloadByteLength;
    }

    @Override
    public long getSendByteLength() {
        return sendByteLength;
    }

    @Override
    public long getSendDataPacketNum() {
        return sendDataPacketNum;
    }

    public long getReceiveDataPacketNum() {
        return receiveDataPacketNum;
    }

    public long getReceivePayloadByteLength() {
        return receivePayloadByteLength;
    }

    public long getReceiveByteLength() {
        return receiveByteLength;
    }

    public long getPayloadByteLength() {
        return receivePayloadByteLength + sendPayloadByteLength;
    }

    public long getByteLength() {
        return receiveByteLength + sendByteLength;
    }

    public long getDataPacketNum() {
        return receiveDataPacketNum + sendDataPacketNum;
    }

    @Override
    public void reset() {
        sendPayloadByteLength = 0;
        sendByteLength = 0;
        sendDataPacketNum = 0;
        receiveDataPacketNum = 0;
        receivePayloadByteLength = 0;
        receiveByteLength = 0;
    }

    @Override
    public void synchronize() {

        partyIdHashMap.keySet().stream().sorted().forEach(otherPartyId -> {
            if (otherPartyId < ownPartyId) {

                DataPacketHeader clientSynchronizeHeader = new DataPacketHeader(
                        Long.MAX_VALUE - ownPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.CLIENT_SYNCHRONIZE.ordinal(),
                        ownPartyId, otherPartyId
                );
                send(DataPacket.fromByteArrayList(clientSynchronizeHeader, new LinkedList<>()));

                DataPacketHeader serverSynchronizeHeader = new DataPacketHeader(
                        Long.MAX_VALUE - otherPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.SERVER_SYNCHRONIZE.ordinal(),
                        otherPartyId, ownPartyId
                );
                receive(serverSynchronizeHeader);
            } else if (otherPartyId > ownPartyId) {

                DataPacketHeader clientSynchronizeHeader = new DataPacketHeader(
                        Long.MAX_VALUE - otherPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.CLIENT_SYNCHRONIZE.ordinal(),
                        otherPartyId, ownPartyId
                );
                receive(clientSynchronizeHeader);
                DataPacketHeader serverSynchronizeHeader = new DataPacketHeader(
                        Long.MAX_VALUE - ownPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.SERVER_SYNCHRONIZE.ordinal(),
                        ownPartyId, otherPartyId
                );
                send(DataPacket.fromByteArrayList(serverSynchronizeHeader, new LinkedList<>()));
            }
        });
        LOGGER.info("{} synchronized", ownParty);
    }

    @Override
    public void disconnect() {

        partyIdHashMap.keySet().stream().sorted().forEach(otherPartyId -> {
            if (otherPartyId < ownPartyId) {

                DataPacketHeader clientFinishHeader = new DataPacketHeader(
                        Long.MAX_VALUE - ownPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.CLIENT_FINISH.ordinal(),
                        ownPartyId, otherPartyId
                );
                send(DataPacket.fromByteArrayList(clientFinishHeader, new LinkedList<>()));

                DataPacketHeader serverFinishHeader = new DataPacketHeader(
                        Long.MAX_VALUE - otherPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.SERVER_FINISH.ordinal(),
                        otherPartyId, ownPartyId
                );
                receive(serverFinishHeader);
            } else if (otherPartyId > ownPartyId) {

                DataPacketHeader clientFinishHeader = new DataPacketHeader(
                        Long.MAX_VALUE - otherPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.CLIENT_FINISH.ordinal(),
                        otherPartyId, ownPartyId
                );
                receive(clientFinishHeader);
                DataPacketHeader serverFinishHeader = new DataPacketHeader(
                        Long.MAX_VALUE - ownPartyId, NettyPtoDesc.getInstance().getPtoId(), NettyPtoDesc.StepEnum.SERVER_FINISH.ordinal(),
                        ownPartyId, otherPartyId
                );
                send(DataPacket.fromByteArrayList(serverFinishHeader, new LinkedList<>()));
            }
        });
        try {

            dataReceiveThread.close();

            cyclicBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }
        LOGGER.info("{} disconnected", ownParty);
    }
}
