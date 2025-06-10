package com.danby.happynode.distributed.generator.api;

import com.danby.happynode.distributed.generator.constant.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = ApiConstants.SERVICE_NAME, path = ApiConstants.SERVICE_PATH)
public interface DistributedIdGeneratorFeign {
    @GetMapping("/segment/get/{key}")
    String getSegmentId(@PathVariable("key") String key);

    @GetMapping("/snowflake/get/{key}")
    String getSnowflakeId(@PathVariable("key") String key);
}
