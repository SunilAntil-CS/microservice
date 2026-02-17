package com.vnfm.lcm.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnfm.lcm.api.dto.InstantiateRequest;
import com.vnfm.lcm.api.dto.InstantiateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for idempotency: duplicate requests (same requestId) must return
 * the cached response without re-executing the operation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdempotencyIntegrationTest {

    @MockBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void duplicateRequest_withSameRequestIdInBody_returnsCachedResponse() throws Exception {
        String requestId = "req-" + System.currentTimeMillis();
        InstantiateRequest request = new InstantiateRequest();
        request.setRequestId(requestId);
        request.setVnfId("vnf-1");
        request.setVnfType("firewall");
        request.setCpuCores(2);
        request.setMemoryGb(4);

        // First request: processed and cached
        ResultActions first = mockMvc.perform(post("/api/v1/vnf/instantiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        String firstBody = first.andReturn().getResponse().getContentAsString();
        InstantiateResponse firstResponse = objectMapper.readValue(firstBody, InstantiateResponse.class);
        assertThat(firstResponse.getRequestId()).isEqualTo(requestId);
        assertThat(firstResponse.getStatus()).isEqualTo("ACCEPTED");

        // Second request: same requestId – must return cached response (same body)
        ResultActions second = mockMvc.perform(post("/api/v1/vnf/instantiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()); // Filter returns 200 when serving from cache

        String secondBody = second.andReturn().getResponse().getContentAsString();
        assertThat(secondBody).isEqualTo(firstBody);
        InstantiateResponse secondResponse = objectMapper.readValue(secondBody, InstantiateResponse.class);
        assertThat(secondResponse.getRequestId()).isEqualTo(requestId);
        assertThat(secondResponse.getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void duplicateRequest_withSameRequestIdInHeader_returnsCachedResponse() throws Exception {
        String requestId = "req-header-" + System.currentTimeMillis();
        InstantiateRequest request = new InstantiateRequest();
        request.setVnfId("vnf-2");
        request.setVnfType("firewall");
        request.setCpuCores(2);
        request.setMemoryGb(4);

        // First request: use X-Request-Id header
        ResultActions first = mockMvc.perform(post("/api/v1/vnf/instantiate")
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        String firstBody = first.andReturn().getResponse().getContentAsString();

        // Second request: same header – must return cached response
        ResultActions second = mockMvc.perform(post("/api/v1/vnf/instantiate")
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        String secondBody = second.andReturn().getResponse().getContentAsString();
        assertThat(secondBody).isEqualTo(firstBody);
    }
}
