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

import java.io.File;
import java.io.IOException;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.eql.EQL;
import org.efaps.esjp.ci.CIPromo;
import org.efaps.esjp.common.file.FileUtil;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.ui.util.ValueUtils;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EFapsUUID("54d47c09-8a7a-46c1-9ccf-6329abb83ffe")
@EFapsApplication("eFapsApp-Promotions")
public class Promotion
{

    private static final Logger LOG = LoggerFactory.getLogger(Promotion.class);

    public Return createJson(final Parameter _parameter)
        throws EFapsException
    {
        final var ret = new Return();
        final var instance = _parameter.getInstance();
        final var promotion = new PromotionService().getPromotion(instance);
        if (promotion != null) {
            final var objectMapper = ValueUtils.getObjectMapper();

            final File file = new FileUtil().getFile(promotion.getName(), "json");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, promotion);
                if (file != null) {
                    ret.put(ReturnValues.VALUES, file);
                    ret.put(ReturnValues.TRUE, true);
                }
            } catch (final IOException e) {
                LOG.error("Catched", e);
            }
        }
        return ret;
    }

    public Return activate(final Parameter parameter)
        throws EFapsException
    {
        final var ret = new Return();
        final var promotInst = parameter.getInstance();
        if (validate(promotInst)) {
            EQL.builder().update(promotInst).set(CIPromo.Promotion.Status, CIPromo.PromotionStatus.Active).execute();
        }
        return ret;
    }

    public boolean validate(final Instance promotInst)
        throws EFapsException
    {
        boolean ret = false;
        if (InstanceUtils.isType(promotInst, CIPromo.Promotion)) {
            ret = true;
            final var eval = EQL.builder().print().query(CIPromo.ProductsEQLCondition)
                            .where()
                            .attribute(CIPromo.ProductsEQLCondition.PromotionLink)
                            .eq(promotInst)
                            .select()
                            .attribute(CIPromo.ProductsEQLCondition.OID)
                            .evaluate();
            while (eval.next()) {
                final var eqlEval = EQL.builder().print().query(CIPromo.EQLAbstract)
                    .where()
                    .attribute(CIPromo.EQLAbstract.ConditionLink)
                    .eq(eval.inst())
                    .select()
                    .attribute(CIPromo.EQLAbstract.OID)
                    .evaluate();
                if (!eqlEval.next()) {
                    ret = false;
                }
            }
            if (!ret) {
                LOG.warn("Setting to active not allowed for: {}", promotInst.getOid());
            }
        }
        return ret;
    }

}
