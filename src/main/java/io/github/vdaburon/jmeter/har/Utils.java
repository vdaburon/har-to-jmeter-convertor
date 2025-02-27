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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class Utils {

    public static String dateToIsoFormat(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        // 2024-05-03T14:30:42.271Z
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String sDateIso = sdf.format(calendar.getTime());
        return sDateIso;
    }



    public static String doubleEpocMicroToIsoFormat(double epocSecMicro) {
        // dTimeMicro
        double dInterMilli = (epocSecMicro * 1000);
        long epocMillis = (long) dInterMilli;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(epocMillis);
        // 2024-05-03T14:30:42.271Z
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String sDateIso = sdf.format(calendar.getTime());
        return sDateIso;
    }

    public static long dateIsoFormatToTimeLong(String  sDateIso) throws ParseException {
        // 2024-05-03T14:30:42.271Z
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date dateIso = sdf.parse(sDateIso);
        return dateIso.getTime();
    }

    /**
     * Extract the mimeType without charset,
     *    e.g: "application/x-www-form-urlencoded; charset=UTF-8" will return "application/x-www-form-urlencoded"
     *    e.g : "multipart/form-data; boundary=----WebKitFormBoundaryqBhyHjcDVm2CaflU" will return "multipart/form-data"
     *    e.g: "text/xml" return "text/xml"
     * @param mimeTypeToExtract the mimeType in the JSON HAR, attribut name : mimeType
     * @return the mimeType without charset
     */
    public static String extractMimeType(String mimeTypeToExtract) {
        if (mimeTypeToExtract == null) {
            return "";
        }

        String mimeTypeInter = mimeTypeToExtract.toLowerCase();
        String[] tabSplitMime = org.apache.commons.lang3.StringUtils.splitPreserveAllTokens(mimeTypeInter,';');
        String mimeType = "";
        if (tabSplitMime.length > 1) {
            for (int i = 0; i < tabSplitMime.length; i++) {
                String param = tabSplitMime[i];
                if (!param.contains("charset=") && !param.contains("boundary=")) {
                    mimeType = param;
                    break;
                }
            }
        } else {
            mimeType = mimeTypeToExtract;
        }
        return mimeType;
    }
}
