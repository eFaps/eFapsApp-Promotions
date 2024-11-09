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
package org.efaps.esjp.promotions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.stmt.selection.Evaluator;
import org.efaps.eql.EQL;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CIPromo;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.ui.rest.modules.dto.CSVImportRequestDto;
import org.efaps.esjp.ui.rest.provider.ICSVImportProvider;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EFapsUUID("6347160c-58f0-4e3f-adf4-7138a920b884")
@EFapsApplication("eFapsApp-Promotions")
public class ImportProductsFromCSV
    implements ICSVImportProvider
{

    private static final Logger LOG = LoggerFactory.getLogger(ImportProductsFromCSV.class);

    @Override
    public List<String> validate(final CSVImportRequestDto requestDto)
    {
        final List<String> messages = new ArrayList<>();
        final var conditionInst = Instance.get(requestDto.getParentOid());
        // check correct instance
        if (InstanceUtils.isType(conditionInst, CIPromo.ProductsCondition)) {
            // check correct fields
            final var fields = requestDto.getResult().getMeta().getFields();
            if (fields.size() != 1) {
                messages.add("El csv solo puede contener una columna 'sku' u 'oid'");
                return messages;
            } else {
                final var field = fields.get(0);
                if (field.equals("sku") || field.equals("oid")) {
                    messages.addAll(validateData(field, requestDto.getResult().getData()));
                } else {
                    messages.add("El csv solo puede contener una columna 'sku' u 'oid'");
                    return messages;
                }
            }
        } else {
            messages.add("No se puede importar para este tipo de objecto");
        }
        return messages;
    }

    protected List<String> validateData(final String key,
                                        final List<Map<String, Object>> data)
    {
        final List<String> messages = new ArrayList<>();
        final Set<String> entries = new HashSet<>();
        final var iter = data.iterator();
        while (iter.hasNext()) {
            final var values = iter.next();
            final var entry = values.get(key);
            if (entry == null) {
                messages.add("Valor vacio no esta permitido");
            } else {
                if (entries.contains(entry)) {
                    messages.add("Valor duplicado: " + entry);
                }
                if (StringUtils.isNotEmpty(Objects.toString(entry))) {
                    entries.add(Objects.toString(entry));
                } else if (iter.hasNext()) {
                    messages.add("Valor vacio no esta permitido");
                }
            }
        }
        try {
            for (final var entry : entries) {
                final Evaluator eval = getEvaluator(key, entry);
                if (!eval.next()) {
                    messages.add("No se econtro un producto para: " + entry);
                }
                if (eval.next()) {
                    messages.add("Mas que un producto econtrado para: " + entry);
                }
            }
        } catch (final Exception e) {
            LOG.error("Catched", e);
            messages.add("No se podria leer el csv");
        }
        return messages;
    }

    protected Evaluator getEvaluator(final String key,
                                     final String entry)
        throws EFapsException
    {
        final Evaluator eval;
        if (key.equals("sku")) {
            eval = EQL.builder().print().query(CIProducts.ProductAbstract)
                            .where()
                            .attribute(CIProducts.ProductAbstract.Name).eq(entry)
                            .select()
                            .attribute(CIProducts.ProductAbstract.ID)
                            .evaluate();
        } else {
            eval = EQL.builder().print(entry)
                            .attribute(CIProducts.ProductAbstract.ID)
                            .evaluate();
        }
        return eval;
    }

    @Override
    public void execute(final CSVImportRequestDto requestDto)
        throws EFapsException
    {
        final var conditionInst = Instance.get(requestDto.getParentOid());
        final var key = requestDto.getResult().getMeta().getFields().get(0);
        final var iter = requestDto.getResult().getData().iterator();
        while (iter.hasNext()) {
            final var entry = Objects.toString(iter.next().get(key));
            if (StringUtils.isNotEmpty(entry)) {
                final Evaluator eval = getEvaluator(key, entry);
                eval.next();
                EQL.builder().insert(CIPromo.ProductsCondition2ProductAbstract)
                                .set(CIPromo.ProductsCondition2ProductAbstract.FromLink, conditionInst)
                                .set(CIPromo.ProductsCondition2ProductAbstract.ToLink, eval.inst())
                                .execute();
            }
        }
    }

    // to prevent an error in the log
    public Return execute(final Parameter _parameter)
        throws EFapsException
    {
        return null;
    }
}
