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

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Logger;

import io.github.vdaburon.jmeter.har.XmlJmx;

/**
 * Create xml blocs in the xml jmeter script for websockets, use samplers from "WebSocket Samplers by Peter Doornbosch"
 */

public class WebSocketPDoornboschXmlJmx {

    private static final Logger LOGGER = Logger.getLogger(WebSocketPDoornboschXmlJmx.class.getName());

    public static int createWebSocketPDoornboschTree(Document document, Element hashTreeForTc, String testname, String scheme, String host, int iPort, int httpSamplernum, WebSocketRequest webSocketRequest) throws URISyntaxException {
        /*
          <eu.luminis.jmeter.wssampler.OpenWebSocketSampler guiclass="eu.luminis.jmeter.wssampler.OpenWebSocketSamplerGui" testclass="eu.luminis.jmeter.wssampler.OpenWebSocketSampler" testname="027  WebSocket Open Connection /api/sockets/poker-planning" enabled="true">
            <boolProp name="TLS">true</boolProp>                                // 1
            <stringProp name="server">${V_HOST}</stringProp>                    // 2
            <stringProp name="port">${V_PORT}</stringProp>                      // 3
            <stringProp name="path">/api/sockets/poker-planning</stringProp>    // 4
            <stringProp name="connectTimeout">20000</stringProp>                // 5
            <stringProp name="readTimeout">6000</stringProp>                    // 6
          </eu.luminis.jmeter.wssampler.OpenWebSocketSampler>
         */
        Element eltOpenWebSocketSampler = document.createElement("eu.luminis.jmeter.wssampler.OpenWebSocketSampler");
        Attr attrGuiclass = document.createAttribute("guiclass");
        attrGuiclass.setValue("eu.luminis.jmeter.wssampler.OpenWebSocketSamplerGui");
        eltOpenWebSocketSampler.setAttributeNode(attrGuiclass);

        Attr attrTestclass = document.createAttribute("testclass");
        attrTestclass.setValue("eu.luminis.jmeter.wssampler.OpenWebSocketSampler");
        eltOpenWebSocketSampler.setAttributeNode(attrTestclass);

        URI url = new URI(webSocketRequest.getUrl());

        String testnameNew = String.format("%03d - WebSocket Open Connection %s", --httpSamplernum, url.getPath());
        Attr attrTestname = document.createAttribute("testname");
        attrTestname.setValue(testnameNew);
        eltOpenWebSocketSampler.setAttributeNode(attrTestname);

        Attr attrEnabled = document.createAttribute("enabled");
        attrEnabled.setValue("true");
        eltOpenWebSocketSampler.setAttributeNode(attrEnabled);

        String hostInter = "";
        hostInter = url.getHost();

        int defautPort = 443;
        boolean isTLS = true;
        if ("ws".equalsIgnoreCase(url.getScheme())) {
            defautPort = 80;
            isTLS = false;
        }

        if ("wss".equalsIgnoreCase(url.getScheme())) {
            defautPort = 443;
            isTLS = true;
        }

        String sPortInter = "";
        int port = url.getPort() == -1 ? defautPort : url.getPort();
        sPortInter = "" + port;

        Element boolProp1 = XmlJmx.createProperty(document, "boolProp", "TLS", Boolean.toString(isTLS));
        eltOpenWebSocketSampler.appendChild(boolProp1);

        Element stringProp2 = XmlJmx.createProperty(document, "stringProp", "server", hostInter);
        eltOpenWebSocketSampler.appendChild(stringProp2);

        Element stringProp3 = XmlJmx.createProperty(document, "stringProp", "port", sPortInter);
        eltOpenWebSocketSampler.appendChild(stringProp3);

        Element stringProp4 = XmlJmx.createProperty(document, "stringProp", "path", url.getPath());
        eltOpenWebSocketSampler.appendChild(stringProp4);

        Element stringProp5 = XmlJmx.createProperty(document, "stringProp", "connectTimeout", "20000");
        eltOpenWebSocketSampler.appendChild(stringProp5);

        Element stringProp6 = XmlJmx.createProperty(document, "stringProp", "readTimeout", "6000");
        eltOpenWebSocketSampler.appendChild(stringProp6);

        hashTreeForTc.appendChild(eltOpenWebSocketSampler);
        hashTreeForTc.appendChild(XmlJmx.createHashTree(document));

        int nbMessages = 0;
        if (webSocketRequest.getListWebSocketMessages() != null) {
            nbMessages = webSocketRequest.getListWebSocketMessages().size();
        }
        List<WebSocketMessage> lWebSocketMessages = webSocketRequest.getListWebSocketMessages();
        List<Integer> lListTypeExchange = webSocketRequest.getListTypeExchange();

        Element eltWebSocketSampler = null;
        for (int i = 0; i < nbMessages; i++) {

            eltWebSocketSampler = null;

            WebSocketMessage webSocketMessage = lWebSocketMessages.get(i);
            int typeExchange = lListTypeExchange.get(i).intValue();

            if (WebSocketRequest.K_WS_EXCHANGE_SEND_ONLY ==  typeExchange) {
                eltWebSocketSampler = createSingleWriteWebSocketSampler(document, ++httpSamplernum, webSocketMessage);
            }

            if (WebSocketRequest.K_WS_EXCHANGE_RECEIVE_ONLY ==  typeExchange) {
                eltWebSocketSampler = createSingleReadWebSocketSampler(document, ++httpSamplernum, webSocketMessage);
            }

            if (WebSocketRequest.K_WS_EXCHANGE_SEND_RECEIVE ==  typeExchange && WebSocketMessage.K_TYPE_SEND.equals(webSocketMessage.getType())) {
                eltWebSocketSampler = createRequestResponseWebSocketSampler(document, ++httpSamplernum, webSocketMessage);
            }

            if (eltWebSocketSampler != null) {
                hashTreeForTc.appendChild(eltWebSocketSampler);
                hashTreeForTc.appendChild(XmlJmx.createHashTree(document));
            }
        }

        Element eltCloseWebSocket = createcloseWebSocket(document, ++httpSamplernum);
        hashTreeForTc.appendChild(eltCloseWebSocket);
        hashTreeForTc.appendChild(XmlJmx.createHashTree(document));

        return httpSamplernum;
    }

