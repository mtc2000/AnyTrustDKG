package org.ccs24.atdkg;

import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketBuffer;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import org.apache.commons.lang3.time.StopWatch;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Based on mpc4j
 * thread-safe data packet buffer. The design follows the Producer-Consumer pattern.
 */
public class DataPacketTimeoutBuffer extends DataPacketBuffer {
    /**
     * default buffer size
     */
    private static final int DEFAULT_BUFFER_SIZE = 1 << 10;
    /**
     * buffer
     */
    private final Map<DataPacketHeader, List<byte[]>> dataPacketBuffer;

    public DataPacketTimeoutBuffer() {
        dataPacketBuffer = new ConcurrentHashMap<>(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Takes a data packet that matches the header.
     *
     * @param header the header.
     * @return a data packet.
     * @throws InterruptedException interrupted exception.
     */
    public synchronized DataPacket takeTimeout(DataPacketHeader header, long timeout) throws InterruptedException {
        assert (header != null);

        long timeLeft = timeout;
        StopWatch watch = new StopWatch();

        // if there is no target data packet in the buffer, waiting until new data packet is added.
        while (!dataPacketBuffer.containsKey(header)) {
            if (timeout < 0) {
                return null;
            } else if (timeout == 0) {
                wait();
            } else {
                watch.start();
                wait(timeLeft);
                watch.stop();
                timeLeft -= watch.getTime(TimeUnit.MILLISECONDS);
                watch.reset();
            }
            if (timeout > 0 && timeLeft <=0 && !dataPacketBuffer.containsKey(header)) return null;
        }
        return DataPacket.fromByteArrayList(header, dataPacketBuffer.remove(header));
    }

    /**
     * Takes a data packet that matches the receiver ID.
     *
     * @param receiverId the receiver ID.
     * @return a data packet.
     * @throws InterruptedException interrupted exception.
     */
    public synchronized DataPacket takeTimeout(int receiverId, long timeout) throws InterruptedException {

        long timeLeft = timeout;
        StopWatch watch = new StopWatch();

        DataPacketHeader targetHeader = null;
        // we first try to find a candidate header
        for (DataPacketHeader dataPacketHeader : dataPacketBuffer.keySet()) {
            if (dataPacketHeader.getReceiverId() == receiverId) {
                targetHeader = dataPacketHeader;
                break;
            }
        }

        while (null == targetHeader) {
            // if we cannot find any candidate, wait for new data packets.
            if (timeout < 0) {
                return null;
            } else if (timeout == 0) {
                wait();
            } else {
                watch.start();
                wait(timeLeft);
                watch.stop();
                timeLeft -= watch.getTime(TimeUnit.MILLISECONDS);
                watch.reset();
            }

            // we try to find a candidate header
            for (DataPacketHeader dataPacketHeader : dataPacketBuffer.keySet()) {
                if (dataPacketHeader.getReceiverId() == receiverId) {
                    targetHeader = dataPacketHeader;
                    break;
                }
            }

            if (timeout > 0 && timeLeft <=0 && null == targetHeader) return null;
        }
        return DataPacket.fromByteArrayList(targetHeader, dataPacketBuffer.remove(targetHeader));
    }

    /**
     * Takes a data packet that matches the receiver ID and the protocol ID.
     *
     * @param receiverId the receiver ID.
     * @param ptoId      the protocol ID.
     * @return a data packet.
     * @throws InterruptedException interrupted exception.
     */
    public synchronized DataPacket takeTimeout(int receiverId, int ptoId, long timeout) throws InterruptedException {

        long timeLeft = timeout;
        StopWatch watch = new StopWatch();

        DataPacketHeader targetHeader = null;
        // we first try to find a candidate header
        for (DataPacketHeader dataPacketHeader : dataPacketBuffer.keySet()) {
            if (dataPacketHeader.getReceiverId() == receiverId && dataPacketHeader.getPtoId() == ptoId) {
                targetHeader = dataPacketHeader;
                break;
            }
        }
        while (null == targetHeader) {
            // if we cannot find any candidate, wait for new data packets.
            if (timeout < 0) {
                return null;
            } else if (timeout == 0) {
                wait();
            } else {
                watch.start();
                wait(timeLeft);
                watch.stop();
                timeLeft -= watch.getTime(TimeUnit.MILLISECONDS);
                watch.reset();
            }

            // we try to find a candidate header
            for (DataPacketHeader dataPacketHeader : dataPacketBuffer.keySet()) {
                if (dataPacketHeader.getReceiverId() == receiverId && dataPacketHeader.getPtoId() == ptoId) {
                    targetHeader = dataPacketHeader;
                    break;
                }
            }

            if (timeout > 0 && timeLeft <=0 && null == targetHeader) return null;
        }
        return DataPacket.fromByteArrayList(targetHeader, dataPacketBuffer.remove(targetHeader));
    }


    /**
     * Puts a data packet into the buffer.
     *
     * @param dataPacket the data packet.
     */
    @Override
    public synchronized void put(DataPacket dataPacket) {
        assert (dataPacket != null);
        dataPacketBuffer.put(dataPacket.getHeader(), dataPacket.getPayload());
        notifyAll();
    }

    /**
     * Takes a data packet that matches the header.
     *
     * @param header the header.
     * @return a data packet.
     * @throws InterruptedException interrupted exception.
     */
    @Override
    public synchronized DataPacket take(DataPacketHeader header) throws InterruptedException {
        return takeTimeout(header, 0);
    }

    /**
     * Takes a data packet that matches the receiver ID.
     *
     * @param receiverId the receiver ID.
     * @return a data packet.
     * @throws InterruptedException interrupted exception.
     */
    @Override
    public synchronized DataPacket take(int receiverId) throws InterruptedException {
        return takeTimeout(receiverId, 0);
    }

    /**
     * Takes a data packet that matches the receiver ID and the protocol ID.
     *
     * @param receiverId the receiver ID.
     * @param ptoId      the protocol ID.
     * @return a data packet.
     * @throws InterruptedException interrupted exception.
     */
    @Override
    public synchronized DataPacket take(int receiverId, int ptoId) throws InterruptedException {
        return takeTimeout(receiverId, ptoId, 0);
    }
}
