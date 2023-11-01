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
package org.efaps.esjp.promotions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.EnumUtils;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.eql.EQL;
import org.efaps.esjp.ci.CIPromo;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.promotions.utils.Promotions.ConditionContainer;
import org.efaps.esjp.promotions.utils.Promotions.EntryOperator;
import org.efaps.promotionengine.action.PercentageDiscountAction;
import org.efaps.promotionengine.condition.ICondition;
import org.efaps.promotionengine.condition.ProductsCondition;
import org.efaps.promotionengine.promotion.Promotion;
import org.efaps.util.EFapsException;

@EFapsUUID("26c36418-2e96-4d89-87c4-ad3740bba939")
@EFapsApplication("eFapsApp-Promotions")
public class PromotionService
{

    public List<Promotion> getPromotions()
        throws EFapsException
    {
        final var promotions = new ArrayList<Promotion>();
        final var promoEval = EQL.builder().print().query(CIPromo.PromotionAbstract)
                        .where()
                        .attribute(CIPromo.PromotionAbstract.StatusAbstract)
                        .eq(CIPromo.PromotionStatus.Active)
                        .select()
                        .attribute(CIPromo.PromotionAbstract.Name, CIPromo.PromotionAbstract.Description,
                                        CIPromo.PromotionAbstract.Priority, CIPromo.PromotionAbstract.StartDateTime,
                                        CIPromo.PromotionAbstract.EndDateTime)
                        .evaluate();
        while (promoEval.next()) {
            final var promotionBldr = Promotion.builder()
                            .withOid(promoEval.inst().getOid())
                            .withName(promoEval.get(CIPromo.PromotionAbstract.Name))
                            .withDescription(promoEval.get(CIPromo.PromotionAbstract.Description))
                            .withPriority(promoEval.get(CIPromo.PromotionAbstract.Priority))
                            .withStartDateTime(promoEval.get(CIPromo.PromotionAbstract.StartDateTime))
                            .withEndDateTime(promoEval.get(CIPromo.PromotionAbstract.EndDateTime));
            evalActions(promoEval.inst(), promotionBldr);
            evalConditions(promoEval.inst(), promotionBldr);
            promotions.add(promotionBldr.build());
        }
        return promotions;
    }

    public void evalActions(final Instance promoInst,
                            final Promotion.Builder promotionBldr)
        throws EFapsException
    {
        final var eval = EQL.builder().print().query(CIPromo.ActionAbstract)
                        .where()
                        .attribute(CIPromo.ActionAbstract.PromotionLink).eq(promoInst)
                        .select()
                        .attribute(CIPromo.ActionAbstract.ID)
                        .evaluate();
        while (eval.next()) {
            if (InstanceUtils.isType(eval.inst(), CIPromo.PercentageDiscountAction)) {
                final var pEval = EQL.builder().print(eval.inst())
                                .attribute(CIPromo.PercentageDiscountAction.Percentage)
                                .evaluate();
                final var percentage = pEval.<BigDecimal>get(CIPromo.PercentageDiscountAction.Percentage);
                promotionBldr.addAction(new PercentageDiscountAction()
                                .setPercentage(percentage));
            }
        }
    }

    public void evalConditions(final Instance promoInst,
                               final Promotion.Builder promotionBldr)
        throws EFapsException
    {
        final var eval = EQL.builder().print().query(CIPromo.ConditionAbstract)
                        .where()
                        .attribute(CIPromo.ConditionAbstract.PromotionLink).eq(promoInst)
                        .select()
                        .attribute(CIPromo.ConditionAbstract.ConditionContainer, CIPromo.ConditionAbstract.Int1)
                        .evaluate();
        while (eval.next()) {
            ICondition condition = null;
            final ConditionContainer container = eval.get(CIPromo.ConditionAbstract.ConditionContainer);
            if (InstanceUtils.isType(eval.inst(), CIPromo.ProductsCondition)) {
                final var ordinal = eval.<Integer>get(CIPromo.ConditionAbstract.Int1);
                final var entryOperator = EntryOperator.values()[ordinal];
                final var prodEval = EQL.builder().print().query(CIPromo.ProductsCondition2ProductAbstract).where()
                                .attribute(CIPromo.ProductsCondition2ProductAbstract.FromLink).eq(eval.inst())
                                .select()
                                .linkto(CIPromo.ProductsCondition2ProductAbstract.ToLink).oid().as("prodOid")
                                .evaluate();
                final var prodOids = new ArrayList<String>();
                while (prodEval.next()) {
                    prodOids.add(prodEval.get("prodOid"));
                }
                condition = new ProductsCondition()
                                .setEntryOperator(EnumUtils.getEnum(
                                                                org.efaps.promotionengine.condition.EntryOperator.class,
                                                                entryOperator.name()))
                                .setEntries(prodOids);
            }

            if (container.equals(ConditionContainer.SOURCE)) {
                promotionBldr.addSourceCondition(condition);
            } else {
                promotionBldr.addTargetCondition(condition);
            }
        }
    }
}
