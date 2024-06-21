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
package org.efaps.esjp.ci;

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsNoUpdate;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIAttribute;

@EFapsUUID("9fd82515-9574-402e-9dab-b6e3f3d88264")
@EFapsApplication("eFapsApp-Promotions")
@EFapsNoUpdate
public final class CIPOS
{

    public static final _Order Order = new _Order("b09e3032-4432-4941-8c23-8bc943e422d0");

    public static class _Order
        extends org.efaps.esjp.ci.CISales._DocumentSumAbstract
    {

        protected _Order(final String _uuid)
        {
            super(_uuid);
        }

        public final CIAttribute BackendLink = new CIAttribute(this, "BackendLink");
        public final CIAttribute Status = new CIAttribute(this, "Status");
    }

    public static final _Ticket Ticket = new _Ticket("c5a16753-ebad-497c-8432-c160913fec29");

    public static class _Ticket
        extends org.efaps.esjp.ci.CISales._DocumentSumAbstract
    {

        protected _Ticket(final String _uuid)
        {
            super(_uuid);
        }

        public final CIAttribute Status = new CIAttribute(this, "Status");
    }

}
