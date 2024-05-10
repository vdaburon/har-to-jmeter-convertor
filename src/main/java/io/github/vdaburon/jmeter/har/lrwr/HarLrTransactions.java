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

import java.util.Objects;

public class HarLrTransactions {

    /* New _transactions from LoadRunner Web Recorder (chrome extension)

            "_transactions": [
            {
                "name": "dmd_form_login",
                "type": "start",
                "startedDateTime": "2024-05-03T14:37:35.794Z"
            },
            {
                "name": "dmd_form_login",
                "type": "stop",
                "startedDateTime": "2024-05-03T14:37:42.206Z"
            },
            {
                "name": "login",
                "type": "start",
                "startedDateTime": "2024-05-03T14:38:09.680Z"
            },
            {
                "name": "login",
                "type": "stop",
                "startedDateTime": "2024-05-03T14:38:16.698Z"
            }
            ]
     */

    public static final String K_TYPE_START = "start";
    public static final String K_TYPE_STOP = "stop";

    private String startedDateTime;
    private String name;
    private String type; // start or stop

    public String getStartedDateTime() {
        return startedDateTime;
    }

    public void setStartedDateTime(String startedDateTime) {
        this.startedDateTime = startedDateTime;
    }

    /**
     * @return name, null if not present.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return _transaction type, null if not present.
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HarLrTransactions)) {
            return false;
        }
        HarLrTransactions that = (HarLrTransactions) o;
        return Objects.equals(startedDateTime, that.startedDateTime) && Objects.equals(name, that.name) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startedDateTime, name, type);
    }
}
