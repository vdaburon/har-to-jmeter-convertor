/*
 * Copyright 2024 Vincent DABURON
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

package io.github.vdaburon.jmeter.har;

// HAR parser
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarPage;
import de.sstoehr.harreader.model.HarPostData;
import de.sstoehr.harreader.model.HarPostDataParam;
import de.sstoehr.harreader.model.HarQueryParam;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HttpMethod;

import io.github.vdaburon.jmeter.har.common.TransactionInfo;
import io.github.vdaburon.jmeter.har.lrwr.ManageLrwr;
import io.github.vdaburon.jmeter.har.websocket.WebSocketPDoornboschXmlJmx;
import io.github.vdaburon.jmeter.har.websocket.WebSocketRequest;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class create a JMeter script jmx file from a HAR JSON file.
 * The jmx (JMeter Xml) file is a XML file, we use XML library to create the file.
 */

public class XmlJmx {

    protected static final String K_SCHEME = "scheme";
    protected static final String K_HOST = "host";
    protected static final String K_PORT = "port";
    private static final String K_JMETER_VERSION = "5.6.3";
    private static final String K_THREAD_GROUP_NAME = "Thead Group HAR Imported";
    private static final String K_VIEW_RESULT_TREE_COMMENT = "For The Recording XML File Created";
    private static final Logger LOGGER = Logger.getLogger(XmlJmx.class.getName());

    protected Document convertHarToJmxXml(Har har, long createNewTransactionAfterRequestMs, boolean isAddPause, boolean isRemoveCookie, boolean isRemoveCacheRequest, String urlFilterToInclude, String urlFilterToExclude, int pageStartNumber, int samplerStartNumber, List<TransactionInfo> listTransactionInfo, boolean isAddViewTreeForRecord, WebSocketRequest webSocketRequest, String recordXmlOut, String removeHeaders) throws ParserConfigurationException, URISyntaxException {

        Pattern patternUrlInclude = null;
        if (!urlFilterToInclude.isEmpty()) {
            patternUrlInclude = Pattern.compile(urlFilterToInclude);
        }

        Pattern patternUrlExclude = null;
        if (!urlFilterToExclude.isEmpty()) {
            patternUrlExclude = Pattern.compile(urlFilterToExclude);
        }

        String[] tabRemoveHeaders = null;
        if (!removeHeaders.isEmpty()) {
            tabRemoveHeaders = removeHeaders.split(",");
            for (int i = 0; i < tabRemoveHeaders.length; i++) {
                tabRemoveHeaders[i] = tabRemoveHeaders[i].trim();
            }
        }

        DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();

        DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();

        Document document = documentBuilder.newDocument();

        Element eltHashTreeAfterTestPlan = createJmxTestPlanAndTheadGroup(document);
        Element hashAfterThreadGroup = createHashTree(document);
        eltHashTreeAfterTestPlan.appendChild(hashAfterThreadGroup);

        if (isAddViewTreeForRecord && !recordXmlOut.isEmpty()) {
            Element eltHttpTestScriptRecorder = createHttpTestScriptRecorder(document);
            eltHashTreeAfterTestPlan.appendChild(eltHttpTestScriptRecorder);

            Element hashTreeAfterTestScriptRecorder = createHashTree(document);
            Element eltViewResultTree = createViewResultTree(document, recordXmlOut);
            hashTreeAfterTestScriptRecorder.appendChild(eltViewResultTree);
            Element hashTreeAfterViewResultTree = createHashTree(document);
            hashTreeAfterTestScriptRecorder.appendChild(hashTreeAfterViewResultTree);
            eltHashTreeAfterTestPlan.appendChild(hashTreeAfterTestScriptRecorder);
        }

        HashMap<String, String> hSchemeHostPort = getSchemeHostPortFirstPageOrUrl(har);
        String scheme = hSchemeHostPort.get(K_SCHEME);
        String host = hSchemeHostPort.get(K_HOST);
        String sPort = hSchemeHostPort.get(K_PORT);
        int iPort = Integer.parseInt(sPort);

        Element eltUdv = createUserDefinedVariable(document, hSchemeHostPort);
        hashAfterThreadGroup.appendChild(eltUdv);
        Element hashTreeEmpty1 = createHashTree(document);
        hashAfterThreadGroup.appendChild(hashTreeEmpty1);

        Element configTestElement = createConfigTestElement(document);
        hashAfterThreadGroup.appendChild(configTestElement);
        Element hashTreeEmpty2 = createHashTree(document);
        hashAfterThreadGroup.appendChild(hashTreeEmpty2);

        Element eltCookieManger = createCookieManager(document);
        hashAfterThreadGroup.appendChild(eltCookieManger);
        Element hashTreeEmpty3 = createHashTree(document);
        hashAfterThreadGroup.appendChild(hashTreeEmpty3);

        Element eltCacheManager = createCacheManager(document);
        hashAfterThreadGroup.appendChild(eltCacheManager);
        Element hashTreeEmpty4 = createHashTree(document);
        hashAfterThreadGroup.appendChild(hashTreeEmpty4);

        List<HarPage> lPages = har.getLog().getPages();

        if (lPages != null) {
            LOGGER.info("Number of page(s) in the HAR : " + lPages.size());
        }

        boolean isNoPage = false;

        if (lPages == null || (lPages != null && lPages.size() == 0)) {
            // no page, need to add one from first entry
            lPages = new ArrayList<HarPage>();
            HarPage harPage = new HarPage();
            harPage.setId("PAGE_00");
            List<HarEntry> lEntries = har.getLog().getEntries();
            if (lEntries != null && lEntries.size() > 0) {
                HarEntry harEntryInter = lEntries.get(0);
                harPage.setTitle(harEntryInter.getRequest().getUrl());
                harPage.setStartedDateTime(harEntryInter.getStartedDateTime());
            } else {
                throw new InvalidParameterException("No Page and No Entry, can't convert this har file");
            }
            lPages.add(harPage);
            isNoPage = true;
        }

        long timePageBefore = 0;
        long timeRequestBefore = 0;
        boolean isCreateNewTransactionAfterRequestMs = false;
        if (createNewTransactionAfterRequestMs > 0 && lPages.size() == 1)  {
            isCreateNewTransactionAfterRequestMs = true;
        }
        int pageNum = pageStartNumber;
        int httpSamplernum = samplerStartNumber;
        for (int p = 0; p < lPages.size(); p++) {
            HarPage pageInter = lPages.get(p);
            String pageId = pageInter.getId();
            String pageTitle = "";
            try {
                URI pageUrl = new URI(pageInter.getTitle());
                pageTitle = pageUrl.getPath();
            } catch (URISyntaxException ex) {
                // the title is not a valid uri, use directly the title
                pageTitle = pageInter.getTitle();
            }

            TransactionInfo transactionInfo = null;
            if (listTransactionInfo != null) {
                // Do we have a page  from lrwr Transaction or external cv file transaction info ?
                Date datePageStartedDateTime = pageInter.getStartedDateTime();
                String pageStartedDateTime = Utils.dateToIsoFormat(datePageStartedDateTime);
                transactionInfo = ManageLrwr.getTransactionInfoAroundDateTime(pageStartedDateTime,listTransactionInfo);
                if (transactionInfo != null) {
                    pageTitle = transactionInfo.getName();
                    LOGGER.info("Set the page title with the transaction name: " + pageTitle);
                }
            }
            String tcName = String.format("PAGE_%02d - " + pageTitle, pageNum); // PAGE_03 - /gestdocqualif/servletStat
            pageNum++;
            if (p == 0) {
                // first page
                timePageBefore = pageInter.getStartedDateTime().getTime();
            } else {
                long timeBetween2Pages = pageInter.getStartedDateTime().getTime() - timePageBefore;
                if (isAddPause && timeBetween2Pages > 0) {
                    Element eltTestAction = createTestActionPause(document, "Flow Control Action PAUSE", timeBetween2Pages);
                    hashAfterThreadGroup.appendChild(eltTestAction);
                    Element hashAfterTestAction = createHashTree(document);
                    hashAfterThreadGroup.appendChild(hashAfterTestAction);
                }
                timePageBefore = pageInter.getStartedDateTime().getTime();
            }
            Element eltTransactionController = createTransactionController(document, tcName);
            hashAfterThreadGroup.appendChild(eltTransactionController);
            Element hashTreeAfterTc = createHashTree(document);
            hashAfterThreadGroup.appendChild(hashTreeAfterTc);

            List<HarEntry> lEntries = har.getLog().getEntries();
            String currentUrl = "";

            for (int e = 0; e < lEntries.size(); e++) {
                HarEntry harEntryInter = lEntries.get(e);
                if (e == 0) {
                    timeRequestBefore = harEntryInter.getStartedDateTime().getTime();
                }
                long timeRequestStarted = harEntryInter.getStartedDateTime().getTime();
                long timeBetween2Requests = timeRequestStarted - timeRequestBefore;
                String pageref = harEntryInter.getPageref();
                if ((pageref != null && pageref.equals(pageId)) || isNoPage) {
                    HarRequest harRequest = harEntryInter.getRequest();
                    currentUrl = harRequest.getUrl();

                    boolean isAddThisRequest = true;

                    if (patternUrlInclude != null) {
                        // first filter include
                        Matcher matcher = patternUrlInclude.matcher(currentUrl);
                        isAddThisRequest = matcher.find();
                    }

                    if (isAddThisRequest && patternUrlExclude != null) {
                        // second filter exclude
                        Matcher matcher = patternUrlExclude.matcher(currentUrl);
                        isAddThisRequest = !matcher.find();
                    }

                    HashMap hAddictional = (HashMap<String, Object>) harEntryInter.getAdditional();
                    if (isAddThisRequest && hAddictional != null) {
                        String fromCache = (String) hAddictional.get("_fromCache");
                        if (fromCache != null) {
                            // this url content is in the browser cache (memory or disk) no need to create a new request
                            isAddThisRequest = false;
                        }
                    }

                    if (isAddThisRequest) {
                        URI url = new URI(harRequest.getUrl());
                        String samplerLabel = String.format("%03d " + url.getPath(), httpSamplernum); // 003 /gestdocqualif/servletStat
                        httpSamplernum++;
                        String sUrl = harRequest.getUrl();
                        String startUrl = sUrl.substring(0, Math.min(2, sUrl.length())); // ht or ws

                        if ("da".equalsIgnoreCase(startUrl)) { //data
                            // jmeter don't support data:image protocole
                            continue;
                        }

                        Element sampler = null;
                        boolean isWebSocket = false;
                        if ("ws".equalsIgnoreCase(startUrl) && webSocketRequest != null) { // ws or wss
                            // WebSocket
                            URI pageUrlFromRequest = new URI(harRequest.getUrl());
                            String tcNameFromRequest = String.format("PAGE_%02d - WebSocket " + pageUrlFromRequest.getPath(), pageNum); // PAGE_03 - /gestdocqualif/servletStat
                            pageNum++;
                            Element eltTransactionControllerNew = createTransactionController(document, tcNameFromRequest);
                            hashTreeAfterTc = createHashTree(document);
                            httpSamplernum = WebSocketPDoornboschXmlJmx.createWebSocketPDoornboschTree(document, hashTreeAfterTc, samplerLabel, scheme, host, iPort, httpSamplernum, webSocketRequest);
                            httpSamplernum++;
                            hashAfterThreadGroup.appendChild(eltTransactionControllerNew);
                            hashAfterThreadGroup.appendChild(hashTreeAfterTc);

                            continue; // websocket and messages was added finish for this sampler

                        } else {
                            sampler = createHttpSamplerProxy(document, samplerLabel, scheme, host, iPort, harRequest);
                        }
                        boolean isCreateNewTcFromTransactionInfo = false;
                        if (listTransactionInfo != null) {
                            // Do we have a page or sub page from lrwr Transaction or external cv file transaction info ?
                            Date dateEntryStartedDateTime = harEntryInter.getStartedDateTime();
                            String entryStartedDateTime = Utils.dateToIsoFormat(dateEntryStartedDateTime);
                            TransactionInfo transactionInfo2 = ManageLrwr.getTransactionInfoAroundDateTime(entryStartedDateTime,listTransactionInfo);
                            if (transactionInfo2 != null) {
                                isCreateNewTcFromTransactionInfo = true;

                                if (transactionInfo !=null) {
                                    if (transactionInfo2.getBeginDateTime().equals(transactionInfo.getBeginDateTime())) {
                                        // same transaction because the same begin timestamp, do nothing
                                        isCreateNewTcFromTransactionInfo = false;
                                    } else {
                                        isCreateNewTcFromTransactionInfo = true;
                                    }
                                }

                                if (isCreateNewTcFromTransactionInfo) {
                                        pageTitle = transactionInfo2.getName();
                                        LOGGER.info("Set the page title with the transaction name: " + pageTitle);
                                        String tcNameFromRequest = String.format("PAGE_%02d - " + pageTitle, pageNum);
                                        transactionInfo = transactionInfo2;
                                        pageNum++;
                                        Element eltTransactionControllerNew = createTransactionController(document, tcNameFromRequest);
                                        hashAfterThreadGroup.appendChild(eltTransactionControllerNew);
                                        hashTreeAfterTc = createHashTree(document);
                                        hashAfterThreadGroup.appendChild(hashTreeAfterTc);
                                }
                            }
                        }

                        if (isCreateNewTransactionAfterRequestMs && timeBetween2Requests > createNewTransactionAfterRequestMs) {
                            if (isAddPause) {
                                Element eltTestAction = createTestActionPause(document, "Flow Control Action PAUSE", timeBetween2Requests);
                                hashAfterThreadGroup.appendChild(eltTestAction);
                                Element hashAfterTestAction = createHashTree(document);
                                hashAfterThreadGroup.appendChild(hashAfterTestAction);
                            }

                            if (!isCreateNewTcFromTransactionInfo) {
                                URI pageUrlFromRequest = new URI(harRequest.getUrl());
                                String tcNameFromRequest = String.format("PAGE_%02d - " + pageUrlFromRequest.getPath(), pageNum); // PAGE_03 - /gestdocqualif/servletStat
                                pageNum++;
                                Element eltTransactionControllerNew = createTransactionController(document, tcNameFromRequest);
                                hashAfterThreadGroup.appendChild(eltTransactionControllerNew);
                                hashTreeAfterTc = createHashTree(document);
                                hashAfterThreadGroup.appendChild(hashTreeAfterTc);
                            }
                        }
                        timeRequestBefore = timeRequestStarted;

                        hashTreeAfterTc.appendChild(sampler);
                        Element hashTreeAfterHttpSampler = createHashTree(document);
                        hashTreeAfterTc.appendChild(hashTreeAfterHttpSampler);

                        Element headers = createHeaderManager(document, harRequest, isRemoveCookie, isRemoveCacheRequest, tabRemoveHeaders);
                        hashTreeAfterHttpSampler.appendChild(headers);
                        Element hashTreeAfterHeaders = createHashTree(document);
                        hashTreeAfterHttpSampler.appendChild(hashTreeAfterHeaders);
                    } else {
                        // isAddThisRequest == false
                        LOGGER.fine("This url is filtred : " + currentUrl);
                    }
                }
            }
        }
        LOGGER.info("JMX file contains " + httpSamplernum + " HTTPSamplerProxy");
        return document;
    }