    public static Element createRequestResponseWebSocketSampler(Document document, int httpSamplernum, WebSocketMessage webSocketMessage) {
        LOGGER.fine("Begin createRequestResponseWebSocketSampler");
        LOGGER.fine("param webSocketMessage=" + webSocketMessage);
        /*
            <eu.luminis.jmeter.wssampler.RequestResponseWebSocketSampler guiclass="eu.luminis.jmeter.wssampler.RequestResponseWebSocketSamplerGui" testclass="eu.luminis.jmeter.wssampler.RequestResponseWebSocketSampler" testname="028 WebSocket request-response Sampler CONNECT" enabled="true">
            <boolProp name="createNewConnection">false</boolProp>               // 1
            <boolProp name="TLS">true</boolProp>                                // 2
            <stringProp name="server">${V_HOST}</stringProp>                    // 3
            <stringProp name="port">${V_PORT}</stringProp>                      // 4
            <stringProp name="path">/api/sockets/poker-planning</stringProp>    // 5
            <stringProp name="connectTimeout">20000</stringProp>                // 6
            <stringProp name="payloadType">Text</stringProp>                    // 7
            <stringProp name="requestData">${V_TXT_CONNECT}</stringProp>        // 8
            <stringProp name="readTimeout">10000</stringProp>                   // 9
            <boolProp name="loadDataFromFile">false</boolProp>                  // 10
            <stringProp name="dataFile"></stringProp>                           // 11
          </eu.luminis.jmeter.wssampler.RequestResponseWebSocketSampler>
        */
        Element eltReqResWSSampler = document.createElement("eu.luminis.jmeter.wssampler.RequestResponseWebSocketSampler");
        Attr attrGuiclass = document.createAttribute("guiclass");
        attrGuiclass.setValue("eu.luminis.jmeter.wssampler.RequestResponseWebSocketSamplerGui");
        eltReqResWSSampler.setAttributeNode(attrGuiclass);

        Attr attrTestclass = document.createAttribute("testclass");
        attrTestclass.setValue("eu.luminis.jmeter.wssampler.RequestResponseWebSocketSampler");
        eltReqResWSSampler.setAttributeNode(attrTestclass);
        String testname = String.format("%03d - WebSocket request-response Sampler", httpSamplernum);
        Attr attrTestname = document.createAttribute("testname");
        attrTestname.setValue(testname);
        eltReqResWSSampler.setAttributeNode(attrTestname);

        Attr attrEnabled = document.createAttribute("enabled");
        attrEnabled.setValue("true");
        eltReqResWSSampler.setAttributeNode(attrEnabled);

        Element boolProp1 = XmlJmx.createProperty(document, "boolProp", "createNewConnection", "false");
        eltReqResWSSampler.appendChild(boolProp1);

        Element stringProp7 = XmlJmx.createProperty(document, "stringProp", "payloadType", UtilsWebSocket.getPayLoadType(webSocketMessage.getData()));
        eltReqResWSSampler.appendChild(stringProp7);

        Element stringProp8 = XmlJmx.createProperty(document, "stringProp", "requestData", UtilsWebSocket.filterData(webSocketMessage.getData()));
        eltReqResWSSampler.appendChild(stringProp8);

        Element stringProp9 = XmlJmx.createProperty(document, "stringProp", "readTimeout", "10000");
        eltReqResWSSampler.appendChild(stringProp9);

        Element boolProp10 = XmlJmx.createProperty(document, "boolProp", "loadDataFromFile", "false");
        eltReqResWSSampler.appendChild(boolProp10);
        LOGGER.fine("End   createRequestResponseWebSocketSampler");

        return eltReqResWSSampler;
    }

