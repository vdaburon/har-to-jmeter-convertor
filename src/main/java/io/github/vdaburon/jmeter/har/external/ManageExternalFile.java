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

package io.github.vdaburon.jmeter.har.external;

import io.github.vdaburon.jmeter.har.Utils;
import io.github.vdaburon.jmeter.har.common.TransactionInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * This class read csv external file that contains transaction information with timestamp, transaction name, start or end
 */
public class ManageExternalFile {

    public static final String K_ELEMENT_TRANSACTION = "TRANSACTION";
    public static final String K_ELEMENT_COMMENT = "COMMENT"; // COMMENT is not yet used but a valid line
    public static final String K_TYPE_START = "start";
    public static final String K_TYPE_STOP = "stop";

    public static final String K_FILE_IN_SEP = ";";
    public static final String K_FILE_IN_DEFAULT_CHARSET = "UTF-8";

    private static final Logger LOGGER = Logger.getLogger(ManageExternalFile.class.getName());

    /**
     * Read the csv file with transaction infos
     * @param fileName the csv file to read and parse, e.g. one line : 2024-05-07T07:56:40.513Z;TRANSACTION;home_page;start
     * @return a list of TransactionInfo
     */
    public static List<TransactionInfo> createListTransactionInfo(String fileName) {
        LOGGER.info("Read external file: " + fileName);
        if (fileName == null) {
            throw new InvalidParameterException("fileName must be not null");
        }

        /*
        TIME_STAMP;ELEMENT;NAME;TYPE
        2024-05-07T07:56:40.513Z;TRANSACTION;home_page;start
        2024-05-07T07:56:56.261Z;TRANSACTION;home_page;stop
        2024-05-07T07:57:08.679Z;TRANSACTION;bt_authent;start
        2024-05-07T07:57:10.123;COMMENT;user Lida ESPA;
        */
        ArrayList<TransactionInfo> listTransactionInfo = new ArrayList();

        BufferedReader in = null;
        try {
            File file = new File(fileName);
            FileInputStream fis = new FileInputStream(file);
            InputStreamReader isr = new InputStreamReader(fis, Charset.forName(K_FILE_IN_DEFAULT_CHARSET));

            in = new BufferedReader(isr);
            String line = null;
            while ((line = in.readLine()) != null) {
                // TIME_STAMP;ELEMENT;NAME;TYPE
                List<String> items = Arrays.asList(line.split(K_FILE_IN_SEP));
                if (items.size() != 4) {
                    continue;
                }

                String dateTimestamp = items.get(0);
                String element = items.get(1);
                String name = items.get(2);
                if (!name.isEmpty()) {
                    name = name.trim();
                }
                String type = items.get(3);
                if (!type.isEmpty()) {
                    type = type.trim();
                }

                if (K_ELEMENT_TRANSACTION.equals(element)) {

                    if (K_TYPE_START.equals(type)) {
                        TransactionInfo transactionInfo = new TransactionInfo();
                        transactionInfo.setName(name);
                        transactionInfo.setBeginDateTime(dateTimestamp);
                        listTransactionInfo.add(transactionInfo);
                    } else if (K_TYPE_STOP.equals(type)) {
                        for (int j = listTransactionInfo.size() -1 ; j >= 0; j--) {
                            TransactionInfo transactionInfoInter = listTransactionInfo.get(j);
                            if (transactionInfoInter.getName().equals(name)) {
                                transactionInfoInter.setEndDateTime(dateTimestamp);
                                listTransactionInfo.set(j,transactionInfoInter);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // silent closing
                }
            }
        }

        // verify not null for begin date or end date to avoid future exception
        for (int i = 0; i < listTransactionInfo.size(); i++) {
            TransactionInfo transactionInfoInter = listTransactionInfo.get(i);
            if (transactionInfoInter.getBeginDateTime() == null) {
                transactionInfoInter.setBeginDateTime(Utils.dateToIsoFormat(new Date()));
                listTransactionInfo.set(i,transactionInfoInter);
            }

            if (transactionInfoInter.getEndDateTime() == null) {
                transactionInfoInter.setEndDateTime(transactionInfoInter.getBeginDateTime());
                listTransactionInfo.set(i,transactionInfoInter);
            }
        }
        LOGGER.info("External infos : " + listTransactionInfo);
        return listTransactionInfo;
    }
}
