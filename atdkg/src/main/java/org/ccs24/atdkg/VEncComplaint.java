package org.ccs24.atdkg;

import edu.alibaba.mpc4j.common.tool.utils.IntUtils;

import java.util.ArrayList;
import java.util.List;

class VEncComplaint {
    int plaintiffPartyId;
    int defendantPartyId;
    VEnc.VEncProof complaintProof;

    public VEncComplaint(int plaintiffPartyId, int defendantPartyId, VEnc.VEncProof complaintProof) {
        this.plaintiffPartyId = plaintiffPartyId;
        this.defendantPartyId = defendantPartyId;
        this.complaintProof = complaintProof;
    }

    public List<byte[]> serialize() {
        List<byte[]> result = new ArrayList<>();
        result.add(
                IntUtils.intToByteArray(this.plaintiffPartyId) // 1
        );
        result.add(
                IntUtils.intToByteArray(this.defendantPartyId)  // 1
        );
        result.addAll(this.complaintProof.serialize()); // 3
        return result;
    }

    public static VEncComplaint deserialize(List<byte[]> serializationList) {
        // System.err.print("size of complaint: ");
        // System.err.println(serializationList.stream().mapToInt(data -> data.length).sum());
        int plaintiffPartyId = IntUtils.byteArrayToInt(serializationList.get(0));
        int defendantPartyId = IntUtils.byteArrayToInt(serializationList.get(1));
        VEnc.VEncProof complaintProof = VEnc.VEncProof.deserialize(serializationList.subList(2, 5));
        return new VEncComplaint(
                plaintiffPartyId,
                defendantPartyId,
                complaintProof
        );
    }
}
