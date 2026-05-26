package com.zhoushuo.eaqb.question.bank.biz.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhoushuo.eaqb.question.bank.resp.FindImportBatchByFileResponseDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionBankApiDtoJacksonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void findImportBatchByFileResponse_shouldSupportJacksonDeserialization() throws Exception {
        String json = """
                {
                  "found": true,
                  "batchId": 6101,
                  "status": "COMMITTED",
                  "totalRowCount": 4,
                  "importedCount": 4
                }
                """;

        FindImportBatchByFileResponseDTO response =
                objectMapper.readValue(json, FindImportBatchByFileResponseDTO.class);

        assertTrue(response.isFound());
        assertEquals(6101L, response.getBatchId());
        assertEquals("COMMITTED", response.getStatus());
        assertEquals(4, response.getTotalRowCount());
        assertEquals(4, response.getImportedCount());
    }
}