    protected Element createHttpTestScriptRecorder(Document document) {
        /*
        <ProxyControl guiclass="ProxyControlGui" testclass="ProxyControl" testname="HTTP(S) Test Script Recorder" enabled="false">
           1 <stringProp name="ProxyControlGui.port">8888</stringProp>
           2 <collectionProp name="ProxyControlGui.exclude_list"/>
           3 <collectionProp name="ProxyControlGui.include_list"/>
           4 <boolProp name="ProxyControlGui.capture_http_headers">true</boolProp>
           5 <intProp name="ProxyControlGui.grouping_mode">0</intProp>
           6 <boolProp name="ProxyControlGui.add_assertion">false</boolProp>
           7 <stringProp name="ProxyControlGui.sampler_type_name"></stringProp>
           8 <boolProp name="ProxyControlGui.sampler_redirect_automatically">false</boolProp>
           9 <boolProp name="ProxyControlGui.sampler_follow_redirects">true</boolProp>
          10 <boolProp name="ProxyControlGui.use_keepalive">true</boolProp>
          11 <boolProp name="ProxyControlGui.detect_graphql_request">true</boolProp>
          12 <boolProp name="ProxyControlGui.sampler_download_images">false</boolProp>
          13 <intProp name="ProxyControlGui.proxy_http_sampler_naming_mode">0</intProp>
          14 <stringProp name="ProxyControlGui.default_encoding"></stringProp>
          15 <stringProp name="ProxyControlGui.proxy_prefix_http_sampler_name"></stringProp>
          16 <stringProp name="ProxyControlGui.proxy_pause_http_sampler"></stringProp>
          17 <boolProp name="ProxyControlGui.notify_child_sl_filtered">false</boolProp>
          18 <boolProp name="ProxyControlGui.regex_match">false</boolProp>
          19 <stringProp name="ProxyControlGui.content_type_include"></stringProp>
          20 <stringProp name="ProxyControlGui.content_type_exclude"></stringProp>
      </ProxyControl>
      <hashTree>
         */
        Element eltProxyControl = document.createElement("ProxyControl");
        Attr attrPcguiclass = document.createAttribute("guiclass");
        attrPcguiclass.setValue("ProxyControlGui");
        eltProxyControl.setAttributeNode(attrPcguiclass);

        Attr attrPctestclass = document.createAttribute("testclass");
        attrPctestclass.setValue("ProxyControl");
        eltProxyControl.setAttributeNode(attrPctestclass);

        Attr attrPctestname = document.createAttribute("testname");
        attrPctestname.setValue("HTTP(S) Test Script Recorder");
        eltProxyControl.setAttributeNode(attrPctestname);

        Attr attrPcenabled = document.createAttribute("enabled");
        attrPcenabled.setValue("false");
        eltProxyControl.setAttributeNode(attrPcenabled);

        Element stringProp1 = createProperty(document, "stringProp", "ProxyControlGui.port", "8888");
        eltProxyControl.appendChild(stringProp1);
        Element collectionProp2 = createProperty(document, "collectionProp", "ProxyControlGui.exclude_list", null);
        eltProxyControl.appendChild(collectionProp2);
        Element collectionProp3 = createProperty(document, "collectionProp", "ProxyControlGui.include_list", null);
        eltProxyControl.appendChild(collectionProp3);
        Element boolProp4 = createProperty(document, "boolProp", "ProxyControlGui.capture_http_headers", "true");
        eltProxyControl.appendChild(boolProp4);
        Element intProp5 = createProperty(document, "boolProp", "ProxyControlGui.grouping_mode", "0");
        eltProxyControl.appendChild(intProp5);
        Element boolProp6 = createProperty(document, "boolProp", "ProxyControlGui.add_assertion", "false");
        eltProxyControl.appendChild(boolProp6);
        Element stringProp7 = createProperty(document, "stringProp", "ProxyControlGui.sampler_type_name", null);
        eltProxyControl.appendChild(stringProp7);
        Element boolProp8 = createProperty(document, "boolProp", "ProxyControlGui.sampler_redirect_automatically", "false");
        eltProxyControl.appendChild(boolProp8);
        Element boolProp9 = createProperty(document, "boolProp", "ProxyControlGui.sampler_follow_redirects", "true");
        eltProxyControl.appendChild(boolProp9);
        Element boolProp10 = createProperty(document, "boolProp", "ProxyControlGui.use_keepalive", "true");
        eltProxyControl.appendChild(boolProp10);
        Element boolProp11 = createProperty(document, "boolProp", "ProxyControlGui.detect_graphql_request", "true");
        eltProxyControl.appendChild(boolProp11);
        Element boolProp12 = createProperty(document, "boolProp", "ProxyControlGui.sampler_download_images", "false");
        eltProxyControl.appendChild(boolProp12);
        Element stringProp13 = createProperty(document, "intProp", "ProxyControlGui.proxy_http_sampler_naming_mode", "0");
        eltProxyControl.appendChild(stringProp13);
        Element stringProp14 = createProperty(document, "stringProp", "ProxyControlGui.default_encoding", null);
        eltProxyControl.appendChild(stringProp14);
        Element stringProp15 = createProperty(document, "stringProp", "ProxyControlGui.proxy_prefix_http_sampler_name", null);
        eltProxyControl.appendChild(stringProp15);
        Element stringProp16 = createProperty(document, "stringProp", "ProxyControlGui.proxy_pause_http_sampler", null);
        eltProxyControl.appendChild(stringProp16);
        Element boolProp17 = createProperty(document, "boolProp", "ProxyControlGui.notify_child_sl_filtered", "false");
        eltProxyControl.appendChild(boolProp17);
        Element boolProp18 = createProperty(document, "boolProp", "ProxyControlGui.regex_match", "false");
        eltProxyControl.appendChild(boolProp18);
        Element stringProp19 = createProperty(document, "stringProp", "ProxyControlGui.content_type_include", null);
        eltProxyControl.appendChild(stringProp19);
        Element stringProp20 = createProperty(document, "stringProp", "ProxyControlGui.content_type_exclude", null);
        eltProxyControl.appendChild(stringProp20);

        return eltProxyControl;
    }

