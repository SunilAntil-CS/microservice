package com.vnfm.vimmanager.client;

/**
 * Port for calling the actual VIM. Implementations may be mock or real VIM adapter.
 */
public interface VimClient {

    /**
     * Execute a VIM command (e.g. reserve or release resources).
     *
     * @param request the VIM-specific request
     * @return success/failure and optional result
     */
    VimResponse execute(VimRequest request);
}
