package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.github.pagehelper.PageInfo;
import com.zhoushuo.eaqb.question.bank.biz.constant.MQConstants;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.biz.model.AIProcessResultMessage;
import com.zhoushuo.eaqb.question.bank.biz.model.QuestionMessage;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.CreateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.QuestionPageQueryDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.ReviewQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.UpdateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.QuestionVO;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.SendToQueueResultVO;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.QuestionDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * QuestionServiceImpl 单服务测试（只测业务层，不依赖真实 DB / MQ / 分布式 ID 服务）。
 *
 * 覆盖目标：
 * 1. 题目增删改查主链路是否按预期返回；
 * 2. 用户权限边界（仅本人可更新/删除）是否生效；
 * 3. 批量导入关键字段（状态、创建人、数量统计）是否正确。
 */
@ExtendWith(MockitoExtension.class)
class QuestionServiceImplTest {

    @Mock
    private QuestionDOMapper questionDOMapper;

    @Mock
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @InjectMocks
    private QuestionServiceImpl questionService;

    @AfterEach
    void tearDown() {
        // 清理线程上下文，避免不同测试之间串用户态。
        LoginUserContextHolder.remove();
    }

    @Test
    void batchImportQuestions_validRequest_shouldInsertAndReturnSuccess() {
        // Given: 当前用户已登录；ID 服务可用；批量入库返回成功条数。
        LoginUserContextHolder.setUserId(1001L);
        when(distributedIdGeneratorRpcService.getQuestionBankId()).thenReturn("2001", "2002");
        when(questionDOMapper.batchInsert(anyList())).thenReturn(2);

        BatchImportQuestionRequestDTO request = new BatchImportQuestionRequestDTO();
        request.setQuestions(Arrays.asList(
                buildQuestionDTO("题目A", "A", "解析A"),
                buildQuestionDTO("题目B", "B", "解析B")
        ));

        // When: 执行批量导入。
        Response<BatchImportQuestionResponseDTO> response = questionService.batchImportQuestions(request);

        // Then: 返回成功，并且导入统计与输入一致。
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(2, response.getData().getTotalCount());
        assertEquals(2, response.getData().getSuccessCount());
        assertEquals(0, response.getData().getFailedCount());

        // 同时断言入库对象的关键业务字段：创建人、初始状态、题目内容。
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(questionDOMapper, times(1)).batchInsert(captor.capture());
        List<QuestionDO> inserted = captor.getValue();
        assertEquals(2, inserted.size());
        assertEquals("WAITING", inserted.get(0).getProcessStatus());
        assertEquals(1001L, inserted.get(0).getCreatedBy());
        assertEquals("题目A", inserted.get(0).getContent());
    }

