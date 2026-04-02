package com.zhoushuo.eaqb.excel.parser.biz.service.app;

import com.alibaba.excel.EasyExcel;
import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.ExcelPreUploadRecordDO;
import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.FileInfoDO;
import com.zhoushuo.eaqb.excel.parser.biz.domain.mapper.ExcelPreUploadRecordDOMapper;
import com.zhoushuo.eaqb.excel.parser.biz.domain.mapper.FileInfoDOMapper;
import com.zhoushuo.eaqb.excel.parser.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.excel.parser.biz.model.dto.ExcelFileUploadDTO;
import com.zhoushuo.eaqb.excel.parser.biz.model.vo.ExcelFileUploadVO;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.OssRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.service.support.ExcelFileRecordSupport;
import com.zhoushuo.eaqb.excel.parser.biz.util.ExcelTemplateValidator;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Excel 上传应用服务。
 *
 * 负责上传入口的完整编排：
 * 1. 文件基础校验；
 * 2. 模板/内容校验；
 * 3. 校验失败落预上传记录；
 * 4. 校验通过后生成正式 fileId 并上传 OSS；
 * 5. 回写上传结果并返回前端可用的上传结果。
 */
@Slf4j
@Service
public class ExcelUploadAppService {

    private static final Set<String> SUPPORTED_EXCEL_EXTENSIONS = new HashSet<>(Arrays.asList("xlsx"));
    private static final Set<String> SUPPORTED_EXCEL_MAGIC_NUMBERS = new HashSet<>(Arrays.asList("504B0304"));
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @Resource
    private OssRpcService ossRpcService;
    @Resource
    private FileInfoDOMapper fileInfoDOMapper;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource
    private ExcelPreUploadRecordDOMapper excelPreUploadRecordDOMapper;
    @Resource
    private ExcelFileRecordSupport excelFileRecordSupport;

    public Response<?> uploadAExcel(ExcelFileUploadDTO excelFileUploadDTO) {
        MultipartFile file = excelFileUploadDTO.getFile();

        String originalFilename = file.getOriginalFilename();
        String extension = validateUploadFileAndGetExtension(file, originalFilename);

        Response<?> validationFailureResponse = validateTemplateAndBuildFailureResponse(file, originalFilename);
        if (validationFailureResponse != null) {
            return validationFailureResponse;
        }

        FileInfoDO fileInfoDO = createUploadingFileRecord(file, originalFilename);
        String uploadedObjectKey = uploadExcelToOss(file, fileInfoDO.getId(), extension);
        excelFileRecordSupport.markUploadSuccess(fileInfoDO, uploadedObjectKey);
        return buildUploadSuccessResponse(fileInfoDO);
    }

    private String validateUploadFileAndGetExtension(MultipartFile file, String originalFilename) {
        if (file.getSize() > MAX_FILE_SIZE) {
            log.error("==> 文件大小超过限制，当前大小: {}KB", file.getSize() / 1024);
            throw new BizException(ResponseCodeEnum.FILE_SIZE_EXCEED);
        }
        if (originalFilename == null || originalFilename.isEmpty()) {
            log.error("==> 文件名为空");
            throw new BizException(ResponseCodeEnum.INVALID_FILE_FORMAT);
        }

        String extension = FilenameUtils.getExtension(originalFilename).toLowerCase();
        if (!SUPPORTED_EXCEL_EXTENSIONS.contains(extension)) {
            log.error("==> 文件格式不支持，当前扩展名: {}", extension);
            throw new BizException(ResponseCodeEnum.FILE_TYPE_ERROR);
        }

        try {
            String magicNumber = getFileMagicNumber(file);
            if (!SUPPORTED_EXCEL_MAGIC_NUMBERS.contains(magicNumber)) {
                log.error("==> 文件魔术头不匹配，当前魔术头: {}", magicNumber);
                throw new BizException(ResponseCodeEnum.FILE_TYPE_ERROR);
            }
        } catch (IOException e) {
            log.error("==> 读取文件魔术头失败", e);
            throw new BizException(ResponseCodeEnum.FILE_READ_ERROR);
        }
        return extension;
    }

