package com.danby.happynode.note.biz.rpc;

import com.danby.happynode.distributed.generator.api.DistributedIdGeneratorFeign;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DistributedIdGeneratorRpcService {

    @Autowired
    private DistributedIdGeneratorFeign distributedIdGeneratorFeign;

    /**
     * 生成雪花算法 ID
     *
     * @return
     */
    public String getSnowflakeId() {
        return distributedIdGeneratorFeign.getSnowflakeId("test");
    }

}
