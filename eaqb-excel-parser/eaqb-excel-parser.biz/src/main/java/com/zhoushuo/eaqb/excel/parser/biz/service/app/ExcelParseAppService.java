package com.zhoushuo.eaqb.excel.parser.biz.service.app;

import com.zhoushuo.eaqb.excel.parser.biz.config.EasyExcelConfig;
import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.FileInfoDO;
import com.zhoushuo.eaqb.excel.parser.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.excel.parser.biz.model.dto.QuestionDataDTO;
import com.zhoushuo.eaqb.excel.parser.biz.model.vo.ExcelProcessVO;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.OssRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.QuestionBankRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.service.support.ExcelFileRecordSupport;
import com.zhoushuo.eaqb.excel.parser.biz.util.DownloadedExcelResource;
import com.zhoushuo.eaqb.excel.parser.biz.util.ExcelParserUtil;
import com.zhoushuo.eaqb.excel.parser.biz.util.PresignedUrlDownloader;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.QuestionDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.eumns.ProcessStatusEnum;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 正式解析导入应用服务。
 */
@Slf4j
@Service
public class ExcelParseAppService {

    private static final String FILE_SERVICE_RETRY_MESSAGE = "文件服务暂时不可用，请稍后重试";

    @Resource
    private OssRpcService ossRpcService;
    @Resource
    private QuestionBankRpcService questionBankRpcService;
    @Resource
    private EasyExcelConfig easyExcelConfig;
    @Resource
    private ExcelFileRecordSupport excelFileRecordSupport;

    public Response<?> parseExcelFileById(Long fileId) {
        Long currentUserId = LoginUserContextHolder.getUserId();
        FileInfoDO fileInfo = excelFileRecordSupport.loadOwnedFile(fileId, currentUserId);
        if (!excelFileRecordSupport.tryMarkParsing(fileId, currentUserId)) {
            log.warn("==> 文件状态不允许开始解析或已被其他请求抢占，fileId: {}, userId: {}", fileId, currentUserId);
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "文件状态已变化，无法重复解析");
        }

        long startTime = System.currentTimeMillis();
        try {
            String downloadUrl = requireFileDownloadUrl(fileInfo.getObjectKey());
            try (DownloadedExcelResource resource = downloadExcelFile(downloadUrl)) {
                List<QuestionDataDTO> questions = parseQuestionData(resource.getInputStream());
                BatchImportQuestionRequestDTO importRequest = buildImportRequest(questions);
                BatchImportQuestionResponseDTO importResult = importQuestions(importRequest);

                excelFileRecordSupport.markFileStatus(fileId, importResult.isSuccess()
                        ? ExcelFileRecordSupport.FILE_STATUS_PARSED
                        : ExcelFileRecordSupport.FILE_STATUS_FAILED);
                return Response.success(buildExcelProcessResult(fileId, questions.size(), importResult, startTime));
            }
        } catch (BizException e) {
            excelFileRecordSupport.markFileStatusQuietly(fileId, ExcelFileRecordSupport.FILE_STATUS_FAILED);
            log.warn("==> 解析Excel文件业务失败，fileId: {}, errorCode: {}, message: {}",
                    fileId, e.getErrorCode(), e.getErrorMessage());
            throw e;
        } catch (IOException e) {
            excelFileRecordSupport.markFileStatusQuietly(fileId, ExcelFileRecordSupport.FILE_STATUS_FAILED);
            log.error("==> Excel解析失败或文件下载错误", e);
            throw new BizException(ResponseCodeEnum.FILE_READ_ERROR);
        } catch (Exception e) {
            excelFileRecordSupport.markFileStatusQuietly(fileId, ExcelFileRecordSupport.FILE_STATUS_FAILED);
            log.error("==> 处理Excel文件时发生未知错误", e);
            throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
        }
    }

    private String requireFileDownloadUrl(String objectKey) {
        try {
            log.info("==> 获取文件下载访问凭证, objectKey: {}", objectKey);
            String downloadUrl = ossRpcService.getExcelDownloadUrl(objectKey);
            if (downloadUrl == null || downloadUrl.isBlank()) {
                throw new BizException(ResponseCodeEnum.FILE_READ_ERROR.getErrorCode(), FILE_SERVICE_RETRY_MESSAGE);
            }
            // TODO: 联调结束后删除，避免日志暴露预签名 URL。
            log.info("==> 文件下载链接: {}", downloadUrl);
            return downloadUrl;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("==> 获取文件下载链接失败", e);
            throw new BizException(ResponseCodeEnum.FILE_READ_ERROR.getErrorCode(), FILE_SERVICE_RETRY_MESSAGE);
        }
    }

    private DownloadedExcelResource downloadExcelFile(String downloadUrl) throws IOException {
        return PresignedUrlDownloader.downloadWithResponse(downloadUrl);
    }

    /**
     * 正式解析链路：模板已校验过，这里只需要正式数据，
     * 因此显式声明 1 行表头并跳过它。
     */
    private List<QuestionDataDTO> parseQuestionData(InputStream stream) {
        List<QuestionDataDTO> questions = ExcelParserUtil.parseExcel(stream, easyExcelConfig.getHeadRowNumber());
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
}
