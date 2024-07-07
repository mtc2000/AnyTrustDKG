package org.ccs24.atdkg;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.netty.NettyParty;
import edu.alibaba.mpc4j.common.rpc.impl.netty.NettyRpcManager;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.IntStream;

/**
 * based on mpc4j
 */
public class UniversalNettyRpcManager implements RpcManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyRpcManager.class);

    private static final int DEFAULT_PORT = 10000;

    private final int partyNum;

    private final Set<NettyParty> nettyPartySet;

    private final Map<Integer, NettyTimeoutRpc> nettyRpcMap;

    private final Map<Pair<String, Integer>, NettyTimeoutRpc> nettyRpcIPPortMap;

    /**
     * initialise Netty manager
     */
    public UniversalNettyRpcManager(List<String> ipAddresses) {
        this.partyNum = ipAddresses.size();
        Preconditions.checkArgument(partyNum > 1, "Number of parties must be greater than 1");
        nettyPartySet = new HashSet<>(partyNum);
        nettyRpcMap = new HashMap<>(partyNum);
        nettyRpcIPPortMap = new HashMap<>(partyNum);
        IntStream.range(0, partyNum).forEach(partyId -> {
            NettyParty nettyParty = new NettyParty(
                    partyId, getPartyName(partyId), ipAddresses.get(partyId), DEFAULT_PORT
            );
            nettyPartySet.add(nettyParty);
        });

        for (NettyParty nettyParty:
                nettyPartySet) {
            int partyId = nettyParty.getPartyId();
            NettyTimeoutRpc nettyRpc = new NettyTimeoutRpc(nettyParty, nettyPartySet);
            nettyRpcMap.put(nettyRpc.ownParty().getPartyId(), nettyRpc);
            nettyRpcIPPortMap.put(new ImmutablePair<>(ipAddresses.get(partyId), DEFAULT_PORT), nettyRpc);
            LOGGER.debug("Add Netty party: {}", nettyParty);
        };
    }

    /**
     * initialise Netty manager
     */
    public UniversalNettyRpcManager(List<String> ipAddresses, List<Integer> ports) {
        this.partyNum = ipAddresses.size();
        Preconditions.checkArgument(partyNum > 1, "Number of parties must be greater than 1");
        Preconditions.checkArgument(ipAddresses.size() == ports.size(), "Number of elements of the arguments must be matched");
        nettyPartySet = new HashSet<>(partyNum);
        nettyRpcMap = new HashMap<>(partyNum);
        nettyRpcIPPortMap = new HashMap<>(partyNum);

        IntStream.range(0, partyNum).forEach(partyId -> {
            NettyParty nettyParty = new NettyParty(
                    partyId, getPartyName(partyId),
                    ipAddresses.get(partyId),
                    ports.get(partyId)
            );
            nettyPartySet.add(nettyParty);
        });

        for (NettyParty nettyParty:
             nettyPartySet) {
            int partyId = nettyParty.getPartyId();
            NettyTimeoutRpc nettyRpc = new NettyTimeoutRpc(nettyParty, nettyPartySet);
            nettyRpcMap.put(nettyRpc.ownParty().getPartyId(), nettyRpc);
            nettyRpcIPPortMap.put(new ImmutablePair<>(ipAddresses.get(partyId), ports.get(partyId)), nettyRpc);
            LOGGER.debug("Add Netty party: {}", nettyParty);
        };
    }

    public UniversalNettyRpcManager(List<Pair<String, Integer>> locations, String locationConstructor) {
        this.partyNum = locations.size();
        Preconditions.checkArgument(partyNum > 1, "Number of parties must be greater than 1");

        nettyPartySet = new HashSet<>(partyNum);
        nettyRpcMap = new HashMap<>(partyNum);
        nettyRpcIPPortMap = new HashMap<>(partyNum);

        IntStream.range(0, partyNum).forEach(partyId -> {
            NettyParty nettyParty = new NettyParty(
                    partyId, getPartyName(partyId),
                    locations.get(partyId).getLeft(),
                    locations.get(partyId).getRight()
            );
            nettyPartySet.add(nettyParty);
        });

        for (NettyParty nettyParty:
                nettyPartySet) {
            int partyId = nettyParty.getPartyId();
            NettyTimeoutRpc nettyRpc = new NettyTimeoutRpc(nettyParty, nettyPartySet);
            nettyRpcMap.put(nettyRpc.ownParty().getPartyId(), nettyRpc);
            nettyRpcIPPortMap.put(
                    new ImmutablePair<>(
                        locations.get(partyId).getLeft(),
                        locations.get(partyId).getRight()),
                    nettyRpc);
            LOGGER.debug("Add Netty party: {}", nettyParty);
        }
    }

    public UniversalNettyRpcManager(String ipAddress, List<Integer> ports) {
        this.partyNum = ports.size();
        Preconditions.checkArgument(partyNum > 1, "Number of parties must be greater than 1");
        nettyPartySet = new HashSet<>(partyNum);
        nettyRpcMap = new HashMap<>(partyNum);
        nettyRpcIPPortMap = new HashMap<>(partyNum);

        IntStream.range(0, partyNum).forEach(partyId -> {
            NettyParty nettyParty = new NettyParty(
                    partyId, getPartyName(partyId),
                    ipAddress,
                    ports.get(partyId)
            );
            nettyPartySet.add(nettyParty);
        });

        for (NettyParty nettyParty:
                nettyPartySet) {
            int partyId = nettyParty.getPartyId();
            NettyTimeoutRpc nettyRpc = new NettyTimeoutRpc(nettyParty, nettyPartySet);
            nettyRpcMap.put(nettyRpc.ownParty().getPartyId(), nettyRpc);
            nettyRpcIPPortMap.put(new ImmutablePair<>(ipAddress, ports.get(partyId)), nettyRpc);
            LOGGER.debug("Add Netty party: {}", nettyParty);
        };
    }

    @Override
    public NettyTimeoutRpc getRpc(int partyId) {
        Preconditions.checkArgument(
                partyId >= 0 && partyId < partyNum, "Party ID must be in range [0, %s)", partyNum
        );
        return nettyRpcMap.get(partyId);
    }

    public NettyTimeoutRpc getRpc(String ipAddress, int port) {
        return nettyRpcIPPortMap.get(new ImmutablePair<>(ipAddress, port));
    }

    public NettyTimeoutRpc getRpc(Pair<String, Integer> location) {
        return nettyRpcIPPortMap.get(new ImmutablePair<>(location.getLeft(), location.getRight()));
    }

    private String getPartyName(int partyId) {
        return "P_" + (partyId + 1);
    }

    @Override
    public int getPartyNum() {
        return partyNum;
    }

    @Override
    public Set<Party> getPartySet() {
        return new HashSet<>(nettyPartySet);
    }
}

