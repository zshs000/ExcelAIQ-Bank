package com.zhoushuo.eaqb.distributed.id.generator.api;

import com.zhoushuo.eaqb.distributed.id.generator.constant.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(
        name = ApiConstants.SERVICE_NAME,
        url = "${rpc.distributed-id-generator.url:#{null}}"
)
public interface DistributedIdGeneratorFeignApi {

    String PREFIX = "/id";

    @GetMapping(value = PREFIX + "/segment/get/{key}")
    String getSegmentId(@PathVariable("key") String key);

    @GetMapping(value = PREFIX + "/segment/get/{key}/batch/{count}")
    List<String> getSegmentIds(@PathVariable("key") String key, @PathVariable("count") Integer count);

    @GetMapping(value = PREFIX + "/snowflake/get/{key}")
    String getSnowflakeId(@PathVariable("key") String key);

}
