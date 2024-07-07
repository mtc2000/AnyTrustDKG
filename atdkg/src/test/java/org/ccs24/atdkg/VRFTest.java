package org.ccs24.atdkg;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VRFTest {

    @Test
    void hash() {
        // public parameters
        // q, a prime number
        // Z_q, integers modulo q
        // Z^*_q, Z_q \setminus {0}
        // G, a cyclic group or prime order q with generator g
        // q, g, G are public

        VRF vrf = new VRF();
        VRF.VRFProof vrfProof = vrf.hash(new byte[] {1});
        Assertions.assertTrue(VRF.verify(vrfProof, vrf.getPublicKey()));
    }

    @Test
    void verify() {
        // public parameters
        // q, a prime number
        // Z_q, integers modulo q
        // Z^*_q, Z_q \setminus {0}
        // G, a cyclic group or prime order q with generator g
        // q, g, G are public

        VRF vrf = new VRF();
        VRF.VRFProof vrfProof = vrf.hash(new byte[] {1,1,1});
        Assertions.assertTrue(VRF.verify(vrfProof, vrf.getPublicKey()));
    }
}