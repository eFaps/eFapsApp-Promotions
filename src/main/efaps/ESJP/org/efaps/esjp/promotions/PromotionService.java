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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.EnumUtils;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.stmt.PrintStmt;
import org.efaps.eql.EQL;
import org.efaps.eql2.IPrintQueryStatement;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CIPromo;
import org.efaps.esjp.common.properties.PropertiesUtil;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.promotions.utils.Promotions;
import org.efaps.esjp.promotions.utils.Promotions.ConditionContainer;
import org.efaps.esjp.promotions.utils.Promotions.EntryOperator;
import org.efaps.promotionengine.action.PercentageDiscountAction;
import org.efaps.promotionengine.condition.ICondition;
import org.efaps.promotionengine.condition.ProductFamilyCondition;
import org.efaps.promotionengine.condition.ProductFamilyConditionEntry;
import org.efaps.promotionengine.condition.ProductsCondition;
import org.efaps.promotionengine.condition.StoreCondition;
import org.efaps.promotionengine.promotion.Promotion;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EFapsUUID("26c36418-2e96-4d89-87c4-ad3740bba939")
@EFapsApplication("eFapsApp-Promotions")
public class PromotionService
{
    private static final Logger LOG = LoggerFactory.getLogger(PromotionService.class);

    public List<Promotion> getPromotions()
        throws EFapsException
    {
        LOG.debug("Getting Promotions");

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
        LOG.debug("Evaluation Conditions");
        final var eval = EQL.builder().print().query(CIPromo.ConditionAbstract)
                        .where()
                        .attribute(CIPromo.ConditionAbstract.PromotionLink).eq(promoInst)
                        .select()
                        .attribute(CIPromo.ConditionAbstract.ConditionContainer, CIPromo.ConditionAbstract.Note,
                                        CIPromo.ConditionAbstract.Int1, CIPromo.ConditionAbstract.Decimal1)
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
                                .setPositionQuantity(eval.get(CIPromo.ConditionAbstract.Decimal1))
                                .setEntryOperator(EnumUtils.getEnum(
                                                org.efaps.promotionengine.condition.EntryOperator.class,
                                                entryOperator.name()))
                                .setEntries(prodOids)
                                .setNote(eval.get(CIPromo.ConditionAbstract.Note));
            }
            if (InstanceUtils.isType(eval.inst(), CIPromo.ProductFamilyCondition)) {
                final var ordinal = eval.<Integer>get(CIPromo.ConditionAbstract.Int1);
                final var entryOperator = EntryOperator.values()[ordinal];
                final var familyEval = EQL.builder().print().query(CIPromo.ProductFamilyCondition2ProductFamilyAbstract)
                                .where()
                                .attribute(CIPromo.ProductFamilyCondition2ProductFamilyAbstract.FromLink)
                                .eq(eval.inst())
                                .select()
                                .linkto(CIPromo.ProductFamilyCondition2ProductFamilyAbstract.ToLink)
                                .instance().as("familyInst")
                                .evaluate();
                final var entries = new ArrayList<ProductFamilyConditionEntry>();
                while (familyEval.next()) {
                    final Instance familyInst = familyEval.get("familyInst");
                    final var entry = new ProductFamilyConditionEntry().setProductFamilyOid(familyInst.getOid());
                    entries.add(entry);
                    final var prodEval = EQL.builder().print().query(CIProducts.ProductAbstract)
                                    .where()
                                    .attribute(CIProducts.ProductAbstract.ProductFamilyLink).eq(familyInst)
                                    .select()
                                    .oid()
                                    .evaluate();
                    while (prodEval.next()) {
                        entry.addProduct(prodEval.inst().getOid());
                    }

                }
                condition = new ProductFamilyCondition()
                                .setEntryOperator(EnumUtils.getEnum(
                                                org.efaps.promotionengine.condition.EntryOperator.class,
                                                entryOperator.name()))
                                .setEntries(entries)
                                .setNote(eval.get(CIPromo.ConditionAbstract.Note));
            }
            if (InstanceUtils.isType(eval.inst(), CIPromo.StoreCondition)) {
                final var ordinal = eval.<Integer>get(CIPromo.ConditionAbstract.Int1);
                final var entryOperator = EntryOperator.values()[ordinal];
                final var backendEval = EQL.builder().print().query(CIPromo.StoreCondition2POSBackend)
                                .where()
                                .attribute(CIPromo.StoreCondition2POSBackend.FromLink)
                                .eq(eval.inst())
                                .select()
                                .linkto(CIPromo.StoreCondition2POSBackend.ToLink)
                                .attribute("Identifier").as("backendIdentifier")
                                .evaluate();

                condition = new StoreCondition()
                                .setEntryOperator(EnumUtils.getEnum(
                                                org.efaps.promotionengine.condition.EntryOperator.class,
                                                entryOperator.name()))
                                .setNote(eval.get(CIPromo.ConditionAbstract.Note));
                while (backendEval.next()) {
                    final String backendIdentifier = backendEval.get("backendIdentifier");
                    ((StoreCondition) condition).addIdentifier(backendIdentifier);
                }
            }
            if (InstanceUtils.isType(eval.inst(), CIPromo.ProductsEQLCondition)) {
                final var ordinal = eval.<Integer>get(CIPromo.ConditionAbstract.Int1);
                final var entryOperator = EntryOperator.values()[ordinal];
                final var prodOids = evalProductOids4EQL(eval.inst());
                condition = new ProductsCondition()
                                .setPositionQuantity(eval.get(CIPromo.ConditionAbstract.Decimal1))
                                .setEntryOperator(EnumUtils.getEnum(
                                                org.efaps.promotionengine.condition.EntryOperator.class,
                                                entryOperator.name()))

                                .setEntries(prodOids)
                                .setNote(eval.get(CIPromo.ConditionAbstract.Note));
            }
            if (container.equals(ConditionContainer.SOURCE)) {
                promotionBldr.addSourceCondition(condition);
            } else {
                promotionBldr.addTargetCondition(condition);
            }
        }
    }

    protected List<String> evalProductOids4EQL(final Instance conditionInstance)
        throws EFapsException
    {
        LOG.debug("Evaluation ProductOid for EQL {}", conditionInstance.getOid());
        final var prodOids = new ArrayList<String>();
        final var properties = Promotions.EQL_ATTRDEF.get();
        final var types = PropertiesUtil.analyseProperty(properties, "Type", 0);
        final var selects = PropertiesUtil.analyseProperty(properties, "Select", 0);
        final var eqlEval = EQL.builder().print()
                        .query(CIPromo.EQLAttributeDefinition)
                        .where()
                        .attribute(CIPromo.EQLAttributeDefinition.ConditionLink).eq(conditionInstance)
                        .select()
                        .attribute(CIPromo.EQLAttributeDefinition.AttributeDefinitionType,
                                        CIPromo.EQLAttributeDefinition.AttributeDefinitionValue)
                        .evaluate();
        final var wheres = new HashMap<String, Long>();
        while (eqlEval.next()) {
            final Long typeId = eqlEval.get(CIPromo.EQLAttributeDefinition.AttributeDefinitionType);
            final Long valueId = eqlEval.get(CIPromo.EQLAttributeDefinition.AttributeDefinitionValue);
            final var type = Type.get(typeId);
            final var keyOpt = types
                            .entrySet()
                            .stream()
                            .filter(entry -> entry.getValue().equals(type.getName())
                                            || entry.getValue().equals(type.getUUID().toString()))
                            .map(Map.Entry::getKey)
                            .findFirst();
            if (keyOpt.isPresent()) {
                wheres.put(selects.get(keyOpt.get()), valueId);
            }
        }
        final var bldr = new StringBuilder().append("print query type ")
                        .append(CIProducts.ProductAbstract.getType().getName());

        if (!wheres.isEmpty()) {
            bldr.append(" where ");
            boolean first = true;
            for (final var oneWhere : wheres.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    bldr.append(" and ");
                }
                bldr.append(oneWhere.getKey()).append(" eq ").append(oneWhere.getValue());
            }
        }
        bldr.append(" select oid");
        final IPrintQueryStatement stmt = (IPrintQueryStatement) EQL.parse(bldr);
        final var eval = PrintStmt.get(stmt).evaluate();
        while (eval.next()) {
            prodOids.add(eval.inst().getOid());
        }
        return prodOids;
    }
}
