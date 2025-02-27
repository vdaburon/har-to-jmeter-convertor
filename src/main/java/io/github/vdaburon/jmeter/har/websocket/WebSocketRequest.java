/*
 * Copyright 2025 Vincent DABURON
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.github.vdaburon.jmeter.har.websocket;

import java.util.List;

/**
 * The websocket connection and all messages in this connection
 */

public class WebSocketRequest {

    public static final int K_WS_EXCHANGE_SEND_RECEIVE = 1;
    public static final int K_WS_EXCHANGE_SEND_ONLY = 2;
    public static final int K_WS_EXCHANGE_RECEIVE_ONLY = 3;

    private String url;
    private String method;
    private String startedDateTime;
    private int statusResponse;
    private List<WebSocketMessage> listWebSocketMessages;
    private List<Integer> listTypeExchange;  // RequestReponse, RequestWrite or ResponseRead

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getStartedDateTime() {
        return startedDateTime;
    }

    public void setStartedDateTime(String startedDateTime) {
        this.startedDateTime = startedDateTime;
    }

    public int getStatusResponse() {
        return statusResponse;
    }

    public void setStatusResponse(int statusResponse) {
        this.statusResponse = statusResponse;
    }

    public List<WebSocketMessage> getListWebSocketMessages() {
        return listWebSocketMessages;
    }

    public void setListWebSocketMessages(List<WebSocketMessage> listWebSocketMessages) {
        this.listWebSocketMessages = listWebSocketMessages;
    }

    public List<Integer> getListTypeExchange() {
        return listTypeExchange;
    }

    public void setListTypeExchange(List<Integer> listTypeExchange) {
        this.listTypeExchange = listTypeExchange;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("WebSocketRequest{");
        sb.append("url='").append(url).append('\'');
        sb.append(", method='").append(method).append('\'');
        sb.append(", startedDateTime='").append(startedDateTime).append('\'');
        sb.append(", statusResponse=").append(statusResponse);
        sb.append(", listWebSocketMessages=").append(listWebSocketMessages);
        sb.append(", listTypeExchange=").append(listTypeExchange);
        sb.append('}');
        return sb.toString();
    }
}
