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

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.user.Company;
import org.efaps.db.Context;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.promotions.PromotionService;
import org.efaps.util.EFapsException;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EFapsUUID("30122a84-a4c1-4155-90fa-b1d1c2e5b68f")
@EFapsApplication("eFapsApp-Promotions")
public class CleanPromotionCacheJob
    implements Job
{

    private static final Logger LOG = LoggerFactory.getLogger(CleanPromotionCacheJob.class);

    @Override
    public void execute(final JobExecutionContext context)
        throws JobExecutionException
    {
        try {

            for (final Long companyId : Context.getThreadContext().getPerson().getCompanies()) {
                final Company company = Company.get(companyId);
                Context.getThreadContext().setCompany(company);
                final var parameter = ParameterUtil.instance();
                new PromotionService().cleanCache(parameter);
            }
            // remove the company to be sure
            Context.getThreadContext().setCompany(null);
        } catch (final EFapsException e) {
            LOG.error("Catched", e);
        }
    }
}
