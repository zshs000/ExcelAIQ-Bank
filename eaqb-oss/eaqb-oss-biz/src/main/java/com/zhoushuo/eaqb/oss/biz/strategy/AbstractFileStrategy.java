package com.zhoushuo.eaqb.oss.biz.strategy;

import com.zhoushuo.eaqb.oss.biz.constant.FileConstants;
import com.zhoushuo.eaqb.oss.biz.constant.ObjectPathConstants;
import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.oss.biz.util.FileTypeUtil;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.exception.BizException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传策略的模板基类。
 *
 * <p>把上传流程的公共骨架（校验→取用户ID→拼 objectKey→调用子类上传）提取到此处，
 * 子类只需实现底层存储厂商的差异部分。</p>
 */
@Slf4j
public abstract class AbstractFileStrategy implements FileStrategy {

    @Override
    @SneakyThrows
    public String uploadExcel(MultipartFile file, String bucketName, String objectName) {
        String objectKey = ObjectPathConstants.buildExcelObjectKey(currentUserId(), objectName);
        doUpload(file, bucketName, objectKey, FileConstants.FILE_TYPE_EXCEL);
        return objectKey;
    }

    @Override
    @SneakyThrows
    public String uploadAvatar(MultipartFile file, String bucketName) {
        String objectKey = ObjectPathConstants.buildAvatarObjectKey(currentUserId());
        doUpload(file, bucketName, objectKey, FileConstants.FILE_TYPE_IMAGE);
        return objectKey;
    }

    @Override
    @SneakyThrows
    public String uploadBackground(MultipartFile file, String bucketName) {
        String objectKey = ObjectPathConstants.buildBackgroundObjectKey(currentUserId());
        doUpload(file, bucketName, objectKey, FileConstants.FILE_TYPE_IMAGE);
        return objectKey;
    }

    /**
     * 校验文件合法性后调用子类上传实现。
     */
    @SneakyThrows
    private void doUpload(MultipartFile file, String bucketName, String objectKey, String expectedFileType) {
        validateFileNotEmpty(file);
        validateFileType(file, expectedFileType);
        doUploadByObjectKey(file, bucketName, objectKey);
    }

    /**
     * 子类实现：将文件上传到具体存储厂商。
     */
    protected abstract void doUploadByObjectKey(MultipartFile file, String bucketName, String objectKey);

    private void validateFileNotEmpty(MultipartFile file) {
        if (file == null || file.getSize() == 0 || file.isEmpty()) {
            log.error("==> 上传文件异常：文件大小为空 ...");
            throw new BizException(ResponseCodeEnum.FILE_EMPTY_ERROR);
        }
    }

    private void validateFileType(MultipartFile file, String expectedFileType) {
        String fileType = FileTypeUtil.getFileType(file);
        if (!expectedFileType.equals(fileType)) {
            throw new BizException(ResponseCodeEnum.FILE_TYPE_ERROR);
        }
    }

    private Long currentUserId() {
        Long userId = LoginUserContextHolder.getUserId();
        if (userId == null) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        return userId;
    }
}
