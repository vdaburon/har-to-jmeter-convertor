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

package io.github.vdaburon.jmeter.har.lrwr;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import io.github.vdaburon.jmeter.har.common.TransactionInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * This class managed a HAR file generated with the LoadRunner Web Recorder (lrwr), specially the _transaction array in the JSON file.
 */
public class ManageLrwr {

    private static final Logger LOGGER = Logger.getLogger(ManageLrwr.class.getName());

    /**
     * It is a HAR generated with LoadRunner Web Recorder and contains an array of _transactions ?
     * @param harIn the har file that could be generated with the LoadRunner Web Recorder
     * @return true if contains an array of _transactions else false if no _transaction or empty array
     */
    public static boolean isHarContainsLrwr(String harIn) {
        boolean isContainsLrTransactions = false;
        List listHarLrTransactions = getListTransactionLrwr(harIn);
        if (listHarLrTransactions != null && listHarLrTransactions.size() > 0) {
            isContainsLrTransactions = true;
        }
        return isContainsLrTransactions;
    }

    /**
     * Get the list of HarLrTransaction, usually call the isHarContainsLrwr before and if the result is true call this method
     * @param harIn the har file that could be generated with the LoadRunner Web Recorder
     * @return the list of HarLrTransaction or null if _transaction not exist or array empty
     */
    public static  List<HarLrTransactions> getListTransactionLrwr(String harIn) {
        List<HarLrTransactions> listHarLrTransactions = null;

        File fileHarIn = new File(harIn);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fileHarIn);
        } catch (FileNotFoundException e) {
            return listHarLrTransactions;
        }

        Object document = Configuration.defaultConfiguration().jsonProvider().parse(fis,"UTF-8");

        //String version = JsonPath.read(document, "$.log.version");
        //System.out.println("version=" + version);

        try {
            ArrayList jsonArray = JsonPath.read(document, "$.log._transactions[*]");
            LOGGER.fine("jsonArray.size=" + jsonArray.size());

            listHarLrTransactions = new ArrayList() ;

            for (int i = 0; i < jsonArray.size(); i++) {
                // transforme the JSON array of LinkedHashMap to a List of HarLrTransactions
                LinkedHashMap lhm = (LinkedHashMap) jsonArray.get(i);
                LOGGER.fine("lhm=" +lhm);
                HarLrTransactions harLrTransactions = new HarLrTransactions();
                harLrTransactions.setName((String) lhm.get("name"));
                String sType = (String) lhm.get("type");
                harLrTransactions.setType(sType);
                harLrTransactions.setStartedDateTime((String) lhm.get("startedDateTime"));
                listHarLrTransactions.add(harLrTransactions);
            }

        } catch (com.jayway.jsonpath.PathNotFoundException e) {
            // no _transactions
            listHarLrTransactions = null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // close silently
                }
            }
        }
        return listHarLrTransactions;
    }

    /**
     * Transform the list of HarLrTransactions to a new List of TransactionInfo, because it's easier to search for information between begin date and end date
     * @param listHarLrTransactions the List listHarLrTransactions, must be not null or InvalidParameterException
     * @return the list of TransactionInfo
     */
    public static List<TransactionInfo> createListTransactionInfo(List<HarLrTransactions> listHarLrTransactions) {
        if (listHarLrTransactions == null) {
            throw new InvalidParameterException("listHarLrTransactions must be not null");
        }

        ArrayList<TransactionInfo> listTransactionInfo = new ArrayList();
        for (int i = 0; i < listHarLrTransactions.size(); i++) {
            HarLrTransactions harLrTransactions = listHarLrTransactions.get(i);

            if (HarLrTransactions.K_TYPE_START.equals(harLrTransactions.getType())) {
                TransactionInfo transactionInfo = new TransactionInfo();
                transactionInfo.setName((String) harLrTransactions.getName());
                transactionInfo.setBeginDateTime(harLrTransactions.getStartedDateTime());
                listTransactionInfo.add(transactionInfo);
            }

            // start with the last value (reverse)
            if (HarLrTransactions.K_TYPE_STOP.equals(harLrTransactions.getType())) {
                for (int j = listTransactionInfo.size() -1 ; j >= 0; j--) {
                    TransactionInfo transactionInfo = listTransactionInfo.get(j);
                    if (transactionInfo.getName().equals(harLrTransactions.getName())) {
                        transactionInfo.setEndDateTime(harLrTransactions.getStartedDateTime());
                        listTransactionInfo.set(j,transactionInfo);
                        break;
                    }
                }
            }

        }
        return listTransactionInfo;
    }

    /**
     * Get the last TransactionInfo from the listTransactionInfo when the parameter startedDateTime is between begin date and end date
     * @param startedDateTime the date to search the corresponding TransactionInfo
     * @param listTransactionInfo the listTransactionInfo contains all TransactionInfo
     * @return if a TransactionInfo which includes start and end dates from startedDateTime exists return this TransactionInfo else return null
     */
    public static TransactionInfo getTransactionInfoAroundDateTime(String startedDateTime, List<TransactionInfo> listTransactionInfo) {
        boolean isFind = false;
        int nbElts = listTransactionInfo.size();
        int i = 0;
        TransactionInfo transactionInfoFind = null;

        while (i < nbElts) {
            TransactionInfo transactionInfo = listTransactionInfo.get(i);
            if (transactionInfo.getBeginDateTime().compareTo(startedDateTime) <= 0 && transactionInfo.getEndDateTime().compareTo(startedDateTime) >= 0) {
                isFind = true;
                transactionInfoFind = transactionInfo;
            }
            i++;
        }

        if (isFind) {
            LOGGER.fine("transactionInfoFind=" + transactionInfoFind + " for startedDateTime=" + startedDateTime);
        } else {
            LOGGER.fine("NOT FIND for startedDateTime=" + startedDateTime);
        }
        return transactionInfoFind;
    }
}
