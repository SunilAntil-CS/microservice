package com.vnfm.vimmanager.simulator;

import com.vnfm.vimmanager.client.VimRequest;
import com.vnfm.vimmanager.client.VimResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryVimSimulatorTest {

    @Test
    void whenSucceedTrue_returnsSuccess() {
        VimSimulatorProperties props = new VimSimulatorProperties();
        props.setSucceed(true);
        InMemoryVimSimulator simulator = new InMemoryVimSimulator(props);

        VimRequest request = new VimRequest("ReserveResources", "saga-1", "vnf-1", Map.of());
        VimResponse response = simulator.execute(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResult()).containsEntry("vnfId", "vnf-1");
        assertThat(response.getResult()).containsKey("reservationId");
    }

    @Test
    void whenSucceedFalse_returnsFailure() {
        VimSimulatorProperties props = new VimSimulatorProperties();
        props.setSucceed(false);
        InMemoryVimSimulator simulator = new InMemoryVimSimulator(props);

        VimRequest request = new VimRequest("ReleaseResources", "saga-1", "vnf-1", Map.of("reason", "rollback"));
        VimResponse response = simulator.execute(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("Simulator configured to fail");
    }
}
