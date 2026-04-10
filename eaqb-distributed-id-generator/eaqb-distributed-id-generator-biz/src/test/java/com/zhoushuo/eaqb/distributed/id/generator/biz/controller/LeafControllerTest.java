package com.zhoushuo.eaqb.distributed.id.generator.biz.controller;

import com.zhoushuo.eaqb.distributed.id.generator.biz.service.SegmentService;
import com.zhoushuo.eaqb.distributed.id.generator.biz.service.SnowflakeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class LeafControllerTest {

    @Mock
    private SegmentService segmentService;
    @Mock
    private SnowflakeService snowflakeService;

    @InjectMocks
    private LeafController leafController;

    @Test
    void getSegmentIds_invalidCount_shouldThrowBadRequest() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> leafController.getSegmentIds("leaf-segment-questionbank-id", 0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("count must be positive", exception.getReason());
    }
}
