package com.zhoushuo.eaqb.excel.parser.biz.service.support;

import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.FileInfoDO;
import com.zhoushuo.eaqb.excel.parser.biz.domain.mapper.FileInfoDOMapper;
import com.zhoushuo.eaqb.excel.parser.biz.enums.ResponseCodeEnum;
import com.zhoushuo.framework.commono.exception.BizException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Excel 文件记录支撑类。
 *
 * 负责与 file_info 表直接交互的公共动作，包括：
 * - 文件归属校验；
 * - 解析资格抢占；
 * - 上传/解析状态回写；
 * - 异常场景下的静默收尾。
 */
@Slf4j
@Component
public class ExcelFileRecordSupport {

    public static final String FILE_STATUS_UPLOADED = "UPLOADED";
    public static final String FILE_STATUS_UPLOADING = "UPLOADING";
    public static final String FILE_STATUS_UPLOAD_FAILED = "UPLOAD_FAILED";
    public static final String FILE_STATUS_PARSED = "PARSED";
    public static final String FILE_STATUS_FAILED = "FAILED";

    @Resource
    private FileInfoDOMapper fileInfoDOMapper;

    /**
     * 先校验文件存在性和归属，再交给调用方继续做状态抢占或后续处理。
     */
    public FileInfoDO loadOwnedFile(Long fileId, Long currentUserId) {
        FileInfoDO fileInfo = fileInfoDOMapper.selectByPrimaryKey(fileId);
        if (fileInfo == null) {
            log.error("==> 文件不存在，fileId: {}", fileId);
            throw new BizException(ResponseCodeEnum.RECORD_NOT_FOUND);
        }

        if (!Objects.equals(fileInfo.getUserId(), currentUserId)) {
            log.warn("==> 无权限解析文件，fileId: {}, ownerUserId: {}, currentUserId: {}",
                    fileId, fileInfo.getUserId(), currentUserId);
            throw new BizException(ResponseCodeEnum.NO_PERMISSION);
        }
        return fileInfo;
    }

    /**
     * 通过条件更新抢占解析资格，避免同一个文件被并发重复解析。
     */
    public boolean tryMarkParsing(Long fileId, Long userId) {
        return fileInfoDOMapper.tryMarkParsing(fileId, userId) > 0;
    }

    /**
     * 普通状态回写，仅更新 status。
     */
    public void markFileStatus(Long fileId, String status) {
        FileInfoDO updateDO = new FileInfoDO();
        updateDO.setId(fileId);
        updateDO.setStatus(status);
        fileInfoDOMapper.updateByPrimaryKeySelective(updateDO);
    }

    /**
     * 上传阶段状态回写，同时可附带 objectKey。
     */
    public void markUploadStatus(Long fileId, String status, String objectKey) {
        FileInfoDO updateDO = new FileInfoDO();
        updateDO.setId(fileId);
        updateDO.setStatus(status);
        updateDO.setObjectKey(objectKey);
        fileInfoDOMapper.updateByPrimaryKeySelective(updateDO);
    }

    /**
     * 上传成功后的本地收尾动作。
     */
    public void markUploadSuccess(FileInfoDO fileInfoDO, String uploadedObjectKey) {
        markUploadStatus(fileInfoDO.getId(), FILE_STATUS_UPLOADED, uploadedObjectKey);
        fileInfoDO.setObjectKey(uploadedObjectKey);
        fileInfoDO.setStatus(FILE_STATUS_UPLOADED);
    }

    /**
     * 异常收尾只记日志，不应覆盖主异常。
     */
    public void markFileStatusQuietly(Long fileId, String status) {
        try {
            markFileStatus(fileId, status);
        } catch (Exception ex) {
            log.error("==> 更新文件状态失败，fileId: {}, status: {}", fileId, status, ex);
        }
    }
}
