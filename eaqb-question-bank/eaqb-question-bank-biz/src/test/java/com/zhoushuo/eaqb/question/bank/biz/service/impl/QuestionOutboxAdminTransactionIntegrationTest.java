package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionOutboxAdminRetryLogDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionOutboxEventDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionProcessTaskDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionOutboxAdminRetryLogDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionOutboxEventDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionProcessTaskDOMapper;
import com.zhoushuo.eaqb.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import com.zhoushuo.eaqb.question.bank.biz.enums.OutboxEventStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessTaskStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(QuestionOutboxAdminTransactionIntegrationTest.TestConfig.class)
class QuestionOutboxAdminTransactionIntegrationTest {

    @jakarta.annotation.Resource
    private QuestionOutboxAdminAppService questionOutboxAdminAppService;

    @jakarta.annotation.Resource
    private TestStores testStores;

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
        testStores.reset();
    }

    @Test
    void retryFailedOutboxEvent_taskUpdateFails_shouldRollbackMainChainButCommitRetryLog() {
        LocalDateTime now = LocalDateTime.now();
        testStores.questions.seed(7001L, QuestionDO.builder()
                .id(7001L)
                .processStatus(QuestionProcessStatusEnum.WAITING.getCode())
                .updatedTime(now)
                .build());
        testStores.tasks.seed(8001L, QuestionProcessTaskDO.builder()
                .id(8001L)
                .questionId(7001L)
                .taskStatus(QuestionProcessTaskStatusEnum.FAILED.getCode())
                .sourceQuestionStatus(QuestionProcessStatusEnum.WAITING.getCode())
                .updatedTime(now)
                .build());
        testStores.events.seed(9001L, QuestionOutboxEventDO.builder()
                .id(9001L)
                .taskId(8001L)
                .eventStatus(OutboxEventStatusEnum.FAILED.getCode())
                .dispatchRetryCount(8)
                .updatedTime(now)
                .build());
        testStores.failTaskStatusUpdate = true;
        LoginUserContextHolder.setUserId(2001L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> questionOutboxAdminAppService.retryFailedOutboxEvent(9001L));

        assertTrue(ex.getMessage().contains("恢复任务到 PENDING_DISPATCH 失败"));
        assertEquals(QuestionProcessStatusEnum.WAITING.getCode(),
                testStores.questions.getCommitted(7001L).getProcessStatus());
        assertEquals(QuestionProcessTaskStatusEnum.FAILED.getCode(),
                testStores.tasks.getCommitted(8001L).getTaskStatus());
        assertEquals(OutboxEventStatusEnum.FAILED.getCode(),
                testStores.events.getCommitted(9001L).getEventStatus());

        List<QuestionOutboxAdminRetryLogDO> logs = testStores.retryLogs.getCommitted();
        assertEquals(1, logs.size());
        QuestionOutboxAdminRetryLogDO log = logs.get(0);
        assertEquals(9001L, log.getEventId());
        assertEquals(8001L, log.getTaskId());
        assertEquals(7001L, log.getQuestionId());
        assertEquals(2001L, log.getAdminUserId());
        assertTrue(log.getErrorMessage().contains("恢复任务到 PENDING_DISPATCH 失败"));
        assertNotNull(log.getId());
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        TestStores testStores() {
            return new TestStores();
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new SimpleTestTransactionManager();
        }

        @Bean
        QuestionDOMapper questionDOMapper(TestStores stores) {
            return proxy(QuestionDOMapper.class, new QuestionDOMapperHandler(stores));
        }

        @Bean
        QuestionProcessTaskDOMapper questionProcessTaskDOMapper(TestStores stores) {
            return proxy(QuestionProcessTaskDOMapper.class, new QuestionProcessTaskDOMapperHandler(stores));
        }

        @Bean
        QuestionOutboxEventDOMapper questionOutboxEventDOMapper(TestStores stores) {
            return proxy(QuestionOutboxEventDOMapper.class, new QuestionOutboxEventDOMapperHandler(stores));
        }

        @Bean
        QuestionOutboxAdminRetryLogDOMapper questionOutboxAdminRetryLogDOMapper(TestStores stores) {
            return proxy(QuestionOutboxAdminRetryLogDOMapper.class, new QuestionOutboxAdminRetryLogDOMapperHandler(stores));
        }

        @Bean
        DistributedIdGeneratorRpcService distributedIdGeneratorRpcService(TestStores stores) {
            return new DistributedIdGeneratorRpcService() {
                @Override
                public String nextQuestionBankEntityId() {
                    return String.valueOf(stores.ids.incrementAndGet());
                }
            };
        }

        @Bean
        DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi() {
            return proxy(DistributedIdGeneratorFeignApi.class, (proxy, method, args) -> {
                if (isObjectMethod(method)) {
                    return objectMethod(proxy, method, args);
                }
                throw new UnsupportedOperationException(method.getName());
            });
        }

        @Bean
        QuestionOutboxAdminRetryLogWriter questionOutboxAdminRetryLogWriter(
                QuestionOutboxAdminRetryLogDOMapper questionOutboxAdminRetryLogDOMapper,
                DistributedIdGeneratorRpcService distributedIdGeneratorRpcService) {
            return new QuestionOutboxAdminRetryLogWriter(questionOutboxAdminRetryLogDOMapper, distributedIdGeneratorRpcService);
        }

        @Bean
        QuestionOutboxAdminAppService questionOutboxAdminAppService(
                QuestionOutboxEventDOMapper questionOutboxEventDOMapper,
                QuestionProcessTaskDOMapper questionProcessTaskDOMapper,
                QuestionDOMapper questionDOMapper,
                QuestionOutboxAdminRetryLogWriter questionOutboxAdminRetryLogWriter) {
            return new QuestionOutboxAdminAppService(
                    questionOutboxEventDOMapper,
                    questionProcessTaskDOMapper,
                    questionDOMapper,
                    questionOutboxAdminRetryLogWriter
            );
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, handler);
        }
    }

    static class SimpleTestTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new TxObject(TransactionSynchronizationManager.isActualTransactionActive());
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TxObject) transaction).existing;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected Object doSuspend(Object transaction) {
            return null;
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
        }

        static class TxObject {
            private final boolean existing;

            TxObject(boolean existing) {
                this.existing = existing;
            }
        }
    }

    static class TestStores {
        private final AtomicLong ids = new AtomicLong(10000L);
        private final TransactionalMapStore<Long, QuestionDO> questions = new TransactionalMapStore<>(TestStores::copyQuestion);
        private final TransactionalMapStore<Long, QuestionProcessTaskDO> tasks = new TransactionalMapStore<>(TestStores::copyTask);
        private final TransactionalMapStore<Long, QuestionOutboxEventDO> events = new TransactionalMapStore<>(TestStores::copyEvent);
        private final TransactionalListStore<QuestionOutboxAdminRetryLogDO> retryLogs = new TransactionalListStore<>(TestStores::copyRetryLog);
        private boolean failTaskStatusUpdate;

        void reset() {
            failTaskStatusUpdate = false;
            questions.clear();
            tasks.clear();
            events.clear();
            retryLogs.clear();
        }

        private static QuestionDO copyQuestion(QuestionDO source) {
            if (source == null) {
                return null;
            }
            return QuestionDO.builder()
                    .id(source.getId())
                    .processStatus(source.getProcessStatus())
                    .lastReviewMode(source.getLastReviewMode())
                    .createdTime(source.getCreatedTime())
                    .updatedTime(source.getUpdatedTime())
                    .createdBy(source.getCreatedBy())
                    .content(source.getContent())
                    .answer(source.getAnswer())
                    .analysis(source.getAnalysis())
                    .build();
        }

        private static QuestionProcessTaskDO copyTask(QuestionProcessTaskDO source) {
            if (source == null) {
                return null;
            }
            return QuestionProcessTaskDO.builder()
                    .id(source.getId())
                    .questionId(source.getQuestionId())
                    .mode(source.getMode())
                    .attemptNo(source.getAttemptNo())
                    .taskStatus(source.getTaskStatus())
                    .callbackKey(source.getCallbackKey())
                    .sourceQuestionStatus(source.getSourceQuestionStatus())
                    .failureReason(source.getFailureReason())
                    .createdTime(source.getCreatedTime())
                    .updatedTime(source.getUpdatedTime())
                    .build();
        }

        private static QuestionOutboxEventDO copyEvent(QuestionOutboxEventDO source) {
            if (source == null) {
                return null;
            }
            return QuestionOutboxEventDO.builder()
                    .id(source.getId())
                    .taskId(source.getTaskId())
                    .eventStatus(source.getEventStatus())
                    .dispatchRetryCount(source.getDispatchRetryCount())
                    .nextRetryTime(source.getNextRetryTime())
                    .lastError(source.getLastError())
                    .lastErrorTime(source.getLastErrorTime())
                    .createdTime(source.getCreatedTime())
                    .updatedTime(source.getUpdatedTime())
                    .build();
        }

        private static QuestionOutboxAdminRetryLogDO copyRetryLog(QuestionOutboxAdminRetryLogDO source) {
            if (source == null) {
                return null;
            }
            return QuestionOutboxAdminRetryLogDO.builder()
                    .id(source.getId())
                    .eventId(source.getEventId())
                    .taskId(source.getTaskId())
                    .questionId(source.getQuestionId())
                    .adminUserId(source.getAdminUserId())
                    .errorMessage(source.getErrorMessage())
                    .createdTime(source.getCreatedTime())
                    .updatedTime(source.getUpdatedTime())
                    .build();
        }
    }

    static class TransactionalMapStore<K, V> {
        private final Map<K, V> committed = new LinkedHashMap<>();
        private final Function<V, V> copyFn;

        TransactionalMapStore(Function<V, V> copyFn) {
            this.copyFn = copyFn;
        }

        void seed(K key, V value) {
            committed.put(key, copyFn.apply(value));
        }

        V get(K key) {
            Map<K, V> pending = pending(false);
            if (pending != null && pending.containsKey(key)) {
                return copyFn.apply(pending.get(key));
            }
            return copyFn.apply(committed.get(key));
        }

        V getCommitted(K key) {
            return copyFn.apply(committed.get(key));
        }

        void put(K key, V value) {
            Map<K, V> pending = pending(true);
            if (pending != null) {
                pending.put(key, copyFn.apply(value));
                return;
            }
            committed.put(key, copyFn.apply(value));
        }

        void clear() {
            committed.clear();
        }

        @SuppressWarnings("unchecked")
        private Map<K, V> pending(boolean create) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                return null;
            }
            Map<K, V> pending = (Map<K, V>) TransactionSynchronizationManager.getResource(this);
            if (pending == null && create) {
                pending = new LinkedHashMap<>();
                Map<K, V> pendingRef = pending;
                TransactionSynchronizationManager.bindResource(this, pendingRef);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        try {
                            if (status == STATUS_COMMITTED) {
                                committed.putAll(pendingRef);
                            }
                        } finally {
                            TransactionSynchronizationManager.unbindResourceIfPossible(TransactionalMapStore.this);
                        }
                    }
                });
            }
            return pending;
        }
    }

    static class TransactionalListStore<V> {
        private final List<V> committed = new ArrayList<>();
        private final Function<V, V> copyFn;

        TransactionalListStore(Function<V, V> copyFn) {
            this.copyFn = copyFn;
        }

        void add(V value) {
            List<V> pending = pending(true);
            if (pending != null) {
                pending.add(copyFn.apply(value));
                return;
            }
            committed.add(copyFn.apply(value));
        }

        List<V> getCommitted() {
            List<V> result = new ArrayList<>();
            for (V item : committed) {
                result.add(copyFn.apply(item));
            }
            return result;
        }

        void clear() {
            committed.clear();
        }

        @SuppressWarnings("unchecked")
        private List<V> pending(boolean create) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                return null;
            }
            List<V> pending = (List<V>) TransactionSynchronizationManager.getResource(this);
            if (pending == null && create) {
                pending = new ArrayList<>();
                List<V> pendingRef = pending;
                TransactionSynchronizationManager.bindResource(this, pendingRef);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        try {
                            if (status == STATUS_COMMITTED) {
                                committed.addAll(pendingRef);
                            }
                        } finally {
                            TransactionSynchronizationManager.unbindResourceIfPossible(TransactionalListStore.this);
                        }
                    }
                });
            }
            return pending;
        }
    }

    static class QuestionDOMapperHandler implements InvocationHandler {
        private final TestStores stores;

        QuestionDOMapperHandler(TestStores stores) {
            this.stores = stores;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (isObjectMethod(method)) {
                return objectMethod(proxy, method, args);
            }
            if ("selectByPrimaryKey".equals(method.getName())) {
                return stores.questions.get((Long) args[0]);
            }
            if ("transitStatus".equals(method.getName())) {
                Long id = (Long) args[0];
                String expected = (String) args[1];
                String target = (String) args[2];
                QuestionDO current = stores.questions.get(id);
                if (current == null || !Objects.equals(expected, current.getProcessStatus())) {
                    return 0;
                }
                current.setProcessStatus(target);
                stores.questions.put(id, current);
                return 1;
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }

    static class QuestionProcessTaskDOMapperHandler implements InvocationHandler {
        private final TestStores stores;

        QuestionProcessTaskDOMapperHandler(TestStores stores) {
            this.stores = stores;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (isObjectMethod(method)) {
                return objectMethod(proxy, method, args);
            }
            if ("selectByPrimaryKey".equals(method.getName())) {
                return stores.tasks.get((Long) args[0]);
            }
            if ("updateTaskStatus".equals(method.getName())) {
                if (stores.failTaskStatusUpdate) {
                    return 0;
                }
                Long id = (Long) args[0];
                String expected = (String) args[1];
                String target = (String) args[2];
                String failureReason = (String) args[3];
                QuestionProcessTaskDO current = stores.tasks.get(id);
                if (current == null || !Objects.equals(expected, current.getTaskStatus())) {
                    return 0;
                }
                current.setTaskStatus(target);
                current.setFailureReason(failureReason);
                stores.tasks.put(id, current);
                return 1;
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }

    static class QuestionOutboxEventDOMapperHandler implements InvocationHandler {
        private final TestStores stores;

        QuestionOutboxEventDOMapperHandler(TestStores stores) {
            this.stores = stores;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (isObjectMethod(method)) {
                return objectMethod(proxy, method, args);
            }
            if ("selectByPrimaryKey".equals(method.getName())) {
                return stores.events.get((Long) args[0]);
            }
            if ("updateAfterDispatchFailure".equals(method.getName())) {
                Long id = (Long) args[0];
                String expected = (String) args[1];
                String target = (String) args[2];
                Integer retryCount = (Integer) args[3];
                LocalDateTime nextRetryTime = (LocalDateTime) args[4];
                String lastError = (String) args[5];
                LocalDateTime lastErrorTime = (LocalDateTime) args[6];
                QuestionOutboxEventDO current = stores.events.get(id);
                if (current == null || !Objects.equals(expected, current.getEventStatus())) {
                    return 0;
                }
                current.setEventStatus(target);
                current.setDispatchRetryCount(retryCount);
                current.setNextRetryTime(nextRetryTime);
                current.setLastError(lastError);
                current.setLastErrorTime(lastErrorTime);
                stores.events.put(id, current);
                return 1;
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }

    static class QuestionOutboxAdminRetryLogDOMapperHandler implements InvocationHandler {
        private final TestStores stores;

        QuestionOutboxAdminRetryLogDOMapperHandler(TestStores stores) {
            this.stores = stores;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (isObjectMethod(method)) {
                return objectMethod(proxy, method, args);
            }
            if ("insert".equals(method.getName())) {
                stores.retryLogs.add((QuestionOutboxAdminRetryLogDO) args[0]);
                return 1;
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }

    private static boolean isObjectMethod(Method method) {
        return method.getDeclaringClass() == Object.class;
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> proxy.getClass().getName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }
}
