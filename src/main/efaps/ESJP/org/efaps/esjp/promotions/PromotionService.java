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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.stmt.PrintStmt;
import org.efaps.db.stmt.selection.Evaluator;
import org.efaps.eql.EQL;
import org.efaps.eql.builder.Print;
import org.efaps.eql2.IPrintQueryStatement;
import org.efaps.esjp.ci.CIPOS;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CIPromo;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.properties.PropertiesUtil;
import org.efaps.esjp.common.serialization.SerializationUtil;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.promotions.rest.modules.PromotionHeadDto;
import org.efaps.esjp.promotions.utils.Promotions;
import org.efaps.esjp.promotions.utils.Promotions.ConditionContainer;
import org.efaps.esjp.promotions.utils.Promotions.EntryOperator;
import org.efaps.esjp.promotions.utils.Promotions.LogicalOperator;
import org.efaps.esjp.ui.util.ValueUtils;
import org.efaps.promotionengine.action.FixedAmountAction;
import org.efaps.promotionengine.action.PercentageDiscountAction;
import org.efaps.promotionengine.action.Strategy;
import org.efaps.promotionengine.api.IPromotionsProvider;
import org.efaps.promotionengine.condition.DateCondition;
import org.efaps.promotionengine.condition.DocTotalCondition;
import org.efaps.promotionengine.condition.ICondition;
import org.efaps.promotionengine.condition.MaxCondition;
import org.efaps.promotionengine.condition.Operator;
import org.efaps.promotionengine.condition.OrCondition;
import org.efaps.promotionengine.condition.ProductFamilyCondition;
import org.efaps.promotionengine.condition.ProductFamilyConditionEntry;
import org.efaps.promotionengine.condition.ProductTotalCondition;
import org.efaps.promotionengine.condition.ProductsCondition;
import org.efaps.promotionengine.condition.StackCondition;
import org.efaps.promotionengine.condition.StoreCondition;
import org.efaps.promotionengine.condition.TimeCondition;
import org.efaps.promotionengine.dto.PromotionInfoDto;
import org.efaps.promotionengine.promotion.Promotion;
import org.efaps.util.EFapsBaseException;
import org.efaps.util.EFapsException;
import org.efaps.util.cache.CacheReloadException;
import org.efaps.util.cache.InfinispanCache;
import org.infinispan.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

