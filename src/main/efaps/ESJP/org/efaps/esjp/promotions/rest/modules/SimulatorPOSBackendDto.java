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

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = SimulatorPOSBackendDto.Builder.class)
@EFapsUUID("c6de022a-cde5-4887-9da4-c96236fbb91d")
@EFapsApplication("eFapsApp-Promotions")
public class SimulatorPOSBackendDto
{

    private final String oid;
    private final String name;
    private final String description;
    private final String identifier;

    private SimulatorPOSBackendDto(Builder builder)
    {
        this.oid = builder.oid;
        this.name = builder.name;
        this.description = builder.description;
        this.identifier = builder.identifier;
    }

    public String getOid()
    {
        return oid;
    }

    public String getName()
    {
        return name;
    }

    public String getDescription()
    {
        return description;
    }

    public String getIdentifier()
    {
        return identifier;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {

        private String oid;
        private String name;
        private String description;
        private String identifier;

        private Builder()
        {
        }

        public Builder withOid(String oid)
        {
            this.oid = oid;
            return this;
        }

        public Builder withName(String name)
        {
            this.name = name;
            return this;
        }

        public Builder withDescription(String description)
        {
            this.description = description;
            return this;
        }

        public Builder withIdentifier(String identifier)
        {
            this.identifier = identifier;
            return this;
        }

        public SimulatorPOSBackendDto build()
        {
            return new SimulatorPOSBackendDto(this);
        }
    }
}