    public static Element createSingleReadWebSocketSampler(Document document, int httpSamplernum, WebSocketMessage webSocketMessage) {
        LOGGER.fine("Begin createSingleReadWebSocketSampler");
        LOGGER.fine("param webSocketMessage=" + webSocketMessage);
        /*
          <eu.luminis.jmeter.wssampler.SingleReadWebSocketSampler guiclass="eu.luminis.jmeter.wssampler.SingleReadWebSocketSamplerGui" testclass="eu.luminis.jmeter.wssampler.SingleReadWebSocketSampler" testname="032 WebSocket Single Read Sampler" enabled="false">
            <boolProp name="TLS">false</boolProp>                   // 1
            <stringProp name="server"></stringProp>                 // 2
            <stringProp name="port">80</stringProp>                 // 3
            <stringProp name="path"></stringProp>                   // 4
            <stringProp name="connectTimeout">20000</stringProp>    // 5
            <stringProp name="dataType">Text</stringProp>           // 6
            <boolProp name="createNewConnection">false</boolProp>   // 7
            <stringProp name="readTimeout">6000</stringProp>        // 8
            <boolProp name="optional">false</boolProp>              // 9
          </eu.luminis.jmeter.wssampler.SingleReadWebSocketSampler>
        */
        Element eltSingleReadWSSampler = document.createElement("eu.luminis.jmeter.wssampler.SingleReadWebSocketSampler");
        Attr attrGuiclass = document.createAttribute("guiclass");
        attrGuiclass.setValue("eu.luminis.jmeter.wssampler.SingleReadWebSocketSamplerGui");
        eltSingleReadWSSampler.setAttributeNode(attrGuiclass);

        Attr attrTestclass = document.createAttribute("testclass");
        attrTestclass.setValue("eu.luminis.jmeter.wssampler.SingleReadWebSocketSampler");
        eltSingleReadWSSampler.setAttributeNode(attrTestclass);
        String testname = String.format("%03d - WebSocket Single Read Sampler", httpSamplernum);
        Attr attrTestname = document.createAttribute("testname");
        attrTestname.setValue(testname);
        eltSingleReadWSSampler.setAttributeNode(attrTestname);

        Attr attrEnabled = document.createAttribute("enabled");
        attrEnabled.setValue("true");
        eltSingleReadWSSampler.setAttributeNode(attrEnabled);

        // create connection == false => no need connection infos
        Element stringProp6 = XmlJmx.createProperty(document, "stringProp", "dataType", "Text");
        eltSingleReadWSSampler.appendChild(stringProp6);

        Element boolProp7 = XmlJmx.createProperty(document, "boolProp", "createNewConnection", "false");
        eltSingleReadWSSampler.appendChild(boolProp7);

        Element stringProp8 = XmlJmx.createProperty(document, "stringProp", "readTimeout", "6000");
        eltSingleReadWSSampler.appendChild(stringProp8);

        Element boolProp9 = XmlJmx.createProperty(document, "boolProp", "optional", "false");
        eltSingleReadWSSampler.appendChild(boolProp9);
        LOGGER.fine("End   createSingleReadWebSocketSampler");

        return eltSingleReadWSSampler;
    }

