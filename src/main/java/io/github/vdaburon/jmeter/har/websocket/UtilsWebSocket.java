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

import org.apache.commons.lang3.StringUtils;

public class UtilsWebSocket {

    public static final String K_PAY_LOAD_TYPE_TEXT = "Text";
    public static final String K_PAY_LOAD_TYPE_TEXT_STOMP = "TextStomp";

    public static String filterData(String data) {
        String sReturn = data;
        if (sReturn != null && !sReturn.isEmpty()) {
            sReturn = replaceDataEndNull(data);
            sReturn = removeContentLength(sReturn);
        }
        return sReturn;
    }

    public static String replaceDataEndNull(String data) {
        String sReturn = data.replace("\u0000", "^@");
        return sReturn;
    }

    public static String removeContentLength(String data) {
        String sAfter = StringUtils.replacePattern(data, "(?i)\ncontent-length: ?\\d+\n\n", "\n\n");
        return sAfter;
    }

    public static String getPayLoadType(String data) {
        String sPayloadType = K_PAY_LOAD_TYPE_TEXT;
        if (data != null && data.endsWith("\u0000")) {
            sPayloadType = K_PAY_LOAD_TYPE_TEXT_STOMP;
        }
        return sPayloadType;
    }
}
