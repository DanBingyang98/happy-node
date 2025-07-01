package com.danby.happynode.comment.biz.rpc;

import com.danby.happynode.distributed.generator.api.DistributedIdGeneratorFeign;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DistributedIdGeneratorRpcService {
    @Autowired
    private DistributedIdGeneratorFeign distributedIdGeneratorFeign;

    public String getGeneratedCommentId() {
        return distributedIdGeneratorFeign.getSegmentId("leaf-segment-comment-id");
    }

}