@EFapsUUID("26c36418-2e96-4d89-87c4-ad3740bba939")
@EFapsApplication("eFapsApp-Promotions")
public class PromotionService
    implements IPromotionsProvider
{

    private static final Logger LOG = LoggerFactory.getLogger(PromotionService.class);

    private static final String CACHENAME = PromotionService.class.getName() + ".Cache";

    private static final String CACHEPREFIX = "ACTIVE";
    private static final String CACHEPREFIX_CLEAN = CACHEPREFIX + "-CLEAN";
    private static final String CACHEPREFIX_LOADING = CACHEPREFIX + "-LOADING";

    public Return cleanCache(final Parameter parameter)
        throws EFapsException
    {
        LOG.info("Clean cache");
        getCache().put(evalCacheKey(CACHEPREFIX_CLEAN), "true");

        for (final var key : getCache().keySet()) {
            if (key.contains(".")) {
                getCache().remove(key);
            }
        }
        return new Return();
    }

    public Promotion getPromotion(final Instance promotionInstance)
        throws EFapsException
    {
        List<Promotion> promotions = null;
        if (getCache().containsKey(promotionInstance.getOid())) {
            promotions = loadPromotions(promotionInstance.getOid());
        } else {
            final Print print = EQL.builder().print(promotionInstance);
            promotions = evalPromotions(print);
            cachePromotions(promotions, promotionInstance.getOid(), 10, TimeUnit.MINUTES);
        }
        return promotions == null ? null : promotions.isEmpty() ? null : promotions.get(0);
    }

    public Promotion getPromotion(final String oid)
    {
        try {
            return getPromotion(Instance.get(oid));
        } catch (final EFapsException e) {
            LOG.error("catched", e);
        }
        return null;
    }

    public List<PromotionHeadDto> getPromotionHeads()
        throws EFapsException
    {
        final List<PromotionHeadDto> ret = new ArrayList<>();
        final var promoEval = EQL.builder().print().query(CIPromo.PromotionAbstract)
                        .where()
                        .attribute(CIPromo.PromotionAbstract.StatusAbstract)
                        .in(CIPromo.PromotionStatus.Active, CIPromo.PromotionStatus.Draft)
                        .select()
                        .attribute(CIPromo.PromotionAbstract.Name, CIPromo.PromotionAbstract.Description,
                                        CIPromo.PromotionAbstract.Label)
                        .orderBy(CIPromo.PromotionAbstract.Name)
                        .evaluate();

        while (promoEval.next()) {
            ret.add(PromotionHeadDto.builder()
                            .withOid(promoEval.inst().getOid())
                            .withName(promoEval.get(CIPromo.PromotionAbstract.Name))
                            .withDescription(promoEval.get(CIPromo.PromotionAbstract.Description))
                            .withLabel(promoEval.get(CIPromo.PromotionAbstract.Label))
                            .build());
        }
        return ret;
    }

    @Override
    public List<Promotion> getPromotions()
        throws EFapsException
    {

        LOG.info("Getting Promotions");
        var promotions = retrievePromotions();
        if (promotions == null && !getCache().containsKey(evalCacheKey(CACHEPREFIX_LOADING))) {
            getCache().put(evalCacheKey(CACHEPREFIX_LOADING), "true", 10, TimeUnit.MINUTES);
            final Print promoEval = EQL.builder().print().query(CIPromo.PromotionAbstract)
                            .where()
                            .attribute(CIPromo.PromotionAbstract.StatusAbstract)
                            .eq(CIPromo.PromotionStatus.Active)
                            .select();
            promotions = evalPromotions(promoEval);
            cachePromotions(promotions, evalCacheKey(CACHEPREFIX));
            getCache().remove(evalCacheKey(CACHEPREFIX_LOADING));
        }
        return promotions;
    }

    protected List<Promotion> evalPromotions(final Print print)
        throws EFapsException
    {
        final List<Promotion> promotions = new ArrayList<>();

        final var promoEval = print
                        .attribute(CIPromo.PromotionAbstract.Name, CIPromo.PromotionAbstract.Description,
                                        CIPromo.PromotionAbstract.Label, CIPromo.PromotionAbstract.Priority,
                                        CIPromo.PromotionAbstract.StartDateTime,
                                        CIPromo.PromotionAbstract.EndDateTime)
                        .evaluate();
        while (promoEval.next()) {
            final var promotionBldr = Promotion.builder()
                            .withOid(promoEval.inst().getOid())
                            .withName(promoEval.get(CIPromo.PromotionAbstract.Name))
                            .withDescription(promoEval.get(CIPromo.PromotionAbstract.Description))
                            .withLabel(promoEval.get(CIPromo.PromotionAbstract.Label))
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
                        .attribute(CIPromo.ActionAbstract.ID, CIPromo.ActionAbstract.Decimal1,
                                        CIPromo.ActionAbstract.Int1)
                        .evaluate();
        while (eval.next()) {
            final var decimal1 = eval.<BigDecimal>get(CIPromo.ActionAbstract.Decimal1);
            final var int1 = eval.<Integer>get(CIPromo.ActionAbstract.Int1);
            if (InstanceUtils.isType(eval.inst(), CIPromo.PercentageDiscountAction)) {
                final var strategy = int1 == null ? Strategy.CHEAPEST : Strategy.values()[int1];
                promotionBldr.addAction(new PercentageDiscountAction()
                                .setPercentage(decimal1)
                                .setStrategy(strategy));
            } else if (InstanceUtils.isType(eval.inst(), CIPromo.FixedAmountAction)) {
                final var strategy = int1 == null ? Strategy.CHEAPEST : Strategy.values()[int1];
                promotionBldr.addAction(new FixedAmountAction()
                                .setAmount(decimal1)
                                .setStrategy(strategy));
            }
        }
    }

    public void evalConditions(final Instance promoInst,
                               final Promotion.Builder promotionBldr)
        throws EFapsException
    {
        LOG.info("Evaluating Conditions for: {}", promoInst.getOid());
        final var eval = EQL.builder().print().query(CIPromo.ConditionAbstract)
                        .where()
                        .attribute(CIPromo.ConditionAbstract.PromotionLink).eq(promoInst)
                        .select()
                        .attribute(CIPromo.ConditionAbstract.ConditionContainer, CIPromo.ConditionAbstract.Note,
                                        CIPromo.ConditionAbstract.Int1, CIPromo.ConditionAbstract.Decimal1,
                                        CIPromo.ConditionAbstract.Boolean1)
                        .evaluate();
        while (eval.next()) {
            final ICondition condition = evalCondition(eval);
            final var container = eval.get(CIPromo.ConditionAbstract.ConditionContainer);
            if (container.equals(ConditionContainer.SOURCE)) {
                promotionBldr.addSourceCondition(condition);
            } else {
                promotionBldr.addTargetCondition(condition);
            }
        }
    }

    protected ICondition evalCondition(final Evaluator eval)
        throws EFapsException
    {
        ICondition condition = null;
        eval.get(CIPromo.ConditionAbstract.ConditionContainer);
        if (InstanceUtils.isType(eval.inst(), CIPromo.ProductsCondition)) {
            final var ordinal = eval.<Integer>get(CIPromo.ConditionAbstract.Int1);
            final var entryOperator = EntryOperator.values()[ordinal];
            final var prodEval = EQL.builder().print().query(CIPromo.ProductsCondition2ProductAbstract).where()
                            .attribute(CIPromo.ProductsCondition2ProductAbstract.FromLink).eq(eval.inst())
                            .select()
                            .linkto(CIPromo.ProductsCondition2ProductAbstract.ToLink).oid().as("prodOid")
                            .evaluate();
            final var prodOids = new HashSet<String>();
            while (prodEval.next()) {
                prodOids.add(prodEval.get("prodOid"));
            }
            condition = new ProductsCondition()
                            .setPositionQuantity(eval.get(CIPromo.ConditionAbstract.Decimal1))
                            .setEntryOperator(EnumUtils.getEnum(
                                            org.efaps.promotionengine.condition.EntryOperator.class,
                                            entryOperator.name()))
                            .setAllowTargetSameAsSource(BooleanUtils
                                            .toBoolean(eval.<Boolean>get(CIPromo.ConditionAbstract.Boolean1)))
                            .setProducts(prodOids)
                            .setNote(eval.get(CIPromo.ConditionAbstract.Note));
        } else if (InstanceUtils.isType(eval.inst(), CIPromo.ProductFamilyCondition)) {
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
                            .setAllowTargetSameAsSource(BooleanUtils
                                            .toBoolean(eval.<Boolean>get(CIPromo.ConditionAbstract.Boolean1)))
                            .setEntries(entries)
                            .setNote(eval.get(CIPromo.ConditionAbstract.Note));
        } else if (InstanceUtils.isType(eval.inst(), CIPromo.StoreCondition)) {
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
        } else if (InstanceUtils.isType(eval.inst(), CIPromo.ProductsEQLCondition)) {
            final var ordinal = eval.<Integer>get(CIPromo.ConditionAbstract.Int1);
            final var entryOperator = EntryOperator.values()[ordinal];
            final var prodOids = evalProductOids4EQL(eval.inst());
            condition = new ProductsCondition()
                            .setPositionQuantity(eval.get(CIPromo.ConditionAbstract.Decimal1))
                            .setEntryOperator(EnumUtils.getEnum(
                                            org.efaps.promotionengine.condition.EntryOperator.class,
                                            entryOperator.name()))
                            .setAllowTargetSameAsSource(BooleanUtils
                                            .toBoolean(eval.<Boolean>get(CIPromo.ConditionAbstract.Boolean1)))
                            .setProducts(prodOids)
                            .setNote(eval.get(CIPromo.ConditionAbstract.Note));
        } else if (InstanceUtils.isType(eval.inst(), CIPromo.DateCondition)) {
            condition = new DateCondition().setNote(eval.get(CIPromo.ConditionAbstract.Note));
            final var entriesEval = EQL.builder().print().query(CIPromo.DateConditionEntry)
                            .where()
                            .attribute(CIPromo.DateConditionEntry.DateConditionLink)
                            .eq(eval.inst())
                            .select()
                            .attribute(CIPromo.DateConditionEntry.StartDate, CIPromo.DateConditionEntry.EndDate)
                            .evaluate();
            while (entriesEval.next()) {
                final LocalDate startDate = entriesEval.get(CIPromo.DateConditionEntry.StartDate);
                final LocalDate endDate = entriesEval.get(CIPromo.DateConditionEntry.EndDate);
                ((DateCondition) condition).addRange(startDate, endDate);
            }
        } else if (InstanceUtils.isType(eval.inst(), CIPromo.TimeCondition)) {
            condition = new TimeCondition().setNote(eval.get(CIPromo.ConditionAbstract.Note));
            final var entriesEval = EQL.builder().print().query(CIPromo.TimeConditionEntry)
                            .where()
                            .attribute(CIPromo.TimeConditionEntry.TimeConditionLink)
                            .eq(eval.inst())
                            .select()
                            .attribute(CIPromo.TimeConditionEntry.StartTime, CIPromo.TimeConditionEntry.EndTime)
                            .evaluate();
            while (entriesEval.next()) {
                final LocalTime startTime = entriesEval.get(CIPromo.TimeConditionEntry.StartTime);
                final LocalTime endTime = entriesEval.get(CIPromo.TimeConditionEntry.EndTime);
                ((TimeCondition) condition).addRange(
                                startTime.atOffset(
                                                OffsetTime.now(Context.getThreadContext().getZoneId()).getOffset()),
                                endTime.atOffset(OffsetTime.now(Context.getThreadContext().getZoneId())
                                                .getOffset()));
            }
        } else if (InstanceUtils.isType(eval.inst(), CIPromo.DocTotalCondition)) {
            final var ordinal = eval.<Integer>get(CIPromo.ConditionAbstract.Int1);
            final var operator = Operator.values()[ordinal];
            condition = new DocTotalCondition()
                            .setTotal(eval.get(CIPromo.ConditionAbstract.Decimal1))
                            .setOperator(operator)
                            .setNote(eval.get(CIPromo.ConditionAbstract.Note));
        } else if (InstanceUtils.isType(eval.inst(), CIPromo.ProductTotalCondition)) {
            final var ordinal = eval.<Integer>get(CIPromo.ConditionAbstract.Int1);
            final var operator = Operator.values()[ordinal];
            condition = new ProductTotalCondition()
                            .setTotal(eval.get(CIPromo.ConditionAbstract.Decimal1))
                            .setOperator(operator)
                            .setNote(eval.get(CIPromo.ConditionAbstract.Note));

            final var prodEval = EQL.builder().print().query(CIPromo.ProductTotalCondition2ProductAbstract).where()
                            .attribute(CIPromo.ProductTotalCondition2ProductAbstract.FromLink).eq(eval.inst())
                            .select()
                            .linkto(CIPromo.ProductTotalCondition2ProductAbstract.ToLink).oid().as("prodOid")
                            .evaluate();
            new ArrayList<String>();
            while (prodEval.next()) {
                ((ProductTotalCondition) condition).addProduct(prodEval.get("prodOid"));
            }
        } else if (InstanceUtils.isType(eval.inst(), CIPromo.MaxCondition)) {
            final var max = eval.<Integer>get(CIPromo.ConditionAbstract.Int1);
            condition = new MaxCondition()
                            .setMax(max)
                            .setNote(eval.get(CIPromo.ConditionAbstract.Note));
        } else if (InstanceUtils.isType(eval.inst(), CIPromo.StackCondition)) {
            condition = new StackCondition()
                            .setNote(eval.get(CIPromo.StackCondition.Note));
        } else if (InstanceUtils.isType(eval.inst(), CIPromo.OrCondition)) {
            final var childEval = EQL.builder().print().query(CIPromo.ConditionAbstract)
                            .where()
                            .attribute(CIPromo.ConditionAbstract.ParentConditionLink).eq(eval.inst())
                            .select()
                            .attribute(CIPromo.ConditionAbstract.ConditionContainer, CIPromo.ConditionAbstract.Note,
                                            CIPromo.ConditionAbstract.Int1, CIPromo.ConditionAbstract.Decimal1,
                                            CIPromo.ConditionAbstract.Boolean1)
                            .evaluate();
            condition = new OrCondition()
                            .setNote(eval.get(CIPromo.ConditionAbstract.Note));
            while (childEval.next()) {
                final ICondition childCondition = evalCondition(childEval);
                ((OrCondition) condition).addCondition(childCondition);
            }
        }
        return condition;
    }

    public Instance registerPromotionInfoForDoc(final String documentOid,
                                                final PromotionInfoDto dto,
                                                final Collection<String> promotions)
        throws EFapsException
    {
        Instance ret = null;
        final var docInst = Instance.get(documentOid);

        CIType ciRelDocType = null;
        CIType ciRelPosType = null;
        if (InstanceUtils.isType(docInst, CISales.Receipt)) {
            ciRelDocType = CIPromo.Promotion2Receipt;
            ciRelPosType = CIPromo.Promotion2ReceiptPosition;
        } else if (InstanceUtils.isType(docInst, CISales.Invoice)) {
            ciRelDocType = CIPromo.Promotion2Invoice;
            ciRelPosType = CIPromo.Promotion2InvoicePosition;
        } else if (InstanceUtils.isType(docInst, CIPOS.Order)) {
            ciRelDocType = CIPromo.Promotion2Order;
            ciRelPosType = CIPromo.Promotion2OrderPosition;
        } else if (InstanceUtils.isType(docInst, CIPOS.Ticket)) {
            ciRelDocType = CIPromo.Promotion2Ticket;
            ciRelPosType = CIPromo.Promotion2TicketPosition;
        }
        if (ciRelDocType != null) {
            try {
                final var objectMapper = SerializationUtil.getObjectMapper();
                final var oid2promotion = new HashMap<String, Promotion>();
                if (promotions == null) {
                    dto.getPromotionOids().forEach(promotionOid -> {
                        final var promotion = getPromotion(promotionOid);
                        if (promotion != null) {
                            oid2promotion.put(promotion.getOid(), promotion);
                        }
                    });
                } else {
                    for (final var promotionStr : promotions) {
                        final var promotion = objectMapper.readValue(promotionStr, Promotion.class);
                        LOG.info("Read promotion: {}", promotion);
                        oid2promotion.put(promotion.getOid(), promotion);
                    }
                }
                final var promoInfo = objectMapper.writeValueAsString(dto);

                for (final var promotionOid : dto.getPromotionOids()) {
                    final var promoInst = Instance.get(promotionOid);
                    if (InstanceUtils.isKindOf(promoInst, CIPromo.PromotionAbstract)) {

                        final String promotion;
                        if (oid2promotion.containsKey(promotionOid)) {
                            promotion = objectMapper.writeValueAsString(oid2promotion.get(promotionOid));
                        } else {
                            promotion = promotions.stream().collect(Collectors.joining("\n"));
                        }
                        ret = EQL.builder().insert(ciRelDocType)
                                        .set(CIPromo.Promotion2DocumentAbstract.FromLink, promoInst)
                                        .set(CIPromo.Promotion2DocumentAbstract.ToLinkAbstract, docInst)
                                        .set(CIPromo.Promotion2DocumentAbstract.PromoInfo, promoInfo)
                                        .set(CIPromo.Promotion2DocumentAbstract.Promotion, promotion)
                                        .set(CIPromo.Promotion2DocumentAbstract.NetTotalDiscount,
                                                        dto.getNetTotalDiscount())
                                        .set(CIPromo.Promotion2DocumentAbstract.CrossTotalDiscount,
                                                        dto.getCrossTotalDiscount())
                                        .execute();
                    }
                }

                final var posEval = EQL.builder().print().query(CISales.PositionAbstract)
                                .where()
                                .attribute(CISales.PositionAbstract.DocumentAbstractLink).eq(docInst)
                                .select()
                                .orderBy(CISales.PositionAbstract.PositionNumber)
                                .attribute(CISales.PositionAbstract.PositionNumber)
                                .evaluate();
                while (posEval.next()) {
                    final int posIndex = posEval.get(CISales.PositionAbstract.PositionNumber);
                    for (final var detail : dto.findDetailsForPosition(posIndex)) {
                        final var promoInst = Instance.get(detail.getPromotionOid());
                        String promotion = null;
                        if (oid2promotion.containsKey(promoInst.getOid())) {
                            promotion = objectMapper.writeValueAsString(oid2promotion.get(promoInst.getOid()));
                        }
                        if (InstanceUtils.isKindOf(promoInst, CIPromo.PromotionAbstract)) {
                            EQL.builder().insert(ciRelPosType)
                                            .set(CIPromo.Promotion2PositionAbstract.FromLink, promoInst)
                                            .set(CIPromo.Promotion2PositionAbstract.ToLinkAbstract, posEval.inst())
                                            .set(CIPromo.Promotion2PositionAbstract.PromoInfo, promoInfo)
                                            .set(CIPromo.Promotion2PositionAbstract.Promotion, promotion)
                                            .set(CIPromo.Promotion2PositionAbstract.NetUnitDiscount,
                                                            detail.getNetUnitDiscount())
                                            .set(CIPromo.Promotion2PositionAbstract.NetDiscount,
                                                            detail.getNetDiscount())
                                            .set(CIPromo.Promotion2PositionAbstract.CrossUnitDiscount,
                                                            detail.getCrossUnitDiscount())
                                            .set(CIPromo.Promotion2PositionAbstract.CrossDiscount,
                                                            detail.getCrossDiscount())
                                            .execute();
                        }
                    }
                }
            } catch (final JsonProcessingException e) {
                LOG.error("Catched", e);
            }
        }
        return ret;
    }

    public static Set<String> evalProductOids4EQL(final Instance conditionInstance)
        throws EFapsException
    {
        LOG.info("Evaluating ProductOids for EQL: {}", conditionInstance.getOid());
        final var condEval = EQL.builder().print(conditionInstance)
                        .attribute(CIPromo.ProductsEQLCondition.LogicalOperator)
                        .evaluate();
        final var operator = condEval.<LogicalOperator>get(CIPromo.ProductsEQLCondition.LogicalOperator);

        final var prodOids = new HashSet<String>();
        final var properties = Promotions.EQL_ATTRDEF.get();
        final var types = PropertiesUtil.analyseProperty(properties, "Type", 0);
        LOG.info("  types: {}", types);
        final var selects = PropertiesUtil.analyseProperty(properties, "Select", 0);
        LOG.info("  selects: {}", selects);
        final var eqlEval = EQL.builder().print()
                        .query(CIPromo.EQLAttributeDefinition)
                        .where()
                        .attribute(CIPromo.EQLAttributeDefinition.ConditionLink).eq(conditionInstance)
                        .select()
                        .attribute(CIPromo.EQLAttributeDefinition.AttributeDefinitionType,
                                        CIPromo.EQLAttributeDefinition.AttributeDefinitionValue)
                        .evaluate();
        final var wheres = new ArrayList<Pair<String, Long>>();

        while (eqlEval.next()) {
            final Long typeId = eqlEval.get(CIPromo.EQLAttributeDefinition.AttributeDefinitionType);
            final Long valueId = eqlEval.get(CIPromo.EQLAttributeDefinition.AttributeDefinitionValue);
            final var type = Type.get(typeId);
            if (type != null) {
                LOG.info("  checking for type: {} with value: {}", type.getName(), valueId);
                final var keyOpt = types
                                .entrySet()
                                .stream()
                                .filter(entry -> entry.getValue().equals(type.getName())
                                                || entry.getValue().equals(type.getUUID().toString()))
                                .map(Map.Entry::getKey)
                                .findFirst();
                if (keyOpt.isPresent()) {
                    wheres.add(Pair.of(selects.get(keyOpt.get()), valueId));
                }
            }
        }
        LOG.info("  wheres: {}", wheres);
        final var bldr = new StringBuilder().append("print query type ")
                        .append(CIProducts.ProductAbstract.getType().getName());

        if (!wheres.isEmpty()) {
            bldr.append(" where ");
            boolean first = true;
            for (final var oneWhere : wheres) {
                if (first) {
                    first = false;
                } else if (LogicalOperator.OR.equals(operator)) {
                    bldr.append(" or ");
                } else {
                    bldr.append(" and ");
                }
                bldr.append(oneWhere.getLeft()).append(" eq ").append(oneWhere.getRight());
            }
        }
        bldr.append(" select oid");
        LOG.info("  stmt: {}", bldr);
        final IPrintQueryStatement stmt = (IPrintQueryStatement) EQL.parse(bldr);
        final var eval = PrintStmt.get(stmt).evaluate();
        while (eval.next()) {
            prodOids.add(eval.inst().getOid());
        }
        return prodOids;
    }

    private List<Promotion> retrievePromotions()
        throws CacheReloadException, EFapsException
    {

        final var clean = getCache().containsKey(evalCacheKey(CACHEPREFIX_CLEAN));
        final var loading = getCache().containsKey(evalCacheKey(CACHEPREFIX_LOADING));
        final var cacheKey = evalCacheKey(CACHEPREFIX);
        LOG.info("Retreiving Promotions for: {}, cleanRequired: {}, isLoading: {}", cacheKey, clean, loading);
        if (clean && !loading) {
            getCache().remove(evalCacheKey(CACHEPREFIX_CLEAN));
            return null;
        }
        return loadPromotions(cacheKey);
    }

    private List<Promotion> loadPromotions(final String cacheKey)
    {
        List<Promotion> ret = null;
        final var json = getCache().get(cacheKey);
        if (json != null) {
            try {
                ret = ValueUtils.getObjectMapper().readValue(json, ValueUtils.getObjectMapper().getTypeFactory()
                                .constructCollectionType(List.class, Promotion.class));
            } catch (final JsonProcessingException e) {
                LOG.error("Catched", e);
            }
        }
        LOG.info("... found {} promotions for: {}", ret == null ? 0 : ret.size(), cacheKey);
        return ret;
    }

    private void cachePromotions(final List<Promotion> promotions,
                                 final String cacheKey)
        throws CacheReloadException, EFapsException
    {
        cachePromotions(promotions, cacheKey, 0, null);
    }

    private void cachePromotions(final List<Promotion> promotions,
                                 final String cacheKey,
                                 long lifespan,
                                 TimeUnit unit)
        throws CacheReloadException, EFapsException
    {
        try {
            final var json = ValueUtils.getObjectMapper().writeValueAsString(promotions);
            LOG.info("Caching Promotions for {}", cacheKey);
            if (unit != null) {
                getCache().put(cacheKey, json, lifespan, unit);
            } else {
                getCache().put(cacheKey, json);
            }
        } catch (final JsonProcessingException e) {
            LOG.error("Catched", e);
        }
    }

    private static String evalCacheKey(final String prefix)
        throws EFapsException
    {
        final var companyId = Context.getThreadContext().getCompany().getId();
        return prefix + "-" + companyId;
    }

    private static Cache<String, String> getCache()
    {
        if (!InfinispanCache.get().exists(CACHENAME)) {
            InfinispanCache.get().initCache(CACHENAME);
        }
        return InfinispanCache.get().<String, String>getCache(CACHENAME);
    }

    @Override
    public void registerPromotionInfo(final PromotionInfoDto promotionInfoDto,
                                      final String documentOid)
        throws EFapsBaseException
    {
        registerPromotionInfoForDoc(documentOid, promotionInfoDto, null);
    }

    public PromotionInfoDto getPromotionInfoForDoc(final Instance documentInstance)
        throws EFapsException
    {
        PromotionInfoDto ret = null;
        final var promoEval = EQL.builder().print().query(CIPromo.Promotion2DocumentAbstract)
                        .where()
                        .attribute(CIPromo.Promotion2DocumentAbstract.ToLinkAbstract).eq(documentInstance)
                        .select()
                        .attribute(CIPromo.Promotion2DocumentAbstract.PromoInfo)
                        .limit(1)
                        .evaluate();
        if (promoEval.next()) {
            final var objectMapper = SerializationUtil.getObjectMapper();
            final String promoInfoStr = promoEval.get(CIPromo.Promotion2DocumentAbstract.PromoInfo);
            if (promoInfoStr != null) {
                try {
                    ret = objectMapper.readValue(promoInfoStr, PromotionInfoDto.class);
                } catch (final JsonProcessingException e) {
                    LOG.error("Catched", e);
                }
            }
        }
        return ret;
    }

}