    protected Element createViewResultTree(Document document, String recordXmlOut) {
        /*
        <ResultCollector guiclass="ViewResultsFullVisualizer" testclass="ResultCollector" testname="View Results Tree">
          <boolProp name="ResultCollector.error_logging">false</boolProp>
          <objProp>
            <name>saveConfig</name>
            <value class="SampleSaveConfiguration">
              <time>true</time>
              <latency>true</latency>
              <timestamp>true</timestamp>
              <success>true</success>
              <label>true</label>
              <code>true</code>
              <message>true</message>
              <threadName>true</threadName>
              <dataType>true</dataType>
              <encoding>false</encoding>
              <assertions>true</assertions>
              <subresults>true</subresults>
              <responseData>false</responseData>
              <samplerData>false</samplerData>
              <xml>false</xml>
              <fieldNames>true</fieldNames>
              <responseHeaders>false</responseHeaders>
              <requestHeaders>false</requestHeaders>
              <responseDataOnError>false</responseDataOnError>
              <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
              <assertionsResultsToSave>0</assertionsResultsToSave>
              <bytes>true</bytes>
              <sentBytes>true</sentBytes>
              <url>true</url>
              <hostname>true</hostname>
              <threadCounts>true</threadCounts>
              <idleTime>true</idleTime>
              <connectTime>true</connectTime>
            </value>
          </objProp>
          <stringProp name="filename">C:/jmeter/har/record.xml</stringProp>
        </ResultCollector>
        <hashTree/>
       */
        Element eltResultCollector = document.createElement("ResultCollector");
        Attr attrRcguiclass = document.createAttribute("guiclass");
        attrRcguiclass.setValue("ViewResultsFullVisualizer");
        eltResultCollector.setAttributeNode(attrRcguiclass);

        Attr attrRctestclass = document.createAttribute("testclass");
        attrRctestclass.setValue("ResultCollector");
        eltResultCollector.setAttributeNode(attrRctestclass);

        Attr attrRctestname = document.createAttribute("testname");
        attrRctestname.setValue("View Results Tree");
        eltResultCollector.setAttributeNode(attrRctestname);


        Element boolProp1 = createProperty(document, "boolProp", "ResultCollector.error_logging", "false");
        eltResultCollector.appendChild(boolProp1);
        Element stringProp2 = createProperty(document, "stringProp", "filename", recordXmlOut);
        eltResultCollector.appendChild(stringProp2);
        Element stringProp3 = createProperty(document, "stringProp", "TestPlan.comments", K_VIEW_RESULT_TREE_COMMENT);
        eltResultCollector.appendChild(stringProp3);


        return eltResultCollector;
    }

    protected Element createJmxTestPlanAndTheadGroup(Document document) {
/*

<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true">
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.tearDown_on_shutdown">false</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="HAR Imported" enabled="false">
        <stringProp name="ThreadGroup.num_threads">1</stringProp>
        <stringProp name="ThreadGroup.ramp_time">1</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" enabled="true">
          <stringProp name="LoopController.loops">1</stringProp>
          <boolProp name="LoopController.continue_forever">false</boolProp>
        </elementProp>
        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
        <boolProp name="ThreadGroup.delayedStart">false</boolProp>
        <boolProp name="ThreadGroup.scheduler">false</boolProp>
        <stringProp name="ThreadGroup.duration"></stringProp>
        <stringProp name="ThreadGroup.delay"></stringProp>
        <boolProp name="ThreadGroup.same_user_on_next_iteration">true</boolProp>
      </ThreadGroup>

 */
        // <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.2">
        Element root = document.createElement("jmeterTestPlan");
        document.appendChild(root);
        Attr attrversion = document.createAttribute("version");
        attrversion.setValue("1.2");
        root.setAttributeNode(attrversion);

        Attr attrproperties = document.createAttribute("properties");
        attrproperties.setValue("5.0");
        root.setAttributeNode(attrproperties);

        Attr attrjmeter = document.createAttribute("jmeter");
        attrjmeter.setValue(K_JMETER_VERSION);
        root.setAttributeNode(attrjmeter);

        //   <hashTree>
        Element eltRoothashTree = createHashTree(document);
        root.appendChild(eltRoothashTree);

        // <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan" enabled="true">
        Element eltTestPlan = document.createElement("TestPlan");
        Attr attrTpguiclass = document.createAttribute("guiclass");
        attrTpguiclass.setValue("TestPlanGui");
        eltTestPlan.setAttributeNode(attrTpguiclass);

        Attr attrTptestclass = document.createAttribute("testclass");
        attrTptestclass.setValue("TestPlanGui");
        eltTestPlan.setAttributeNode(attrTptestclass);

        Attr attrTptestname = document.createAttribute("testname");
        attrTptestname.setValue("Test Plan");
        eltTestPlan.setAttributeNode(attrTptestname);

        Attr attrTpenabled = document.createAttribute("enabled");
        attrTpenabled.setValue("true");
        eltTestPlan.setAttributeNode(attrTpenabled);

        /*
       <stringProp name="TestPlan.comments">This test plan was created by io.github.vdaburon:har-to-jmeter-convertor v1.0</stringProp>
       <boolProp name="TestPlan.functional_mode">false</boolProp>
       <boolProp name="TestPlan.tearDown_on_shutdown">false</boolProp>
       <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>

         */
        Element eltBoolProp1 = createProperty(document, "boolProp", "TestPlan.functional_mode", "false");
        eltTestPlan.appendChild(eltBoolProp1);

        Element eltBoolProp2 = createProperty(document, "boolProp", "TestPlan.tearDown_on_shutdown", "false");
        eltTestPlan.appendChild(eltBoolProp2);

        Element eltBoolProp3 = createProperty(document, "boolProp", "TestPlan.serialize_threadgroups", "false");
        eltTestPlan.appendChild(eltBoolProp3);


        /*
        <elementProp name="TestPlan.user_defined_variables" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
      */
        Element eltTpElementProp = createElementProp(document, "TestPlan.user_defined_variables", "Arguments", "ArgumentsPanel", "Arguments", "User Defined Variables");
        eltTestPlan.appendChild(eltTpElementProp);

        Element eltTpCollectionProp = document.createElement("collectionProp");
        Attr attrTpCollectionPropname = document.createAttribute("name");
        attrTpCollectionPropname.setValue("Arguments.arguments");
        eltTpCollectionProp.setAttributeNode(attrTpCollectionPropname);

        eltTpElementProp.appendChild(eltTpCollectionProp);

        /*
        <stringProp name="TestPlan.comments"></stringProp>
      <stringProp name="TestPlan.user_define_classpath"></stringProp>
         */
        String versionComment = "This test plan was created by io.github.vdaburon:har-to-jmeter-convertor Version " + HarForJMeter.APPLICATION_VERSION;
        Element eltTpStringProp1 = createProperty(document, "stringProp", "TestPlan.comments", versionComment);
        eltTestPlan.appendChild(eltTpStringProp1);

        Element eltTpStringProp2 = createProperty(document, "stringProp", "TestPlan.user_define_classpath", null);
        eltTestPlan.appendChild(eltTpStringProp2);

        eltRoothashTree.appendChild(eltTestPlan);

        Element eltHashTreeAfterTestPlan = createHashTree(document);
        eltRoothashTree.appendChild(eltHashTreeAfterTestPlan);

        Element eltThreadGroup = createThreadGroup(document, K_THREAD_GROUP_NAME);
        eltHashTreeAfterTestPlan.appendChild(eltThreadGroup);
        return eltHashTreeAfterTestPlan;
    }

