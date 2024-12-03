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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.EnumUtils;
import org.efaps.abacus.api.IConfig;
import org.efaps.abacus.api.ITax;
import org.efaps.abacus.api.TaxType;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.eql.EQL;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.promotions.PromotionService;
import org.efaps.esjp.sales.CalculatorConfig;
import org.efaps.esjp.sales.PriceUtil;
import org.efaps.esjp.sales.tax.Tax;
import org.efaps.esjp.sales.tax.TaxCat_Base;
import org.efaps.promotionengine.api.IDocument;
import org.efaps.promotionengine.pojo.Document;
import org.efaps.promotionengine.pojo.Position;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@EFapsUUID("49bda9f9-bbaf-4153-91be-04345112e00b")
@EFapsApplication("eFapsApp-Promotions")
@Path("/ui/modules/promo-simulator")
public class SimulatorController
{

    private static final Logger LOG = LoggerFactory.getLogger(SimulatorController.class);

    @Path("/products")
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getProducts(@QueryParam("term") final String term)
        throws EFapsException
    {
        LOG.info("Searching for products with: {}", term);

        final var dtos = new ArrayList<SimulatorProductDto>();
        //
        final var productEval = EQL.builder().print().query(CIProducts.ProductStandart)
                        .where()
                        .attribute(CIProducts.ProductStandart.Name).ilike(term).or()
                        .attribute(CIProducts.ProductStandart.Description).ilike(term)
                        .select()
                        .attribute(CIProducts.ProductStandart.Name, CIProducts.ProductStandart.Description)
                        .evaluate();
        while (productEval.next()) {
            dtos.add(SimulatorProductDto.builder()
                            .withOid(productEval.inst().getOid())
                            .withName(productEval.get(CIProducts.ProductStandart.Name))
                            .withDescription(productEval.get(CIProducts.ProductStandart.Description))
                            .build());
        }
        return Response.ok(dtos).build();
    }


    @Path("/promotions/{oid}")
    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getPromotion(@PathParam("oid") final String oid)
        throws EFapsException
    {
        final var promotion = new PromotionService().getPromotion(Instance.get(oid));
        return Response.ok(promotion).build();
    }

    @Path("/calculate")
    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    public Response calculate(final CalculateRequestDto dto)
        throws EFapsException
    {
        LOG.info("CalculateRequestDto: {}", dto);

        final Parameter parameter = ParameterUtil.instance();
        final var taxMap = new HashMap<String, Tax>();
        final var document = new Document();
        int idx = 0;
        for (final var pos : dto.getItems()) {
            final var prodInst = Instance.get(pos.getProductOid());
            final var prodPrice = new PriceUtil().getPrice(parameter, DateTime.now(), prodInst,
                            CIProducts.ProductPricelistRetail.uuid, "DefaultPosition", false);

            final var prodEval = EQL.builder()
                            .print(prodInst)
                            .attribute(CIProducts.ProductAbstract.TaxCategory)
                            .evaluate();
            prodEval.next();

            final var taxCatId = prodEval.<Long>get(CIProducts.ProductAbstract.TaxCategory);
            final List<ITax> taxes = TaxCat_Base.get(taxCatId).getTaxes().stream()
                            .map(tax -> {
                                try {
                                    taxMap.put(tax.getName(), tax);

                                    return (ITax) new org.efaps.abacus.pojo.Tax()
                                                    .setKey(tax.getName())
                                                    .setPercentage(tax.getFactor().multiply(new BigDecimal("100")))
                                                    .setAmount(tax.getAmount())
                                                    .setType(EnumUtils.getEnum(TaxType.class, tax.getTaxType().name()));
                                } catch (final EFapsException e) {
                                    LOG.error("Catched", e);
                                }
                                return null;
                            })
                            .toList();

            document.addPosition(new Position()
                            .setNetUnitPrice(prodPrice.getCurrentPrice())
                            .setTaxes(taxes)
                            .setIndex(idx++)
                            .setProductOid(pos.getProductOid())
                            .setQuantity(pos.getQuantity()));
        }
        final var result = calculate(document);
        LOG.info("result: {}", result);

        return Response.ok(result).build();
    }

    public IDocument calculate(final IDocument document) throws EFapsException
    {
        final var calculator = new org.efaps.promotionengine.Calculator(getConfig());
        final var promotions = new PromotionService().getPromotions();
        calculator.calc(document, promotions);
        return document;
    }

    protected IConfig getConfig()
    {
        return new CalculatorConfig();
    }
}