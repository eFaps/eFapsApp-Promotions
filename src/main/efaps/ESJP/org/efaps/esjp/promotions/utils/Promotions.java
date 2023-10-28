/*
 * Copyright 2003 - 2023 The eFaps Team
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
 *
 */
package org.efaps.esjp.promotions.utils;

import java.util.UUID;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.IEnum;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.api.annotation.EFapsSysConfAttribute;
import org.efaps.api.annotation.EFapsSystemConfiguration;
import org.efaps.esjp.admin.common.systemconfiguration.BooleanSysConfAttribute;
import org.efaps.util.cache.CacheReloadException;

@EFapsUUID("73db3d9c-9d5d-49c2-a779-2b8b8e7ae707")
@EFapsApplication("eFapsApp-Promotions")
@EFapsSystemConfiguration("e3055a0d-6b3d-4d44-88df-a3042400260b")
public class Promotions
{

    public static final String BASE = "org.efaps.promotions.";

    /** Promotions-Configuration. */
    public static final UUID SYSCONFUUID = UUID.fromString("e3055a0d-6b3d-4d44-88df-a3042400260b");

    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute ACTIVATE = new BooleanSysConfAttribute()
                    .sysConfUUID(Promotions.SYSCONFUUID)
                    .key(Promotions.BASE + "Activate")
                    .description("Activate promotions.");

    public enum ConditionContainer implements IEnum
    {

        SOURCE, TARGET;

        @Override
        public int getInt()
        {
            return ordinal();
        }
    }

    public enum EntryOperator implements IEnum
    {

        INCLUDES_ANY, INCLUDES_ALL, EXCLUDES;

        @Override
        public int getInt()
        {
            return ordinal();
        }
    }

    public static SystemConfiguration getSysConfig()
        throws CacheReloadException
    {
        return SystemConfiguration.get(SYSCONFUUID);
    }
}