    private Response<?> validateTemplateAndBuildFailureResponse(MultipartFile file, String originalFilename) {
        log.info("1. 开始Excel校验流程");
        ExcelTemplateValidator validator = new ExcelTemplateValidator();
        try (InputStream inputStream = file.getInputStream()) {
            log.info("2. 创建输入流成功");
            if (inputStream.available() == 0) {
                log.error("文件内容为空");
                throw new BizException(ResponseCodeEnum.FILE_EMPTY_ERROR);
            }

            log.info("3. 开始EasyExcel读取");
            // 上传校验链路：需要读取表头本身做模板校验，因此不跳过表头，
            // 交给 validator 自行判断第 1 行是否合法。
            EasyExcel.read(inputStream, validator)
                    .sheet()
                    .headRowNumber(0)
                    .doRead();
            log.info("4. EasyExcel读取完成，错误数: {}", validator.getErrorMessages().size());

            if (validator.isValid()) {
                return null;
            }
            return buildValidationFailureResponse(file, originalFilename, validator.getErrorMessages());
        } catch (IOException e) {
            log.error("Excel文件读取失败", e);
            throw new BizException(ResponseCodeEnum.FILE_READ_ERROR);
        }
    }

    private Response<?> buildValidationFailureResponse(MultipartFile file, String originalFilename, List<String> errors) {
        String errorMessages = String.join("\n", errors);
        log.error("Excel文件内容校验失败: {}", errorMessages);

        Long preUploadId = Long.valueOf(distributedIdGeneratorRpcService.getPreFileId());
        ExcelPreUploadRecordDO record = ExcelPreUploadRecordDO.builder()
                .id(preUploadId)
                .userId(LoginUserContextHolder.getUserId())
                .fileName(originalFilename)
                .fileSize(file.getSize())
                .verifyStatus("FAIL")
                .errorMessages(errorMessages)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        excelPreUploadRecordDOMapper.insert(record);

        ExcelFileUploadVO resultVO = ExcelFileUploadVO.builder()
                .preUploadId(preUploadId)
                .fileName(originalFilename)
                .fileSize(file.getSize())
                .verifyStatus("FAIL")
                .errorSummary("Excel内容格式错误：" + errors.get(0) + "等" + errors.size() + "个错误")
                .formattedSize(formatFileSize(file.getSize()))
                .build();
        return Response.success(resultVO);
    }

    private FileInfoDO createUploadingFileRecord(MultipartFile file, String originalFilename) {
        Long userId = LoginUserContextHolder.getUserId();
        Long fileId = Long.valueOf(distributedIdGeneratorRpcService.getFileId());
        FileInfoDO fileInfoDO = FileInfoDO.builder()
                .id(fileId)
                .userId(userId)
                .fileName(originalFilename)
                .fileSize(file.getSize())
                .uploadTime(LocalDateTime.now())
                .status(ExcelFileRecordSupport.FILE_STATUS_UPLOADING)
                .build();
        fileInfoDOMapper.insert(fileInfoDO);
        return fileInfoDO;
    }

    /**
     * 调用 OSS 服务执行正式上传。
     */
    private String uploadExcelToOss(MultipartFile file, Long fileId, String extension) {
        String objectName = fileId + "." + extension;
        try {
            return ossRpcService.uploadExcel(file, objectName);
        } catch (BizException ex) {
            excelFileRecordSupport.markUploadStatus(fileId, ExcelFileRecordSupport.FILE_STATUS_UPLOAD_FAILED, null);
            log.warn("==> 文件上传OSS失败，fileId: {}, errorCode: {}, message: {}",
                    fileId, ex.getErrorCode(), ex.getErrorMessage());
            throw ex;
        } catch (Exception ex) {
            excelFileRecordSupport.markUploadStatus(fileId, ExcelFileRecordSupport.FILE_STATUS_UPLOAD_FAILED, null);
            log.error("==> 文件上传OSS异常，fileId: {}", fileId, ex);
            throw new BizException(ResponseCodeEnum.FILE_UPLOAD_ERROR);
        }
    }

    private Response<?> buildUploadSuccessResponse(FileInfoDO fileInfoDO) {
        ExcelFileUploadVO resultVO = ExcelFileUploadVO.builder()
                .fileId(fileInfoDO.getId())
                .fileName(fileInfoDO.getFileName())
                .fileSize(fileInfoDO.getFileSize())
                .uploadTime(fileInfoDO.getUploadTime())
                .status(fileInfoDO.getStatus())
                .formattedSize(formatFileSize(fileInfoDO.getFileSize()))
                .build();
        log.info("==> 返回结果: {}", resultVO);
        return Response.success(resultVO);
    }

    private String formatFileSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String getFileMagicNumber(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[4];
            int bytesRead = is.read(buffer, 0, 4);
            if (bytesRead < 4) {
                throw new IOException("File too small");
            }
            StringBuilder hexString = new StringBuilder();
            for (byte b : buffer) {
                hexString.append(String.format("%02X", b));
            }
            return hexString.toString();
        }
    }
}
