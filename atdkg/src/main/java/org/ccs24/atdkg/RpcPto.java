package org.ccs24.atdkg;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * based on mpc4j
 * RPC protocol info
 */
class RpcPto implements PtoDesc {
    /**
     * protocol ID
     */
    private static final int PTO_ID = Math.abs((int)20231012L);
    /**
     * protocol name
     */
    private static final String PTO_NAME = "RPC_ATDKG";

    /**
     * steps
     */
    enum PtoStep {
        EMPTY,
        ZERO_LENGTH,
        SINGLE,
        EXTRA_INFO,
        TAKE_ANY,
    }

    private static final RpcPto INSTANCE = new RpcPto();

    private RpcPto() {
        // empty
    }

    public static PtoDesc getInstance() {
        return INSTANCE;
    }

    static {
        PtoDescManager.registerPtoDesc(RpcPto.getInstance());
    }

    @Override
    public int getPtoId() {
        return PTO_ID;
    }

    @Override
    public String getPtoName() {
        return PTO_NAME;
    }
}