    @Test
    void batchImportQuestions_emptyRequest_shouldReturnParamError() {
        // Given: 空请求（无题目列表）。
        BatchImportQuestionRequestDTO request = new BatchImportQuestionRequestDTO();
        request.setQuestions(Collections.emptyList());

        // When: 执行批量导入。
        Response<BatchImportQuestionResponseDTO> response = questionService.batchImportQuestions(request);

        // Then: 返回参数错误，不触发入库。
        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), response.getErrorCode());
        verify(questionDOMapper, never()).batchInsert(anyList());
    }

    @Test
    void createQuestion_validRequest_shouldInsertAndReturnVO() {
        // Given: 登录用户 + 可生成题目 ID + insert 成功。
        LoginUserContextHolder.setUserId(123L);
        when(distributedIdGeneratorRpcService.getQuestionBankId()).thenReturn("9001");
        when(questionDOMapper.insertSelective(any(QuestionDO.class))).thenReturn(1);

        CreateQuestionDTO request = new CreateQuestionDTO();
        request.setContent("什么是JVM");
        request.setAnswer("Java虚拟机");
        request.setAnalysis("基础概念题");

        // When: 创建题目。
        Response<QuestionVO> response = questionService.createQuestion(request);

        // Then: 返回创建结果 VO 且核心字段正确。
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(9001L, response.getData().getId());
        assertEquals("什么是JVM", response.getData().getContent());
        assertEquals("WAITING", response.getData().getProcessStatus());

        // 校验入库对象中创建人和时间字段已由后端补齐。
        ArgumentCaptor<QuestionDO> captor = ArgumentCaptor.forClass(QuestionDO.class);
        verify(questionDOMapper).insertSelective(captor.capture());
        QuestionDO inserted = captor.getValue();
        assertEquals(123L, inserted.getCreatedBy());
        assertNotNull(inserted.getCreatedTime());
        assertNotNull(inserted.getUpdatedTime());
    }

    @Test
    void createQuestion_missingUserIdHeaderContext_shouldThrowParamError() {
        // Given: 未设置 userId 上下文（等价于请求头未携带 userId）。
        CreateQuestionDTO request = new CreateQuestionDTO();
        request.setContent("测试题目");

        // When + Then: 直接抛出参数异常，并明确提示缺少 userId。
        BizException ex = assertThrows(BizException.class, () -> questionService.createQuestion(request));
        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
        assertEquals("请求头 userId 不能为空", ex.getErrorMessage());
        verifyNoInteractions(distributedIdGeneratorRpcService);
        verify(questionDOMapper, never()).insertSelective(any(QuestionDO.class));
    }

    @Test
    void createQuestion_invalidUserIdHeaderContext_shouldThrowParamError() {
        // Given: userId 上下文是非数字字符串（等价于请求头 userId=abc）。
        LoginUserContextHolder.setUserId("abc");
        CreateQuestionDTO request = new CreateQuestionDTO();
        request.setContent("测试题目");

        // When + Then: 抛出参数异常，并明确提示 userId 格式错误。
        BizException ex = assertThrows(BizException.class, () -> questionService.createQuestion(request));
        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
        assertEquals("请求头 userId 必须是数字", ex.getErrorMessage());
        verifyNoInteractions(distributedIdGeneratorRpcService);
        verify(questionDOMapper, never()).insertSelective(any(QuestionDO.class));
    }

    @Test
    void pageQuestions_shouldApplyCurrentUserFilterAndReturnPageInfo() {
        // Given: 当前用户分页查询；Mapper 返回 1 条本人题目。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByExample(any(QuestionDO.class))).thenReturn(List.of(
                QuestionDO.builder()
                        .id(1L)
                        .content("集合框架")
                        .answer("List/Set/Map")
                        .analysis("基础")
                        .processStatus("WAITING")
                        .createdBy(123L)
                        .createdTime(LocalDateTime.now())
                        .updatedTime(LocalDateTime.now())
                        .build()
        ));

        QuestionPageQueryDTO request = new QuestionPageQueryDTO();
        request.setPage(1);
        request.setPageSize(10);
        request.setContent("集合");

        // When: 执行分页查询。
        Response<?> response = questionService.pageQuestions(request);

        // Then: 返回分页对象，并包含查询结果。
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertInstanceOf(PageInfo.class, response.getData());
        PageInfo<?> pageInfo = (PageInfo<?>) response.getData();
        assertEquals(1, pageInfo.getList().size());

        // 同时断言 service 已把 createdBy 设置为当前登录用户，避免越权读。
        ArgumentCaptor<QuestionDO> captor = ArgumentCaptor.forClass(QuestionDO.class);
        verify(questionDOMapper).selectByExample(captor.capture());
        assertEquals(123L, captor.getValue().getCreatedBy());
        assertEquals("集合", captor.getValue().getContent());
    }

    @Test
    void deleteQuestions_emptyIds_shouldReturnParamError() {
        // Given: 删除 ID 列表为空。
        LoginUserContextHolder.setUserId(123L);

        // When: 执行删除。
        Response<?> response = questionService.deleteQuestions(Collections.emptyList());

        // Then: 直接返回参数错误，避免“静默成功”。
        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), response.getErrorCode());
        verify(questionDOMapper, never()).deleteBatch(anyList());
    }

    @Test
    void deleteQuestions_noAuthorizedIds_shouldReturnSuccessWithMessage() {
        // Given: 用户请求删除 [1,2]，但本人可见题目只有 [3]。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByExample(any(QuestionDO.class))).thenReturn(List.of(
                QuestionDO.builder().id(3L).createdBy(123L).build()
        ));

        // When: 执行删除。
        Response<?> response = questionService.deleteQuestions(List.of(1L, 2L));

        // Then: 返回成功但提示“未删除任何题目”，且不执行 deleteBatch。
        assertTrue(response.isSuccess());
        assertEquals("未删除任何题目（无权限或题目不存在）", response.getData());
        verify(questionDOMapper, never()).deleteBatchByCreatorAndStatuses(anyList(), anyLong(), anyList());
    }

    @Test
    void deleteQuestions_authorizedIds_shouldDeleteBatch() {
        // Given: 用户请求删除 [1,2,3]，本人可删题目为 [2,3]。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByExample(any(QuestionDO.class))).thenReturn(List.of(
                QuestionDO.builder().id(2L).createdBy(123L).processStatus("WAITING").build(),
                QuestionDO.builder().id(3L).createdBy(123L).processStatus("PROCESS_FAILED").build(),
                QuestionDO.builder().id(4L).createdBy(123L).processStatus("WAITING").build()
        ));
        when(questionDOMapper.deleteBatchByCreatorAndStatuses(anyList(), eq(123L), anyList())).thenReturn(2);

        // When: 执行删除。
        Response<?> response = questionService.deleteQuestions(List.of(1L, 2L, 3L));

        // Then: deleteBatch 只应收到授权后的 [2,3]。
        assertTrue(response.isSuccess());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(questionDOMapper).deleteBatchByCreatorAndStatuses(captor.capture(), eq(123L), anyList());
        assertEquals(List.of(2L, 3L), captor.getValue());
    }

    @Test
    void deleteQuestions_onlyNonMutableStatus_shouldReturnStatusError() {
        // Given: 当前用户命中的题目都处于不可删除状态。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByExample(any(QuestionDO.class))).thenReturn(List.of(
                QuestionDO.builder().id(9L).createdBy(123L).processStatus("REVIEW_PENDING").build(),
                QuestionDO.builder().id(10L).createdBy(123L).processStatus("COMPLETED").build()
        ));

        // When
        Response<?> response = questionService.deleteQuestions(List.of(9L, 10L));

        // Then
        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.QUESTION_STATUS_NOT_ALLOWED.getErrorCode(), response.getErrorCode());
        assertEquals("仅允许删除 WAITING 或 PROCESS_FAILED 状态的题目", response.getMessage());
        verify(questionDOMapper, never()).deleteBatchByCreatorAndStatuses(anyList(), anyLong(), anyList());
    }

    @Test
    void deleteQuestions_mixedMutableAndNonMutable_shouldDeletePartialAndReturnMessage() {
        // Given: 命中题目里既有可删状态，也有不可删状态。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByExample(any(QuestionDO.class))).thenReturn(List.of(
                QuestionDO.builder().id(21L).createdBy(123L).processStatus("WAITING").build(),
                QuestionDO.builder().id(22L).createdBy(123L).processStatus("REVIEW_PENDING").build(),
                QuestionDO.builder().id(23L).createdBy(123L).processStatus("PROCESS_FAILED").build()
        ));
        when(questionDOMapper.deleteBatchByCreatorAndStatuses(anyList(), eq(123L), anyList())).thenReturn(2);

        // When
        Response<?> response = questionService.deleteQuestions(List.of(21L, 22L, 23L));

        // Then
        assertTrue(response.isSuccess());
        assertEquals("已删除 2 条，跳过 1 条（仅允许删除 WAITING/PROCESS_FAILED 状态题目）", response.getData());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(questionDOMapper).deleteBatchByCreatorAndStatuses(captor.capture(), eq(123L), anyList());
        assertEquals(List.of(21L, 23L), captor.getValue());
    }

    @Test
    void updateQuestion_nonOwner_shouldThrowNoPermission() {
        // Given: 题目存在，但创建人不是当前用户。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByPrimaryKey(88L)).thenReturn(
                QuestionDO.builder().id(88L).createdBy(999L).build()
        );

        UpdateQuestionDTO request = new UpdateQuestionDTO();
        request.setContent("新内容");

        // When + Then: 应抛出无权限异常，且不触发更新 SQL。
        BizException ex = assertThrows(BizException.class, () -> questionService.updateQuestion(88L, request));
        assertEquals(ResponseCodeEnum.NO_PERMISSION.getErrorCode(), ex.getErrorCode());
        verify(questionDOMapper, never()).updateEditableQuestion(any(QuestionDO.class), anyLong(), anyString());
    }

    @Test
    void updateQuestion_ownerShouldUpdateAndReturnVO() {
        // Given: 当前用户是题目创建人，更新 SQL 生效。
        LoginUserContextHolder.setUserId(123L);
        QuestionDO existing = QuestionDO.builder().id(66L).createdBy(123L).content("旧内容").processStatus("WAITING").build();
        QuestionDO updated = QuestionDO.builder()
                .id(66L)
                .createdBy(123L)
                .content("新内容")
                .answer("新答案")
                .analysis("新解析")
                .processStatus("WAITING")
                .createdTime(LocalDateTime.now().minusDays(1))
                .updatedTime(LocalDateTime.now())
                .build();
        when(questionDOMapper.selectByPrimaryKey(66L)).thenReturn(existing, updated);
        when(questionDOMapper.updateEditableQuestion(any(QuestionDO.class), eq(123L), eq("WAITING"))).thenReturn(1);

        UpdateQuestionDTO request = new UpdateQuestionDTO();
        request.setContent("新内容");
        request.setAnswer("新答案");
        request.setAnalysis("新解析");

        // When: 执行更新。
        Response<QuestionVO> response = questionService.updateQuestion(66L, request);

        // Then: 返回更新后的题目信息。
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(66L, response.getData().getId());
        assertEquals("新内容", response.getData().getContent());
        assertEquals("新答案", response.getData().getAnswer());
        assertEquals("新解析", response.getData().getAnalysis());

        // 校验更新请求体是否带上 id 和 updatedTime。
        ArgumentCaptor<QuestionDO> captor = ArgumentCaptor.forClass(QuestionDO.class);
        verify(questionDOMapper).updateEditableQuestion(captor.capture(), eq(123L), eq("WAITING"));
        assertEquals(66L, captor.getValue().getId());
        assertNotNull(captor.getValue().getUpdatedTime());
    }

    @Test
    void updateQuestion_nonMutableStatus_shouldThrowStatusNotAllowed() {
        // Given: 题目属于当前用户，但已进入 REVIEW_PENDING，不应再允许编辑。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByPrimaryKey(67L)).thenReturn(
                QuestionDO.builder().id(67L).createdBy(123L).processStatus("REVIEW_PENDING").build()
        );

        UpdateQuestionDTO request = new UpdateQuestionDTO();
        request.setContent("不该允许的修改");

        // When + Then
        BizException ex = assertThrows(BizException.class, () -> questionService.updateQuestion(67L, request));
        assertEquals(ResponseCodeEnum.QUESTION_STATUS_NOT_ALLOWED.getErrorCode(), ex.getErrorCode());
        assertEquals("仅允许编辑 WAITING 或 PROCESS_FAILED 状态的题目", ex.getErrorMessage());
        verify(questionDOMapper, never()).updateEditableQuestion(any(QuestionDO.class), anyLong(), anyString());
    }

    @Test
    void deleteQuestions_statusChangedBeforeDelete_shouldReturnSkippedMessage() {
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByExample(any(QuestionDO.class))).thenReturn(List.of(
                QuestionDO.builder().id(31L).createdBy(123L).processStatus("WAITING").build()
        ));
        when(questionDOMapper.deleteBatchByCreatorAndStatuses(anyList(), eq(123L), anyList())).thenReturn(0);

        Response<?> response = questionService.deleteQuestions(List.of(31L));

        assertTrue(response.isSuccess());
        assertEquals("未删除任何题目（状态已变化或当前状态不允许删除）", response.getData());
    }

    @Test
    void updateQuestion_statusChangedBeforePersist_shouldThrowRefreshHint() {
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByPrimaryKey(166L)).thenReturn(
                QuestionDO.builder().id(166L).createdBy(123L).content("旧内容").processStatus("WAITING").build()
        );
        when(questionDOMapper.updateEditableQuestion(any(QuestionDO.class), eq(123L), eq("WAITING"))).thenReturn(0);

        UpdateQuestionDTO request = new UpdateQuestionDTO();
        request.setContent("新内容");

        BizException ex = assertThrows(BizException.class, () -> questionService.updateQuestion(166L, request));
        assertEquals(ResponseCodeEnum.QUESTION_UPDATE_FAILED.getErrorCode(), ex.getErrorCode());
        assertEquals("题目状态已变化，请刷新后重试", ex.getErrorMessage());
    }

    @Test
    void reviewQuestion_approve_shouldTransitToCompleted() {
        // Given: 待审核题目由本人执行 APPROVE。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByPrimaryKey(77L)).thenReturn(
                QuestionDO.builder().id(77L).createdBy(123L).processStatus("REVIEW_PENDING").build()
        );
        when(questionDOMapper.transitStatus(77L, "REVIEW_PENDING", "COMPLETED")).thenReturn(1);
        ReviewQuestionRequestDTO request = new ReviewQuestionRequestDTO();
        request.setAction("APPROVE");

        // When
        Response<?> response = questionService.reviewQuestion(77L, request);

        // Then
        assertTrue(response.isSuccess());
        verify(questionDOMapper).transitStatus(77L, "REVIEW_PENDING", "COMPLETED");
    }

    @Test
    void reviewQuestion_reject_shouldTransitToWaiting() {
        // Given: 待审核题目由本人执行 REJECT。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByPrimaryKey(78L)).thenReturn(
                QuestionDO.builder().id(78L).createdBy(123L).processStatus("REVIEW_PENDING").build()
        );
        when(questionDOMapper.transitStatus(78L, "REVIEW_PENDING", "WAITING")).thenReturn(1);
        ReviewQuestionRequestDTO request = new ReviewQuestionRequestDTO();
        request.setAction("REJECT");

        // When
        Response<?> response = questionService.reviewQuestion(78L, request);

        // Then
        assertTrue(response.isSuccess());
        verify(questionDOMapper).transitStatus(78L, "REVIEW_PENDING", "WAITING");
    }

    @Test
    void reviewQuestion_rejectWithClearFlag_shouldClearAnswer() {
        // Given: 驳回且显式要求清空答案。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByPrimaryKey(781L)).thenReturn(
                QuestionDO.builder().id(781L).createdBy(123L).processStatus("REVIEW_PENDING").answer("AI答案").build()
        );
        when(questionDOMapper.transitStatusAndClearAnswerByExpectedStatus(781L, "REVIEW_PENDING", "WAITING")).thenReturn(1);
        ReviewQuestionRequestDTO request = new ReviewQuestionRequestDTO();
        request.setAction("REJECT");
        request.setClearAnswerOnReject(true);

        // When
        Response<?> response = questionService.reviewQuestion(781L, request);

        // Then: 走“状态+清空答案”专用更新。
        assertTrue(response.isSuccess());
        verify(questionDOMapper).transitStatusAndClearAnswerByExpectedStatus(781L, "REVIEW_PENDING", "WAITING");
        verify(questionDOMapper, never()).transitStatus(781L, "REVIEW_PENDING", "WAITING");
    }

    @Test
    void reviewQuestion_invalidState_shouldReturnParamError() {
        // Given: WAITING 状态不允许执行 APPROVE。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByPrimaryKey(79L)).thenReturn(
                QuestionDO.builder().id(79L).createdBy(123L).processStatus("WAITING").build()
        );
        ReviewQuestionRequestDTO request = new ReviewQuestionRequestDTO();
        request.setAction("APPROVE");

        // When
        Response<?> response = questionService.reviewQuestion(79L, request);

        // Then
        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), response.getErrorCode());
        assertTrue(response.getMessage().contains("不允许执行"));
        verify(questionDOMapper, never()).transitStatus(anyLong(), anyString(), anyString());
    }

    @Test
    void reviewQuestion_statusChangedDuringUpdate_shouldReturnFriendlyError() {
        // Given: 查询时还是 REVIEW_PENDING，但真正更新时状态已变化。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectByPrimaryKey(880L)).thenReturn(
                QuestionDO.builder().id(880L).createdBy(123L).processStatus("REVIEW_PENDING").build()
        );
        when(questionDOMapper.transitStatus(880L, "REVIEW_PENDING", "COMPLETED")).thenReturn(0);
        ReviewQuestionRequestDTO request = new ReviewQuestionRequestDTO();
        request.setAction("APPROVE");

        // When
        Response<?> response = questionService.reviewQuestion(880L, request);

        // Then
        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.QUESTION_UPDATE_FAILED.getErrorCode(), response.getErrorCode());
        assertEquals("题目状态已变化，请刷新后重试", response.getMessage());
    }

    @Test
    void sendQuestionsToQueue_emptyMode_shouldDefaultToGenerateAndSend() {
        // Given: 入参 mode 为空，服务应自动按 GENERATE 处理。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.transitStatus(1L, "WAITING", "DISPATCHING")).thenReturn(1);
        when(questionDOMapper.transitStatus(1L, "DISPATCHING", "PROCESSING")).thenReturn(1);
        when(questionDOMapper.selectBatchByIds(List.of(1L))).thenReturn(List.of(
                QuestionDO.builder().id(1L).content("线程和进程区别").createdBy(123L).processStatus("WAITING").build()
        ));
        when(rocketMQTemplate.syncSend(anyString(), any(Message.class))).thenReturn(null);

        // When: 发送到队列（mode 为空）。
        Response<?> response = questionService.sendQuestionsToQueue(List.of(1L), null);

        // Then: 返回成功，且消息体中的 mode 已被标准化为 GENERATE。
        assertTrue(response.isSuccess());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<QuestionMessage>> captor = ArgumentCaptor.forClass((Class) Message.class);
        verify(rocketMQTemplate).syncSend(eq(MQConstants.TOPIC_TEST), captor.capture());
        assertEquals("GENERATE", captor.getValue().getPayload().getMode());
        assertNull(captor.getValue().getPayload().getCurrentAnswer());
    }

    @Test
    void sendQuestionsToQueue_invalidMode_shouldReturnParamError() {
        // Given: 非法 mode。
        // When: 调用发送接口。
        Response<?> response = questionService.sendQuestionsToQueue(List.of(1L), "ABC");

        // Then: 直接返回参数错误，不执行状态更新与消息发送。
        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), response.getErrorCode());
        verify(questionDOMapper, never()).transitStatus(anyLong(), anyString(), anyString());
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    void sendQuestionsToQueue_generateMode_shouldSkipQuestionsWithAnswer() {
        // Given: 两道题，其中一道已有答案（应被 GENERATE 跳过）。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectBatchByIds(List.of(1L, 2L))).thenReturn(List.of(
                QuestionDO.builder().id(1L).content("无答案题").answer(null).createdBy(123L).processStatus("WAITING").build(),
                QuestionDO.builder().id(2L).content("有答案题").answer("A").createdBy(123L).processStatus("WAITING").build()
        ));
        when(questionDOMapper.transitStatus(1L, "WAITING", "DISPATCHING")).thenReturn(1);
        when(questionDOMapper.transitStatus(1L, "DISPATCHING", "PROCESSING")).thenReturn(1);
        when(rocketMQTemplate.syncSend(anyString(), any(Message.class))).thenReturn(null);

        // When: GENERATE 模式发送。
        Response<?> response = questionService.sendQuestionsToQueue(List.of(1L, 2L), "GENERATE");

        // Then: 仅发送无答案题，返回结构化统计正确。
        assertTrue(response.isSuccess());
        assertInstanceOf(SendToQueueResultVO.class, response.getData());
        SendToQueueResultVO result = (SendToQueueResultVO) response.getData();
        assertEquals("GENERATE", result.getMode());
        assertEquals(2, result.getRequestedCount());
        assertEquals(2, result.getFoundCount());
        assertEquals(1, result.getEligibleCount());
        assertEquals(1, result.getSentCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(1, result.getSkippedHasAnswerCount());
        assertEquals(0, result.getSkippedNoAnswerCount());
        verify(questionDOMapper).transitStatus(1L, "WAITING", "DISPATCHING");
        verify(questionDOMapper).transitStatus(1L, "DISPATCHING", "PROCESSING");
        verify(rocketMQTemplate, times(1)).syncSend(eq(MQConstants.TOPIC_TEST), any(Message.class));
    }

    @Test
    void sendQuestionsToQueue_generateMode_allHaveAnswer_shouldNotSendMq() {
        // Given: 所有题目都已有答案。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectBatchByIds(List.of(11L, 12L))).thenReturn(List.of(
                QuestionDO.builder().id(11L).content("题目11").answer("A").createdBy(123L).processStatus("WAITING").build(),
                QuestionDO.builder().id(12L).content("题目12").answer("B").createdBy(123L).processStatus("WAITING").build()
        ));

        // When: GENERATE 模式发送。
        Response<?> response = questionService.sendQuestionsToQueue(List.of(11L, 12L), "GENERATE");

        // Then: 不发 MQ，不改状态，结构化结果显示全部被“已有答案”跳过。
        assertTrue(response.isSuccess());
        assertInstanceOf(SendToQueueResultVO.class, response.getData());
        SendToQueueResultVO result = (SendToQueueResultVO) response.getData();
        assertEquals(0, result.getEligibleCount());
        assertEquals(0, result.getSentCount());
        assertEquals(2, result.getSkippedCount());
        assertEquals(2, result.getSkippedHasAnswerCount());
        assertEquals(0, result.getSkippedNoAnswerCount());
        verify(questionDOMapper, never()).transitStatus(anyLong(), anyString(), anyString());
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    void sendQuestionsToQueue_validateMode_shouldSkipQuestionsWithoutAnswer() {
        // Given: 两道题，其中一道无答案（应被 VALIDATE 跳过）。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectBatchByIds(List.of(21L, 22L))).thenReturn(List.of(
                QuestionDO.builder().id(21L).content("无答案题").answer(null).createdBy(123L).processStatus("WAITING").build(),
                QuestionDO.builder().id(22L).content("有答案题").answer("C").createdBy(123L).processStatus("WAITING").build()
        ));
        when(questionDOMapper.transitStatus(22L, "WAITING", "DISPATCHING")).thenReturn(1);
        when(questionDOMapper.transitStatus(22L, "DISPATCHING", "PROCESSING")).thenReturn(1);
        when(rocketMQTemplate.syncSend(anyString(), any(Message.class))).thenReturn(null);

        // When: VALIDATE 模式发送。
        Response<?> response = questionService.sendQuestionsToQueue(List.of(21L, 22L), "VALIDATE");

        // Then: 仅发送已有答案题，且“无答案跳过”统计正确。
        assertTrue(response.isSuccess());
        assertInstanceOf(SendToQueueResultVO.class, response.getData());
        SendToQueueResultVO result = (SendToQueueResultVO) response.getData();
        assertEquals("VALIDATE", result.getMode());
        assertEquals(1, result.getEligibleCount());
        assertEquals(1, result.getSentCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getSkippedHasAnswerCount());
        assertEquals(1, result.getSkippedNoAnswerCount());
        verify(questionDOMapper).transitStatus(22L, "WAITING", "DISPATCHING");
        verify(questionDOMapper).transitStatus(22L, "DISPATCHING", "PROCESSING");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<QuestionMessage>> captor = ArgumentCaptor.forClass((Class) Message.class);
        verify(rocketMQTemplate, times(1)).syncSend(eq(MQConstants.TOPIC_TEST), captor.capture());
        assertEquals("VALIDATE", captor.getValue().getPayload().getMode());
        assertEquals("C", captor.getValue().getPayload().getCurrentAnswer());
    }

    @Test
    void sendQuestionsToQueue_validateMode_allNoAnswer_shouldNotSendMq() {
        // Given: 所有题目都没有答案。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectBatchByIds(List.of(31L, 32L))).thenReturn(List.of(
                QuestionDO.builder().id(31L).content("题目31").answer(null).createdBy(123L).processStatus("WAITING").build(),
                QuestionDO.builder().id(32L).content("题目32").answer("").createdBy(123L).processStatus("WAITING").build()
        ));

        // When: VALIDATE 模式发送。
        Response<?> response = questionService.sendQuestionsToQueue(List.of(31L, 32L), "VALIDATE");

        // Then: 没有可发送题目，不应更新状态和发送 MQ。
        assertTrue(response.isSuccess());
        assertInstanceOf(SendToQueueResultVO.class, response.getData());
        SendToQueueResultVO result = (SendToQueueResultVO) response.getData();
        assertEquals(0, result.getEligibleCount());
        assertEquals(0, result.getSentCount());
        assertEquals(2, result.getSkippedNoAnswerCount());
        verify(questionDOMapper, never()).transitStatus(anyLong(), anyString(), anyString());
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    void sendQuestionsToQueue_nonWaitingStatus_shouldSkipByStateMachine() {
        // Given: 题目状态非 WAITING（例如 PROCESSING），不允许再次执行 SEND 动作。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectBatchByIds(List.of(41L))).thenReturn(List.of(
                QuestionDO.builder().id(41L).content("处理中题目").answer(null).createdBy(123L).processStatus("PROCESSING").build()
        ));

        // When: GENERATE 模式发送。
        Response<?> response = questionService.sendQuestionsToQueue(List.of(41L), "GENERATE");

        // Then: 被状态机拦截，不更新状态也不发送 MQ。
        assertTrue(response.isSuccess());
        assertInstanceOf(SendToQueueResultVO.class, response.getData());
        SendToQueueResultVO result = (SendToQueueResultVO) response.getData();
        assertEquals(0, result.getEligibleCount());
        assertEquals(0, result.getSentCount());
        assertEquals(1, result.getSkippedCount());
        assertTrue(result.getMessage().contains("状态不允许发送"));
        verify(questionDOMapper, never()).transitStatus(anyLong(), anyString(), anyString());
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    void sendQuestionsToQueue_mqDisabledAndMockEnabled_shouldSimulateAndUpdateToReviewPending() {
        // Given: MQ 不可用，但开启了本地 mock 模式。
        LoginUserContextHolder.setUserId(123L);
        ReflectionTestUtils.setField(questionService, "rocketMQTemplate", null);
        ReflectionTestUtils.setField(questionService, "mqMockEnabled", true);
        when(questionDOMapper.selectBatchByIds(List.of(101L))).thenReturn(List.of(
                QuestionDO.builder().id(101L).content("什么是索引").answer(null).createdBy(123L).processStatus("WAITING").build()
        ));
        when(questionDOMapper.transitStatus(101L, "WAITING", "DISPATCHING")).thenReturn(1);
        when(questionDOMapper.transitStatus(101L, "DISPATCHING", "PROCESSING")).thenReturn(1);
        when(questionDOMapper.transitStatusAndAnswer(101L, "PROCESSING", "REVIEW_PENDING", "【MOCK-AI】什么是索引"))
                .thenReturn(1);

        // When: 发送到队列。
        Response<?> response = questionService.sendQuestionsToQueue(List.of(101L), "GENERATE");

        // Then: 不走 MQ，直接本地模拟推进到 REVIEW_PENDING。
        assertTrue(response.isSuccess());
        assertInstanceOf(SendToQueueResultVO.class, response.getData());
        SendToQueueResultVO result = (SendToQueueResultVO) response.getData();
        assertEquals(1, result.getEligibleCount());
        assertEquals(1, result.getSentCount());
        assertTrue(result.getMessage().contains("本地模拟处理"));
        verify(questionDOMapper).transitStatus(101L, "WAITING", "DISPATCHING");
        verify(questionDOMapper).transitStatus(101L, "DISPATCHING", "PROCESSING");
        verify(questionDOMapper).transitStatusAndAnswer(101L, "PROCESSING", "REVIEW_PENDING", "【MOCK-AI】什么是索引");
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    void sendQuestionsToQueue_mqDisabledAndMockDisabled_shouldReturnError() {
        // Given: MQ 不可用且 mock 关闭。
        LoginUserContextHolder.setUserId(123L);
        ReflectionTestUtils.setField(questionService, "rocketMQTemplate", null);
        ReflectionTestUtils.setField(questionService, "mqMockEnabled", false);
        when(questionDOMapper.selectBatchByIds(List.of(201L))).thenReturn(List.of(
                QuestionDO.builder().id(201L).content("测试题").answer(null).createdBy(123L).processStatus("WAITING").build()
        ));

        // When: 发送到队列。
        Response<?> response = questionService.sendQuestionsToQueue(List.of(201L), "GENERATE");

        // Then: 返回 MQ 未启用提示，不进行本地模拟更新。
        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), response.getErrorCode());
        assertEquals("当前环境未启用 MQ，请开启 feature.mq.enabled 后再发送", response.getMessage());
        verify(questionDOMapper, never()).transitStatus(anyLong(), anyString(), anyString());
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    void sendQuestionsToQueue_sendFailed_shouldRollbackQuestionToWaiting() {
        // Given: 状态已推进到 DISPATCHING，但 MQ 发送失败，应补偿回滚。
        LoginUserContextHolder.setUserId(123L);
        when(questionDOMapper.selectBatchByIds(List.of(301L))).thenReturn(List.of(
                QuestionDO.builder().id(301L).content("失败补偿题").answer(null).createdBy(123L).processStatus("WAITING").build()
        ));
        when(questionDOMapper.transitStatus(301L, "WAITING", "DISPATCHING")).thenReturn(1);
        when(rocketMQTemplate.syncSend(anyString(), any(Message.class))).thenThrow(new RuntimeException("mq down"));
        when(questionDOMapper.transitStatus(301L, "DISPATCHING", "WAITING")).thenReturn(1);

        // When
        Response<?> response = questionService.sendQuestionsToQueue(List.of(301L), "GENERATE");

        // Then
        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.QUESTION_SEND_FAILED.getErrorCode(), response.getErrorCode());
        assertTrue(response.getMessage().contains("已回滚"));
        verify(questionDOMapper).transitStatus(301L, "DISPATCHING", "WAITING");
    }

    @Test
    void batchUpdateSuccessQuestions_generateMode_shouldOverwriteAnswer() {
        // Given: GENERATE 模式成功回调，应将 aiAnswer 写入答案字段。
        AIProcessResultMessage message = new AIProcessResultMessage("501", "GENERATE", 1,
                "AI生成答案", "NA", null);
        when(questionDOMapper.selectByPrimaryKey(501L))
                .thenReturn(QuestionDO.builder().id(501L).processStatus("PROCESSING").answer(null).build());
        when(questionDOMapper.transitStatusAndAnswer(501L, "PROCESSING", "REVIEW_PENDING", "AI生成答案")).thenReturn(1);

        // When
        int count = questionService.batchUpdateSuccessQuestions(Map.of("501", message));

        // Then
        assertEquals(1, count);
        verify(questionDOMapper).transitStatusAndAnswer(501L, "PROCESSING", "REVIEW_PENDING", "AI生成答案");
    }

    @Test
    void batchUpdateSuccessQuestions_validateMode_shouldNotOverwriteAnswer() {
        // Given: VALIDATE 模式成功回调，默认不覆盖原答案，仅推进状态。
        AIProcessResultMessage message = new AIProcessResultMessage("601", "VALIDATE", 1,
                "AI建议答案", "FAIL", "建议人工复核");
        when(questionDOMapper.selectByPrimaryKey(601L))
                .thenReturn(QuestionDO.builder().id(601L).processStatus("PROCESSING").answer("原答案").build());
        when(questionDOMapper.transitStatus(601L, "PROCESSING", "REVIEW_PENDING")).thenReturn(1);

        // When
        int count = questionService.batchUpdateSuccessQuestions(Map.of("601", message));

        // Then
        assertEquals(1, count);
        verify(questionDOMapper).transitStatus(601L, "PROCESSING", "REVIEW_PENDING");
    }

    @Test
    void batchUpdateSuccessQuestions_missingModeButValidateShape_shouldNotOverwriteAnswer() {
        // Given: 回包未带 mode，但 validationResult=FAIL 且原题目已有答案，应按 VALIDATE 处理。
        AIProcessResultMessage message = new AIProcessResultMessage("602", null, 1,
                "AI建议答案", "FAIL", "建议人工复核");
        when(questionDOMapper.selectByPrimaryKey(602L))
                .thenReturn(QuestionDO.builder().id(602L).processStatus("PROCESSING").answer("原答案").build());
        when(questionDOMapper.transitStatus(602L, "PROCESSING", "REVIEW_PENDING")).thenReturn(1);

        // When
        int count = questionService.batchUpdateSuccessQuestions(Map.of("602", message));

        // Then: 不应误判为 GENERATE 覆盖答案。
        assertEquals(1, count);
        verify(questionDOMapper).transitStatus(602L, "PROCESSING", "REVIEW_PENDING");
    }

    private static QuestionDTO buildQuestionDTO(String content, String answer, String analysis) {
        QuestionDTO dto = new QuestionDTO();
        dto.setContent(content);
        dto.setAnswer(answer);
        dto.setAnalysis(analysis);
        return dto;
    }
}
