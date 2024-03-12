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

import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarCookie;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarPostData;
import de.sstoehr.harreader.model.HarPostDataParam;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import de.sstoehr.harreader.model.HarTiming;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Har2TestResultsXml {
    private static final Logger LOGGER = Logger.getLogger(Har2TestResultsXml.class.getName());

    protected Document convertHarToTestResultXml(Har har, String urlFilterToInclude, String urlFilterToExclude, int samplerStartNumber) throws ParserConfigurationException, URISyntaxException, MalformedURLException {

        Pattern patternUrlInclude = null;
        if (!urlFilterToInclude.isEmpty()) {
            patternUrlInclude = Pattern.compile(urlFilterToInclude);
        }

        Pattern patternUrlExclude = null;
        if (!urlFilterToExclude.isEmpty()) {
            patternUrlExclude = Pattern.compile(urlFilterToExclude);
        }

        DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();

        DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();

        Document document = documentBuilder.newDocument();

        Element eltTestResults = createTestResults(har, document, patternUrlInclude, patternUrlExclude, samplerStartNumber);
        document.appendChild(eltTestResults);
        return document;
    }

    protected Element createTestResults(Har har, Document document, Pattern patternUrlInclude, Pattern patternUrlExclude, int samplerStartNumber) throws MalformedURLException, URISyntaxException {
        Element eltTestResults = document.createElement("testResults");
        Attr attrTrversion = document.createAttribute("version");
        attrTrversion.setValue("1.2");
        eltTestResults.setAttributeNode(attrTrversion);

        List<HarEntry> lEntries = har.getLog().getEntries();
        String currentUrl = "";
        int num = samplerStartNumber;
        for (int e = 0; e < lEntries.size(); e++) {
            HarEntry harEntryInter = lEntries.get(e);

            HarRequest harRequest = harEntryInter.getRequest();
            currentUrl = harRequest.getUrl();

            boolean isAddThisRequest = true;
            if (patternUrlInclude != null) {
                Matcher matcher = patternUrlInclude.matcher(currentUrl);
                isAddThisRequest = matcher.find();
            }
            if (patternUrlExclude != null) {
                Matcher matcher = patternUrlExclude.matcher(currentUrl);
                isAddThisRequest = !matcher.find();
            }

            HashMap hAddictional = (HashMap<String, Object>) harEntryInter.getAdditional();
            if (hAddictional != null) {
                String fromCache = (String) hAddictional.get("_fromCache");
                if (fromCache != null) {
                    // this url content is in the browser cache (memory or disk) no need to create a new request
                    isAddThisRequest = false;
                }
            }

            String sURl = harEntryInter.getRequest().getUrl();
            URI uri = new URI(sURl);
            String scheme = uri.getScheme();

            if ("ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme)) {
                // No Web Socket because a http samler with scheme ws (wss) will generate error when open file in Listener View Tree
                // Need to add a specific sampler from WebSocket Samplers by Peter Doornbosch
                if (isAddThisRequest) {
                    num++; // for no offset between jmx script and record.xml, because ws are in the JMeter script in a http sampler
                }
                isAddThisRequest = false;
            }

            if (isAddThisRequest) {
                Element eltHttpSample = createHttSample(document, harEntryInter, num);
                eltTestResults.appendChild(eltHttpSample);
                num++;
            }
        }
        LOGGER.info("testResuts file contains " + num + " httpSample");
        return eltTestResults;
    }

    protected Element createHttSample(Document document, HarEntry harEntry, int num) throws MalformedURLException, URISyntaxException {

        HarRequest harRequest = harEntry.getRequest();
        HarResponse harResponse = harEntry.getResponse();

        Element eltHttpSample = createEltHttpSample(document, harEntry, num);

        Element eltRequestponseHeaders = createRequestHeaders(document, harEntry.getRequest());
        eltHttpSample.appendChild(eltRequestponseHeaders);
        Element eltResponseHeaders = createResponseHeaders(document, harEntry.getResponse());
        eltHttpSample.appendChild(eltResponseHeaders);

        Element eltresponseFile = document.createElement("responseFile");
        eltresponseFile = addAttributeToElement(document, eltresponseFile, "class", "java.lang.String");
        eltHttpSample.appendChild(eltresponseFile);

        Element eltcookies = createCookies(document, harRequest);
        eltHttpSample.appendChild(eltcookies);

        Element eltmethod = document.createElement("method");
        eltmethod = addAttributeToElement(document, eltmethod, "class", "java.lang.String");
        String method = harRequest.getMethod().name();
        eltmethod.setTextContent(method);
        eltHttpSample.appendChild(eltmethod);

        Element eltqueryString = document.createElement("queryString");
        eltqueryString = addAttributeToElement(document, eltqueryString, "class", "java.lang.String");
        String queryString = "";
        if (harRequest.getQueryString().size() > 0) {
            queryString = new URI(harRequest.getUrl()).getQuery();
        } else {
            if ("POST".equalsIgnoreCase(method)) {
                queryString = createQueryStringForPost(harRequest);
            }
        }
        eltqueryString.setTextContent(queryString);
        eltHttpSample.appendChild(eltqueryString);

        String urlRedirect = harResponse.getRedirectURL();
        if (!urlRedirect.isEmpty()) {
            Element eltredirectLocation = document.createElement("redirectLocation");
            eltqueryString = addAttributeToElement(document, eltredirectLocation, "class", "java.lang.String");
            eltredirectLocation.setTextContent(urlRedirect);
            eltHttpSample.appendChild(eltredirectLocation);
        }

        Element eltJavaNetUrl = document.createElement("java.net.URL");
        eltJavaNetUrl.setTextContent(harRequest.getUrl());
        eltHttpSample.appendChild(eltJavaNetUrl);

        boolean isText = false;
        String dt_reponse = eltHttpSample.getAttribute("dt");
        if ("text".equalsIgnoreCase(dt_reponse)) {
            isText = true;
        }
        Element eltResponseData = createEltReponseData(document, harResponse, isText);
        eltHttpSample.appendChild(eltResponseData);
        return eltHttpSample;
    }

    protected Element createEltHttpSample(Document document, HarEntry harEntry, int num) throws MalformedURLException, URISyntaxException {
        /*
        <httpSample t="18" it="0" lt="18" ct="9" ts="1699889754878" s="true" lb="002 /gestdocqualif/styles/styles.css" rc="200" rm="OK" tn="" dt="text" de="" by="7904" sc="1" ec="0" ng="0" na="0" hn="browser">

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
        String lb_label = String.format("%03d " + urlRequest.getPath(), num); // 003 /gestdocqualif/servletStat

        String rc_response = "" + harResponse.getStatus();
        String rm_response = harResponse.getStatusText();

        String dt_response = textFromMimeType(harContent.getMimeType());
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
        String de_response = responseEncoding(harResponse);

        String by_response = "" + harContent.getSize(); // Bytes receive
        String sby_request = "" + harRequest.getBodySize();	// Sent Bytes
        String sc_count = "1";
        String ec_count = "0";
        String ng_count = "0";
        String na_count = "0";
        String hn = "browser";

        Element eltHttpSample = document.createElement("httpSample");
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "t", t_time);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "it", it_time);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "lt", lt_time);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "ct", ct_time);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "ts", ts_time);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "s", s_response);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "lb", lb_label);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "rc", rc_response);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "rm", rm_response);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "dt", dt_response);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "de", de_response);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "by", by_response);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "sby", sby_request);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "sc", sc_count);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "ec", ec_count);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "ng", ng_count);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "na", na_count);
        eltHttpSample = addAttributeToElement(document, eltHttpSample, "hn", hn);

        return eltHttpSample;

    }

    protected Element addAttributeToElement(Document document, Element element, String attributeName, String attributeValue) {
        Attr attr = document.createAttribute(attributeName);
        attr.setValue(attributeValue);
        element.setAttributeNode(attr);
        return element;
    }

    protected Attr createAttribute(Document document, String attributeName, String attributeValue) {
        Attr attr = document.createAttribute(attributeName);
        attr.setValue(attributeValue);
        return attr;
    }
    protected Element createRequestHeaders(Document document, HarRequest harRequest) {
        Element eltRequestHeader = document.createElement("requestHeader");
        eltRequestHeader = addAttributeToElement(document, eltRequestHeader, "class", "java.lang.String");
        List<HarHeader> lRequestHeaders =  harRequest.getHeaders();
        StringBuffer sb = new StringBuffer(2048);
        for (int i = 0; i < lRequestHeaders.size(); i++) {
            HarHeader harHeader = lRequestHeaders.get(i);
            sb.append(harHeader.getName());
            sb.append(": ");
            sb.append(harHeader.getValue());
            sb.append("\n");
        }
        eltRequestHeader.setTextContent(sb.toString());
        return eltRequestHeader;
    }

    protected Element createResponseHeaders(Document document, HarResponse harResponse) {
        Element eltResponseHeader = document.createElement("responseHeader");
        eltResponseHeader = addAttributeToElement(document, eltResponseHeader, "class", "java.lang.String");
        List<HarHeader> lResponseHeaders =  harResponse.getHeaders();
        StringBuffer sb = new StringBuffer(2048);
        for (int i = 0; i < lResponseHeaders.size(); i++) {
            HarHeader harHeader = lResponseHeaders.get(i);
            sb.append(harHeader.getName());
            sb.append(": ");
            sb.append(harHeader.getValue());
            sb.append("\n");
        }
        eltResponseHeader.setTextContent(sb.toString());
        return eltResponseHeader;
    }

    protected Element createCookies(Document document, HarRequest harRequest) {
        Element eltcookies = document.createElement("cookies");
        eltcookies = addAttributeToElement(document, eltcookies, "class", "java.lang.String");
        List<HarCookie> lCookies =  harRequest.getCookies();
        StringBuffer sb = new StringBuffer(2048);
        for (int i = 0; i < lCookies.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            HarCookie cookie = lCookies.get(i);
            sb.append(cookie.getName());
            sb.append("=");
            sb.append(cookie.getValue());
        }
        eltcookies.setTextContent(sb.toString());
        return eltcookies;
    }

    protected Element createEltReponseData(Document document, HarResponse harResponse, boolean isText) {
        Element eltresponseData = document.createElement("responseData");
        eltresponseData = addAttributeToElement(document, eltresponseData, "class", "java.lang.String");
        HarContent harContent = harResponse.getContent();
        if (harContent != null) {
            String contentText = harContent.getText();
            String contentEncoding = harContent.getEncoding();

            if (contentText != null && "base64".equalsIgnoreCase(contentEncoding) && isText) {
                byte[] contentDecodeByte = Base64.getDecoder().decode(contentText.getBytes());
                String contentDecodeString = new String(contentDecodeByte);
                eltresponseData.setTextContent(contentDecodeString);
            }

            if (contentText != null && contentEncoding == null && isText) {
                eltresponseData.setTextContent(contentText);
            }
        }
        return eltresponseData;
    }

    protected String createQueryStringForPost(HarRequest harRequest) {
        StringBuffer sb = new StringBuffer(2048);
        HarPostData postData = harRequest.getPostData();
        String mimeType = postData.getMimeType();
        boolean isParamAdd = false;
        if ("application/x-www-form-urlencoded".equalsIgnoreCase(mimeType)) {
            List<HarPostDataParam> lDataParams = postData.getParams();
            for (int i = 0; i < lDataParams.size(); i++) {
                if (i > 0) {
                    sb.append("&");
                }
                HarPostDataParam dataParamInter = lDataParams.get(i);
                String name = dataParamInter.getName();
                String value = dataParamInter.getValue();
                sb.append(name);
                sb.append("=");
                sb.append(value);
            }
            isParamAdd = true;
        }
        if (mimeType != null && mimeType.contains("multipart/form-data")) {
            HarPostData postDataFormData = HarForJMeter.extractParamsFromMultiPart(harRequest);
            String boundary = StringUtils.substringAfter(mimeType,"boundary=");
            LOGGER.fine("boundary=<" + boundary + ">");
            List<HarPostDataParam> listParams  = postDataFormData.getParams();
            StringBuffer sbFormData = new StringBuffer(1024);
            for (int j = 0; j < listParams.size(); j++) {
                HarPostDataParam harPostDataParamInter = listParams.get(j);
                String fileName = harPostDataParamInter.getFileName();
                String contentType = harPostDataParamInter.getContentType();
                String nameParam = harPostDataParamInter.getName();
                String valueParam = harPostDataParamInter.getValue();

                sbFormData.append(boundary + "\n");
                sbFormData.append("Content-Disposition: form-data; name=\"");
                sbFormData.append(nameParam + "\"");
                if (fileName != null) {
                    sbFormData.append("; filename=\"" + fileName + "\"\n");
                    sbFormData.append("Content-Type: " + contentType + "\n");
                    sbFormData.append("\n\n");
                    sbFormData.append("<actual file content, not shown here>");
                    sbFormData.append("\n");
                } else {
                    sbFormData.append("\n\n");
                    sbFormData.append(valueParam);
                    sbFormData.append("\n");
                }
            }
            sb.append(sbFormData);
            // sb.append(postData.getText()); // not the text because may contain binary from the upload file (e.g. PDF or DOCX ...)
            isParamAdd = true;
        }

        if (!isParamAdd) {
            sb.append(postData.getText());
            isParamAdd = true;
        }
        return sb.toString();
    }
    public static boolean isTextFromMimeType(String mimeType) {
        boolean isText = false;

        String mimeTypeInter = mimeType;
        String partBeforeSemiColum = StringUtils.substringBefore(mimeType, ";"); // application/xml; charset=UTF-8 => application/xml
        if (partBeforeSemiColum != null) {
            mimeTypeInter = partBeforeSemiColum;
        }

        String type = StringUtils.substringBefore(mimeTypeInter, "/"); // application
        String subType = StringUtils.substringAfter(mimeTypeInter, "/"); // xml

        if (subType == null || type.isEmpty()) {
            return false;
        }

        if ("text".equalsIgnoreCase(type)) {
            isText = true;
        }

        if (subType.contains("-xml") || subType.contains("+xml") || subType.contains("-json") || subType.contains("+json")) {
            isText = true;
        }

        if (subType.contains("gzip") || subType.contains("zip") || subType.contains("compressed") || subType.equalsIgnoreCase("octet-stream")) {
            isText = false;
        }

        switch (subType) {
            case "css":
            case "html":
            case "csv":
            case "richtext":
            case "x-www-form-urlencoded":
            case "javascript":
            case "x-javascript":
            case "json":
            case "xml":
            case "xhtml":
            case "xhtml+xml":
            case "atom+xml":
            case "postscript":
            case "base64":
            case "problem+json":
            isText = true;
        }
        return isText;
    }

    public static String textFromMimeType(String mimeType) {
        String sText = "text";

        if (!isTextFromMimeType(mimeType)) {
            sText = "bin";
        }
        return sText;
    }

    public static String responseEncoding(HarResponse harResponse) {
        List<HarHeader> lResponseHeaders =  harResponse.getHeaders();
        String encoding = "UTF-8";
        for (int i = 0; i < lResponseHeaders.size(); i++) {
            HarHeader harHeader = lResponseHeaders.get(i);
            String name = harHeader.getName();
            String value = harHeader.getValue();
            if ("Content-Type".equalsIgnoreCase(name) && value != null) {
                String charset = StringUtils.substringAfter(value, "charset=");
                if (charset != null) {
                    encoding = charset;
                }
                break;
            }
        }
        return encoding;
    }
}
