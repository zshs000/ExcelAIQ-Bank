package com.zhoushuo.eaqb.user.biz.rpc;

import com.zhoushuo.eaqb.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import com.zhoushuo.eaqb.user.biz.enums.ResponseCodeEnum;
import com.zhoushuo.framework.commono.exception.BizException;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class DistributedIdGeneratorRpcService {

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /**
     * Leaf 号段模式：题库系统 ID 业务标识
     */
    private static final String BIZ_TAG_EAQB_ID = "leaf-segment-eaqb-id";

    /**
     * Leaf 号段模式：用户 ID 业务标识
     */
    private static final String BIZ_TAG_USER_ID = "leaf-segment-user-id";

    /**
     * 调用分布式 ID 生成服务生成题库系统 ID
     *
     * @return
     */
    public String getEaqbId() {
        return requireSegmentId(BIZ_TAG_EAQB_ID);
    }

    /**
     * 调用分布式 ID 生成服务用户 ID
     *
     * @return
     */
    public String getUserId() {
        return requireSegmentId(BIZ_TAG_USER_ID);
    }

    /**
     * 当前统一将远程调用异常收口为 ID 生成失败；若后续引入重试、熔断或差异化降级，再细分异常类型。
     */
    private String requireSegmentId(String bizTag) {
        try {
            String id = distributedIdGeneratorFeignApi.getSegmentId(bizTag);
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
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ResponseCodeEnum.ID_GENERATE_FAILED.getErrorCode(), "分布式ID服务调用失败");
        }
    }
}
