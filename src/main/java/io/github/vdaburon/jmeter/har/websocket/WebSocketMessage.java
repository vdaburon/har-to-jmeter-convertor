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

import java.util.Objects;

/**
 * a single websocket message (send or receive)
 */
public class WebSocketMessage {

    /* in HAR
     "_webSocketMessages": [
    {
        "type": "send",
            "time": 1739364938.770262,
            "opcode": 1,
            "data": "CONNECT\naccept-version:1.2,1.1,1.0\nheart-beat:10000,10000\n\n\u0000"
    },
    {
        "type": "receive",
            "time": 1739364938.797293,
            "opcode": 1,
            "data": "CONNECTED\nversion:1.2\nheart-beat:0,0\nuser-name:c1a81af6-4b69-4052-a1ad-285786eef52b\n\n\u0000"
    },
    {
        "type": "send",
            "time": 1739364938.80056,
            "opcode": 1,
            "data": "SUBSCRIBE\npseudo:alice\nid:sub-0\ndestination:/topic/sessions/d81c6857-1142-4593-91c9-d3a1ce10128c\n\n\u0000"
    },
    {
        "type": "receive",
            "time": 1739364938.839524,
            "opcode": 1,
            "data": "MESSAGE\ndestination:/topic/sessions/d81c6857-1142-4593-91c9-d3a1ce10128c\ncontent-type:application/json\nsubscription:sub-0\nmessage-id:86d527ea-961b-90ee-7fcc-cc55212690e9-20\ncontent-length:162\n\n{\"id\":\"d81c6857-1142-4593-91c9-d3a1ce10128c\",\"ps\":[{\"id\":\"c1a81af6-4b69-4052-a1ad-285786eef52b\",\"ps\":\"alice\",\"hv\":false,\"vo\":null}],\"cv\":[],\"rv\":false,\"re\":false}\u0000"
    },
    {
        "type": "send",
            "time": 1739364951.292356,
            "opcode": 1,
            "data": "SEND\ndestination:/app/actions/d81c6857-1142-4593-91c9-d3a1ce10128c\ncontent-length:72\n\n{\"type\":\"vote\",\"idUser\":\"c1a81af6-4b69-4052-a1ad-285786eef52b\",\"vote\":4}\u0000"
    },
    {
        "type": "receive",
            "time": 1739364951.319798,
            "opcode": 1,
            "data": "MESSAGE\ndestination:/topic/sessions/d81c6857-1142-4593-91c9-d3a1ce10128c\ncontent-type:application/json\nsubscription:sub-0\nmessage-id:86d527ea-961b-90ee-7fcc-cc55212690e9-22\ncontent-length:158\n\n{\"id\":\"d81c6857-1142-4593-91c9-d3a1ce10128c\",\"ps\":[{\"id\":\"c1a81af6-4b69-4052-a1ad-285786eef52b\",\"ps\":\"alice\",\"hv\":true,\"vo\":4}],\"cv\":[],\"rv\":false,\"re\":false}\u0000"
    },
    {
        "type": "send",
            "time": 1739364958.729252,
            "opcode": 1,
            "data": "SEND\ndestination:/app/actions/d81c6857-1142-4593-91c9-d3a1ce10128c\ncontent-length:17\n\n{\"type\":\"reveal\"}\u0000"
    },
    {
        "type": "receive",
            "time": 1739364958.7794462,
            "opcode": 1,
            "data": "MESSAGE\ndestination:/topic/sessions/d81c6857-1142-4593-91c9-d3a1ce10128c\ncontent-type:application/json\nsubscription:sub-0\nmessage-id:86d527ea-961b-90ee-7fcc-cc55212690e9-26\ncontent-length:172\n\n{\"id\":\"d81c6857-1142-4593-91c9-d3a1ce10128c\",\"ps\":[{\"id\":\"c1a81af6-4b69-4052-a1ad-285786eef52b\",\"ps\":\"alice\",\"hv\":true,\"vo\":4}],\"cv\":[{\"va\":4,\"no\":1}],\"rv\":true,\"re\":false}\u0000"
    }
     ],
     */

    public static final String K_TYPE_SEND = "send";
    public static final String K_TYPE_RECEIVE = "receive";

    private String type; // send or receive
    private String startedDateTime; // Date ISO ms GMT e.g : 2024-05-03T14:30:42.271Z
    private String data;


    public String getStartedDateTime() {
        return startedDateTime;
    }

    public void setStartedDateTime(String startedDateTime) {
        this.startedDateTime = startedDateTime;
    }

    /**
     * @return data, null if not present.
     */
    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    /**
     * @return type
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("WebSocketMessage{");
        sb.append("type='").append(type).append('\'');
        sb.append(", startedDateTime='").append(startedDateTime).append('\'');
        sb.append(", data='").append(data).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebSocketMessage)) {
            return false;
        }
        WebSocketMessage that = (WebSocketMessage) o;
        return Objects.equals(startedDateTime, that.startedDateTime) && Objects.equals(data, that.data) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startedDateTime, data, type);
    }
}
