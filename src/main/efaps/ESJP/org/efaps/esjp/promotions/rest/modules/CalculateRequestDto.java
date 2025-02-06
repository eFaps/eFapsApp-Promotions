/*
 * Copyright © 2003 - 2024 The eFaps Team (-)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.efaps.esjp.promotions.rest.modules;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = CalculateRequestDto.Builder.class)
@EFapsUUID("e90245cf-bd0f-4234-9de4-9a4cadfdd80c")
@EFapsApplication("eFapsApp-Promotions")
public class CalculateRequestDto
{

    private final List<CalculateRequestPositionDto> items;

    private final LocalDate date;

    private final List<String> promotionOids;

    private CalculateRequestDto(Builder builder)
    {
        this.items = builder.items;
        this.date = builder.date;
        this.promotionOids = builder.promotionOids;
    }

    public List<CalculateRequestPositionDto> getItems()
    {
        return items;
    }

    public LocalDate getDate()
    {
        return date;
    }

    public List<String> getPromotionOids()
    {
        return promotionOids;
    }

    @Override
    public String toString()
    {
        return ToStringBuilder.reflectionToString(this);
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {

        private List<CalculateRequestPositionDto> items = Collections.emptyList();
        private LocalDate date;
        private List<String> promotionOids;

        private Builder()
        {
        }

        public Builder withItems(List<CalculateRequestPositionDto> items)
        {
            this.items = items;
            return this;
        }

        public Builder withDate(LocalDate date)
        {
            this.date = date;
            return this;
        }

        public Builder withPromotionOids(final List<String> promotionOids)
        {
            this.promotionOids = promotionOids;
            return this;
        }

        public CalculateRequestDto build()
        {
            return new CalculateRequestDto(this);
        }
    }
}