    public static Element createSingleWriteWebSocketSampler(Document document, int httpSamplernum, WebSocketMessage webSocketMessage) {
        LOGGER.fine("Begin createSingleWriteWebSocketSampler");
        LOGGER.fine("param webSocketMessage=" + webSocketMessage);
        /*
           <eu.luminis.jmeter.wssampler.SingleWriteWebSocketSampler guiclass="eu.luminis.jmeter.wssampler.SingleWriteWebSocketSamplerGui" testclass="eu.luminis.jmeter.wssampler.SingleWriteWebSocketSampler" testname="033 WebSocket Single Write Sampler" enabled="false">
            <boolProp name="TLS">false</boolProp>                       // 1
            <stringProp name="server"></stringProp>                     // 2
            <stringProp name="port">80</stringProp>                     // 3
            <stringProp name="path"></stringProp>                       // 4
            <stringProp name="connectTimeout">20000</stringProp>        // 5
            <stringProp name="payloadType">Text</stringProp>            // 6
            <stringProp name="requestData">${V_TXT_WRITE}</stringProp>  // 7
            <boolProp name="createNewConnection">false</boolProp>       // 8
            <boolProp name="loadDataFromFile">false</boolProp>          // 9
            <stringProp name="dataFile"></stringProp>                   // 10
          </eu.luminis.jmeter.wssampler.SingleWriteWebSocketSampler>
        */
        Element eltSingleWriteWSSampler = document.createElement("eu.luminis.jmeter.wssampler.SingleWriteWebSocketSampler");
        Attr attrGuiclass = document.createAttribute("guiclass");
        attrGuiclass.setValue("eu.luminis.jmeter.wssampler.SingleWriteWebSocketSamplerGui");
        eltSingleWriteWSSampler.setAttributeNode(attrGuiclass);

        Attr attrTestclass = document.createAttribute("testclass");
        attrTestclass.setValue("eu.luminis.jmeter.wssampler.SingleWriteWebSocketSampler");
        eltSingleWriteWSSampler.setAttributeNode(attrTestclass);
        String testname = String.format("%03d - WebSocket Single Write Sampler", httpSamplernum);
        Attr attrTestname = document.createAttribute("testname");
        attrTestname.setValue(testname);
        eltSingleWriteWSSampler.setAttributeNode(attrTestname);

        Attr attrEnabled = document.createAttribute("enabled");
        attrEnabled.setValue("true");
        eltSingleWriteWSSampler.setAttributeNode(attrEnabled);

        // create connection == false => no need connection infos
        Element stringProp6 = XmlJmx.createProperty(document, "stringProp", "payloadType", UtilsWebSocket.getPayLoadType(webSocketMessage.getData()));
        eltSingleWriteWSSampler.appendChild(stringProp6);

        Element stringProp7 = XmlJmx.createProperty(document, "stringProp", "requestData", UtilsWebSocket.filterData(webSocketMessage.getData()));
        eltSingleWriteWSSampler.appendChild(stringProp7);

        Element boolProp8 = XmlJmx.createProperty(document, "boolProp", "createNewConnection", "false");
        eltSingleWriteWSSampler.appendChild(boolProp8);

        Element boolProp9 = XmlJmx.createProperty(document, "boolProp", "loadDataFromFile", "false");
        eltSingleWriteWSSampler.appendChild(boolProp9);

        LOGGER.fine("End   createSingleWriteWebSocketSampler");

        return eltSingleWriteWSSampler;
    }

    public static Element createcloseWebSocket(Document document, int httpSamplernum) {
    /*
          <eu.luminis.jmeter.wssampler.CloseWebSocketSampler guiclass="eu.luminis.jmeter.wssampler.CloseWebSocketSamplerGui" testclass="eu.luminis.jmeter.wssampler.CloseWebSocketSampler" testname="034 WebSocket Close" enabled="true">
            <stringProp name="statusCode">1000</stringProp>  // 1
            <stringProp name="readTimeout">6000</stringProp> // 2
          </eu.luminis.jmeter.wssampler.CloseWebSocketSampler>
     */
        Element eltCloseWSSampler = document.createElement("eu.luminis.jmeter.wssampler.CloseWebSocketSampler");
        Attr attrGuiclass = document.createAttribute("guiclass");
        attrGuiclass.setValue("eu.luminis.jmeter.wssampler.CloseWebSocketSamplerGui");
        eltCloseWSSampler.setAttributeNode(attrGuiclass);

        Attr attrTestclass = document.createAttribute("testclass");
        attrTestclass.setValue("eu.luminis.jmeter.wssampler.CloseWebSocketSampler");
        eltCloseWSSampler.setAttributeNode(attrTestclass);
        String testname = String.format("%03d - WebSocket Close", httpSamplernum);
        Attr attrTestname = document.createAttribute("testname");
        attrTestname.setValue(testname);
        eltCloseWSSampler.setAttributeNode(attrTestname);

        Attr attrEnabled = document.createAttribute("enabled");
        attrEnabled.setValue("true");
        eltCloseWSSampler.setAttributeNode(attrEnabled);

        Element stringProp1 = XmlJmx.createProperty(document, "stringProp", "statusCode", "1000");
        eltCloseWSSampler.appendChild(stringProp1);

        Element stringProp2 = XmlJmx.createProperty(document, "stringProp", "readTimeout", "6000");
        eltCloseWSSampler.appendChild(stringProp2);

        return eltCloseWSSampler;
    }

}
