package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportBatchDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportTempDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportBatchDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportTempDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportBatchAssembler;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportBatchCommitExecutor;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportBatchStateMachine;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportChunkDecisionService;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportChunkHashValidator;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportChunkRequestValidator;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportWorkflowFacade;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CommitImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CreateImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FinishImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.ImportQuestionRowDTO;
import com.zhoushuo.eaqb.question.bank.resp.AppendImportChunkResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CreateImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FinishImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.util.ImportChunkHashUtil;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionImportBatchAppServiceTest {

    @Mock
    private QuestionImportBatchDOMapper questionImportBatchDOMapper;
    @Mock
    private QuestionImportTempDOMapper questionImportTempDOMapper;
    @Mock
    private QuestionDOMapper questionDOMapper;
    @Mock
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Mock
    private QuestionImportBatchStatusWriter questionImportBatchStatusWriter;
    @Mock
    private TransactionTemplate transactionTemplate;

    private final QuestionAccessSupport questionAccessSupport = new QuestionAccessSupport();

    @InjectMocks
    private QuestionImportBatchAppService questionImportBatchAppService;

    @BeforeEach
    void setUp() {
        ImportBatchAssembler assembler = new ImportBatchAssembler();
        ImportBatchStateMachine stateMachine = new ImportBatchStateMachine(questionImportBatchDOMapper, questionImportBatchStatusWriter);
        ImportBatchCommitExecutor commitExecutor = new ImportBatchCommitExecutor(questionDOMapper, stateMachine, assembler);
        ImportWorkflowFacade importWorkflowFacade = new ImportWorkflowFacade(
                new ImportChunkRequestValidator(),
                new ImportChunkHashValidator(),
                new ImportChunkDecisionService(),
                assembler,
                stateMachine,
                commitExecutor
        );

        ReflectionTestUtils.setField(questionImportBatchAppService, "importWorkflowFacade", importWorkflowFacade);
    }

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void createImportBatch_shouldPersistAppendingBatch() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityId()).thenReturn("6001");
        when(questionImportBatchDOMapper.insertSelective(any(QuestionImportBatchDO.class))).thenReturn(1);

        CreateImportBatchRequestDTO request = new CreateImportBatchRequestDTO();
        request.setFileId(88L);
        request.setChunkSize(500);

        Response<CreateImportBatchResponseDTO> response = questionImportBatchAppService.createImportBatch(request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(6001L, response.getData().getBatchId());
        assertEquals("APPENDING", response.getData().getStatus());

        ArgumentCaptor<QuestionImportBatchDO> captor = ArgumentCaptor.forClass(QuestionImportBatchDO.class);
        verify(questionImportBatchDOMapper).insertSelective(captor.capture());
        QuestionImportBatchDO saved = captor.getValue();
        assertEquals(88L, saved.getFileId());
        assertEquals(1001L, saved.getUserId());
        assertEquals(500, saved.getChunkSize());
        assertEquals("APPENDING", saved.getStatus());
        assertEquals(0, saved.getReceivedChunkCount());
        assertEquals(0, saved.getTotalRowCount());
    }

    @Test
    void appendImportChunk_shouldBeTransactional() throws NoSuchMethodException {
        Method method = QuestionImportBatchAppService.class
                .getMethod("appendImportChunk", AppendImportChunkRequestDTO.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(1, transactional.rollbackFor().length);
        assertEquals(Exception.class, transactional.rollbackFor()[0]);
    }

    @Test
    void questionImportBatchStatusWriter_shouldUseRequiresNewTransaction() throws NoSuchMethodException {
        Method method = QuestionImportBatchStatusWriter.class
                .getMethod("markFailed", Long.class, String.class, String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
        assertEquals(1, transactional.rollbackFor().length);
        assertEquals(Exception.class, transactional.rollbackFor()[0]);
    }

    @Test
    void appendImportChunk_firstWrite_shouldInsertTempRowsAndIncreaseCounters() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(buildAppendingBatch());
        when(questionImportTempDOMapper.selectChunkMeta(7001L, 1)).thenReturn(null);
        when(questionImportTempDOMapper.batchInsert(any())).thenReturn(2);
        when(questionImportBatchDOMapper.increaseAfterChunkAccepted(7001L, "APPENDING", 1, 2)).thenReturn(1);

        List<ImportQuestionRowDTO> rows = List.of(
                buildRow("题目A", "答案A", "解析A"),
                buildRow("题目B", "答案B", "解析B")
        );
        String contentHash = computeChunkHash(rows);
        AppendImportChunkRequestDTO request = buildAppendRequest(7001L, 1, rows, contentHash);

        Response<AppendImportChunkResponseDTO> response = questionImportBatchAppService.appendImportChunk(request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertFalse(response.getData().isDuplicateChunk());
        assertEquals(1, response.getData().getReceivedChunkCount());
        assertEquals(2, response.getData().getTotalRowCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionImportTempDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(questionImportTempDOMapper).batchInsert(captor.capture());
        List<QuestionImportTempDO> savedRows = captor.getValue();
        assertEquals(2, savedRows.size());
        assertEquals(1, savedRows.get(0).getRowNo());
        assertEquals(contentHash, savedRows.get(0).getContentHash());
        assertEquals(2, savedRows.get(0).getChunkRowCount());
    }

    @Test
    void appendImportChunk_sameContentRetry_shouldReturnIdempotentSuccess() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setReceivedChunkCount(1);
        batch.setTotalRowCount(2);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);

        List<ImportQuestionRowDTO> rows = List.of(
                buildRow("题目A", "答案A", "解析A"),
                buildRow("题目B", "答案B", "解析B")
        );
        String contentHash = computeChunkHash(rows);
        when(questionImportTempDOMapper.selectChunkMeta(7001L, 1)).thenReturn(
                QuestionImportTempDO.builder()
                        .batchId(7001L)
                        .chunkNo(1)
                        .chunkRowCount(2)
                        .contentHash(contentHash)
                        .build()
        );

        AppendImportChunkRequestDTO request = buildAppendRequest(7001L, 1, rows, contentHash);
        Response<AppendImportChunkResponseDTO> response = questionImportBatchAppService.appendImportChunk(request);

        assertTrue(response.isSuccess());
        assertTrue(response.getData().isDuplicateChunk());
        assertEquals(1, response.getData().getReceivedChunkCount());
        assertEquals(2, response.getData().getTotalRowCount());
        verify(questionImportTempDOMapper, never()).batchInsert(any());
        verify(questionImportBatchDOMapper, never()).increaseAfterChunkAccepted(any(), any(), any(), any());
    }

    @Test
    void appendImportChunk_differentRetryPayload_shouldMarkBatchFailedAndThrow() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(buildAppendingBatch());
        when(questionImportTempDOMapper.selectChunkMeta(7001L, 1)).thenReturn(
                QuestionImportTempDO.builder()
                        .batchId(7001L)
                        .chunkNo(1)
                        .chunkRowCount(2)
                        .contentHash("old-hash")
                        .build()
        );

        List<ImportQuestionRowDTO> rows = List.of(
                buildRow("题目A", "答案A", "解析A"),
                buildRow("题目B", "答案B", "解析B")
        );
        String contentHash = computeChunkHash(rows);
        AppendImportChunkRequestDTO request = buildAppendRequest(7001L, 1, rows, contentHash);

        BizException ex = assertThrows(BizException.class, () -> questionImportBatchAppService.appendImportChunk(request));

        assertTrue(ex.getErrorMessage().contains("chunk"));
        verify(questionImportBatchStatusWriter).markFailed(eq(7001L), eq("APPENDING"), any());
        verify(questionImportBatchDOMapper, never()).markFailed(eq(7001L), eq("APPENDING"), any());
        verify(questionImportTempDOMapper, never()).batchInsert(any());
    }

    @Test
    void appendImportChunk_hashTampered_shouldMarkBatchFailedAndThrow() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(buildAppendingBatch());

        List<ImportQuestionRowDTO> rows = List.of(
                buildRow("题目A", "答案A", "解析A"),
                buildRow("题目B", "答案B", "解析B")
        );
        AppendImportChunkRequestDTO request = buildAppendRequest(7001L, 1, rows, "tampered-hash");

        BizException ex = assertThrows(BizException.class, () -> questionImportBatchAppService.appendImportChunk(request));

        assertTrue(ex.getErrorMessage().contains("hash mismatch"));
        verify(questionImportBatchStatusWriter).markFailed(eq(7001L), eq("APPENDING"), any());
        verify(questionImportTempDOMapper, never()).batchInsert(any());
    }

    @Test
    void finishImportBatch_shouldTransitAppendingBatchToReady() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(4);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);
        when(questionImportBatchDOMapper.markReady(7001L, "APPENDING", 2, 4)).thenReturn(1);

        FinishImportBatchRequestDTO request = new FinishImportBatchRequestDTO();
        request.setBatchId(7001L);
        request.setExpectedChunkCount(2);
        request.setExpectedRowCount(4);

        Response<FinishImportBatchResponseDTO> response = questionImportBatchAppService.finishImportBatch(request);

        assertTrue(response.isSuccess());
        assertEquals("READY", response.getData().getStatus());
        assertEquals(2, response.getData().getExpectedChunkCount());
        assertEquals(4, response.getData().getTotalRowCount());
    }

    @Test
    void commitImportBatch_shouldInsertFormalQuestionsAndMarkCommitted() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setStatus("READY");
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(2);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);
        when(questionImportTempDOMapper.selectByBatchIdOrderByChunkNoAndRowNo(7001L)).thenReturn(List.of(
                QuestionImportTempDO.builder()
                        .batchId(7001L)
                        .chunkNo(1)
                        .rowNo(1)
                        .content("题目A")
                        .answer("答案A")
                        .analysis("解析A")
                        .build(),
                QuestionImportTempDO.builder()
                        .batchId(7001L)
                        .chunkNo(2)
                        .rowNo(1)
                        .content("题目B")
                        .answer("答案B")
                        .analysis("解析B")
                        .build()
        ));
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityIds(2)).thenReturn(List.of(9001L, 9002L));
        when(questionDOMapper.batchInsert(any())).thenReturn(2);
        when(questionImportBatchDOMapper.markCommitted(7001L, "READY", 2)).thenReturn(1);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));

        CommitImportBatchRequestDTO request = new CommitImportBatchRequestDTO();
        request.setBatchId(7001L);

        Response<CommitImportBatchResponseDTO> response = questionImportBatchAppService.commitImportBatch(request);

        assertTrue(response.isSuccess());
        assertEquals("COMMITTED", response.getData().getStatus());
        assertEquals(2, response.getData().getImportedCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(questionDOMapper).batchInsert(captor.capture());
        List<QuestionDO> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(9001L, saved.get(0).getId());
        assertEquals(9002L, saved.get(1).getId());
        assertEquals("WAITING", saved.get(0).getProcessStatus());
        assertEquals(1001L, saved.get(0).getCreatedBy());
        assertEquals(LocalDateTime.class, saved.get(0).getCreatedTime().getClass());
        verify(distributedIdGeneratorRpcService).nextQuestionBankEntityIds(2);
        verify(distributedIdGeneratorRpcService, never()).nextQuestionBankEntityId();
    }

    private QuestionImportBatchDO buildAppendingBatch() {
        return QuestionImportBatchDO.builder()
                .id(7001L)
                .fileId(88L)
                .userId(1001L)
                .status("APPENDING")
                .chunkSize(500)
                .receivedChunkCount(0)
                .totalRowCount(0)
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();
    }

    private AppendImportChunkRequestDTO buildAppendRequest(Long batchId, int chunkNo,
                                                           List<ImportQuestionRowDTO> rows, String contentHash) {
        AppendImportChunkRequestDTO request = new AppendImportChunkRequestDTO();
        request.setBatchId(batchId);
        request.setChunkNo(chunkNo);
        request.setRowCount(rows.size());
        request.setHashVersion(ImportChunkHashUtil.HASH_VERSION_V2);
        request.setContentHash(contentHash);
        request.setRows(rows);
        return request;
    }

    private String computeChunkHash(List<ImportQuestionRowDTO> rows) {
        return ImportChunkHashUtil.computeHash(ImportChunkHashUtil.HASH_VERSION_V2, rows);
    }

    private ImportQuestionRowDTO buildRow(String content, String answer, String analysis) {
        ImportQuestionRowDTO row = new ImportQuestionRowDTO();
        row.setContent(content);
        row.setAnswer(answer);
        row.setAnalysis(analysis);
        return row;
    }
}
