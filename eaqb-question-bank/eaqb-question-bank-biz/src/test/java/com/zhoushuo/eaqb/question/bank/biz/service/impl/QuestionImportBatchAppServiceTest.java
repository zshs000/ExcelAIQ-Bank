package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportBatchDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportTempDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.model.QuestionImportFormalIdBinding;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportBatchDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportTempDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
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
import com.zhoushuo.eaqb.question.bank.req.FindImportBatchByFileRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FinishImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.ImportQuestionRowDTO;
import com.zhoushuo.eaqb.question.bank.resp.AppendImportChunkResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CreateImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FindImportBatchByFileResponseDTO;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
        ImportBatchCommitExecutor commitExecutor = new ImportBatchCommitExecutor(questionDOMapper, stateMachine);
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
    void findImportBatchByFile_shouldReturnHighestPriorityRecoverableBatchForCurrentUser() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO committedBatch = buildAppendingBatch();
        committedBatch.setId(8001L);
        committedBatch.setStatus("COMMITTED");
        committedBatch.setTotalRowCount(3);
        committedBatch.setImportedCount(3);
        when(questionImportBatchDOMapper.selectRecoverableByFileIdAndUserId(88L, 1001L))
                .thenReturn(committedBatch);

        FindImportBatchByFileRequestDTO request = new FindImportBatchByFileRequestDTO();
        request.setFileId(88L);

        Response<FindImportBatchByFileResponseDTO> response = questionImportBatchAppService.findImportBatchByFile(request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertTrue(response.getData().isFound());
        assertEquals(8001L, response.getData().getBatchId());
        assertEquals("COMMITTED", response.getData().getStatus());
        assertEquals(3, response.getData().getTotalRowCount());
        assertEquals(3, response.getData().getImportedCount());
    }

    @Test
    void abortAppendingImportBatch_shouldOnlyAbortStillAppendingBatch() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(buildAppendingBatch());
        when(questionImportBatchDOMapper.markAborted(7001L, "APPENDING", "restart file import")).thenReturn(1);

        Response<?> response = questionImportBatchAppService.abortAppendingImportBatch(7001L, "restart file import");

        assertTrue(response.isSuccess());
        verify(questionImportBatchDOMapper).markAborted(7001L, "APPENDING", "restart file import");
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
    void finishImportBatch_shouldTransitAppendingBatchToBindingIdsThenReady() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(4);
        QuestionImportBatchDO lockedBatch = buildAppendingBatch();
        lockedBatch.setStatus("BINDING_IDS");
        lockedBatch.setReceivedChunkCount(2);
        lockedBatch.setTotalRowCount(4);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);
        when(questionImportBatchDOMapper.markBindingIds(7001L, "APPENDING", 2, 4)).thenReturn(1);
        when(questionImportTempDOMapper.selectUnboundIdsByBatchId(7001L, 1000)).thenReturn(List.of(501L, 502L, 503L, 504L), List.of());
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityIds(4)).thenReturn(List.of(9001L, 9002L, 9003L, 9004L));
        when(questionImportTempDOMapper.bindFormalIds(eq(7001L), any())).thenReturn(4);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(questionImportBatchDOMapper.selectByPrimaryKeyForUpdate(7001L)).thenReturn(lockedBatch);
        when(questionImportTempDOMapper.countByBatchId(7001L)).thenReturn(4);
        when(questionImportTempDOMapper.countUnboundFormalId(7001L)).thenReturn(0);
        when(questionImportTempDOMapper.countDistinctFormalId(7001L)).thenReturn(4);
        when(questionImportBatchDOMapper.markReady(7001L, "BINDING_IDS", 2, 4)).thenReturn(1);

        FinishImportBatchRequestDTO request = new FinishImportBatchRequestDTO();
        request.setBatchId(7001L);
        request.setExpectedChunkCount(2);
        request.setExpectedRowCount(4);

        Response<FinishImportBatchResponseDTO> response = questionImportBatchAppService.finishImportBatch(request);

        assertTrue(response.isSuccess());
        assertEquals("READY", response.getData().getStatus());
        assertEquals(2, response.getData().getExpectedChunkCount());
        assertEquals(4, response.getData().getTotalRowCount());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionImportFormalIdBinding>> bindingCaptor = ArgumentCaptor.forClass(List.class);
        verify(questionImportTempDOMapper).bindFormalIds(eq(7001L), bindingCaptor.capture());
        List<QuestionImportFormalIdBinding> bindings = bindingCaptor.getValue();
        assertEquals(4, bindings.size());
        assertEquals(501L, bindings.get(0).getTempId());
        assertEquals(9001L, bindings.get(0).getFormalId());
        verify(questionImportBatchDOMapper).markBindingIds(7001L, "APPENDING", 2, 4);
        verify(questionImportBatchDOMapper).selectByPrimaryKeyForUpdate(7001L);
        verify(questionImportTempDOMapper).countByBatchId(7001L);
        verify(questionImportBatchDOMapper).markReady(7001L, "BINDING_IDS", 2, 4);
    }

    @Test
    void finishImportBatch_unboundFormalIdAfterBinding_shouldMarkBindingIdsFailedAndThrow() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(4);
        QuestionImportBatchDO lockedBatch = buildAppendingBatch();
        lockedBatch.setStatus("BINDING_IDS");
        lockedBatch.setReceivedChunkCount(2);
        lockedBatch.setTotalRowCount(4);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);
        when(questionImportBatchDOMapper.markBindingIds(7001L, "APPENDING", 2, 4)).thenReturn(1);
        when(questionImportTempDOMapper.selectUnboundIdsByBatchId(7001L, 1000)).thenReturn(List.of(501L, 502L, 503L, 504L), List.of());
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityIds(4)).thenReturn(List.of(9001L, 9002L, 9003L, 9004L));
        when(questionImportTempDOMapper.bindFormalIds(eq(7001L), any())).thenReturn(3);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(questionImportBatchDOMapper.selectByPrimaryKeyForUpdate(7001L)).thenReturn(lockedBatch);
        when(questionImportTempDOMapper.countByBatchId(7001L)).thenReturn(4);
        when(questionImportTempDOMapper.countUnboundFormalId(7001L)).thenReturn(1);

        FinishImportBatchRequestDTO request = new FinishImportBatchRequestDTO();
        request.setBatchId(7001L);
        request.setExpectedChunkCount(2);
        request.setExpectedRowCount(4);

        assertThrows(BizException.class, () -> questionImportBatchAppService.finishImportBatch(request));

        verify(questionImportBatchDOMapper).markFailed(eq(7001L), eq("BINDING_IDS"), any());
        verify(questionImportBatchDOMapper, never()).markReady(any(), any(), any(), any());
    }

    @Test
    void finishImportBatch_bindingIdsBatch_shouldResumeBindingOnlyUnboundRows() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setStatus("BINDING_IDS");
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(4);
        QuestionImportBatchDO lockedBatch = buildAppendingBatch();
        lockedBatch.setStatus("BINDING_IDS");
        lockedBatch.setReceivedChunkCount(2);
        lockedBatch.setTotalRowCount(4);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);
        when(questionImportTempDOMapper.selectUnboundIdsByBatchId(7001L, 1000)).thenReturn(List.of(503L, 504L), List.of());
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityIds(2)).thenReturn(List.of(9003L, 9004L));
        when(questionImportTempDOMapper.bindFormalIds(eq(7001L), any())).thenReturn(2);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(questionImportBatchDOMapper.selectByPrimaryKeyForUpdate(7001L)).thenReturn(lockedBatch);
        when(questionImportTempDOMapper.countByBatchId(7001L)).thenReturn(4);
        when(questionImportTempDOMapper.countUnboundFormalId(7001L)).thenReturn(0);
        when(questionImportTempDOMapper.countDistinctFormalId(7001L)).thenReturn(4);
        when(questionImportBatchDOMapper.markReady(7001L, "BINDING_IDS", 2, 4)).thenReturn(1);

        FinishImportBatchRequestDTO request = new FinishImportBatchRequestDTO();
        request.setBatchId(7001L);
        request.setExpectedChunkCount(2);
        request.setExpectedRowCount(4);

        Response<FinishImportBatchResponseDTO> response = questionImportBatchAppService.finishImportBatch(request);

        assertTrue(response.isSuccess());
        assertEquals("READY", response.getData().getStatus());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionImportFormalIdBinding>> bindingCaptor = ArgumentCaptor.forClass(List.class);
        verify(questionImportTempDOMapper).bindFormalIds(eq(7001L), bindingCaptor.capture());
        List<QuestionImportFormalIdBinding> bindings = bindingCaptor.getValue();
        assertEquals(2, bindings.size());
        assertEquals(503L, bindings.get(0).getTempId());
        assertEquals(9003L, bindings.get(0).getFormalId());
        verify(questionImportBatchDOMapper, never()).markBindingIds(any(), any(), any(), any());
        verify(questionImportBatchDOMapper).markReady(7001L, "BINDING_IDS", 2, 4);
    }

    @Test
    void finishImportBatch_bindingIdsBatchWithMismatchedExpectedCount_shouldThrowWithoutGeneratingIds() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setStatus("BINDING_IDS");
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(4);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);

        FinishImportBatchRequestDTO request = new FinishImportBatchRequestDTO();
        request.setBatchId(7001L);
        request.setExpectedChunkCount(2);
        request.setExpectedRowCount(5);

        assertThrows(BizException.class, () -> questionImportBatchAppService.finishImportBatch(request));

        verify(distributedIdGeneratorRpcService, never()).nextQuestionBankEntityIds(anyInt());
        verify(questionImportTempDOMapper, never()).bindFormalIds(any(), any());
        verify(questionImportBatchDOMapper, never()).markReady(any(), any(), any(), any());
    }

    @Test
    void finishImportBatch_readyBatch_shouldReturnIdempotentSuccessWithoutBinding() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setStatus("READY");
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(4);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);

        FinishImportBatchRequestDTO request = new FinishImportBatchRequestDTO();
        request.setBatchId(7001L);
        request.setExpectedChunkCount(2);
        request.setExpectedRowCount(4);

        Response<FinishImportBatchResponseDTO> response = questionImportBatchAppService.finishImportBatch(request);

        assertTrue(response.isSuccess());
        assertEquals("READY", response.getData().getStatus());
        assertEquals(2, response.getData().getExpectedChunkCount());
        assertEquals(4, response.getData().getTotalRowCount());
        verify(questionImportTempDOMapper, never()).selectUnboundIdsByBatchId(any(), anyInt());
        verify(distributedIdGeneratorRpcService, never()).nextQuestionBankEntityIds(anyInt());
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void finishImportBatch_appendingMarkBindingIdsLostRace_shouldResumeBindingIdsState() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO appendingBatch = buildAppendingBatch();
        appendingBatch.setReceivedChunkCount(2);
        appendingBatch.setTotalRowCount(4);
        QuestionImportBatchDO bindingIdsBatch = buildAppendingBatch();
        bindingIdsBatch.setStatus("BINDING_IDS");
        bindingIdsBatch.setReceivedChunkCount(2);
        bindingIdsBatch.setTotalRowCount(4);
        QuestionImportBatchDO lockedBatch = buildAppendingBatch();
        lockedBatch.setStatus("BINDING_IDS");
        lockedBatch.setReceivedChunkCount(2);
        lockedBatch.setTotalRowCount(4);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(appendingBatch, bindingIdsBatch);
        when(questionImportBatchDOMapper.markBindingIds(7001L, "APPENDING", 2, 4)).thenReturn(0);
        when(questionImportTempDOMapper.selectUnboundIdsByBatchId(7001L, 1000)).thenReturn(List.of());
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(questionImportBatchDOMapper.selectByPrimaryKeyForUpdate(7001L)).thenReturn(lockedBatch);
        when(questionImportTempDOMapper.countByBatchId(7001L)).thenReturn(4);
        when(questionImportTempDOMapper.countUnboundFormalId(7001L)).thenReturn(0);
        when(questionImportTempDOMapper.countDistinctFormalId(7001L)).thenReturn(4);
        when(questionImportBatchDOMapper.markReady(7001L, "BINDING_IDS", 2, 4)).thenReturn(1);

        FinishImportBatchRequestDTO request = new FinishImportBatchRequestDTO();
        request.setBatchId(7001L);
        request.setExpectedChunkCount(2);
        request.setExpectedRowCount(4);

        Response<FinishImportBatchResponseDTO> response = questionImportBatchAppService.finishImportBatch(request);

        assertTrue(response.isSuccess());
        assertEquals("READY", response.getData().getStatus());
        verify(questionImportBatchDOMapper).markBindingIds(7001L, "APPENDING", 2, 4);
        verify(questionImportBatchDOMapper).markReady(7001L, "BINDING_IDS", 2, 4);
    }

    @Test
    void finishImportBatch_concurrentRetryUpdatedLessThanSelected_shouldContinueToFinalVerification() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setStatus("BINDING_IDS");
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(4);
        QuestionImportBatchDO lockedBatch = buildAppendingBatch();
        lockedBatch.setStatus("BINDING_IDS");
        lockedBatch.setReceivedChunkCount(2);
        lockedBatch.setTotalRowCount(4);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);
        when(questionImportTempDOMapper.selectUnboundIdsByBatchId(7001L, 1000)).thenReturn(List.of(503L, 504L), List.of());
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityIds(2)).thenReturn(List.of(9003L, 9004L));
        when(questionImportTempDOMapper.bindFormalIds(eq(7001L), any())).thenReturn(0);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(questionImportBatchDOMapper.selectByPrimaryKeyForUpdate(7001L)).thenReturn(lockedBatch);
        when(questionImportTempDOMapper.countByBatchId(7001L)).thenReturn(4);
        when(questionImportTempDOMapper.countUnboundFormalId(7001L)).thenReturn(0);
        when(questionImportTempDOMapper.countDistinctFormalId(7001L)).thenReturn(4);
        when(questionImportBatchDOMapper.markReady(7001L, "BINDING_IDS", 2, 4)).thenReturn(1);

        FinishImportBatchRequestDTO request = new FinishImportBatchRequestDTO();
        request.setBatchId(7001L);
        request.setExpectedChunkCount(2);
        request.setExpectedRowCount(4);

        Response<FinishImportBatchResponseDTO> response = questionImportBatchAppService.finishImportBatch(request);

        assertTrue(response.isSuccess());
        verify(questionImportBatchDOMapper, never()).markFailed(any(), any(), any());
        verify(questionImportBatchDOMapper).markReady(7001L, "BINDING_IDS", 2, 4);
    }

    @Test
    void finishImportBatch_formalIdGenerateFailed_shouldKeepBindingIdsRecoverableAndThrow() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(4);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);
        when(questionImportBatchDOMapper.markBindingIds(7001L, "APPENDING", 2, 4)).thenReturn(1);
        when(questionImportTempDOMapper.selectUnboundIdsByBatchId(7001L, 1000)).thenReturn(List.of(501L, 502L, 503L, 504L));
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityIds(4))
                .thenThrow(new BizException(ResponseCodeEnum.ID_GENERATE_FAILED));

        FinishImportBatchRequestDTO request = new FinishImportBatchRequestDTO();
        request.setBatchId(7001L);
        request.setExpectedChunkCount(2);
        request.setExpectedRowCount(4);

        assertThrows(BizException.class, () -> questionImportBatchAppService.finishImportBatch(request));

        verify(questionImportBatchDOMapper, never()).markFailed(any(), any(), any());
        verify(questionImportBatchDOMapper, never()).markReady(any(), any(), any(), any());
    }

    @Test
    void finishImportBatch_tempRowCountMismatch_shouldMarkBindingIdsFailedAndNotReady() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setStatus("BINDING_IDS");
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(4);
        QuestionImportBatchDO lockedBatch = buildAppendingBatch();
        lockedBatch.setStatus("BINDING_IDS");
        lockedBatch.setReceivedChunkCount(2);
        lockedBatch.setTotalRowCount(4);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);
        when(questionImportTempDOMapper.selectUnboundIdsByBatchId(7001L, 1000)).thenReturn(List.of());
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(questionImportBatchDOMapper.selectByPrimaryKeyForUpdate(7001L)).thenReturn(lockedBatch);
        when(questionImportTempDOMapper.countByBatchId(7001L)).thenReturn(3);

        FinishImportBatchRequestDTO request = new FinishImportBatchRequestDTO();
        request.setBatchId(7001L);
        request.setExpectedChunkCount(2);
        request.setExpectedRowCount(4);

        assertThrows(BizException.class, () -> questionImportBatchAppService.finishImportBatch(request));

        verify(questionImportBatchDOMapper).markFailed(eq(7001L), eq("BINDING_IDS"), any());
        verify(questionImportBatchDOMapper, never()).markReady(any(), any(), any(), any());
    }

    @Test
    void finishImportBatch_duplicateFormalId_shouldMarkBindingIdsFailedAndNotReady() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setStatus("BINDING_IDS");
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(4);
        QuestionImportBatchDO lockedBatch = buildAppendingBatch();
        lockedBatch.setStatus("BINDING_IDS");
        lockedBatch.setReceivedChunkCount(2);
        lockedBatch.setTotalRowCount(4);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);
        when(questionImportTempDOMapper.selectUnboundIdsByBatchId(7001L, 1000)).thenReturn(List.of());
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(questionImportBatchDOMapper.selectByPrimaryKeyForUpdate(7001L)).thenReturn(lockedBatch);
        when(questionImportTempDOMapper.countByBatchId(7001L)).thenReturn(4);
        when(questionImportTempDOMapper.countUnboundFormalId(7001L)).thenReturn(0);
        when(questionImportTempDOMapper.countDistinctFormalId(7001L)).thenReturn(3);

        FinishImportBatchRequestDTO request = new FinishImportBatchRequestDTO();
        request.setBatchId(7001L);
        request.setExpectedChunkCount(2);
        request.setExpectedRowCount(4);

        assertThrows(BizException.class, () -> questionImportBatchAppService.finishImportBatch(request));

        verify(questionImportBatchDOMapper).markFailed(eq(7001L), eq("BINDING_IDS"), any());
        verify(questionImportBatchDOMapper, never()).markReady(any(), any(), any(), any());
    }

    @Test
    void commitImportBatch_shouldInsertFormalQuestionsBySelectAndMarkCommitted() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setStatus("READY");
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(2);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);
        when(questionImportBatchDOMapper.selectByPrimaryKeyForUpdate(7001L)).thenReturn(batch);
        when(questionImportTempDOMapper.countByBatchId(7001L)).thenReturn(2);
        when(questionImportTempDOMapper.countUnboundFormalId(7001L)).thenReturn(0);
        when(questionImportTempDOMapper.countDistinctFormalId(7001L)).thenReturn(2);
        when(questionDOMapper.insertFromImportTemp(7001L, 1001L)).thenReturn(2);
        when(questionImportBatchDOMapper.markCommitted(7001L, "READY", 2)).thenReturn(1);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));

        CommitImportBatchRequestDTO request = new CommitImportBatchRequestDTO();
        request.setBatchId(7001L);

        Response<CommitImportBatchResponseDTO> response = questionImportBatchAppService.commitImportBatch(request);

        assertTrue(response.isSuccess());
        assertEquals("COMMITTED", response.getData().getStatus());
        assertEquals(2, response.getData().getImportedCount());

        verify(questionDOMapper).insertFromImportTemp(7001L, 1001L);
        verify(questionDOMapper, never()).batchInsert(any());
        verify(distributedIdGeneratorRpcService, never()).nextQuestionBankEntityIds(anyInt());
        verify(distributedIdGeneratorRpcService, never()).nextQuestionBankEntityId();
    }

    @Test
    void commitImportBatch_committedBatch_shouldReturnIdempotentSuccessWithoutInsert() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setStatus("COMMITTED");
        batch.setTotalRowCount(2);
        batch.setImportedCount(2);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);

        CommitImportBatchRequestDTO request = new CommitImportBatchRequestDTO();
        request.setBatchId(7001L);

        Response<CommitImportBatchResponseDTO> response = questionImportBatchAppService.commitImportBatch(request);

        assertTrue(response.isSuccess());
        assertEquals("COMMITTED", response.getData().getStatus());
        assertEquals(2, response.getData().getImportedCount());
        verify(questionImportTempDOMapper, never()).countByBatchId(any());
        verify(questionDOMapper, never()).insertFromImportTemp(any(), any());
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void commitImportBatch_readyBatchBecomesCommittedInTransaction_shouldReturnIdempotentSuccessWithoutInsert() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO readySnapshot = buildAppendingBatch();
        readySnapshot.setStatus("READY");
        readySnapshot.setTotalRowCount(2);
        QuestionImportBatchDO lockedCommittedBatch = buildAppendingBatch();
        lockedCommittedBatch.setStatus("COMMITTED");
        lockedCommittedBatch.setTotalRowCount(2);
        lockedCommittedBatch.setImportedCount(2);

        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(readySnapshot);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(questionImportBatchDOMapper.selectByPrimaryKeyForUpdate(7001L)).thenReturn(lockedCommittedBatch);

        CommitImportBatchRequestDTO request = new CommitImportBatchRequestDTO();
        request.setBatchId(7001L);

        Response<CommitImportBatchResponseDTO> response = questionImportBatchAppService.commitImportBatch(request);

        assertTrue(response.isSuccess());
        assertEquals("COMMITTED", response.getData().getStatus());
        assertEquals(2, response.getData().getImportedCount());
        verify(questionImportBatchDOMapper).selectByPrimaryKeyForUpdate(7001L);
        verify(questionImportTempDOMapper, never()).countByBatchId(any());
        verify(questionDOMapper, never()).insertFromImportTemp(any(), any());
    }

    @Test
    void commitImportBatch_formalIdNotFullyBound_shouldMarkFailedAndThrow() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setStatus("READY");
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(2);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);
        when(questionImportBatchDOMapper.selectByPrimaryKeyForUpdate(7001L)).thenReturn(batch);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(questionImportTempDOMapper.countByBatchId(7001L)).thenReturn(2);
        when(questionImportTempDOMapper.countUnboundFormalId(7001L)).thenReturn(1);

        CommitImportBatchRequestDTO request = new CommitImportBatchRequestDTO();
        request.setBatchId(7001L);

        assertThrows(BizException.class, () -> questionImportBatchAppService.commitImportBatch(request));

        verify(questionImportBatchDOMapper).markFailed(eq(7001L), eq("READY"), any());
        verify(questionDOMapper, never()).insertFromImportTemp(any(), any());
    }

    @Test
    void commitImportBatch_duplicateFormalId_shouldMarkFailedAndThrow() {
        LoginUserContextHolder.setUserId(1001L);
        ReflectionTestUtils.setField(questionImportBatchAppService, "questionAccessSupport", questionAccessSupport);
        QuestionImportBatchDO batch = buildAppendingBatch();
        batch.setStatus("READY");
        batch.setReceivedChunkCount(2);
        batch.setTotalRowCount(2);
        when(questionImportBatchDOMapper.selectByPrimaryKey(7001L)).thenReturn(batch);
        when(questionImportBatchDOMapper.selectByPrimaryKeyForUpdate(7001L)).thenReturn(batch);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(questionImportTempDOMapper.countByBatchId(7001L)).thenReturn(2);
        when(questionImportTempDOMapper.countUnboundFormalId(7001L)).thenReturn(0);
        when(questionImportTempDOMapper.countDistinctFormalId(7001L)).thenReturn(1);

        CommitImportBatchRequestDTO request = new CommitImportBatchRequestDTO();
        request.setBatchId(7001L);

        assertThrows(BizException.class, () -> questionImportBatchAppService.commitImportBatch(request));

        verify(questionImportBatchDOMapper).markFailed(eq(7001L), eq("READY"), any());
        verify(questionDOMapper, never()).insertFromImportTemp(any(), any());
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