    protected Element createThreadGroup(Document document, String groupName) {
    /*
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Thead Group HAR Imported" enabled="false">
        <stringProp name="ThreadGroup.num_threads">1</stringProp>
        <stringProp name="ThreadGroup.ramp_time">1</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" enabled="true">
          <stringProp name="LoopController.loops">1</stringProp>
          <boolProp name="LoopController.continue_forever">false</boolProp>
        </elementProp>
        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
        <boolProp name="ThreadGroup.delayedStart">false</boolProp>
        <boolProp name="ThreadGroup.scheduler">false</boolProp>
        <stringProp name="ThreadGroup.duration"></stringProp>
        <stringProp name="ThreadGroup.delay"></stringProp>
        <boolProp name="ThreadGroup.same_user_on_next_iteration">true</boolProp>
      </ThreadGroup>

     */
        Element eltThreadGroup = document.createElement("ThreadGroup");
        Attr attrThreadGroupguiclass = document.createAttribute("guiclass");
        attrThreadGroupguiclass.setValue("ThreadGroupGui");
        eltThreadGroup.setAttributeNode(attrThreadGroupguiclass);

        Attr attrThreadGrouptestclass = document.createAttribute("testclass");
        attrThreadGrouptestclass.setValue("ThreadGroup");
        eltThreadGroup.setAttributeNode(attrThreadGrouptestclass);

        Attr attrThreadGrouptestname = document.createAttribute("testname");
        attrThreadGrouptestname.setValue(groupName);
        eltThreadGroup.setAttributeNode(attrThreadGrouptestname);

        Attr attrThreadGroupenabled = document.createAttribute("enabled");
        attrThreadGroupenabled.setValue("true");
        eltThreadGroup.setAttributeNode(attrThreadGroupenabled);

        Element eltTgStringProp1 = createProperty(document, "stringProp", "ThreadGroup.num_threads", "1");
        eltThreadGroup.appendChild(eltTgStringProp1);

        Element eltTgStringProp2 = createProperty(document, "stringProp", "ThreadGroup.ramp_time", "1");
        eltThreadGroup.appendChild(eltTgStringProp2);

        /*
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" enabled="true">
          <stringProp name="LoopController.loops">1</stringProp>
          <boolProp name="LoopController.continue_forever">false</boolProp>
        </elementProp>
         */
        Element eltTgElementProp = createElementProp(document, "ThreadGroup.main_controller", "LoopController", "LoopControlPanel", "LoopController", "");
        Element eltEltPropStringProp1 = createProperty(document, "stringProp", "LoopController.loops", "1");
        eltTgElementProp.appendChild(eltEltPropStringProp1);

        Element eltEltPropBoolProp2 = createProperty(document, "boolProp", "LoopController.continue_forever", "false");
        eltTgElementProp.appendChild(eltEltPropBoolProp2);

        eltThreadGroup.appendChild(eltTgElementProp);

        /*
         <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
        <boolProp name="ThreadGroup.delayedStart">false</boolProp>
        <boolProp name="ThreadGroup.scheduler">false</boolProp>
        <stringProp name="ThreadGroup.duration"></stringProp>
        <stringProp name="ThreadGroup.delay"></stringProp>
        <boolProp name="ThreadGroup.same_user_on_next_iteration">true</boolProp>
         */
        Element eltTgStringProp3 = createProperty(document, "stringProp", "ThreadGroup.on_sample_error", "continue");
        eltThreadGroup.appendChild(eltTgStringProp3);

        Element eltTgBoolProp4 = createProperty(document, "boolProp", "ThreadGroup.delayedStart", "false");
        eltThreadGroup.appendChild(eltTgBoolProp4);

        Element eltTgBoolProp5 = createProperty(document, "boolProp", "ThreadGroup.scheduler", "false");
        eltThreadGroup.appendChild(eltTgBoolProp5);

        Element eltTgStringProp6 = createProperty(document, "stringProp", "ThreadGroup.duration", "");
        eltThreadGroup.appendChild(eltTgStringProp6);

        Element eltTgStringProp7 = createProperty(document, "stringProp", "ThreadGroup.delay", "");
        eltThreadGroup.appendChild(eltTgStringProp7);

        Element eltTgBoolProp8 = createProperty(document, "boolProp", "ThreadGroup.same_user_on_next_iteration", "true");
        eltThreadGroup.appendChild(eltTgBoolProp8);

        return eltThreadGroup;
    }

    protected Element createCookieManager(Document document) {

        /*
        <CookieManager guiclass="CookiePanel" testclass="CookieManager" testname="HTTP Cookie Manager" enabled="true">
          <collectionProp name="CookieManager.cookies"/>
          <boolProp name="CookieManager.clearEachIteration">true</boolProp>
          <boolProp name="CookieManager.controlledByThreadGroup">false</boolProp>
        </CookieManager>
         */
        Element eltCookieManager = document.createElement("CookieManager");
        Attr attrCookieManagerguiclass = document.createAttribute("guiclass");
        attrCookieManagerguiclass.setValue("CookiePanel");
        eltCookieManager.setAttributeNode(attrCookieManagerguiclass);

        Attr attrCookieManagertestclass = document.createAttribute("testclass");
        attrCookieManagertestclass.setValue("CookieManager");
        eltCookieManager.setAttributeNode(attrCookieManagertestclass);

        Attr attrCookieManagertestname = document.createAttribute("testname");
        attrCookieManagertestname.setValue("HTTP Cookie Manager");
        eltCookieManager.setAttributeNode(attrCookieManagertestname);

        Attr attrCookieManagerenabled = document.createAttribute("enabled");
        attrCookieManagerenabled.setValue("true");
        eltCookieManager.setAttributeNode(attrCookieManagerenabled);

        Element eltCollectionProp1 = createProperty(document, "collectionProp", "CookieManager.cookies", null);
        eltCookieManager.appendChild(eltCollectionProp1);
        Element eltBoolProp1 = createProperty(document, "boolProp", "CookieManager.clearEachIteration", "true");
        eltCookieManager.appendChild(eltBoolProp1);
        Element eltBoolProp2 = createProperty(document, "boolProp", "CookieManager.controlledByThreadGroup", "false");
        eltCookieManager.appendChild(eltBoolProp2);

        return eltCookieManager;
    }

    protected Element createCacheManager(Document document) {
        /*
        <CacheManager guiclass="CacheManagerGui" testclass="CacheManager" testname="HTTP Cache Manager" enabled="true">
          <boolProp name="clearEachIteration">true</boolProp>
          <boolProp name="useExpires">true</boolProp>
          <boolProp name="CacheManager.controlledByThread">false</boolProp>
        </CacheManager>
        */
        Element eltCacheManager = document.createElement("CacheManager");
        Attr attrCacheManagerguiclass = document.createAttribute("guiclass");
        attrCacheManagerguiclass.setValue("CacheManagerGui");
        eltCacheManager.setAttributeNode(attrCacheManagerguiclass);

        Attr attrCacheManagertestclass = document.createAttribute("testclass");
        attrCacheManagertestclass.setValue("CacheManager");
        eltCacheManager.setAttributeNode(attrCacheManagertestclass);

        Attr attrCacheManagertestname = document.createAttribute("testname");
        attrCacheManagertestname.setValue("HTTP Cache Manager");
        eltCacheManager.setAttributeNode(attrCacheManagertestname);

        Attr attrCacheManagerenabled = document.createAttribute("enabled");
        attrCacheManagerenabled.setValue("true");
        eltCacheManager.setAttributeNode(attrCacheManagerenabled);

        Element eltBoolProp1 = createProperty(document, "boolProp", "clearEachIteration", "true");
        eltCacheManager.appendChild(eltBoolProp1);
        Element eltBoolProp2 = createProperty(document, "boolProp", "useExpires", "true");
        eltCacheManager.appendChild(eltBoolProp2);
        Element eltBoolProp3 = createProperty(document, "boolProp", "CacheManager.controlledByThread", "false");
        eltCacheManager.appendChild(eltBoolProp3);
        return eltCacheManager;
    }

    protected Element createTransactionController(Document document, String testname) {
        /*
        <TransactionController guiclass="TransactionControllerGui" testclass="TransactionController" testname="SC03_P01_ACCUEIL" enabled="true">
          <boolProp name="TransactionController.parent">false</boolProp>
          <boolProp name="TransactionController.includeTimers">false</boolProp>
        </TransactionController>
         */
        Element eltTransactionController = document.createElement("TransactionController");
        Attr attrTransactionControllerguiclass = document.createAttribute("guiclass");
        attrTransactionControllerguiclass.setValue("TransactionControllerGui");
        eltTransactionController.setAttributeNode(attrTransactionControllerguiclass);

        Attr attrTransactionControllertestclass = document.createAttribute("testclass");
        attrTransactionControllertestclass.setValue("TransactionController");
        eltTransactionController.setAttributeNode(attrTransactionControllertestclass);

        Attr attrTransactionControllertestname = document.createAttribute("testname");
        attrTransactionControllertestname.setValue(testname);
        eltTransactionController.setAttributeNode(attrTransactionControllertestname);

        Attr attrTransactionControllerenabled = document.createAttribute("enabled");
        attrTransactionControllerenabled.setValue("true");
        eltTransactionController.setAttributeNode(attrTransactionControllerenabled);

        Element eltBoolProp1 = createProperty(document, "boolProp", "TransactionController.parent", "false");
        eltTransactionController.appendChild(eltBoolProp1);
        Element eltBoolProp2 = createProperty(document, "boolProp", "TransactionController.includeTimers", "false");
        eltTransactionController.appendChild(eltBoolProp2);

        return eltTransactionController;
    }

