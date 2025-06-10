package com.danby.happynode.user.biz.rpc;

import com.danby.happynode.distributed.generator.api.DistributedIdGeneratorFeign;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DistributedIdGeneratorRpcService {

    @Autowired
    private DistributedIdGeneratorFeign distributedIdGeneratorFeign;


    /**
     * Leaf 号段模式：小哈书 ID 业务标识
     */
    private static final String BIZ_TAG_HAPPYNODE_ID = "leaf-segment-happynode-id";
    /**
     * Leaf 号段模式：用户 ID 业务标识
     */
    private static final String BIZ_TAG_USER_ID = "leaf-segment-user-id";

    /**
     * 调用分布式 ID 生成服务生成小哈书 ID
     *
     * @return
     */
    public String getHappynodeId() {
        return distributedIdGeneratorFeign.getSegmentId(BIZ_TAG_HAPPYNODE_ID);
    }

    /**
     * 调用分布式 ID 生成服务用户 ID
     *
     * @return
     */
    public String getUserId() {
        return distributedIdGeneratorFeign.getSegmentId(BIZ_TAG_USER_ID);
    }
}
