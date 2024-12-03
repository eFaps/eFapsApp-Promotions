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

import java.math.BigDecimal;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = CalculateRequestPositionDto.Builder.class)
@EFapsUUID("12b02aa7-776b-4331-9834-844cdb997ddb")
@EFapsApplication("eFapsApp-Promotions")
public class CalculateRequestPositionDto
{
    private final Integer index;

    private final BigDecimal quantity;

    private final String productOid;

    private CalculateRequestPositionDto(Builder builder)
    {
        this.index = builder.index;
        this.quantity = builder.quantity;
        this.productOid = builder.productOid;
    }

    public Integer getIndex()
    {
        return index;
    }


    public BigDecimal getQuantity()
    {
        return quantity;
    }


    public String getProductOid()
    {
        return productOid;
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

        private Integer index;
        private BigDecimal quantity;
        private String productOid;

        private Builder()
        {
        }

        public Builder withIndex(Integer index)
        {
            this.index = index;
            return this;
        }

        public Builder withQuantity(BigDecimal quantity)
        {
            this.quantity = quantity;
            return this;
        }

        public Builder withProductOid(String productOid)
        {
            this.productOid = productOid;
            return this;
        }

        public CalculateRequestPositionDto build()
        {
            return new CalculateRequestPositionDto(this);
        }
    }

}