    public static Element createHashTree(Document document) {
        Element eltHashTree = document.createElement("hashTree");
        return eltHashTree;
    }

    public static Element createProperty(Document document, String elementProp, String parameterNameValue, String elementValue) {
        Element eltProperty = document.createElement(elementProp); // boolProp or stringProp or intProp
        if (parameterNameValue != null) {
            Attr attrPropertyname = document.createAttribute("name");
            attrPropertyname.setValue(parameterNameValue);
            eltProperty.setAttributeNode(attrPropertyname);
        }

        if (elementValue != null) {
            eltProperty.setTextContent(elementValue);
        }
        return eltProperty;
    }

    protected Element createElementProp(Document document, String nameValue, String elementTypeValue, String guiClassValue, String testClassValue, String testNameValue) {
        Element eltElementProp = document.createElement("elementProp");
        Attr attrTpElementPropname = document.createAttribute("name");
        attrTpElementPropname.setValue(nameValue);
        eltElementProp.setAttributeNode(attrTpElementPropname);

        Attr attrTpElementPropelementType = document.createAttribute("elementType");
        attrTpElementPropelementType.setValue(elementTypeValue);
        eltElementProp.setAttributeNode(attrTpElementPropelementType);

        if (guiClassValue != null) {
            Attr attrTpElementPropguiclass = document.createAttribute("guiclass");
            attrTpElementPropguiclass.setValue(guiClassValue);
            eltElementProp.setAttributeNode(attrTpElementPropguiclass);
        }

        if (testClassValue != null) {
            Attr attrTpElementProptestclass = document.createAttribute("testclass");
            attrTpElementProptestclass.setValue(testClassValue);
            eltElementProp.setAttributeNode(attrTpElementProptestclass);
        }

        if (testNameValue != null) {
            Attr attrTpElementProptestname = document.createAttribute("testname");
            attrTpElementProptestname.setValue(testNameValue);
            eltElementProp.setAttributeNode(attrTpElementProptestname);
        }

        if (guiClassValue != null && testClassValue != null) {
            Attr attrTpElementPropenabled = document.createAttribute("enabled");
            attrTpElementPropenabled.setValue("true");
            eltElementProp.setAttributeNode(attrTpElementPropenabled);
        }
        return eltElementProp;
    }

    protected Element createUserDefinedVariable(Document document, HashMap <String, String> hSchemeHostPort) {
        /*
         <Arguments guiclass="ArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
          <collectionProp name="Arguments.arguments">
            <elementProp name="V_SCHEME" elementType="Argument">
              <stringProp name="Argument.name">V_SCHEME</stringProp>
              <stringProp name="Argument.value">http</stringProp>
              <stringProp name="Argument.metadata">=</stringProp>
            </elementProp>
            <elementProp name="V_HOST" elementType="Argument">
              <stringProp name="Argument.name">V_HOST</stringProp>
              <stringProp name="Argument.value">myhost</stringProp>
              <stringProp name="Argument.metadata">=</stringProp>
            </elementProp>
            <elementProp name="V_PORT" elementType="Argument">
              <stringProp name="Argument.name">V_PORT</stringProp>
              <stringProp name="Argument.value">8180</stringProp>
              <stringProp name="Argument.metadata">=</stringProp>
            </elementProp>
          </collectionProp>
        </Arguments>
         */
        Element eltArguments = document.createElement("Arguments");
        Attr attrArgumentsguiclass = document.createAttribute("guiclass");
        attrArgumentsguiclass.setValue("ArgumentsPanel");
        eltArguments.setAttributeNode(attrArgumentsguiclass);

        Attr attrArgumentstestclass = document.createAttribute("testclass");
        attrArgumentstestclass.setValue("Arguments");
        eltArguments.setAttributeNode(attrArgumentstestclass);

        Attr attrArgumentstestname = document.createAttribute("testname");
        attrArgumentstestname.setValue("User Defined Variables");
        eltArguments.setAttributeNode(attrArgumentstestname);

        Attr attrArgumentsenabled = document.createAttribute("enabled");
        attrArgumentsenabled.setValue("true");
        eltArguments.setAttributeNode(attrArgumentsenabled);

        Element eltCollectionProp = createProperty(document, "collectionProp", "Arguments.arguments", null);
        eltArguments.appendChild(eltCollectionProp);

        Element eltCollProElementProp1 = createElementProp(document, "V_SCHEME", "Argument", null, null, null);
        eltCollectionProp.appendChild(eltCollProElementProp1);
        Element eltCollProElementProp1StringProp1 = createProperty(document, "stringProp", "Argument.name", "V_SCHEME");
        eltCollProElementProp1.appendChild(eltCollProElementProp1StringProp1);

        String scheme = hSchemeHostPort.get(K_SCHEME);
        Element eltCollProElementProp1StringProp2 = createProperty(document, "stringProp", "Argument.value", scheme);
        eltCollProElementProp1.appendChild(eltCollProElementProp1StringProp2);

        Element eltCollProElementProp1StringProp3 = createProperty(document, "stringProp", "Argument.metadata", "=");
        eltCollProElementProp1.appendChild(eltCollProElementProp1StringProp3);

        Element eltCollProElementProp2 = createElementProp(document, "V_HOST", "Argument", null, null, null);
        eltCollectionProp.appendChild(eltCollProElementProp2);
        Element eltCollProElementProp2StringProp1 = createProperty(document, "stringProp", "Argument.name", "V_HOST");
        eltCollProElementProp2.appendChild(eltCollProElementProp2StringProp1);

        String host = hSchemeHostPort.get(K_HOST);
        Element eltCollProElementProp2StringProp2 = createProperty(document, "stringProp", "Argument.value", host);
        eltCollProElementProp2.appendChild(eltCollProElementProp2StringProp2);

        Element eltCollProElementProp2StringProp3 = createProperty(document, "stringProp", "Argument.metadata", "=");
        eltCollProElementProp2.appendChild(eltCollProElementProp2StringProp3);

        Element eltCollProElementProp3 = createElementProp(document, "V_PORT", "Argument", null, null, null);
        eltCollectionProp.appendChild(eltCollProElementProp3);

        String sPort = hSchemeHostPort.get(K_PORT);
        Element eltCollProElementProp3StringProp1 = createProperty(document, "stringProp", "Argument.name", "V_PORT");
        eltCollProElementProp3.appendChild(eltCollProElementProp3StringProp1);

        Element eltCollProElementProp3StringProp2 = createProperty(document, "stringProp", "Argument.value", sPort);
        eltCollProElementProp3.appendChild(eltCollProElementProp3StringProp2);

        Element eltCollProElementProp3StringProp3 = createProperty(document, "stringProp", "Argument.metadata", "=");
        eltCollProElementProp3.appendChild(eltCollProElementProp3StringProp3);

        return eltArguments;
    }

    protected Element createConfigTestElement(Document document) {
        /*
        <ConfigTestElement guiclass="HttpDefaultsGui" testclass="ConfigTestElement" testname="HTTP Request Defaults" enabled="true">
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
            <collectionProp name="Arguments.arguments"/>
          </elementProp>
          <stringProp name="HTTPSampler.domain">${V_HOST}</stringProp>      // 1
          <stringProp name="HTTPSampler.port">${V_PORT}</stringProp>        // 2
          <stringProp name="HTTPSampler.protocol">${V_SCHEME}</stringProp>  // 3
          <stringProp name="HTTPSampler.contentEncoding"></stringProp>      // 4
          <stringProp name="HTTPSampler.path"></stringProp>                 // 5
          <stringProp name="HTTPSampler.concurrentPool">6</stringProp>      // 6
          <stringProp name="HTTPSampler.connect_timeout"></stringProp>      // 7
          <stringProp name="HTTPSampler.response_timeout"></stringProp>     // 8
        </ConfigTestElement>
         */
        Element eltConfigTestElement = document.createElement("ConfigTestElement");
        Attr attrConfigTestElementguiclass = document.createAttribute("guiclass");
        attrConfigTestElementguiclass.setValue("HttpDefaultsGui");
        eltConfigTestElement.setAttributeNode(attrConfigTestElementguiclass);

        Attr attrConfigTestElementtestclass = document.createAttribute("testclass");
        attrConfigTestElementtestclass.setValue("ConfigTestElement");
        eltConfigTestElement.setAttributeNode(attrConfigTestElementtestclass);

        Attr attrConfigTestElementtestname = document.createAttribute("testname");
        attrConfigTestElementtestname.setValue("HTTP Request Defaults");
        eltConfigTestElement.setAttributeNode(attrConfigTestElementtestname);

        Attr attrConfigTestElementenabled = document.createAttribute("enabled");
        attrConfigTestElementenabled.setValue("true");
        eltConfigTestElement.setAttributeNode(attrConfigTestElementenabled);

        Element eltConfigTestElementProp = createElementProp(document,"HTTPsampler.Arguments","Arguments","HTTPArgumentsPanel", "Arguments", "User Defined Variables" );
        eltConfigTestElement.appendChild(eltConfigTestElementProp);
        Element eltCollectionProp1 = createProperty(document, "collectionProp", "Arguments.arguments", null);
        eltConfigTestElement.appendChild(eltCollectionProp1);
        Element eltStringProp1 = createProperty(document, "stringProp", "HTTPSampler.domain", "${V_HOST}");
        eltConfigTestElement.appendChild(eltStringProp1);
        Element eltStringProp2 = createProperty(document, "stringProp", "HTTPSampler.port", "${V_PORT}");
        eltConfigTestElement.appendChild(eltStringProp2);
        Element eltStringProp3 = createProperty(document, "stringProp", "HTTPSampler.protocol", "${V_SCHEME}");
        eltConfigTestElement.appendChild(eltStringProp3);
        Element eltStringProp4 = createProperty(document, "stringProp", "HTTPSampler.contentEncoding", "");
        eltConfigTestElement.appendChild(eltStringProp4);
        Element eltStringProp5 = createProperty(document, "stringProp", "HTTPSampler.path", "");
        eltConfigTestElement.appendChild(eltStringProp5);
        Element eltStringProp6 = createProperty(document, "stringProp", "HTTPSampler.concurrentPool", "6");
        eltConfigTestElement.appendChild(eltStringProp6);
        Element eltStringProp7 = createProperty(document, "stringProp", "HTTPSampler.connect_timeout", "");
        eltConfigTestElement.appendChild(eltStringProp7);
        Element eltStringProp8 = createProperty(document, "stringProp", "HTTPSampler.response_timeout", "");
        eltConfigTestElement.appendChild(eltStringProp8);

        return eltConfigTestElement;
    }

