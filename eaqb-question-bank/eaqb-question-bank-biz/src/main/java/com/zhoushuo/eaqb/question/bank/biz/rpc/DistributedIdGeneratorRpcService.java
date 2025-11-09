package com.zhoushuo.eaqb.question.bank.biz.rpc;

import com.zhoushuo.eaqb.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class DistributedIdGeneratorRpcService {

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;


    /**
     * Leaf 号段模式：题目主键 ID 业务标识
     */
    private static final String BIZ_TAG_QUESTIONBANK_ID = "leaf-segment-questionbank-id";




    /**
     * 调用分布式 ID 生成服务题目 ID
     *
     * @return
     */
    public String getQuestionBankId() {
        return distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_QUESTIONBANK_ID);
    }





}