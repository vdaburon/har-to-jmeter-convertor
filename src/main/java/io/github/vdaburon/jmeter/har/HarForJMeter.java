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

import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarCreatorBrowser;
import de.sstoehr.harreader.model.HarPostData;
import de.sstoehr.harreader.model.HarPostDataParam;
import de.sstoehr.harreader.model.HarRequest;

import io.github.vdaburon.jmeter.har.external.ManageExternalFile;
import io.github.vdaburon.jmeter.har.lrwr.HarLrTransactions;
import io.github.vdaburon.jmeter.har.lrwr.ManageLrwr;
import io.github.vdaburon.jmeter.har.common.TransactionInfo;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.Properties;
import java.util.regex.PatternSyntaxException;

/**
 * The main class to read a har file and generate a JMeter script and a Record.xml file
 */

public class HarForJMeter {

    public static final String APPLICATION_VERSION="5.3";

    // CLI OPTIONS
    public static final String K_HAR_IN_OPT = "har_in";
    public static final String K_JMETER_FILE_OUT_OPT = "jmx_out";
    public static final String K_CREATE_NEW_TC_AFTER_MS_OPT = "new_tc_pause";
    public static final String K_ADD_PAUSE_OPT = "add_pause";
    public static final String K_REGEX_FILTER_INCLUDE_OPT = "filter_include";
    public static final String K_REGEX_FILTER_EXCLUDE_OPT = "filter_exclude";
    public static final String K_RECORD_FILE_OUT_OPT = "record_out";
    public static final String K_REMOVE_COOKIE_OPT = "remove_cookie";
    public static final String K_REMOVE_CACHE_REQUEST_OPT = "remove_cache_request";
    public static final String K_PAGE_START_NUMBER = "page_start_number";
    public static final String K_SAMPLER_START_NUMBER = "sampler_start_number";
    public static final String K_LRWR_USE_INFOS = "use_lrwr_infos";
    public static final String K_LRWR_USE_TRANSACTION_NAME = "transaction_name";
    public static final String K_EXTERNAL_FILE_INFOS = "external_file_infos";

    private static final Logger LOGGER = Logger.getLogger(HarForJMeter.class.getName());

