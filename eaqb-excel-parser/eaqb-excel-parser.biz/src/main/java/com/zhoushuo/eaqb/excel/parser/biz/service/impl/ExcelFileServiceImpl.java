package com.zhoushuo.eaqb.excel.parser.biz.service.impl;
import com.zhoushuo.framework.commono.eumns.ProcessStatusEnum;
import com.alibaba.excel.EasyExcel;
import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.ExcelPreUploadRecordDO;
import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.FileInfoDO;
import com.zhoushuo.eaqb.excel.parser.biz.domain.mapper.ExcelPreUploadRecordDOMapper;
import com.zhoushuo.eaqb.excel.parser.biz.domain.mapper.FileInfoDOMapper;
import com.zhoushuo.eaqb.excel.parser.biz.model.dto.ExcelFileUploadDTO;
import com.zhoushuo.eaqb.excel.parser.biz.model.dto.QuestionDataDTO;
import com.zhoushuo.eaqb.excel.parser.biz.model.vo.ExcelFileUploadVO;
import com.zhoushuo.eaqb.excel.parser.biz.model.vo.ExcelProcessVO;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.OssRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.QuestionBankRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.service.ExcelFileService;
import com.zhoushuo.eaqb.excel.parser.biz.util.DownloadedExcelResource;
import com.zhoushuo.eaqb.excel.parser.biz.util.ExcelParserUtil;
import com.zhoushuo.eaqb.excel.parser.biz.util.ExcelTemplateValidator;
import com.zhoushuo.eaqb.excel.parser.biz.util.PresignedUrlDownloader;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.QuestionDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import com.zhoushuo.eaqb.excel.parser.biz.enums.ResponseCodeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class ExcelFileServiceImpl implements ExcelFileService {
    private static final String FILE_STATUS_UPLOADED = "UPLOADED";
    private static final String FILE_STATUS_PARSING = "PARSING";
    private static final String FILE_STATUS_PARSED = "PARSED";
    private static final String FILE_STATUS_FAILED = "FAILED";

    @Resource
    private OssRpcService ossRpcService;
    @Resource
    private FileInfoDOMapper fileInfoDOMapper;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @Resource
    private ExcelPreUploadRecordDOMapper excelPreUploadRecordDOMapper;

    @Resource
    private QuestionBankRpcService questionBankRpcService;

    // 支持的Excel文件扩展名
    private static final Set<String> SUPPORTED_EXCEL_EXTENSIONS = new HashSet<>(
            Arrays.asList("xlsx"));
    // 支持的Excel魔术头
    private static final Set<String> SUPPORTED_EXCEL_MAGIC_NUMBERS = new HashSet<>(

            Arrays.asList("504B0304")
    );
    // 文件大小限制（10MB）
    //todo 后期改为配置，默认10M
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    @Override
    public Response<?> uploadAExcel(ExcelFileUploadDTO excelFileUploadDTO) {
        // 获取上传的文件
        var file = excelFileUploadDTO.getFile();

        //1.文件大小是否超过限制
        if (file.getSize() > MAX_FILE_SIZE) {
            log.error("==> 文件大小超过限制，当前大小: {}KB", file.getSize() / 1024);
            throw new BizException(ResponseCodeEnum.FILE_SIZE_EXCEED);
        }
        //2.文件格式是否为xlsx，需要查看后缀和魔术头
        String originalFilename = file.getOriginalFilename();
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

        //3.校验是否符合模板，使用工具类
        // 3. 校验是否符合模板，使用工具类
        log.info("1. 开始Excel校验流程");
        ExcelTemplateValidator validator = new ExcelTemplateValidator();
        try (InputStream inputStream = file.getInputStream()) {
            log.info("2. 创建输入流成功");
            // 使用EasyExcel进行读取和校验，但不进行实际的数据处理
            // 检查文件是否可读且有内容
            if (inputStream.available() == 0) {
                log.error("文件内容为空");
                throw new BizException(ResponseCodeEnum.FILE_EMPTY_ERROR);
            }

            log.info("3. 开始EasyExcel读取");
            EasyExcel.read(inputStream,validator)
                    .sheet()
                    .headRowNumber(0) // 从第0行开始读取（标题行）
                    .doRead();
            log.info("4. EasyExcel读取完成，错误数: {}", validator.getErrorMessages().size());
            // 检查校验结果
            if (!validator.isValid()) {
                List<String> errors = validator.getErrorMessages();
                String errorMessages = String.join("\n", errors);
                log.error("Excel文件内容校验失败: {}", errorMessages);

                // 生成预上传ID（仅校验失败场景使用；校验成功不会生成该ID）
                Long preUploadId = Long.valueOf(distributedIdGeneratorRpcService.getPreFileId());

                // 创建预上传记录并保存错误信息
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

                // 构建返回结果（错误场景）
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
        } catch (IOException e) {
            log.error("Excel文件读取失败", e);
            throw new BizException(ResponseCodeEnum.FILE_READ_ERROR);
        }



        //4.校验成功，调用oss服务上传文件，返回url
        String ossUrl = ossRpcService.uploadFile(file);
        if (ossUrl == null) {
            log.error("==> 文件上传OSS失败");
            throw new BizException(ResponseCodeEnum.FILE_UPLOAD_ERROR);
        }
        //5.保存文件信息到数据库
        Long userId = LoginUserContextHolder.getUserId();
        log.info("==> 用户ID: {},准备保存文件", userId);
        //6.调用分布式id生成器生成文件ID
        Long fileId = Long.valueOf(distributedIdGeneratorRpcService.getFileId());
        FileInfoDO fileInfoDO = FileInfoDO.builder()
                .id(fileId)
                .userId(userId)
                .fileName(originalFilename)
                .fileSize(file.getSize())
                .ossUrl(ossUrl)
                .uploadTime(LocalDateTime.now())
                .status(FILE_STATUS_UPLOADED) // 文件已上传状态
                //默认未删除,就不写了吧
                .build();
        fileInfoDOMapper.insert(fileInfoDO);
        log.info("==> 文件信息保存数据库成功，文件ID: {}", fileInfoDO.getId());
        //构建返回结果
        // 6. 构建并返回VO对象（不包含OSS链接）
        ExcelFileUploadVO resultVO = ExcelFileUploadVO.builder()
                .fileId(fileInfoDO.getId())
                .fileName(fileInfoDO.getFileName())
                .fileSize(fileInfoDO.getFileSize())
                .uploadTime(fileInfoDO.getUploadTime())
                .status(fileInfoDO.getStatus())
                .formattedSize(formatFileSize(fileInfoDO.getFileSize())) // 格式化文件大小
                .build();
        log.info("==> 返回结果: {}", resultVO);
        //6.返回结果
        return Response.success(resultVO);

    }

    /**
     * 获取完整文件错误信息
     * @param preUploadId
     * @return
     */

    @Override
    public Response<?> getValidationErrors(Long preUploadId) {

        if (preUploadId == null) {
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        // 查询预上传记录
        ExcelPreUploadRecordDO record = excelPreUploadRecordDOMapper.selectById(preUploadId);
        if (record == null) {
            return Response.fail(ResponseCodeEnum.RECORD_NOT_FOUND);
        }

        // 验证权限（确保只有记录所有者可以查看）
        Long currentUserId = LoginUserContextHolder.getUserId();
        if (!record.getUserId().equals(currentUserId)) {
            return Response.fail(ResponseCodeEnum.NO_PERMISSION);
        }

        // 提取详细错误信息并返回
        List<String> errorList = Arrays.asList(record.getErrorMessages().split("\\n"));
        return Response.success(errorList);

    }

    @Override
    public Response<?> parseExcelFileById(Long fileId) {
        FileInfoDO fileInfo = loadOwnedFile(fileId);
        Long currentUserId = LoginUserContextHolder.getUserId();
        if (!tryMarkParsing(fileId, currentUserId)) {
            log.warn("==> 文件状态不允许开始解析或已被其他请求抢占，fileId: {}, userId: {}", fileId, currentUserId);
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "文件状态已变化，无法重复解析");
        }

        String downloadUrl = requireFileDownloadUrl(fileInfo.getOssUrl());
        log.info("==> 文件下载链接: {}", downloadUrl);

        long startTime = System.currentTimeMillis();
        
        try {
            try (DownloadedExcelResource resource = downloadExcelFile(downloadUrl)) {
                List<QuestionDataDTO> questions = parseQuestionData(resource.getInputStream());
                BatchImportQuestionRequestDTO importRequest = buildImportRequest(questions);
                BatchImportQuestionResponseDTO importResult = importQuestions(importRequest);

                markFileStatus(fileId, importResult.isSuccess() ? FILE_STATUS_PARSED : FILE_STATUS_FAILED);

                return Response.success(buildExcelProcessResult(fileId, questions.size(), importResult, startTime));
            }

        } catch (BizException e) {
            markFileStatusQuietly(fileId, FILE_STATUS_FAILED);
            log.warn("==> 解析Excel文件业务失败，fileId: {}, errorCode: {}, message: {}",
                    fileId, e.getErrorCode(), e.getErrorMessage());
            return Response.fail(e);
        } catch (IOException e) {
            markFileStatusQuietly(fileId, FILE_STATUS_FAILED);
            log.error("==> Excel解析失败或文件下载错误", e);
            throw new BizException(ResponseCodeEnum.FILE_READ_ERROR);
        } catch (Exception e) {
            markFileStatusQuietly(fileId, FILE_STATUS_FAILED);
            log.error("==> 处理Excel文件时发生未知错误", e);
            throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
        }
    }

    // 先保留“文件不存在/无权限”的明确错误语义，再进入原子状态抢占。
    private FileInfoDO loadOwnedFile(Long fileId) {
        FileInfoDO fileInfo = fileInfoDOMapper.selectByPrimaryKey(fileId);
        if (fileInfo == null) {
            log.error("==> 文件不存在，fileId: {}", fileId);
            throw new BizException(ResponseCodeEnum.RECORD_NOT_FOUND);
        }

        Long currentUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(fileInfo.getUserId(), currentUserId)) {
            log.warn("==> 无权限解析文件，fileId: {}, ownerUserId: {}, currentUserId: {}",
                    fileId, fileInfo.getUserId(), currentUserId);
            throw new BizException(ResponseCodeEnum.NO_PERMISSION);
        }
        return fileInfo;
    }

    // 只有真正抢到解析资格的请求，才值得继续向 OSS 申请下载链接。
    private String requireFileDownloadUrl(String ossUrl) {
        log.info("==> 获取文件下载链接: {}", ossUrl);
        String downloadUrl = ossRpcService.getShortUrl(ossUrl);
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new BizException(ResponseCodeEnum.FILE_READ_ERROR);
        }
        return downloadUrl;
    }

    private DownloadedExcelResource downloadExcelFile(String downloadUrl) throws IOException {
        return PresignedUrlDownloader.downloadWithResponse(downloadUrl);
    }

    private List<QuestionDataDTO> parseQuestionData(InputStream stream) {
        List<QuestionDataDTO> questions = ExcelParserUtil.parseExcel(stream);
        log.info("成功解析Excel文件，共{}道题目", questions.size());
        return questions;
    }

    private BatchImportQuestionRequestDTO buildImportRequest(List<QuestionDataDTO> questions) {
        List<QuestionDTO> questionDTOList = new ArrayList<>();
        for (QuestionDataDTO questionData : questions) {
            QuestionDTO questionDTO = new QuestionDTO();
            questionDTO.setContent(questionData.getQuestionContent());
            questionDTO.setAnswer(questionData.getAnswer());
            questionDTO.setAnalysis(questionData.getExplanation());
            questionDTOList.add(questionDTO);
        }

        BatchImportQuestionRequestDTO importRequest = new BatchImportQuestionRequestDTO();
        importRequest.setQuestions(questionDTOList);
        log.info("==> 批量导入请求: {}", importRequest);
        return importRequest;
    }

    private BatchImportQuestionResponseDTO importQuestions(BatchImportQuestionRequestDTO importRequest) {
        return questionBankRpcService.batchImportQuestions(importRequest);
    }

    private ExcelProcessVO buildExcelProcessResult(Long fileId, int totalCount,
                                                   BatchImportQuestionResponseDTO importResult,
                                                   long startTime) {
        ExcelProcessVO excelProcessVO = new ExcelProcessVO();
        excelProcessVO.setFileId(String.valueOf(fileId));
        excelProcessVO.setTotalCount(totalCount);
        excelProcessVO.setFinishTime(System.currentTimeMillis());
        excelProcessVO.setProcessTimeMs(System.currentTimeMillis() - startTime);

        if (importResult.isSuccess()) {
            excelProcessVO.setProcessStatus(ProcessStatusEnum.SUCCESS.getValue());
            excelProcessVO.setSuccessCount(importResult.getSuccessCount());
            excelProcessVO.setFailCount(importResult.getFailedCount());
            return excelProcessVO;
        }

        excelProcessVO.setProcessStatus(ProcessStatusEnum.FAILED.getValue());
        excelProcessVO.setSuccessCount(importResult.getSuccessCount());
        excelProcessVO.setFailCount(importResult.getFailedCount());
        excelProcessVO.setErrorMessage(importResult.getErrorMessage());
        log.info("==> 批量导入失败，错误信息: {}, 错误类型{}",
                importResult.getErrorMessage(),
                importResult.getErrorType());
        return excelProcessVO;
    }

    // 普通状态写回：适用于 PARSED / FAILED 这类已进入主流程后的结果落库。
    private void markFileStatus(Long fileId, String status) {
        FileInfoDO updateDO = new FileInfoDO();
        updateDO.setId(fileId);
        updateDO.setStatus(status);
        fileInfoDOMapper.updateByPrimaryKeySelective(updateDO);
    }

    // 收尾动作不应覆盖主异常，所以这里只记录日志，不再抛出第二个异常。
    private void markFileStatusQuietly(Long fileId, String status) {
        try {
            markFileStatus(fileId, status);
        } catch (Exception ex) {
            log.error("==> 更新文件状态失败，fileId: {}, status: {}", fileId, status, ex);
        }
    }

    // 把“当前用户 + 允许的旧状态 + 改成 PARSING”压成一次条件更新，避免重复解析。
    private boolean tryMarkParsing(Long fileId, Long userId) {
        return fileInfoDOMapper.tryMarkParsing(fileId, userId) > 0;
    }

    /**
     * 格式化文件大小，将字节数转换为人类可读的格式（如：1.5MB）
     */
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";

        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

        // 限制最大单位为TB，并进行格式化
        digitGroups = Math.min(digitGroups, units.length - 1);
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
    /**
     * 获取文件魔术头
     * @param file 上传的文件
     * @return 魔术头十六进制字符串
     * @throws IOException IO异常
     */
    private String getFileMagicNumber(org.springframework.web.multipart.MultipartFile file) throws IOException {
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
