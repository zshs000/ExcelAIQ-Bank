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
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CommitImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CreateImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FinishImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.ImportQuestionRowDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CreateImportBatchResponseDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.eumns.ProcessStatusEnum;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
            log.warn("文件状态不允许开始解析或已被其他请求抢占, fileId={}, userId={}", fileId, currentUserId);
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "文件状态已变化，无法重复解析");
        }

        long startTime = System.currentTimeMillis();
        try {
            String downloadUrl = requireFileDownloadUrl(fileInfo.getObjectKey());
            try (DownloadedExcelResource resource = downloadExcelFile(downloadUrl)) {
                ImportExecutionSummary summary = importExcelByChunks(fileId, resource.getInputStream());
                excelFileRecordSupport.markFileStatus(fileId, ExcelFileRecordSupport.FILE_STATUS_PARSED);
                return Response.success(buildExcelProcessResult(fileId, summary, startTime));
            }
        } catch (BizException e) {
            excelFileRecordSupport.markFileStatusQuietly(fileId, ExcelFileRecordSupport.FILE_STATUS_FAILED);
            log.warn("解析Excel文件业务失败, fileId={}, errorCode={}, message={}",
                    fileId, e.getErrorCode(), e.getErrorMessage());
            throw e;
        } catch (IOException e) {
            excelFileRecordSupport.markFileStatusQuietly(fileId, ExcelFileRecordSupport.FILE_STATUS_FAILED);
            log.error("Excel解析失败或文件下载错误", e);
            throw new BizException(ResponseCodeEnum.FILE_READ_ERROR);
        } catch (Exception e) {
            excelFileRecordSupport.markFileStatusQuietly(fileId, ExcelFileRecordSupport.FILE_STATUS_FAILED);
            log.error("处理Excel文件时发生未知错误", e);
            throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
        }
    }

    private ImportExecutionSummary importExcelByChunks(Long fileId, InputStream stream) {
        CreateImportBatchResponseDTO batch = questionBankRpcService.createImportBatch(buildCreateBatchRequest(fileId));
        ImportExecutionSummary summary = new ImportExecutionSummary(batch.getBatchId());

        ExcelParserUtil.parseExcelInChunks(stream, easyExcelConfig.getHeadRowNumber(), easyExcelConfig.getBatchSize(), chunk -> {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }
            summary.chunkCount++;
            summary.totalRows += chunk.size();
            questionBankRpcService.appendImportChunk(buildAppendChunkRequest(summary.batchId, summary.chunkCount, chunk));
        });

        if (summary.totalRows <= 0) {
            throw new BizException(ResponseCodeEnum.FILE_EMPTY_ERROR);
        }

        FinishImportBatchRequestDTO finishRequest = new FinishImportBatchRequestDTO();
        finishRequest.setBatchId(summary.batchId);
        finishRequest.setExpectedChunkCount(summary.chunkCount);
        finishRequest.setExpectedRowCount(summary.totalRows);
        questionBankRpcService.finishImportBatch(finishRequest);

        CommitImportBatchRequestDTO commitRequest = new CommitImportBatchRequestDTO();
        commitRequest.setBatchId(summary.batchId);
        CommitImportBatchResponseDTO commitResult = questionBankRpcService.commitImportBatch(commitRequest);
        summary.importedCount = commitResult.getImportedCount();
        return summary;
    }

    private CreateImportBatchRequestDTO buildCreateBatchRequest(Long fileId) {
        CreateImportBatchRequestDTO request = new CreateImportBatchRequestDTO();
        request.setFileId(fileId);
        request.setChunkSize(easyExcelConfig.getBatchSize());
        return request;
    }

    private AppendImportChunkRequestDTO buildAppendChunkRequest(Long batchId, int chunkNo, List<QuestionDataDTO> chunk) {
        AppendImportChunkRequestDTO request = new AppendImportChunkRequestDTO();
        request.setBatchId(batchId);
        request.setChunkNo(chunkNo);
        request.setRowCount(chunk.size());
        request.setContentHash(computeChunkHash(chunk));
        request.setRows(toImportRows(chunk));
        return request;
    }

    private List<ImportQuestionRowDTO> toImportRows(List<QuestionDataDTO> chunk) {
        List<ImportQuestionRowDTO> rows = new ArrayList<>(chunk.size());
        for (QuestionDataDTO question : chunk) {
            ImportQuestionRowDTO row = new ImportQuestionRowDTO();
            row.setContent(question.getQuestionContent());
            row.setAnswer(question.getAnswer());
            row.setAnalysis(question.getExplanation());
            rows.add(row);
        }
        return rows;
    }

    private String computeChunkHash(List<QuestionDataDTO> chunk) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (QuestionDataDTO question : chunk) {
                updateDigest(digest, question.getQuestionContent());
                updateDigest(digest, question.getAnswer());
                updateDigest(digest, question.getExplanation());
            }
            byte[] bytes = digest.digest();
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算分块内容哈希失败", e);
        }
    }

    private void updateDigest(MessageDigest digest, String value) {
        digest.update(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private String requireFileDownloadUrl(String objectKey) {
        try {
            log.info("获取文件下载访问凭证, objectKey={}", objectKey);
            String downloadUrl = ossRpcService.getExcelDownloadUrl(objectKey);
            if (downloadUrl == null || downloadUrl.isBlank()) {
                throw new BizException(ResponseCodeEnum.FILE_READ_ERROR.getErrorCode(), FILE_SERVICE_RETRY_MESSAGE);
            }
            return downloadUrl;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取文件下载链接失败", e);
            throw new BizException(ResponseCodeEnum.FILE_READ_ERROR.getErrorCode(), FILE_SERVICE_RETRY_MESSAGE);
        }
    }

    private DownloadedExcelResource downloadExcelFile(String downloadUrl) throws IOException {
        return PresignedUrlDownloader.downloadWithResponse(downloadUrl);
    }

    private ExcelProcessVO buildExcelProcessResult(Long fileId, ImportExecutionSummary summary, long startTime) {
        ExcelProcessVO excelProcessVO = new ExcelProcessVO();
        excelProcessVO.setFileId(String.valueOf(fileId));
        excelProcessVO.setTotalCount(summary.totalRows);
        excelProcessVO.setFinishTime(System.currentTimeMillis());
        excelProcessVO.setProcessTimeMs(System.currentTimeMillis() - startTime);
        excelProcessVO.setProcessStatus(ProcessStatusEnum.SUCCESS.getValue());
        excelProcessVO.setSuccessCount(summary.importedCount);
        excelProcessVO.setFailCount(Math.max(0, summary.totalRows - summary.importedCount));
        return excelProcessVO;
    }

    private static final class ImportExecutionSummary {
        private final Long batchId;
        private int chunkCount;
        private int totalRows;
        private int importedCount;

        private ImportExecutionSummary(Long batchId) {
            this.batchId = batchId;
        }
    }
}
