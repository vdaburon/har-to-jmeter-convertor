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

import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import de.sstoehr.harreader.model.HarTiming;

import io.github.vdaburon.jmeter.har.Har2TestResultsXml;
import io.github.vdaburon.jmeter.har.Utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Create xml blocs in the xml jmeter recording file for websocket messages
 */

public class WebSocketPDoornboshResultXml {

    private static final Logger LOGGER = Logger.getLogger(WebSocketPDoornboshResultXml.class.getName());


    public static int createWsSample(Document document, Element eltTestResults, HarEntry harEntry, int num, WebSocketRequest webSocketRequest) throws URISyntaxException {
        HarRequest harRequest = harEntry.getRequest();
        HarResponse harResponse = harEntry.getResponse();

        Element eltSample = createEltSample(document, harEntry, num);
        Element eltRequestponseHeaders = Har2TestResultsXml.createRequestHeaders(document, harEntry.getRequest());
        eltSample.appendChild(eltRequestponseHeaders);
        Element eltResponseHeaders = Har2TestResultsXml.createResponseHeaders(document, harEntry.getResponse());
        eltSample.appendChild(eltResponseHeaders);

        Element eltcookies = Har2TestResultsXml.createCookies(document, harRequest);
        eltSample.appendChild(eltcookies);

        Element eltmethod = document.createElement("method");
        eltmethod = Har2TestResultsXml.addAttributeToElement(document, eltmethod, "class", "java.lang.String");
        String method = harRequest.getMethod().name();
        eltmethod.setTextContent(method);
        eltSample.appendChild(eltmethod);

        Element eltqueryString = document.createElement("queryString");
        eltqueryString = Har2TestResultsXml.addAttributeToElement(document, eltqueryString, "class", "java.lang.String");
        String queryString = "";
        if (harRequest.getQueryString().size() > 0) {
            queryString = new URI(harRequest.getUrl()).getQuery();
        }
        eltqueryString.setTextContent(queryString);
        eltSample.appendChild(eltqueryString);

        boolean isText = false;
        String dt_reponse = eltSample.getAttribute("dt");
        if ("text".equalsIgnoreCase(dt_reponse)) {
            isText = true;
        }
        Element eltResponseData = Har2TestResultsXml.createEltReponseData(document, harResponse, isText);
        eltSample.appendChild(eltResponseData);

        eltTestResults.appendChild(eltSample);

        int nbMessages = 0;
        if (webSocketRequest.getListWebSocketMessages() != null) {
            nbMessages = webSocketRequest.getListWebSocketMessages().size();
        }
        List<WebSocketMessage> lWebSocketMessages = webSocketRequest.getListWebSocketMessages();
        List<Integer> lListTypeExchange = webSocketRequest.getListTypeExchange();
        Element createEltSample = null;

        for (int i = 0; i < nbMessages; i++) {
            WebSocketMessage webSocketMessage = lWebSocketMessages.get(i);
            int typeExchange = lListTypeExchange.get(i).intValue();

            createEltSample = null;

            if (WebSocketRequest.K_WS_EXCHANGE_SEND_ONLY ==  typeExchange) {
                createEltSample = createWsEltSample(document, webSocketMessage /* send use */, webSocketMessage /* receive */, WebSocketRequest.K_WS_EXCHANGE_SEND_ONLY, ++num);
            }

            if (WebSocketRequest.K_WS_EXCHANGE_RECEIVE_ONLY ==  typeExchange) {
                createEltSample = createWsEltSample(document, webSocketMessage /* send */, webSocketMessage /* receive use */, WebSocketRequest.K_WS_EXCHANGE_RECEIVE_ONLY, ++num);
            }

            if (WebSocketRequest.K_WS_EXCHANGE_SEND_RECEIVE ==  typeExchange && WebSocketMessage.K_TYPE_RECEIVE.equals(webSocketMessage.getType())) {
                WebSocketMessage webSocketMessagePrevious = lWebSocketMessages.get((i-1));
                createEltSample = createWsEltSample(document, webSocketMessagePrevious /* send use */, webSocketMessage /* receive use */, WebSocketRequest.K_WS_EXCHANGE_SEND_RECEIVE, ++num);
            }

            if (createEltSample != null) {
                eltTestResults.appendChild(createEltSample);
            }
        }

        return num;

    }

