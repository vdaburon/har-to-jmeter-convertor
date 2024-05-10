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

package io.github.vdaburon.jmeter.har.common;

import java.util.Objects;

public class TransactionInfo {
    private String name;
    private String beginDateTime;
    private String endDateTime = "2054-01-01T10:10:10.001Z"; // a far futur date by default for transaction with start but no stop

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBeginDateTime() {
        return beginDateTime;
    }

    public void setBeginDateTime(String beginDateTime) {
        this.beginDateTime = beginDateTime;
    }

    public String getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(String endDateTime) {
        this.endDateTime = endDateTime;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TransactionInfo{");
        sb.append("name='").append(name).append('\'');
        sb.append(", beginDateTime='").append(beginDateTime).append('\'');
        sb.append(", endDateTime='").append(endDateTime).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionInfo)) {
            return false;
        }
        TransactionInfo that = (TransactionInfo) o;
        return Objects.equals(name, that.name) && Objects.equals(beginDateTime, that.beginDateTime) && Objects.equals(endDateTime, that.endDateTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, beginDateTime, endDateTime);
    }
}
