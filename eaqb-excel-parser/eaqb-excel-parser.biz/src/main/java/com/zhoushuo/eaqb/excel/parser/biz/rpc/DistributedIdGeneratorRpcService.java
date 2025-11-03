package com.zhoushuo.eaqb.excel.parser.biz.rpc;

import com.zhoushuo.eaqb.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class DistributedIdGeneratorRpcService {

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;


    /**
     * Leaf 号段模式：文件主键 ID 业务标识
     */
    private static final String BIZ_TAG_FILE_ID = "leaf-segment-file-id";



    public static final String BIZ_TAG_PRE_FILE_ID = "leaf-segment-pre-file-id";


    /**
     * 调用分布式 ID 生成服务文件 ID
     *
     * @return
     */
    public String getFileId() {
        return distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_FILE_ID);
    }


    public String getPreFileId() {return distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_PRE_FILE_ID);}



}