    public static Element createWsEltSample(Document document, WebSocketMessage webSocketMessageSend, WebSocketMessage webSocketMessageReceive, int typeWsEchange, int num)  {
        LOGGER.fine("Begin createWsEltSample");
        LOGGER.fine("param : typeWsEchange = " + typeWsEchange + ", webSocketMessageSend = " + webSocketMessageSend + ", webSocketMessageReceive = " + webSocketMessageReceive);

        /*
        <sample t="0" it="0" lt="0" ct="0" ts="1740478589205" s="true" lb="003 - WebSocket request-response Sampler" rc="200" rm="OK" tn="Thead Group HAR Imported 1-1" dt="text" by="34" sby="13" ng="1" na="1" hn="ITEM-S113144">

        // https://jmeter.apache.org/usermanual/listeners.html#attributes
        /*
        Attribute	Content
        by	Bytes
        sby	Sent Bytes
        de	Data encoding
        dt	Data type
        ec	Error count (0 or 1, unless multiple samples are aggregated)
        hn	Hostname where the sample was generated
        it	Idle Time = time not spent sampling (milliseconds) (generally 0)
        lb	Label
        lt	Latency = time to initial response (milliseconds) - not all samplers support this
        ct	Connect Time = time to establish the connection (milliseconds) - not all samplers support this
        na	Number of active threads for all thread groups
        ng	Number of active threads in this group
        rc	Response Code (e.g. 200)
        rm	Response Message (e.g. OK)
        s	Success flag (true/false)
        sc	Sample count (1, unless multiple samples are aggregated)
        t	Elapsed time (milliseconds)
        tn	Thread Name
        ts	timeStamp (milliseconds since midnight Jan 1, 1970 UTC)
        varname	Value of the named variable
         */


        String t_time = "0";
        if (typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_SEND_RECEIVE) {
            try {
                long lTimeReceive = Utils.dateIsoFormatToTimeLong(webSocketMessageReceive.getStartedDateTime());
                long lTimeSend = Utils.dateIsoFormatToTimeLong(webSocketMessageSend.getStartedDateTime());
                long lDelta = lTimeReceive - lTimeSend;
                t_time = "" + lDelta;
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        if (typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_SEND_ONLY || typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_RECEIVE_ONLY) {
            t_time = "0";
        }

        String it_time = "0";
        String lt_time = "0";
        String ct_time = "0";

        String ts_time = "0";

        if (typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_SEND_RECEIVE) {
            try {
                long lTimeSend = Utils.dateIsoFormatToTimeLong(webSocketMessageSend.getStartedDateTime());
                ts_time = "" + lTimeSend;
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        if (typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_SEND_ONLY) {
            try {
                long lTimeSend = Utils.dateIsoFormatToTimeLong(webSocketMessageSend.getStartedDateTime());
                ts_time = "" + lTimeSend;
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        if (typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_RECEIVE_ONLY) {
            try {
                long lTimeReceive = Utils.dateIsoFormatToTimeLong(webSocketMessageReceive.getStartedDateTime());
                ts_time = "" + lTimeReceive;
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        String s_response = "true";
        String lb_label = "";
        if (typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_SEND_RECEIVE) {
            lb_label = String.format("%03d - " + "WebSocket request-response Sampler", num); // 003 WebSocket request-response Sampler
        }

        if (typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_SEND_ONLY) {
            lb_label = String.format("%03d - " + "WebSocket Single Write Sampler", num); // 003 WebSocket Single Write Sampler
        }

        if (typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_RECEIVE_ONLY) {
            lb_label = String.format("%03d - " + "WebSocket Single Read Sampler", num); // 003 WebSocket Single Write Sampler
        }

        String rc_response = "200";
        String rm_response = "OK";

        String dt_response = "text";

        String by_response = "0";
        if (typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_SEND_RECEIVE || typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_RECEIVE_ONLY) {
            by_response = "" + webSocketMessageReceive.getData().length();
        }


        String sby_request = "0";
        if (typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_SEND_RECEIVE || typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_SEND_ONLY) {
            sby_request = "" + webSocketMessageSend.getData().length();
        }

        String ng_count = "1";
        String na_count = "1";
        String hn = "browser";

        Element eltSample = document.createElement("sample");
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "t", t_time);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "it", it_time);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "lt", lt_time);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "ct", ct_time);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "ts", ts_time);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "s", s_response);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "lb", lb_label);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "rc", rc_response);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "rm", rm_response);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "dt", dt_response);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "by", by_response);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "sby", sby_request);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "ng", ng_count);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "na", na_count);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "hn", hn);

        /*
<sample t="21" it="0" lt="0" ct="0" ts="1740478589264" s="true" lb="008 - WebSocket request-response Sampler" rc="200" rm="OK" tn="Thead Group HAR Imported 1-1" dt="text" by="9" sby="13" ng="1" na="1" hn="ITEM-S113144">
  <responseHeader class="java.lang.String"></responseHeader>
  <requestHeader class="java.lang.String"></requestHeader>
  <responseData class="java.lang.String">4 = 0x4</responseData>
  <samplerData class="java.lang.String">Connect URL:
wss://echo.websocket.org:443/
(using existing connection)

Request data:
5 = 0x5
</samplerData>
         */

        Element eltResponseHeader = document.createElement("responseHeader");
        eltResponseHeader = Har2TestResultsXml.addAttributeToElement(document, eltResponseHeader, "class", "java.lang.String");
        eltSample.appendChild(eltResponseHeader);

        Element eltRequestHeader = document.createElement("requestHeader");
        eltRequestHeader = Har2TestResultsXml.addAttributeToElement(document, eltRequestHeader, "class", "java.lang.String");
        eltSample.appendChild(eltRequestHeader);

        Element eltresponseData = document.createElement("responseData");
        eltresponseData = Har2TestResultsXml.addAttributeToElement(document, eltresponseData, "class", "java.lang.String");
        if (typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_SEND_RECEIVE || typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_RECEIVE_ONLY) {
            eltresponseData.setTextContent(webSocketMessageReceive.getData());
        }
        eltSample.appendChild(eltresponseData);

        Element eltsamplerData = document.createElement("samplerData");
        eltsamplerData = Har2TestResultsXml.addAttributeToElement(document, eltsamplerData, "class", "java.lang.String");
        if (typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_SEND_RECEIVE || typeWsEchange == WebSocketRequest.K_WS_EXCHANGE_SEND_ONLY) {
            eltsamplerData.setTextContent(webSocketMessageSend.getData());
        }
        eltSample.appendChild(eltsamplerData);

        LOGGER.fine("End   createWsEltSample");

        return eltSample;

    }


    protected static Element createEltSample(Document document, HarEntry harEntry, int num) throws URISyntaxException {
        /*
        <sample t="0" it="0" lt="0" ct="0" ts="1740478589205" s="true" lb="003 - WebSocket request-response Sampler" rc="200" rm="OK" tn="Thead Group HAR Imported 1-1" dt="text" by="34" sby="13" ng="1" na="1" hn="ITEM-S113144">

        // https://jmeter.apache.org/usermanual/listeners.html#attributes
        /*
        Attribute	Content
        by	Bytes
        sby	Sent Bytes
        de	Data encoding
        dt	Data type
        ec	Error count (0 or 1, unless multiple samples are aggregated)
        hn	Hostname where the sample was generated
        it	Idle Time = time not spent sampling (milliseconds) (generally 0)
        lb	Label
        lt	Latency = time to initial response (milliseconds) - not all samplers support this
        ct	Connect Time = time to establish the connection (milliseconds) - not all samplers support this
        na	Number of active threads for all thread groups
        ng	Number of active threads in this group
        rc	Response Code (e.g. 200)
        rm	Response Message (e.g. OK)
        s	Success flag (true/false)
        sc	Sample count (1, unless multiple samples are aggregated)
        t	Elapsed time (milliseconds)
        tn	Thread Name
        ts	timeStamp (milliseconds since midnight Jan 1, 1970 UTC)
        varname	Value of the named variable
         */
        HarTiming harTimings = harEntry.getTimings();
        HarResponse harResponse = harEntry.getResponse();
        HarRequest harRequest = harEntry.getRequest();
        HarContent harContent = harResponse.getContent();

        String t_time = "" + harEntry.getTime();
        String it_time = "0";
        String lt_time = "" + harTimings.getWait();
        String ct_time = "" + harTimings.getConnect();
        String ts_time = "" + harEntry.getStartedDateTime().getTime();
        String s_response = "true";
        if (harResponse.getStatus() >= 400) {
            s_response = "false";
        }
        URI urlRequest = new URI(harRequest.getUrl());
        String lb_label = String.format("%03d - " + urlRequest.toString(), num); // 003 /gestdocqualif/servletStat

        String rc_response = "" + harResponse.getStatus();
        String rm_response = harResponse.getStatusText();

        String urlPath = urlRequest.getPath();
        String dt_response = Har2TestResultsXml.textFromMimeType(harContent.getMimeType(), urlPath);
        /* encoding
        "response": {
            "status": 200,
                    "statusText": "OK",
                    "httpVersion": "HTTP/1.1",
                    "headers": [
            {
                "name": "Content-Length",
                    "value": "1379"
            },
            {
                "name": "Content-Type",
                    "value": "text/html;charset=ISO-8859-1"
            }
            */
        String de_response = Har2TestResultsXml.responseEncoding(harResponse);

        String by_response = "" + harContent.getSize(); // Bytes receive
        String sby_request = "" + harRequest.getBodySize();	// Sent Bytes
        String sc_count = "1";
        String ec_count = "0";
        String ng_count = "0";
        String na_count = "0";
        String hn = "browser";

        Element eltSample = document.createElement("sample");
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "t", t_time);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "it", it_time);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "lt", lt_time);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "ct", ct_time);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "ts", ts_time);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "s", s_response);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "lb", lb_label);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "rc", rc_response);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "rm", rm_response);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "dt", dt_response);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "de", de_response);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "by", by_response);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "sby", sby_request);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "sc", sc_count);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "ec", ec_count);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "ng", ng_count);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "na", na_count);
        eltSample = Har2TestResultsXml.addAttributeToElement(document, eltSample, "hn", hn);

        return eltSample;

    }

}
