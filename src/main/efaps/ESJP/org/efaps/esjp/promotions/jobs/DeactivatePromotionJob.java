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
package org.efaps.esjp.promotions.jobs;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.user.Company;
import org.efaps.db.Context;
import org.efaps.eql.EQL;
import org.efaps.esjp.ci.CIPromo;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.promotions.Promotion;
import org.efaps.util.EFapsException;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EFapsUUID("c138b05d-b6ee-4c11-a629-def7c7b93c62")
@EFapsApplication("eFapsApp-Promotions")
public class DeactivatePromotionJob
    implements Job
{

    private static final Logger LOG = LoggerFactory.getLogger(Promotion.class);

    @Override
    public void execute(JobExecutionContext context)
        throws JobExecutionException
    {
        try {

            for (final Long companyId : Context.getThreadContext().getPerson().getCompanies()) {
                final Company company = Company.get(companyId);
                Context.getThreadContext().setCompany(company);
                final var parameter = ParameterUtil.instance();
                deactivatePromotion(parameter);
            }
            // remove the company to be sure
            Context.getThreadContext().setCompany(null);
        } catch (final EFapsException e) {
            LOG.error("Catched", e);
        }
    }

    public void deactivatePromotion(final Parameter parameter)
        throws EFapsException
    {

        final var eval = EQL.builder().print()
                        .query(CIPromo.Promotion)
                        .where().attribute(CIPromo.Promotion.Status)
                        .eq(CIPromo.PromotionStatus.Active).select()
                        .attribute(CIPromo.Promotion.EndDateTime)
                        .evaluate();

        while (eval.next()) {
            final var endDateTime = eval.<OffsetDateTime>get(CIPromo.Promotion.EndDateTime);
            if (OffsetDateTime.now().isAfter(endDateTime)) {
                LOG.info("Promotion {} endDateTime of {} passed.", eval.inst(), endDateTime);
                final var days = ChronoUnit.DAYS.between(endDateTime, OffsetDateTime.now());
                LOG.info(" {} days have passed for promotion {}", days, eval.inst());
                if (days > 0) {
                    EQL.builder()
                                    .update(eval.inst())
                                    .set(CIPromo.Promotion.Status, CIPromo.PromotionStatus.Inactive)
                                    .execute();
                }
            }
        }
    }

}
