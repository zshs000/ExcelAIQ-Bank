package com.zhoushuo.eaqb.question.bank.biz.rpc;

import com.zhoushuo.eaqb.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.framework.common.exception.BizException;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DistributedIdGeneratorRpcService {

    private static final String BIZ_TAG_QUESTIONBANK_ID = "leaf-segment-questionbank-id";

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /**
     * 获取 question-bank 业务域内的通用实体 ID。
     * TODO: 后续可按 question / task / outbox / inbox / record 进一步拆成独立发号方法与 bizTag。
     */
    public String nextQuestionBankEntityId() {
        return requireSegmentId(BIZ_TAG_QUESTIONBANK_ID);
    }

    /**
     * 按需批量取号，适用于导入提交这类已知最终落库条数的场景，避免在本地事务内逐条远程取号。
     */
    public List<Long> nextQuestionBankEntityIds(int count) {
        if (count <= 0) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        try {
            List<String> rawIds = distributedIdGeneratorFeignApi.getSegmentIds(BIZ_TAG_QUESTIONBANK_ID, count);
            if (rawIds == null) {
                throw new BizException(ResponseCodeEnum.ID_GENERATE_FAILED.getErrorCode(), "分布式ID服务批量响应为空");
            }
            if (rawIds.size() != count) {
                throw new BizException(ResponseCodeEnum.ID_GENERATE_FAILED.getErrorCode(),
                        "分布式ID服务批量返回数量不匹配");
            }
            List<Long> ids = new ArrayList<>(count);
            for (String rawId : rawIds) {
                ids.add(Long.valueOf(requireNumericId(rawId)));
            }
            return ids;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ResponseCodeEnum.ID_GENERATE_FAILED.getErrorCode(), "分布式ID服务调用失败");
        }
    }

    /**
     * 当前统一将远程调用异常收口为 ID 生成失败；若后续引入重试、熔断或差异化降级，再细分异常类型。
     */
    private String requireSegmentId(String bizTag) {
        try {
            return requireNumericId(distributedIdGeneratorFeignApi.getSegmentId(bizTag));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ResponseCodeEnum.ID_GENERATE_FAILED.getErrorCode(), "分布式ID服务调用失败");
        }
    }

    private String requireNumericId(String id) {
        if (id == null) {
            throw new BizException(ResponseCodeEnum.ID_GENERATE_FAILED.getErrorCode(), "分布式ID服务响应为空");
        }
        if (StringUtils.isBlank(id)) {
            throw new BizException(ResponseCodeEnum.ID_GENERATE_FAILED.getErrorCode(), "分布式ID服务返回了空白ID");
        }
        if (!StringUtils.isNumeric(id)) {
            throw new BizException(ResponseCodeEnum.ID_GENERATE_FAILED.getErrorCode(),
                    "分布式ID服务返回的ID非法: " + id);
        }
        return id;
    }
}