    protected Element createHttpSamplerProxy(Document document, String testname, String scheme, String host, int iPort, HarRequest harRequest) throws URISyntaxException {
        /*
         <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="007 /gestdocqualif/servletLogin" enabled="true">
            <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" enabled="true">
         */
        Element eltHTTPSamplerProxy = document.createElement("HTTPSamplerProxy");
        Attr attrHTTPSamplerProxyguiclass = document.createAttribute("guiclass");
        attrHTTPSamplerProxyguiclass.setValue("HttpTestSampleGui");
        eltHTTPSamplerProxy.setAttributeNode(attrHTTPSamplerProxyguiclass);

        Attr attrHTTPSamplerProxytestclass = document.createAttribute("testclass");
        attrHTTPSamplerProxytestclass.setValue("HTTPSamplerProxy");
        eltHTTPSamplerProxy.setAttributeNode(attrHTTPSamplerProxytestclass);

        Attr attrHTTPSamplerProxytestname = document.createAttribute("testname");
        attrHTTPSamplerProxytestname.setValue(testname);
        eltHTTPSamplerProxy.setAttributeNode(attrHTTPSamplerProxytestname);

        Attr attrHTTPSamplerProxyenabled = document.createAttribute("enabled");
        attrHTTPSamplerProxyenabled.setValue("true");
        eltHTTPSamplerProxy.setAttributeNode(attrHTTPSamplerProxyenabled);

        HarPostData postData = harRequest.getPostData();
        String mimeType = postData.getMimeType();
        String doMultiPart = "false";
        if (mimeType != null && mimeType.contains("multipart/form-data")) {
            doMultiPart = "true";
        }
        // Element eltHTTPSamplerProxyElementProp = createElementProp(document, "HTTPsampler.Arguments", "Arguments", "HTTPArgumentsPanel", "Arguments", null);
        /*
            <stringProp name="HTTPSampler.domain"></stringProp>              // 1
            <stringProp name="HTTPSampler.port"></stringProp>                // 2
            <stringProp name="HTTPSampler.protocol">http</stringProp>        // 3
            <stringProp name="HTTPSampler.contentEncoding"></stringProp>     // 4
            <stringProp name="HTTPSampler.path">/gestdocqualif/</stringProp> // 5
            <stringProp name="HTTPSampler.method">GET</stringProp>           // 6
            <boolProp name="HTTPSampler.follow_redirects">true</boolProp>    // 7
            <boolProp name="HTTPSampler.auto_redirects">false</boolProp>     // 8
            <boolProp name="HTTPSampler.use_keepalive">true</boolProp>       // 9
            <boolProp name="HTTPSampler.DO_MULTIPART_POST">false</boolProp>  // 10
            <stringProp name="HTTPSampler.embedded_url_re"></stringProp>     // 11
            <stringProp name="HTTPSampler.connect_timeout"></stringProp>     // 12
            <stringProp name="HTTPSampler.response_timeout"></stringProp>    // 13
                */
        URI url = new URI(harRequest.getUrl());

        String hostInter = "";
        if (!host.equalsIgnoreCase(url.getHost())) {
            hostInter = url.getHost();
        }
        Element stringProp1 = createProperty(document, "stringProp", "HTTPSampler.domain", hostInter);
        eltHTTPSamplerProxy.appendChild(stringProp1);


        int defautPort = 443;
        if ("http".equalsIgnoreCase(url.getScheme())) {
            defautPort = 80;
        }
        if ("ws".equalsIgnoreCase(url.getScheme())) {
            defautPort = 80;
        }

        String sPortInter = "";
        int port = url.getPort() == -1 ? defautPort : url.getPort();
        if (iPort != port) {
            sPortInter = "" + port;
        }
        Element stringProp2 = createProperty(document, "stringProp", "HTTPSampler.port", sPortInter);
        eltHTTPSamplerProxy.appendChild(stringProp2);

        String schemeInter = "";
        if (!scheme.equalsIgnoreCase(url.getScheme())) {
            schemeInter= url.getScheme();
        }
        Element stringProp3 = createProperty(document, "stringProp", "HTTPSampler.protocol", schemeInter);
        eltHTTPSamplerProxy.appendChild(stringProp3);


        String methodInter = harRequest.getMethod().name();

        String contentEncodingInter = "";
        if ("POST".equalsIgnoreCase(methodInter) || "PUT".equalsIgnoreCase(methodInter) || "PATCH".equalsIgnoreCase(methodInter)) {
            if (harRequest.getHeaders() != null && harRequest.getHeaders().size() > 0) {
                for (HarHeader header : harRequest.getHeaders()) {
                    String headerName = header.getName();
                    String headerValue = header.getValue();
                    if ("Content-Type".equalsIgnoreCase(headerName)) {
                        if ("application/json".equalsIgnoreCase(headerValue)) {
                            contentEncodingInter = "UTF-8";
                            break;
                        }
                    }
                }
            }
        }

        Element stringProp4 = createProperty(document, "stringProp", "HTTPSampler.contentEncoding", contentEncodingInter);
        eltHTTPSamplerProxy.appendChild(stringProp4);

        String pathInter = url.getPath();
        if ("POST".equalsIgnoreCase(methodInter) || "PUT".equalsIgnoreCase(methodInter) || "PATCH".equalsIgnoreCase(methodInter)) {
            if (url.getQuery() != null) {
                pathInter += "?" + url.getQuery();
            }
        }
        Element stringProp5 = createProperty(document, "stringProp", "HTTPSampler.path", pathInter);
        eltHTTPSamplerProxy.appendChild(stringProp5);

        Element stringProp6 = createProperty(document, "stringProp", "HTTPSampler.method", methodInter);
        eltHTTPSamplerProxy.appendChild(stringProp6);

        Element boolProp7 = createProperty(document, "boolProp", "HTTPSampler.follow_redirects", "false");
        eltHTTPSamplerProxy.appendChild(boolProp7);

        Element boolProp8 = createProperty(document, "boolProp", "HTTPSampler.auto_redirects", "false");
        eltHTTPSamplerProxy.appendChild(boolProp8);

        Element boolProp9 = createProperty(document, "boolProp", "HTTPSampler.use_keepalive", "true");
        eltHTTPSamplerProxy.appendChild(boolProp9);

        Element boolProp10 = createProperty(document, "boolProp", "HTTPSampler.DO_MULTIPART_POST", doMultiPart);
        eltHTTPSamplerProxy.appendChild(boolProp10);

        Element stringProp11 = createProperty(document, "stringProp", "HTTPSampler.embedded_url_re", null);
        eltHTTPSamplerProxy.appendChild(stringProp11);

        Element stringProp12 = createProperty(document, "stringProp", "HTTPSampler.connect_timeout", null);
        eltHTTPSamplerProxy.appendChild(stringProp12);

        Element stringProp13 = createProperty(document, "stringProp", "HTTPSampler.response_timeout", null);
        eltHTTPSamplerProxy.appendChild(stringProp13);

        eltHTTPSamplerProxy = createHttpSamplerParams(document, harRequest, eltHTTPSamplerProxy);
        return eltHTTPSamplerProxy;
    }

