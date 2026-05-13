package com.zhoushuo.eaqb.oss.biz.service.impl;

import com.zhoushuo.eaqb.oss.biz.config.PresignProperties;
import com.zhoushuo.eaqb.oss.biz.constant.ObjectPathConstants;
import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.oss.biz.service.FileService;
import com.zhoushuo.eaqb.oss.biz.strategy.FileStrategy;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Resource
    private FileStrategy fileStrategy;
    @Resource
    private PresignProperties presignProperties;

    private static final String BUCKET_NAME = "eaqb";
    private static final Pattern PATH_ANOMALY_PATTERN =
            Pattern.compile(".*((^|/)\\.\\.(/|$)|//|%[0-9A-Fa-f]{2}).*");

    @Override
    public Response<?> uploadExcel(MultipartFile file, String objectName) {
        if (StringUtils.isBlank(objectName)) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        String objectKey = fileStrategy.uploadExcel(file, BUCKET_NAME, objectName);
        return Response.success(objectKey);
    }

    @Override
    public Response<?> uploadAvatar(MultipartFile file) {
        String objectKey = fileStrategy.uploadAvatar(file, BUCKET_NAME);
        return Response.success(objectKey);
    }

    @Override
    public Response<?> uploadBackground(MultipartFile file) {
        String objectKey = fileStrategy.uploadBackground(file, BUCKET_NAME);
        return Response.success(objectKey);
    }

    @Override
    public Response<String> getExcelDownloadUrl(String objectKey) {
        String checkedObjectKey = validateOwnedObjectKey(objectKey, ObjectPathConstants::buildExcelUserPathPrefix);
        return buildPresignedUrl(
                checkedObjectKey,
                Duration.ofSeconds(presignProperties.getExcelDownloadExpireSeconds()),
                "Excel 下载");
    }

    @Override
    public Response<String> getImageViewUrl(String objectKey) {
        String checkedObjectKey = validateOwnedObjectKey(objectKey, ObjectPathConstants::buildImageUserPathPrefix);
        return buildPresignedUrl(
                checkedObjectKey,
                Duration.ofSeconds(presignProperties.getImageViewExpireSeconds()),
                "图片查看");
    }

    private String validateOwnedObjectKey(String objectKey, Function<Long, String> userPathPrefixBuilder) {
        if (StringUtils.isBlank(objectKey)) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        String trimmedObjectKey = objectKey.trim();
        if (PATH_ANOMALY_PATTERN.matcher(trimmedObjectKey).matches()) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        Long userId = LoginUserContextHolder.getUserId();
        if (userId == null) {
            throw new BizException(ResponseCodeEnum.OBJECT_KEY_ACCESS_DENIED);
        }
        String ownedPathPrefix = userPathPrefixBuilder.apply(userId);
        if (!trimmedObjectKey.startsWith(ownedPathPrefix)) {
            throw new BizException(ResponseCodeEnum.OBJECT_KEY_ACCESS_DENIED);
        }
        return trimmedObjectKey;
    }

    /**
     * 生成预签名访问 URL 的公共实现。
     * <p>
     * {@link #getExcelDownloadUrl} 和 {@link #getImageViewUrl} 的差异仅在于过期时间和日志场景名，
     * 收敛到此方法统一处理，避免重复的校验与日志逻辑。
     * <p>
     * TODO: 如果未来预签名场景继续增多（例如视频预览、导出下载等），
     * 可以进一步收敛为统一的 getPresignedUrl(objectKey, PresignScene) 入口，
     * 再由 scene 枚举映射对应 TTL，避免 service 方法继续膨胀。
     *
     * @param objectKey  对象存储中的完整路径
     * @param expire     签名有效期，由各业务场景决定
     * @param sceneName  场景名称，仅用于日志，如 "Excel 下载"、"图片查看"
     * @return 预签名访问 URL
     */
    private Response<String> buildPresignedUrl(String objectKey, Duration expire, String sceneName) {
        // TODO: 如后续存在管理员/系统任务生成用户文件预签名 URL 的场景，
        // 应新增独立 internal/admin 接口，并显式传入 ownerUserId 与权限校验；
        // 不要在当前用户态接口中绕过用户命名空间校验。
        log.info("准备生成{}访问凭证：{}", sceneName, objectKey);
        String accessUrl = fileStrategy.getPresignedUrl(BUCKET_NAME, objectKey, expire);
        log.info("{}访问凭证生成成功，objectKey：{}", sceneName, objectKey);
        return Response.success(accessUrl);
    }
}
