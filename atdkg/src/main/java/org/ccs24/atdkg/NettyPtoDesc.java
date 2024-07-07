package org.ccs24.atdkg;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * based on mpc4j
 * Netty protocol info
 */
class NettyPtoDesc implements PtoDesc {
    /**
     * protocol ID
     */
    private static final int PTO_ID = Math.abs((int)3448038492420117282L);
    /**
     * protocol name
     */
    private static final String PTO_NAME = "NETTY_CONNECT";

    /**
     * steps
     */
    enum StepEnum {
        CLIENT_CONNECT,
        SERVER_CONNECT,
        CLIENT_SYNCHRONIZE,
        SERVER_SYNCHRONIZE,
        CLIENT_FINISH,
        SERVER_FINISH,
        CLIENT_TRUE_SYNC,
    }

    private static final NettyPtoDesc INSTANCE = new NettyPtoDesc();

    private NettyPtoDesc() {
        // empty
    }

    public static PtoDesc getInstance() {
        return INSTANCE;
    }

    static {
        PtoDescManager.registerPtoDesc(INSTANCE);
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