    protected Element createHttpSamplerParams(Document document, HarRequest harRequest, Element eltHTTPSamplerProxy) {
        /*
        <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables">
        <collectionProp name="Arguments.arguments">
                <elementProp name="mode" elementType="HTTPArgument">
                  <boolProp name="HTTPArgument.always_encode">false</boolProp> // 1
                  <stringProp name="Argument.name">mode</stringProp>           // 2
                  <stringProp name="Argument.value">mod2</stringProp>          // 3
                  <stringProp name="Argument.metadata">=</stringProp>          // 4
                  <boolProp name="HTTPArgument.use_equals">true</boolProp>     // 5
                </elementProp>
                <elementProp name="tfdLogin" elementType="HTTPArgument">
                  <boolProp name="HTTPArgument.always_encode">true</boolProp>
                  <stringProp name="Argument.name">tfdLogin</stringProp>
                  <stringProp name="Argument.value">${V_LOGIN}</stringProp>
                  <stringProp name="Argument.metadata">=</stringProp>
                  <boolProp name="HTTPArgument.use_equals">true</boolProp>
                </elementProp>
         </collectionProp>
         </elementProp>
         */
        Element eltHTTPSamplerProxyElementPropArguments = createElementProp(document, "HTTPsampler.Arguments", "Arguments", "HTTPArgumentsPanel", "Arguments", null);
        Element collectionProp = document.createElement("collectionProp");
        Attr attrcollectionPropname = document.createAttribute("name");
        attrcollectionPropname.setValue("Arguments.arguments");
        collectionProp.setAttributeNode(attrcollectionPropname);
        boolean isParamAdd = false;

        if (harRequest.getMethod().equals(HttpMethod.POST) || harRequest.getMethod().equals(HttpMethod.PUT) || harRequest.getMethod().equals(HttpMethod.PATCH)) {
            HarPostData postData = harRequest.getPostData();
            String mimeType = postData.getMimeType();
            mimeType = Utils.extractMimeType(mimeType); // remove charset if exists



            if ("application/x-www-form-urlencoded".equalsIgnoreCase(mimeType)) {
                Element boolPropPostBodyRaw = createProperty(document, "boolProp", "HTTPSampler.postBodyRaw", "false");
                eltHTTPSamplerProxy.appendChild(boolPropPostBodyRaw);

                for (HarPostDataParam dataParam : postData.getParams()) {
                    String paramName = dataParam.getName();
                    String paramValue = dataParam.getValue();
                    String contentType = dataParam.getContentType();
                    String fileName = dataParam.getFileName();
                    String comment = dataParam.getComment();

                    Element elementProp = document.createElement("elementProp");
                    Attr attrElementPropname = document.createAttribute("name");
                    attrElementPropname.setValue(paramName);
                    elementProp.setAttributeNode(attrElementPropname);

                    Attr attrElementPropelementType = document.createAttribute("elementType");
                    attrElementPropelementType.setValue("HTTPArgument");
                    elementProp.setAttributeNode(attrElementPropelementType);

                    boolean isNeedEncode = false;
                    if (paramValue.contains(" ") || paramValue.contains("=") || paramValue.contains("/") || paramValue.contains("+")) {
                        isNeedEncode = true;
                    }

                    Element boolProp1 = createProperty(document, "boolProp", "HTTPArgument.always_encode", "" + isNeedEncode);
                    elementProp.appendChild(boolProp1);

                    Element stringProp2 = createProperty(document, "stringProp", "Argument.name", paramName);
                    elementProp.appendChild(stringProp2);

                    Element stringProp3 = createProperty(document, "stringProp", "Argument.value", paramValue);
                    elementProp.appendChild(stringProp3);

                    Element stringProp4 = createProperty(document, "stringProp", "Argument.metadata", "=");
                    elementProp.appendChild(stringProp4);

                    Element boolProp5 = createProperty(document, "boolProp", "HTTPArgument.use_equals", "true");
                    elementProp.appendChild(boolProp5);

                    collectionProp.appendChild(elementProp);
                    isParamAdd = true;
                }

            }

            if (mimeType != null && mimeType.contains("multipart/form-data")) {
                isParamAdd = true;
                Element boolPropPostBodyRaw = createProperty(document, "boolProp", "HTTPSampler.postBodyRaw", "false");
                eltHTTPSamplerProxy.appendChild(boolPropPostBodyRaw);

                HarPostData postDataModified =  HarForJMeter.extractParamsFromMultiPart(harRequest);

                for (HarPostDataParam dataParam : postDataModified.getParams()) {
                    String paramName = dataParam.getName();
                    String paramValue = dataParam.getValue();
                    String contentType = dataParam.getContentType();
                    String fileName = dataParam.getFileName();
                    String comment = dataParam.getComment();

                    if (fileName != null) {
                        Element eltHTTPSamplerProxyHTTPsamplerFiles = createElementProp(document, "HTTPsampler.Files", "HTTPFileArgs", null, null, null);
                        Element eltPropCollectionProp = document.createElement("collectionProp");
                        Attr attrPropCollectionPropname = document.createAttribute("name");
                        attrPropCollectionPropname.setValue("HTTPFileArgs.files");
                        eltPropCollectionProp.setAttributeNode(attrPropCollectionPropname);

                        if (contentType == null) {
                            contentType ="";
                        }
                        Element eltPropFileName = createElementProp(document, fileName, "HTTPFileArg", null, null, null);
                        Element stringProp1 = createProperty(document, "stringProp", "File.mimetype", contentType);
                        eltPropFileName.appendChild(stringProp1);
                        Element stringProp2 = createProperty(document, "stringProp", "File.path", fileName);
                        eltPropFileName.appendChild(stringProp2);
                        Element stringProp3 = createProperty(document, "stringProp", "File.paramname", paramName);
                        eltPropFileName.appendChild(stringProp3);

                        eltPropCollectionProp.appendChild(eltPropFileName);
                        eltHTTPSamplerProxyHTTPsamplerFiles.appendChild(eltPropCollectionProp);
                        eltHTTPSamplerProxy.appendChild(eltHTTPSamplerProxyHTTPsamplerFiles);

                    } else {
                        Element elementProp = document.createElement("elementProp");
                        Attr attrElementPropname = document.createAttribute("name");
                        attrElementPropname.setValue(paramName);
                        elementProp.setAttributeNode(attrElementPropname);

                        Attr attrElementPropelementType = document.createAttribute("elementType");
                        attrElementPropelementType.setValue("HTTPArgument");
                        elementProp.setAttributeNode(attrElementPropelementType);

                        Element boolProp1 = createProperty(document, "boolProp", "HTTPArgument.always_encode", "false");
                        elementProp.appendChild(boolProp1);

                        Element stringProp2 = createProperty(document, "stringProp", "Argument.name", paramName);
                        elementProp.appendChild(stringProp2);

                        Element stringProp3 = createProperty(document, "stringProp", "Argument.value", paramValue);
                        elementProp.appendChild(stringProp3);

                        Element stringProp4 = createProperty(document, "stringProp", "Argument.metadata", "=");
                        elementProp.appendChild(stringProp4);

                        Element boolProp5 = createProperty(document, "boolProp", "HTTPArgument.use_equals", "true");
                        elementProp.appendChild(boolProp5);
                        collectionProp.appendChild(elementProp);
                    }
                }
            }

            if (!isParamAdd) {
                // postBodyRaw
                Element boolPropPostBodyRaw = createProperty(document, "boolProp", "HTTPSampler.postBodyRaw", "true");
                eltHTTPSamplerProxy.appendChild(boolPropPostBodyRaw);
                /*
                  <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
                  <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
                    <collectionProp name="Arguments.arguments">
                      <elementProp name="" elementType="HTTPArgument">
                        <boolProp name="HTTPArgument.always_encode">false</boolProp>
                        <stringProp name="Argument.value">{"name":"Vincent", "code":"GREEN"}</stringProp>
                        <stringProp name="Argument.metadata">=</stringProp>
                      </elementProp>
                    </collectionProp>
                  </elementProp>

                 */
                Element elementProp = document.createElement("elementProp");
                Attr attrElementPropname = document.createAttribute("name");
                attrElementPropname.setValue("");
                elementProp.setAttributeNode(attrElementPropname);

                Attr attrElementPropelementType = document.createAttribute("elementType");
                attrElementPropelementType.setValue("HTTPArgument");
                elementProp.setAttributeNode(attrElementPropelementType);

                Element boolProp1 = createProperty(document, "boolProp", "HTTPArgument.always_encode", "false");
                elementProp.appendChild(boolProp1);

                String paramValue = postData.getText();
                if (paramValue == null) {
                    paramValue = "";
                }
                Element stringProp3 = createProperty(document, "stringProp", "Argument.value", paramValue);
                elementProp.appendChild(stringProp3);

                Element stringProp4 = createProperty(document, "stringProp", "Argument.metadata", "=");
                elementProp.appendChild(stringProp4);

                collectionProp.appendChild(elementProp);
                isParamAdd = true;
            }
        }

        if (!(harRequest.getMethod().equals(HttpMethod.POST) || harRequest.getMethod().equals(HttpMethod.PUT) || harRequest.getMethod().equals(HttpMethod.PATCH))) {
            // NOT POST NOT PUT NOT PATCH, e.g : DELETE HEAD, OPTIONS, PATCH, PROPFIND

            Element boolPropPostBodyRaw = createProperty(document, "boolProp", "HTTPSampler.postBodyRaw", "false");
            eltHTTPSamplerProxy.appendChild(boolPropPostBodyRaw);

            for (HarQueryParam queryParam : harRequest.getQueryString()) {
                String paramName = queryParam.getName();
                String paramValue = queryParam.getValue();

                boolean isNeedEncode = false;
                if (paramValue.contains(" ") || paramValue.contains("=") || paramValue.contains("/") || paramValue.contains("/") || paramValue.contains("+")) {
                    isNeedEncode = true;
                }

                Element elementProp = document.createElement("elementProp");
                Attr attrElementPropname = document.createAttribute("name");
                attrElementPropname.setValue(paramName);
                elementProp.setAttributeNode(attrElementPropname);

                Attr attrElementPropelementType = document.createAttribute("elementType");
                attrElementPropelementType.setValue("HTTPArgument");
                elementProp.setAttributeNode(attrElementPropelementType);

                Element boolProp1 = createProperty(document, "boolProp", "HTTPArgument.always_encode", "" + isNeedEncode);
                elementProp.appendChild(boolProp1);

                Element stringProp2 = createProperty(document, "stringProp", "Argument.name", paramName);
                elementProp.appendChild(stringProp2);

                Element stringProp3 = createProperty(document, "stringProp", "Argument.value", paramValue);
                elementProp.appendChild(stringProp3);

                Element stringProp4 = createProperty(document, "stringProp", "Argument.metadata", "=");
                elementProp.appendChild(stringProp4);

                Element boolProp5 = createProperty(document, "boolProp", "HTTPArgument.use_equals", "true");
                elementProp.appendChild(boolProp5);

                collectionProp.appendChild(elementProp);
            }

        }
        eltHTTPSamplerProxyElementPropArguments.appendChild(collectionProp);
        eltHTTPSamplerProxy.appendChild(eltHTTPSamplerProxyElementPropArguments);
        return eltHTTPSamplerProxy;
    }