    public static void main(String[] args) {
        String harFile = "";
        String jmxOut = "";
        long createNewTransactionAfterRequestMs = 0;
        boolean isAddPause = true;
        String urlFilterToInclude = "";
        String urlFilterToExclude = "";
        String recordXmlOut = "";
        boolean isRemoveCookie = true;
        boolean isRemoveCacheRequest = true;
        int pageStartNumber = 1;
        int samplerStartNumber = 1;
        String lrwr_info = ""; // for LoadRunner Web Recorder Chrome Extension
        String fileExternalInfo = ""; // csv file name contains infos like : 2024-05-07T07:56:40.513Z;TRANSACTION;welcome_page;start


        long lStart = System.currentTimeMillis();
        LOGGER.info("Start main");

        Options options = createOptions();
        Properties parseProperties = null;

        try {
            parseProperties = parseOption(options, args);
        } catch (ParseException ex) {
            helpUsage(options);
            LOGGER.info("main end (exit 1) ERROR");
            System.exit(1);
        }

        String sTmp = "";
        sTmp = (String) parseProperties.get(K_HAR_IN_OPT);
        if (sTmp != null) {
            harFile = sTmp;
        }

        sTmp = (String) parseProperties.get(K_JMETER_FILE_OUT_OPT);
        if (sTmp != null) {
            jmxOut = sTmp;
        }

        sTmp = (String) parseProperties.get(K_CREATE_NEW_TC_AFTER_MS_OPT);
        if (sTmp != null) {
            try {
                createNewTransactionAfterRequestMs = Integer.parseInt(sTmp);
            } catch (Exception ex) {
                LOGGER.warning("Error parsing long parameter " + K_CREATE_NEW_TC_AFTER_MS_OPT + ", value = " + sTmp + ", set to 0 (default)");
                createNewTransactionAfterRequestMs = 0;
            }
        }

        sTmp = (String) parseProperties.get(K_ADD_PAUSE_OPT);
        if (sTmp != null) {
            isAddPause= Boolean.parseBoolean(sTmp);
        }

        sTmp = (String) parseProperties.get(K_REGEX_FILTER_INCLUDE_OPT);
        if (sTmp != null) {
            urlFilterToInclude = sTmp;
        }

        sTmp = (String) parseProperties.get(K_REGEX_FILTER_EXCLUDE_OPT);
        if (sTmp != null) {
            urlFilterToExclude = sTmp;
        }

        sTmp = (String) parseProperties.get(K_RECORD_FILE_OUT_OPT);
        if (sTmp != null) {
            recordXmlOut = sTmp;
        }

        sTmp = (String) parseProperties.get(K_REMOVE_COOKIE_OPT);
        if (sTmp != null) {
            isRemoveCookie= Boolean.parseBoolean(sTmp);
        }

        sTmp = (String) parseProperties.get(K_REMOVE_CACHE_REQUEST_OPT);
        if (sTmp != null) {
            isRemoveCacheRequest= Boolean.parseBoolean(sTmp);
        }

        sTmp = (String) parseProperties.get(K_PAGE_START_NUMBER);
        if (sTmp != null) {
            try {
                pageStartNumber = Integer.parseInt(sTmp);
            } catch (Exception ex) {
                LOGGER.warning("Error parsing long parameter " + K_PAGE_START_NUMBER + ", value = " + sTmp + ", set to 1 (default)");
                pageStartNumber = 1;
            }
        }
        if (pageStartNumber <= 0) {
            pageStartNumber = 1;
        }

        sTmp = (String) parseProperties.get(K_SAMPLER_START_NUMBER);
        if (sTmp != null) {
            try {
                samplerStartNumber = Integer.parseInt(sTmp);
            } catch (Exception ex) {
                LOGGER.warning("Error parsing long parameter " + K_SAMPLER_START_NUMBER + ", value = " + sTmp + ", set to 1 (default)");
                samplerStartNumber = 1;
            }
        }
        if (samplerStartNumber <= 0) {
            samplerStartNumber = 1;
        }

        sTmp = (String) parseProperties.get(K_LRWR_USE_INFOS);
        if (sTmp != null) {
            lrwr_info= sTmp;
            if (!lrwr_info.isEmpty() && !K_LRWR_USE_TRANSACTION_NAME.equalsIgnoreCase(sTmp)) {
                LOGGER.warning("This Parameter " + K_LRWR_USE_INFOS + " is not an expected value, value = " + sTmp + ", set to empty (default)");
                lrwr_info = "";
            }
        } else {
            lrwr_info = "";
        }

        sTmp = (String) parseProperties.get(K_EXTERNAL_FILE_INFOS);
        if (sTmp != null) {
            fileExternalInfo = sTmp;
        }

        HarForJMeter harForJMeter = new HarForJMeter();
        LOGGER.info("************* PARAMETERS ***************");
        LOGGER.info(K_HAR_IN_OPT + ", harFile=" + harFile);
        LOGGER.info(K_JMETER_FILE_OUT_OPT + ", jmxOut=" + jmxOut);
        LOGGER.info(K_RECORD_FILE_OUT_OPT + ", recordXmlOut=" + recordXmlOut);
        LOGGER.info(K_CREATE_NEW_TC_AFTER_MS_OPT + ", createNewTransactionAfterRequestMs=" + createNewTransactionAfterRequestMs);
        LOGGER.info(K_ADD_PAUSE_OPT + ", isAddPause=" + isAddPause);
        LOGGER.info(K_REGEX_FILTER_INCLUDE_OPT + ", urlFilterToInclude=" + urlFilterToInclude);
        LOGGER.info(K_REGEX_FILTER_EXCLUDE_OPT + ", urlFilterToExclude=" + urlFilterToExclude);
        LOGGER.info(K_REMOVE_COOKIE_OPT + ", isRemoveCookie=" + isRemoveCookie);
        LOGGER.info(K_REMOVE_CACHE_REQUEST_OPT + ", isRemoveCacheRequest=" + isRemoveCacheRequest);
        LOGGER.info(K_PAGE_START_NUMBER + ", pageStartNumber=" + pageStartNumber);
        LOGGER.info(K_SAMPLER_START_NUMBER + ", samplerStartNumber=" + samplerStartNumber);
        LOGGER.info(K_LRWR_USE_INFOS + ", lrwr_info=" + lrwr_info);
        LOGGER.info(K_EXTERNAL_FILE_INFOS + ", fileExternalInfo=" + fileExternalInfo);
        LOGGER.info("***************************************");
        try {
            generateJmxAndRecord(harFile,  jmxOut,createNewTransactionAfterRequestMs,isAddPause, isRemoveCookie, isRemoveCacheRequest, urlFilterToInclude, urlFilterToExclude, recordXmlOut, pageStartNumber, samplerStartNumber, lrwr_info, fileExternalInfo);

            long lEnd = System.currentTimeMillis();
            long lDurationMs = lEnd - lStart;
            LOGGER.info("Duration ms : " + lDurationMs);
            LOGGER.info("End main OK exit(0)");
            System.exit(0);

        } catch (HarReaderException | ParserConfigurationException | TransformerException | MalformedURLException |
                 PatternSyntaxException e) {
            LOGGER.severe(e.toString());
            e.printStackTrace();
            System.exit(1);
        } catch (URISyntaxException e) {
            LOGGER.severe(e.toString());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Create the JMeter script jmx file and the Record.xml file
     * @param harFile the har file to read
     * @param jmxOut the JMeter script to create
     * @param recordXmlOut the record.xml file to open with a Listener View Result Tree
     * @param createNewTransactionAfterRequestMs how many milliseconds for creating a new Transaction Controller
     * @param isAddPause do we add Flow Control Action PAUSE ?
     * @param isRemoveCookie do we remove Cookie information ?
     * @param isRemoveCacheRequest do we remove the cache information for the Http Request ?
     * @param urlFilterToInclude the regex filter to include url
     * @param urlFilterToExclude the regex filter to exclude url
     * @param pageStartNumber the first page number
     * @param samplerStartNumber the first http sampler number
     * @param lrwr_info what information from the HAR do we use ? The transaction_name or empty. For HAR generated with LoadRunner Web Recorder.
     * @param fileExternalInfo file contains external informations like 2024-05-07T07:56:40.513Z;TRANSACTION;home_page;start
     * @throws HarReaderException trouble when reading HAR file
     * @throws MalformedURLException trouble to convert String to a URL
     * @throws ParserConfigurationException regex expression is incorrect
     * @throws URISyntaxException trouble to convert String to a URL
     * @throws TransformerException Megatron we have a problem
     */
    public static void generateJmxAndRecord(String harFile, String jmxOut, long createNewTransactionAfterRequestMs, boolean isAddPause, boolean isRemoveCookie, boolean isRemoveCacheRequest, String urlFilterToInclude, String urlFilterToExclude, String recordXmlOut, int pageStartNumber, int samplerStartNumber, String lrwr_info, String fileExternalInfo) throws HarReaderException, MalformedURLException, ParserConfigurationException, URISyntaxException, TransformerException {
        HarForJMeter harForJMeter = new HarForJMeter();

        Har har = harForJMeter.loadHarFile(harFile);
        HarCreatorBrowser creator = har.getLog().getCreator();
        String harCreator = "HAR File, Creator : Not Declared";
        if (creator != null) {
            harCreator = "HAR File, Creator : " + creator.getName() + ", version : " + creator.getVersion();
        }
        LOGGER.info(harCreator);

        List<TransactionInfo> listTransactionInfo = null;
        if (K_LRWR_USE_TRANSACTION_NAME.equals(lrwr_info)) {
            boolean isHarWithLrwr = ManageLrwr.isHarContainsLrwr(harFile);
            if (isHarWithLrwr) {
                List<HarLrTransactions> listHarLrTransactions = ManageLrwr.getListTransactionLrwr(harFile);
                listTransactionInfo = ManageLrwr.createListTransactionInfo(listHarLrTransactions);
            }
        }

        if (!fileExternalInfo.isEmpty()) {
            try {
                listTransactionInfo = ManageExternalFile.createListTransactionInfo(fileExternalInfo);
            } catch (Exception e) {
                LOGGER.severe("Can't read file or content : " + fileExternalInfo + ", exception : " + e.toString());
            }
        }

        LOGGER.info("************ Start of JMX file creation (JMeter script file) **");
        harForJMeter.convertHarToJmx(har, jmxOut, createNewTransactionAfterRequestMs, isAddPause, isRemoveCookie, isRemoveCacheRequest, urlFilterToInclude, urlFilterToExclude, pageStartNumber, samplerStartNumber, listTransactionInfo);
        LOGGER.info("************ End of JMX file creation              ************");

        if (!recordXmlOut.isEmpty()) {
            LOGGER.info("************ Start of Recording XML file creation ************");
            harForJMeter.harToRecordXml(har, recordXmlOut, urlFilterToInclude, urlFilterToExclude, pageStartNumber, samplerStartNumber);
            LOGGER.info("************ End of Recording XML file creation   ************");
        }
    }

    /**
     * Load the har file and return the HAR object
     * @param fileHar the har to read
     * @return the HAR object
     * @throws HarReaderException trouble when reading HAR file
     */
    protected Har loadHarFile(String fileHar) throws HarReaderException {
        Har har = new HarReader().readFromFile(new File(fileHar));
        return har;
    }

    /**
     * Create a JMeter script jmx from the Har file
     * @param har the har file to read
     * @param jmxXmlOutFile the JMeter script created
     * @param createNewTransactionAfterRequestMs how many milliseconds for creating a new Transaction Controller
     * @param isAddPause do we add Flow Control Action PAUSE ?
     * @param isRemoveCookie do we remove Cookie information ?
     * @param isRemoveCacheRequest do we remove the cache information for the Http Request ?
     * @param urlFilterToInclude the regex filter to include url
     * @param urlFilterToExclude the regex filter to exclude url
     * @param pageStartNumber the first page number
     * @param samplerStartNumber the first http sampler number
     * @param listTransactionInfo list with TransactionInfo for HAR generated from LoadRunner Web Recorder
     * @throws ParserConfigurationException regex expression is incorrect
     * @throws TransformerException Megatron we have a problem
     * @throws URISyntaxException trouble to convert String to a URI
     * @throws MalformedURLException  trouble to convert String to a URL
     */
    protected void convertHarToJmx(Har har, String jmxXmlOutFile, long createNewTransactionAfterRequestMs, boolean isAddPause, boolean isRemoveCookie, boolean isRemoveCacheRequest, String urlFilterToInclude, String urlFilterToExclude, int pageStartNumber, int samplerStartNumber, List<TransactionInfo> listTransactionInfo) throws ParserConfigurationException, TransformerException, URISyntaxException, MalformedURLException {
        XmlJmx xmlJmx = new XmlJmx();
        Document jmxDocument = xmlJmx.convertHarToJmxXml(har, createNewTransactionAfterRequestMs, isAddPause, isRemoveCookie, isRemoveCacheRequest, urlFilterToInclude, urlFilterToExclude, pageStartNumber, samplerStartNumber, listTransactionInfo);

        xmlJmx.saveXmFile(jmxDocument, jmxXmlOutFile);

    }

    /**
     * Create the Record.xml file that could be open this a Listener View Results Tree
     * @param har the har file to read
     * @param jmxXmlOutFile the xml file created
     * @param urlFilterToInclude the regex filter to include url
     * @param urlFilterToExclude the regex filter to exclude url
     * @param pageStartNumber the first page number
     * @param samplerStartNumber the first http sampler number
     * @throws ParserConfigurationException regex expression is incorrect
     * @throws TransformerException Megatron we have a problem
     * @throws URISyntaxException  trouble to convert String to a URI
     * @throws MalformedURLException trouble to convert String to a URL
     */
    protected void harToRecordXml(Har har, String jmxXmlOutFile, String urlFilterToInclude, String urlFilterToExclude, int pageStartNumber, int samplerStartNumber) throws ParserConfigurationException, TransformerException, URISyntaxException, MalformedURLException {
        Har2TestResultsXml har2TestResultsXml = new Har2TestResultsXml();
        Document jmxDocument = har2TestResultsXml.convertHarToTestResultXml(har, urlFilterToInclude, urlFilterToExclude, samplerStartNumber);

        XmlJmx.saveXmFile(jmxDocument, jmxXmlOutFile);

    }

    /**
     * Special treatment for multi-part (usually upload file)
     * @param harRequest the harRequest with multipart/form-data;
     * @return HarPostData modified with file information and others parameters
     */
    public static HarPostData extractParamsFromMultiPart(HarRequest harRequest) {
        HarPostData harPostData = harRequest.getPostData();
        String mimeType =  harPostData.getMimeType(); // "multipart/form-data; boundary=---------------------------57886876840140655003344272961"

        HarPostData harPostDataModified = new HarPostData();
        List<HarPostDataParam> listParams = new ArrayList<>();

        String boundary = StringUtils.substringAfter(mimeType,"boundary=");
        LOGGER.fine("boundary=<" + boundary + ">");
        String text = harPostData.getText();
        String[] tabParams = StringUtils.splitByWholeSeparator(text,"--" + boundary + "\r\n");
        LOGGER.info("tabParams.length=" + tabParams.length);

        for (int i = 0; i < tabParams.length; i++) {
            String paramInter = tabParams[i];
            paramInter = paramInter.substring(0,Math.min(512, paramInter.length()));
            LOGGER.fine("param=<" + paramInter + ">");
            String paramName = StringUtils.substringBetween(paramInter,"Content-Disposition: form-data; name=\"", "\"");
            LOGGER.fine("paramName=<" + paramName + ">");
            String paramNameLine = "Content-Disposition: form-data; name=\"" + paramName + "\"\rn";
            String afterParamName = paramInter.substring(paramNameLine.length());
            LOGGER.fine("afterParamName=<" + afterParamName + ">");

            String paramValue = afterParamName.trim();
            String paramValue2 = StringUtils.substringBefore(paramValue,"\r\n");
            if (paramValue2 != null) {
                paramValue = paramValue2;
            }
            LOGGER.fine("paramValue=<" + paramValue + ">");
            String fileName = StringUtils.substringBetween(paramValue,"filename=\"", "\"");
            String contentType= StringUtils.substringBetween(afterParamName,"Content-Type: ", "\r\n");
            LOGGER.fine("fileName=<" + fileName + ">");
            LOGGER.fine("contentType=<" + contentType + ">");

            HarPostDataParam harPostDataParam = new HarPostDataParam();
            harPostDataParam.setName(paramName);
            if (fileName == null) {
                harPostDataParam.setValue(paramValue);
            }
            harPostDataParam.setContentType(contentType);
            harPostDataParam.setFileName(fileName);
            listParams.add(harPostDataParam);
        }
        harPostDataModified.setParams(listParams);
        return harPostDataModified;
    }

    /**
     * Create the Command Line Parameters Options
     * @return Option CLI
     */
    private static Options createOptions() {
        Options options = new Options();

        Option helpOpt = Option.builder("help").hasArg(false).desc("Help and show parameters").build();
        options.addOption(helpOpt);

        Option harFileInOpt = Option.builder(K_HAR_IN_OPT).argName(K_HAR_IN_OPT).hasArg(true)
                .required(true).desc("Har file to read (e.g : my_file.har)").build();
        options.addOption(harFileInOpt);

        Option jmeterFileOutOpt = Option.builder(K_JMETER_FILE_OUT_OPT).argName(K_JMETER_FILE_OUT_OPT).hasArg(true)
                .required(true).desc("JMeter file created to write (e.g : script.jmx)").build();
        options.addOption(jmeterFileOutOpt);

        Option createNewTcOpt = Option.builder(K_CREATE_NEW_TC_AFTER_MS_OPT).argName(K_CREATE_NEW_TC_AFTER_MS_OPT).hasArg(true)
                .required(false)
                .desc("Optional, create new Transaction Controller after request ms, same as jmeter property : proxy.pause, need to be > 0 if set. Usefully for Har created by Firefox or Single Page Application (Angular, ReactJS, VuesJS ...)")
                .build();
        options.addOption(createNewTcOpt);

        Option addPauseOpt = Option.builder(K_ADD_PAUSE_OPT).argName(K_ADD_PAUSE_OPT).hasArg(true)
                .required(false)
                .desc("Optional boolean, add Flow Control Action Pause after Transaction Controller (default true)")
                .build();
        options.addOption(addPauseOpt);

        Option removeCookieHeaderOpt = Option.builder(K_REMOVE_COOKIE_OPT).argName(K_REMOVE_COOKIE_OPT).hasArg(true)
                .required(false)
                .desc("Optional boolean, remove cookie in http header (default true because add a Cookie Manager)")
                .build();
        options.addOption(removeCookieHeaderOpt);

        Option removeCacherHeaderRequestOpt = Option.builder(K_REMOVE_CACHE_REQUEST_OPT).argName(K_REMOVE_CACHE_REQUEST_OPT).hasArg(true)
                .required(false)
                .desc("Optional boolean, remove cache header in the http request (default true because add a Cache Manager)")
                .build();
        options.addOption(removeCacherHeaderRequestOpt);

        Option filterRegIncludeOpt = Option.builder(K_REGEX_FILTER_INCLUDE_OPT).argName(K_REGEX_FILTER_INCLUDE_OPT).hasArg(true)
                .required(false)
                .desc("Optional, regular expression to include url")
                .build();
        options.addOption(filterRegIncludeOpt);

        Option filterRegExcludeOpt = Option.builder(K_REGEX_FILTER_EXCLUDE_OPT).argName(K_REGEX_FILTER_EXCLUDE_OPT).hasArg(true)
                .required(false)
                .desc("Optional, regular expression to exclude url")
                .build();
        options.addOption(filterRegExcludeOpt);

        Option recordFileOutOpt = Option.builder(K_RECORD_FILE_OUT_OPT).argName(K_RECORD_FILE_OUT_OPT).hasArg(true)
                .required(false)
                .desc("Optional, file xml contains exchanges likes recorded by JMeter")
                .build();
        options.addOption(recordFileOutOpt);

        Option pageStartNumberOpt = Option.builder(K_PAGE_START_NUMBER).argName(K_PAGE_START_NUMBER).hasArg(true)
                .required(false)
                .desc("Optional, the start page number for partial recording (default 1)")
                .build();
        options.addOption(pageStartNumberOpt);

        Option samplerStartNumberOpt = Option.builder(K_SAMPLER_START_NUMBER).argName(K_SAMPLER_START_NUMBER).hasArg(true)
                .required(false)
                .desc("Optional, the start sampler number for partial recording (default 1)")
                .build();
        options.addOption(samplerStartNumberOpt);

        Option lrwrUseInfosOpt = Option.builder(K_LRWR_USE_INFOS).argName(K_LRWR_USE_INFOS).hasArg(true)
                .required(false)
                .desc("Optional, the har file has been generated with LoadRunner Web Recorder and contains Transaction Name, expected value : 'transaction_name' or don't add this parameter")
                .build();
        options.addOption(lrwrUseInfosOpt);


        Option externalFileInfosOpt = Option.builder(K_EXTERNAL_FILE_INFOS).argName(K_EXTERNAL_FILE_INFOS).hasArg(true)
                .required(false)
                .desc("Optional, csv file contains external infos : timestamp transaction name and start or end")
                .build();
        options.addOption(externalFileInfosOpt);

        return options;
    }

    /**
     * Convert the main args parameters to properties
     * @param optionsP the command line options declared
     * @param args the cli parameters
     * @return properties
     * @throws ParseException can't parse command line parmeter
     * @throws MissingOptionException a parameter is mandatory but not present
     */
    private static Properties parseOption(Options optionsP, String[] args)
            throws ParseException, MissingOptionException {
        Properties properties = new Properties();

        CommandLineParser parser = new DefaultParser();
        // parse the command line arguments
        CommandLine line = parser.parse(optionsP, args);

        if (line.hasOption("help")) {
            properties.setProperty("help", "help value");
            return properties;
        }

        if (line.hasOption(K_HAR_IN_OPT)) {
            properties.setProperty(K_HAR_IN_OPT, line.getOptionValue(K_HAR_IN_OPT));
        }

        if (line.hasOption(K_JMETER_FILE_OUT_OPT)) {
            properties.setProperty(K_JMETER_FILE_OUT_OPT, line.getOptionValue(K_JMETER_FILE_OUT_OPT));
        }

        if (line.hasOption(K_CREATE_NEW_TC_AFTER_MS_OPT)) {
            properties.setProperty(K_CREATE_NEW_TC_AFTER_MS_OPT, line.getOptionValue(K_CREATE_NEW_TC_AFTER_MS_OPT));
        }

        if (line.hasOption(K_ADD_PAUSE_OPT)) {
            properties.setProperty(K_ADD_PAUSE_OPT, line.getOptionValue(K_ADD_PAUSE_OPT));
        }

        if (line.hasOption(K_REMOVE_COOKIE_OPT)) {
            properties.setProperty(K_REMOVE_COOKIE_OPT, line.getOptionValue(K_REMOVE_COOKIE_OPT));
        }

        if (line.hasOption(K_REMOVE_CACHE_REQUEST_OPT)) {
            properties.setProperty(K_ADD_PAUSE_OPT, line.getOptionValue(K_ADD_PAUSE_OPT));
        }

        if (line.hasOption(K_REGEX_FILTER_INCLUDE_OPT)) {
            properties.setProperty(K_REGEX_FILTER_INCLUDE_OPT, line.getOptionValue(K_REGEX_FILTER_INCLUDE_OPT));
        }

        if (line.hasOption(K_REGEX_FILTER_EXCLUDE_OPT)) {
            properties.setProperty(K_REGEX_FILTER_EXCLUDE_OPT, line.getOptionValue(K_REGEX_FILTER_EXCLUDE_OPT));
        }

        if (line.hasOption(K_RECORD_FILE_OUT_OPT)) {
            properties.setProperty(K_RECORD_FILE_OUT_OPT, line.getOptionValue(K_RECORD_FILE_OUT_OPT));
        }

        if (line.hasOption(K_PAGE_START_NUMBER)) {
            properties.setProperty(K_PAGE_START_NUMBER, line.getOptionValue(K_PAGE_START_NUMBER));
        }

        if (line.hasOption(K_SAMPLER_START_NUMBER)) {
            properties.setProperty(K_SAMPLER_START_NUMBER, line.getOptionValue(K_SAMPLER_START_NUMBER));
        }

        if (line.hasOption(K_LRWR_USE_INFOS)) {
            properties.setProperty(K_LRWR_USE_INFOS, line.getOptionValue(K_LRWR_USE_INFOS));
        }

        if (line.hasOption(K_EXTERNAL_FILE_INFOS)) {
            properties.setProperty(K_EXTERNAL_FILE_INFOS, line.getOptionValue(K_EXTERNAL_FILE_INFOS));
        }
        return properties;
    }

    /**
     * Help to command line parameters
     * @param options the command line options declared
     */
    private static void helpUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        String footer = "E.g : java -jar har-for-jmeter-<version>-jar-with-dependencies.jar -" + K_HAR_IN_OPT + " myhar.har -" + K_JMETER_FILE_OUT_OPT + " scriptout.jmx -"
                + K_CREATE_NEW_TC_AFTER_MS_OPT + " 5000 -" + K_ADD_PAUSE_OPT + " true -" + K_REGEX_FILTER_INCLUDE_OPT + " \"https://mysite/.*\" -" + K_REGEX_FILTER_EXCLUDE_OPT + " \"https://notmysite/*\" -"
                + K_PAGE_START_NUMBER + " 50 -" + K_SAMPLER_START_NUMBER + " 250 \n";

        formatter.printHelp(120, HarForJMeter.class.getName(),
                HarForJMeter.class.getName(), options, footer, true);
    }

}
