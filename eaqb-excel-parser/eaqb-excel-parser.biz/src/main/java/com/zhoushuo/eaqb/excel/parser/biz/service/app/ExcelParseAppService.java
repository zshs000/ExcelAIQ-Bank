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
import com.zhoushuo.eaqb.question.bank.req.FindImportBatchByFileRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FinishImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.ImportQuestionRowDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CreateImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FindImportBatchByFileResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FinishImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.constant.ApiConstants;
import com.zhoushuo.eaqb.question.bank.util.ImportChunkHashUtil;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.enums.ProcessStatusEnum;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ExcelParseAppService {

    private static final String FILE_SERVICE_RETRY_MESSAGE = "文件服务暂时不可用，请稍后重试";
    private static final String STATUS_APPENDING = "APPENDING";
    private static final String STATUS_BINDING_IDS = "BINDING_IDS";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_COMMITTED = "COMMITTED";
    private static final String RESTART_FILE_IMPORT_REASON = "restart file import";
    private static final int MAX_APPENDING_STATUS_RELOAD_ATTEMPTS = 2;

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
            ImportExecutionSummary recovered = recoverExistingBatchIfPossible(fileId);
            if (recovered != null) {
                excelFileRecordSupport.markFileStatus(fileId, ExcelFileRecordSupport.FILE_STATUS_PARSED);
                return Response.success(buildExcelProcessResult(fileId, recovered, startTime));
            }

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

    private ImportExecutionSummary recoverExistingBatchIfPossible(Long fileId) {
        int reloadAttempts = 0;
        while (true) {
            FindImportBatchByFileResponseDTO existingBatch = questionBankRpcService.findImportBatchByFile(buildFindBatchRequest(fileId));
            if (existingBatch == null || !existingBatch.isFound()) {
                return null;
            }
            if (STATUS_COMMITTED.equals(existingBatch.getStatus())) {
                return ImportExecutionSummary.recovered(existingBatch.getBatchId(),
                        safeCount(existingBatch.getTotalRowCount()),
                        safeCount(existingBatch.getImportedCount()));
            }
            if (STATUS_READY.equals(existingBatch.getStatus())) {
                CommitImportBatchResponseDTO commitResult = commitBatch(existingBatch.getBatchId());
                return ImportExecutionSummary.recovered(existingBatch.getBatchId(),
                        safeCount(existingBatch.getTotalRowCount()),
                        safeCount(commitResult.getImportedCount()));
            }
            if (STATUS_BINDING_IDS.equals(existingBatch.getStatus())) {
                FinishImportBatchResponseDTO finishResult = finishBindingIdsBatch(existingBatch);
                CommitImportBatchResponseDTO commitResult = commitBatch(existingBatch.getBatchId());
                return ImportExecutionSummary.recovered(existingBatch.getBatchId(),
                        safeCount(finishResult.getTotalRowCount()),
                        safeCount(commitResult.getImportedCount()));
            }
            if (!STATUS_APPENDING.equals(existingBatch.getStatus())) {
                return null;
            }
            if (abortAppendingBatch(fileId, existingBatch.getBatchId())) {
                return null;
            }
            if (reloadAttempts >= MAX_APPENDING_STATUS_RELOAD_ATTEMPTS) {
                log.warn("废弃APPENDING批次后多次重查仍看到不可废弃状态，停止本次导入恢复, fileId={}, batchId={}, reloadAttempts={}",
                        fileId, existingBatch.getBatchId(), reloadAttempts);
                throw new BizException(ResponseCodeEnum.QUESTION_SERVICE_CALL_FAILED);
            }
            reloadAttempts++;
        }
    }

    private FinishImportBatchResponseDTO finishBindingIdsBatch(FindImportBatchByFileResponseDTO existingBatch) {
        Integer expectedChunkCount = existingBatch.getExpectedChunkCount() != null
                ? existingBatch.getExpectedChunkCount()
                : existingBatch.getReceivedChunkCount();
        Integer expectedRowCount = existingBatch.getTotalRowCount();
        if (expectedChunkCount == null || expectedChunkCount <= 0
                || expectedRowCount == null || expectedRowCount <= 0) {
            log.warn("BINDING_IDS batch lacks finish recovery counts, batchId={}, expectedChunkCount={}, receivedChunkCount={}, totalRowCount={}",
                    existingBatch.getBatchId(),
                    existingBatch.getExpectedChunkCount(),
                    existingBatch.getReceivedChunkCount(),
                    existingBatch.getTotalRowCount());
            throw new BizException(ResponseCodeEnum.QUESTION_SERVICE_CALL_FAILED);
        }
        FinishImportBatchRequestDTO finishRequest = new FinishImportBatchRequestDTO();
        finishRequest.setBatchId(existingBatch.getBatchId());
        finishRequest.setExpectedChunkCount(expectedChunkCount);
        finishRequest.setExpectedRowCount(expectedRowCount);
        return questionBankRpcService.finishImportBatch(finishRequest);
    }

    private boolean abortAppendingBatch(Long fileId, Long batchId) {
        try {
            questionBankRpcService.abortAppendingImportBatch(batchId, RESTART_FILE_IMPORT_REASON);
            return true;
        } catch (BizException e) {
            if (!ApiConstants.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL.equals(e.getErrorCode())) {
                throw e;
            }
            log.info("废弃APPENDING批次时状态已变化，重新查询导入批次, fileId={}, batchId={}", fileId, batchId);
            return false;
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

        CommitImportBatchResponseDTO commitResult = commitBatch(summary.batchId);
        summary.importedCount = commitResult.getImportedCount();
        return summary;
    }

    private CommitImportBatchResponseDTO commitBatch(Long batchId) {
        CommitImportBatchRequestDTO commitRequest = new CommitImportBatchRequestDTO();
        commitRequest.setBatchId(batchId);
        return questionBankRpcService.commitImportBatch(commitRequest);
    }

    private CreateImportBatchRequestDTO buildCreateBatchRequest(Long fileId) {
        CreateImportBatchRequestDTO request = new CreateImportBatchRequestDTO();
        request.setFileId(fileId);
        request.setChunkSize(easyExcelConfig.getBatchSize());
        return request;
    }

    private FindImportBatchByFileRequestDTO buildFindBatchRequest(Long fileId) {
        FindImportBatchByFileRequestDTO request = new FindImportBatchByFileRequestDTO();
        request.setFileId(fileId);
        return request;
    }

    private AppendImportChunkRequestDTO buildAppendChunkRequest(Long batchId, int chunkNo, List<QuestionDataDTO> chunk) {
        AppendImportChunkRequestDTO request = new AppendImportChunkRequestDTO();
        List<ImportQuestionRowDTO> rows = toImportRows(chunk);
        request.setBatchId(batchId);
        request.setChunkNo(chunkNo);
        request.setRowCount(rows.size());
        request.setHashVersion(ImportChunkHashUtil.HASH_VERSION_V2);
        request.setContentHash(ImportChunkHashUtil.computeHash(request.getHashVersion(), rows));
        request.setRows(rows);
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

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
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

        private static ImportExecutionSummary recovered(Long batchId, int totalRows, int importedCount) {
            ImportExecutionSummary summary = new ImportExecutionSummary(batchId);
            summary.totalRows = totalRows;
            summary.importedCount = importedCount;
            return summary;
        }
    }
}