    protected Element createHeaderManager(Document document, HarRequest harRequest, boolean isRemoveCookie, boolean isRemoveCacheRequest, String[] tabRemoveHeaders) {
        /*
        <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="HTTP Header Manager" enabled="true">
             <collectionProp name="HeaderManager.headers">
                <elementProp name="Referer" elementType="Header">
                  <stringProp name="Header.name">Referer</stringProp>
                  <stringProp name="Header.value">http://myhost:8180/gestdocqualif/servletMenu</stringProp>
                </elementProp>
                <elementProp name="Accept-Language" elementType="Header">
                  <stringProp name="Header.name">Accept-Language</stringProp>
                  <stringProp name="Header.value">fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3</stringProp>
                </elementProp>
                <elementProp name="Accept-Encoding" elementType="Header">
                  <stringProp name="Header.name">Accept-Encoding</stringProp>
                  <stringProp name="Header.value">gzip, deflate</stringProp>
                </elementProp>
                <elementProp name="User-Agent" elementType="Header">
                  <stringProp name="Header.name">User-Agent</stringProp>
                  <stringProp name="Header.value">Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/113.0</stringProp>
                </elementProp>
              </collectionProp>
            </HeaderManager>
         */
        Element eltHeadManager = document.createElement("HeaderManager");
        Attr attreltHeadManagerguiclass = document.createAttribute("guiclass");
        attreltHeadManagerguiclass.setValue("HeaderPanel");
        eltHeadManager.setAttributeNode(attreltHeadManagerguiclass);

        Attr attreltHeadManagertestclass = document.createAttribute("testclass");
        attreltHeadManagertestclass.setValue("HeaderManager");
        eltHeadManager.setAttributeNode(attreltHeadManagertestclass);

        Attr attreltHeadManagertestname = document.createAttribute("testname");
        attreltHeadManagertestname.setValue("HTTP Header Manager");
        eltHeadManager.setAttributeNode(attreltHeadManagertestname);

        Attr attreltHeadManagerenabled = document.createAttribute("enabled");
        attreltHeadManagerenabled.setValue("true");
        eltHeadManager.setAttributeNode(attreltHeadManagerenabled);

        Element headers = createHttpSamplerHeaders(document, harRequest, isRemoveCookie, isRemoveCacheRequest, tabRemoveHeaders);
        eltHeadManager.appendChild(headers);
        return eltHeadManager;
    }

    protected Element createHttpSamplerHeaders(Document document, HarRequest harRequest, boolean isRemoveCookie, boolean isRemoveCacheRequest, String[] tabRemoveHeaders) {
        /*
            <collectionProp name="HeaderManager.headers">
                <elementProp name="Referer" elementType="Header">
                  <stringProp name="Header.name">Referer</stringProp>
                  <stringProp name="Header.value">http://myshost:8180/gestdocqualif/servletMenu</stringProp>
                </elementProp>
                <elementProp name="Accept-Language" elementType="Header">
                  <stringProp name="Header.name">Accept-Language</stringProp>
                  <stringProp name="Header.value">fr,fr-FR;q=0.8,en-US;q=0.5,en;q=0.3</stringProp>
                </elementProp>
                <elementProp name="Accept-Encoding" elementType="Header">
                  <stringProp name="Header.name">Accept-Encoding</stringProp>
                  <stringProp name="Header.value">gzip, deflate</stringProp>
                </elementProp>
              </collectionProp>
         */
        Element collectionProp = document.createElement("collectionProp");
        Attr attrcollectionPropname = document.createAttribute("name");
        attrcollectionPropname.setValue("HeaderManager.headers");
        collectionProp.setAttributeNode(attrcollectionPropname);

        if (harRequest.getHeaders() != null && harRequest.getHeaders().size() > 0) {
            for (HarHeader header : harRequest.getHeaders()) {
                String headerName = header.getName();
                String headerValue = header.getValue();
                boolean addThisHeader = true;

                if ("Cookie".equalsIgnoreCase(headerName)) {
                    if (isRemoveCookie) {
                        // no cookie because add a Cookie Manager
                        addThisHeader = false;
                    }
                }

                if ("If-Modified-Since".equalsIgnoreCase(headerName) || "If-None-Match".equalsIgnoreCase(headerName) || "If-Last-Modified".equalsIgnoreCase(headerName)) {
                    if (isRemoveCacheRequest) {
                        // no cache If-Modified-Since or If-None-Match because add a Cache Manager
                        addThisHeader = false;
                    }
                }

                if ("Content-Length".equalsIgnoreCase(headerName)) {
                    // the Content-length is computed by JMeter when the request is created, so remove it
                    addThisHeader = false;
                }

                if (headerName != null && !headerName.isEmpty() && headerName.startsWith(":")) {
                    // don't add headers from http/2 protocol starting with ':', likes :  ":authority" or ":method" or ":path" or ":scheme"
                    addThisHeader = false;
                }

                if (tabRemoveHeaders !=  null && headerName != null && !headerName.isEmpty()) {
                    for (int i = 0; i < tabRemoveHeaders.length; i++) {
                        String headerToRemove = tabRemoveHeaders[i];
                        if (headerToRemove.equalsIgnoreCase(headerName)) {
                            addThisHeader = false;
                            break;
                        }
                    }
                }

                if (addThisHeader) {
                    Element elementProp = createElementProp(document, headerName, "Header", null, null, null);

                    Element stringProp1 = createProperty(document, "stringProp", "Header.name", headerName);
                    elementProp.appendChild(stringProp1);

                    Element stringProp2 = createProperty(document, "stringProp", "Header.value", headerValue);
                    elementProp.appendChild(stringProp2);
                    collectionProp.appendChild(elementProp);
                }
            }
        }
        return collectionProp;
    }

    protected Element createTestActionPause(Document document, String testname, long pauseMs) {
        /*
        <TestAction guiclass="TestActionGui" testclass="TestAction" testname="fca PAUSE TEMPS_COURT" enabled="true">
          <intProp name="ActionProcessor.action">1</intProp>
          <intProp name="ActionProcessor.target">0</intProp>
          <stringProp name="ActionProcessor.duration">${K_TEMPS_COURT}</stringProp>
        </TestAction>
         */
        Element eltTestAction = document.createElement("TestAction");
        Attr attrTestActionguiclass = document.createAttribute("guiclass");
        attrTestActionguiclass.setValue("TestActionGui");
        eltTestAction.setAttributeNode(attrTestActionguiclass);

        Attr attrTestActiontestclass = document.createAttribute("testclass");
        attrTestActiontestclass.setValue("TestAction");
        eltTestAction.setAttributeNode(attrTestActiontestclass);

        Attr attrTestActiontestname = document.createAttribute("testname");
        attrTestActiontestname.setValue(testname);
        eltTestAction.setAttributeNode(attrTestActiontestname);

        Attr attrTestActionenabled = document.createAttribute("enabled");
        attrTestActionenabled.setValue("true");
        eltTestAction.setAttributeNode(attrTestActionenabled);

        Element eltIntProp1 = createProperty(document, "intProp","ActionProcessor.action", "1");
        eltTestAction.appendChild(eltIntProp1);
        Element eltIntProp2 = createProperty(document, "intProp","ActionProcessor.target", "0");
        eltTestAction.appendChild(eltIntProp2);
        Element eltStringProp3 = createProperty(document, "stringProp","ActionProcessor.duration", "" + pauseMs);
        eltTestAction.appendChild(eltStringProp3);

        return eltTestAction;
    }

    HashMap getSchemeHostPortFirstPageOrUrl(Har har) throws URISyntaxException {
        String scheme = "";
        String host = "";
        int iPort = 0;

        List<HarEntry> lEntries = har.getLog().getEntries();
        if (lEntries != null && lEntries.size() > 0) {
            HarEntry harEntryInter = lEntries.get(0);
            URI pageUrl = new URI(harEntryInter.getRequest().getUrl());
            scheme = pageUrl.getScheme(); // http or https
            host = pageUrl.getHost(); // google.com
            iPort = pageUrl.getPort(); // -1 (default for 80 or 443) or port number likes 8080
            if (iPort == -1 && "http".equalsIgnoreCase(scheme)) {
                iPort = 80;
            }
            if (iPort == -1 && "https".equalsIgnoreCase(scheme)) {
                iPort = 443;
            }
        }

        HashMap hashMap = new HashMap<>();
        hashMap.put(K_SCHEME, scheme);
        hashMap.put(K_HOST, host);
        hashMap.put(K_PORT, "" + iPort); // port is a String in the hashMap

        return hashMap;
    }

    /**
     * Save the JMX Document in a XML file
     * @param document JMX Document
     * @param jmxXmlFileOut XML file to write
     * @throws TransformerException error when write XML file
     */
    public static void saveXmFile(Document document, String jmxXmlFileOut) throws TransformerException {
        // create the xml file
        //transform the DOM Object to an XML File
        LOGGER.fine("saveXmFile, param jmxXmlFileOut=<" + jmxXmlFileOut + ">" );
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource domSource = new DOMSource(document);
        try {
            Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(jmxXmlFileOut), StandardCharsets.UTF_8));
            StreamResult streamResult = new StreamResult(out);
            transformer.transform(domSource, streamResult);
        } catch (Exception e) {
            throw new TransformerException(e);
        }
    }
}
