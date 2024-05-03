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
package org.efaps.esjp.promotions.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.AbstractCommand;
import org.efaps.admin.ui.field.Field;
import org.efaps.db.Instance;
import org.efaps.eql.EQL;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CIPromo;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.promotions.PromotionService;
import org.efaps.esjp.ui.rest.provider.ITableProvider;
import org.efaps.util.EFapsException;

@EFapsUUID("1aa3763e-0e4d-4190-9c83-459d726bf2a5")
@EFapsApplication("eFapsApp-Promotions")
public class ProductsEQLConditionPreviewProvider
    implements ITableProvider
{

    @Override
    public Collection<Map<String, ?>> getValues(final AbstractCommand cmd,
                                                final List<Field> fields,
                                                final Map<String, String> properties,
                                                final String oid)
        throws EFapsException
    {
        final List<Map<String, ?>> values = new ArrayList<>();
        final var conditionInstance = Instance.get(oid);
        if (InstanceUtils.isType(conditionInstance, CIPromo.ProductsEQLCondition)) {
            final var prodOids = PromotionService.evalProductOids4EQL(conditionInstance);
            if (!prodOids.isEmpty()) {
                final var eval = EQL.builder().print(prodOids.toArray(String[]::new))
                                .attribute(CIProducts.ProductAbstract.Type).label().as("type")
                                .attribute(CIProducts.ProductAbstract.Name).as("name")
                                .attribute(CIProducts.ProductAbstract.Description).as("description")
                                .evaluate();
                while (eval.next()) {
                    final Map<String, Object> map = new HashMap<>();
                    values.add(map);
                    map.put("OID", eval.inst().getOid());
                    map.put("type", eval.get("type"));
                    map.put("name", eval.get("name"));
                    map.put("description", eval.get("description"));
                }
            }
        }
        return values;
    }

}
