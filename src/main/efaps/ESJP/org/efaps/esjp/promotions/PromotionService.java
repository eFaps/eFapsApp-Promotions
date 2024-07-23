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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.EnumUtils;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.AbstractUserInterfaceObject;
import org.efaps.ci.CIType;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.stmt.PrintStmt;
import org.efaps.eql.EQL;
import org.efaps.eql.builder.Print;
import org.efaps.eql2.IPrintQueryStatement;
import org.efaps.esjp.ci.CIPOS;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CIPromo;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.properties.PropertiesUtil;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.promotions.utils.Promotions;
import org.efaps.esjp.promotions.utils.Promotions.ConditionContainer;
import org.efaps.esjp.promotions.utils.Promotions.EntryOperator;
import org.efaps.esjp.ui.util.ValueUtils;
import org.efaps.promotionengine.action.FixedAmountAction;
import org.efaps.promotionengine.action.PercentageDiscountAction;
import org.efaps.promotionengine.action.Strategy;
import org.efaps.promotionengine.condition.DateCondition;
import org.efaps.promotionengine.condition.DocTotalCondition;
import org.efaps.promotionengine.condition.ICondition;
import org.efaps.promotionengine.condition.ProductFamilyCondition;
import org.efaps.promotionengine.condition.ProductFamilyConditionEntry;
import org.efaps.promotionengine.condition.ProductsCondition;
import org.efaps.promotionengine.condition.StoreCondition;
import org.efaps.promotionengine.condition.TimeCondition;
import org.efaps.promotionengine.dto.PromotionInfoDto;
import org.efaps.promotionengine.promotion.Promotion;
import org.efaps.util.EFapsException;
import org.efaps.util.cache.CacheReloadException;
import org.efaps.util.cache.InfinispanCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@EFapsUUID("26c36418-2e96-4d89-87c4-ad3740bba939")
@EFapsApplication("eFapsApp-Promotions")
public class PromotionService
{

    private static final Logger LOG = LoggerFactory.getLogger(PromotionService.class);

    private static final String CACHENAME = PromotionService.class.getName() + ".Cache";

    public Promotion getPromotion(Instance promotionInstance)
        throws EFapsException
    {
        final Print print = EQL.builder().print(promotionInstance);
        final var promotions = evalPromotions(print);
        return promotions.isEmpty() ? null : promotions.get(0);
    }

    public List<Promotion> getPromotions()
        throws EFapsException
    {
        LOG.debug("Getting Promotions");
        var promotions = retrievePromotions();
        if (promotions == null) {
            final Print promoEval = EQL.builder().print().query(CIPromo.PromotionAbstract)
                            .where()
                            .attribute(CIPromo.PromotionAbstract.StatusAbstract)
                            .eq(CIPromo.PromotionStatus.Active)
                            .select();
            promotions = evalPromotions(promoEval);
            cachePromotions(promotions);
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
            if (InstanceUtils.isType(eval.inst(), CIPromo.DateCondition)) {
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
            }
            if (InstanceUtils.isType(eval.inst(), CIPromo.TimeCondition)) {
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
            }

            if (InstanceUtils.isType(eval.inst(), CIPromo.DocTotalCondition)) {
                condition = new DocTotalCondition().setTotal(eval.get(CIPromo.ConditionAbstract.Decimal1))
                                .setNote(eval.get(CIPromo.ConditionAbstract.Note));
            }

            if (container.equals(ConditionContainer.SOURCE)) {
                promotionBldr.addSourceCondition(condition);
            } else {
                promotionBldr.addTargetCondition(condition);
            }
        }
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
                final var objectMapper = getObjectMapper();
                final var oid2promotion = new HashMap<String, Promotion>();
                for (final var promotionStr : promotions) {
                    final var promotion = objectMapper.readValue(promotionStr, Promotion.class);
                    LOG.info("Read promotion: {}", promotion);
                    oid2promotion.put(promotion.getOid(), promotion);
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
                int idx = 0;
                while (posEval.next()) {
                    if (dto.getDetails().size() > idx) {
                        final var detail = dto.getDetails().get(idx);
                        final var promoInst = Instance.get(detail.getPromotionOid());

                        final String promotion;
                        if (oid2promotion.containsKey(detail.getPromotionOid())) {
                            promotion = objectMapper.writeValueAsString(oid2promotion.get(detail.getPromotionOid()));
                        } else {
                            promotion = promotions.stream().collect(Collectors.joining("\n"));
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
                    idx++;
                }
            } catch (final JsonProcessingException e) {
                LOG.error("Catched", e);
            }
        }
        return ret;
    }

    protected ObjectMapper getObjectMapper()
    {
        final var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    public static List<String> evalProductOids4EQL(final Instance conditionInstance)
        throws EFapsException
    {
        LOG.debug("Evaluation ProductOid for EQL {}", conditionInstance.getOid());
        final var prodOids = new ArrayList<String>();
        final var properties = Promotions.EQL_ATTRDEF.get();
        final var types = PropertiesUtil.analyseProperty(properties, "Type", 0);
        LOG.debug("  types: {}", types);
        final var selects = PropertiesUtil.analyseProperty(properties, "Select", 0);
        LOG.debug("  selects: {}", selects);
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
            LOG.debug("  checking for type: {}", type);
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
        LOG.debug("  stmt: {}", bldr);
        final IPrintQueryStatement stmt = (IPrintQueryStatement) EQL.parse(bldr);
        final var eval = PrintStmt.get(stmt).evaluate();
        while (eval.next()) {
            prodOids.add(eval.inst().getOid());
        }
        return prodOids;
    }

    public static Long getPromotionsKey(final AbstractUserInterfaceObject uiObject)
        throws CacheReloadException, EFapsException
    {
        if (uiObject.getUUID() != null) {
            uiObject.getUUID().toString();
        }
        return Context.getThreadContext().getCompany().getId();
    }

    public static List<Promotion> retrievePromotions()
        throws CacheReloadException, EFapsException
    {
        List<Promotion> ret = null;
        final var cache = InfinispanCache.get().<Long, String>getCache(CACHENAME);
        final var companyId = Context.getThreadContext().getCompany().getId();
        LOG.debug("Retreiving Promotions for {} from {}", companyId, CACHENAME);
        final var json = cache.get(companyId);
        if (json != null) {
            try {
                ret = ValueUtils.getObjectMapper().readValue(json, ValueUtils.getObjectMapper().getTypeFactory()
                                .constructCollectionType(List.class, Promotion.class));
            } catch (final JsonProcessingException e) {
                LOG.error("Catched", e);
            }
        }
        LOG.debug("... promotions: {}", ret);
        return ret;
    }

    public static void cachePromotions(final List<Promotion> promotions)
        throws CacheReloadException, EFapsException
    {

        final var cache = InfinispanCache.get().<Long, String>getCache(CACHENAME);
        try {
            final var json = ValueUtils.getObjectMapper().writeValueAsString(promotions);
            final var companyId = Context.getThreadContext().getCompany().getId();
            LOG.debug("Caching Promotions for {}", companyId);
            cache.put(companyId, json, 1, TimeUnit.HOURS);
        } catch (final JsonProcessingException e) {
            LOG.error("Catched", e);
        }
    }
}
