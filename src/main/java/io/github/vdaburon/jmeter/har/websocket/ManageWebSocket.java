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

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import io.github.vdaburon.jmeter.har.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * Find a websocket connection (ws or wss) and messages (send or receive) then create a webSocketRequest to save informations about the websocket exchanges.
 */

public class ManageWebSocket {

    private static final Logger LOGGER = Logger.getLogger(ManageWebSocket.class.getName());

    public static boolean isHarContainsWebSocketMessage(String harIn) {
        boolean isContainsWebSocketMessage = false;
        WebSocketRequest webSocketRequest = getWebSocketRequest(harIn);
        if (webSocketRequest != null && webSocketRequest.getListWebSocketMessages() != null && webSocketRequest.getListWebSocketMessages().size() > 0) {
            isContainsWebSocketMessage = true;
        }
        return isContainsWebSocketMessage;
    }

    public static boolean isHarContainsWebSocketMessage(WebSocketRequest webSocketRequest) {
        boolean isContainsWebSocketMessage = false;
        if (webSocketRequest != null && webSocketRequest.getListWebSocketMessages() != null && webSocketRequest.getListWebSocketMessages().size() > 0) {
            isContainsWebSocketMessage = true;
        }
        return isContainsWebSocketMessage;
    }

    public static WebSocketRequest getWebSocketRequest(String harIn) {
        WebSocketRequest webSocketRequest = null;

        File fileHarIn = new File(harIn);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fileHarIn);
        } catch (FileNotFoundException e) {
            LOGGER.warning("WARNING :" + e);
            return webSocketRequest;
        }

        Object document = Configuration.defaultConfiguration().jsonProvider().parse(fis, "UTF-8");

        //String version = JsonPath.read(document, "$.log.version");
        //System.out.println("version=" + version);

        try {

            ArrayList jsonArrayWsRequest = JsonPath.read(document, "$.log.entries[*].request[?(@.url =~ /^wss?:\\/\\/.*?/i)]"); // wss://...
            LOGGER.info("Number of websocket (ws or wss) connection(s) : " + jsonArrayWsRequest.size());

            if (jsonArrayWsRequest.size() >= 1) {
                webSocketRequest = new WebSocketRequest();
                LinkedHashMap lhmWs = (LinkedHashMap) jsonArrayWsRequest.get(0);
                webSocketRequest.setMethod((String) lhmWs.get("method"));
                webSocketRequest.setUrl((String) lhmWs.get("url"));

                ArrayList jsonArray = JsonPath.read(document, "$.log.entries[*]._webSocketMessages[*]");
                LOGGER.info("Number of websocket messages : " + jsonArray.size());

                List<WebSocketMessage> listWebSocketMessages = new ArrayList();

                for (int i = 0; i < jsonArray.size(); i++) {
                    // transforme the JSON array of LinkedHashMap to a List of WebSocketMessage
                    LinkedHashMap lhm = (LinkedHashMap) jsonArray.get(i);
                    LOGGER.fine("lhm=" + lhm);
                    WebSocketMessage webSocketMessage = new WebSocketMessage();
                    webSocketMessage.setData((String) lhm.get("data"));
                    String sType = (String) lhm.get("type");
                    webSocketMessage.setType(sType);
                    double dTimeMicro = (double) lhm.get("time"); //  "time": 1739364958.729252 = epoc_sec.micro_sec = double : 1.739364938770262E9
                    String dateIso = Utils.doubleEpocMicroToIsoFormat(dTimeMicro);
                    webSocketMessage.setStartedDateTime(dateIso);
                    listWebSocketMessages.add(webSocketMessage);
                }
                webSocketRequest.setListWebSocketMessages(listWebSocketMessages);
            }
        } catch (com.jayway.jsonpath.PathNotFoundException e) {
            // no webSocketMessage
            webSocketRequest = null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // close silently
                }
            }
        }

        if (webSocketRequest != null) {
            // at least one websocket connection and one message
            webSocketRequest = computeTypeExchange(webSocketRequest);
        }
        return webSocketRequest;
    }

    public static WebSocketRequest computeTypeExchange(WebSocketRequest webSocketRequest) {
        List<WebSocketMessage> listWebSocketMessages = webSocketRequest.getListWebSocketMessages();
        if (webSocketRequest != null && listWebSocketMessages != null) {
            int nbMessages = webSocketRequest.getListWebSocketMessages().size();
            List<Integer> listTypeExchange = new ArrayList<>();
            for (int i = 0; i < nbMessages; i++) {
                WebSocketMessage wsm = listWebSocketMessages.get(i);
                if (WebSocketMessage.K_TYPE_SEND.equals(wsm.getType())) {
                    listTypeExchange.add(new Integer(WebSocketRequest.K_WS_EXCHANGE_SEND_ONLY));
                }
                if (WebSocketMessage.K_TYPE_RECEIVE.equals(wsm.getType())) {
                    listTypeExchange.add(new Integer(WebSocketRequest.K_WS_EXCHANGE_RECEIVE_ONLY));
                }

                if (i > 0 && WebSocketMessage.K_TYPE_RECEIVE.equals(wsm.getType())) {
                    WebSocketMessage wsmPrevious = listWebSocketMessages.get((i - 1));
                    if (WebSocketMessage.K_TYPE_SEND.equals(wsmPrevious.getType())) {
                        listTypeExchange.set((i - 1), WebSocketRequest.K_WS_EXCHANGE_SEND_RECEIVE); // previous = SEND_RECEIVE
                        listTypeExchange.set(i, WebSocketRequest.K_WS_EXCHANGE_SEND_RECEIVE); // current = SEND_RECEIVE
                    }
                }
            }
            webSocketRequest.setListTypeExchange(listTypeExchange);
        }
        return webSocketRequest;
    }
}
