/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.jaxrs;

import java.sql.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Catalog;
import org.killbill.billing.client.model.Plan;
import org.killbill.billing.client.model.PlanDetail;
import org.killbill.billing.client.model.Product;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.io.Resources;

public class TestCatalog extends TestJaxrsBase {

    @Test(groups = "slow", description = "Upload and retrieve a per tenant catalog")
    public void testMultiTenantCatalog() throws Exception {
        final String catalogPath = Resources.getResource("SpyCarBasic.xml").getPath();
        killBillClient.uploadXMLCatalog(catalogPath, createdBy, reason, comment);

        final String catalog = killBillClient.getXMLCatalog();
        Assert.assertNotNull(catalog);
    }

    @Test(groups = "slow", description = "Can retrieve a json version of the catalog")
    public void testCatalog() throws Exception {
        final Set<String> allBasePlans = new HashSet<String>();

        final Catalog catalogJson = killBillClient.getJSONCatalog();

        Assert.assertEquals(catalogJson.getName(), "Firearms");
        Assert.assertEquals(catalogJson.getEffectiveDate(), Date.valueOf("2011-01-01"));
        Assert.assertEquals(catalogJson.getCurrencies().size(), 3);
        Assert.assertEquals(catalogJson.getProducts().size(), 11);
        Assert.assertEquals(catalogJson.getPriceLists().size(), 4);

        for (final Product productJson : catalogJson.getProducts()) {
            if (!"BASE".equals(productJson.getType())) {
                Assert.assertEquals(productJson.getIncluded().size(), 0);
                Assert.assertEquals(productJson.getAvailable().size(), 0);
                continue;
            }

            // Save all plans for later (see below)
            for (final Plan planJson : productJson.getPlans()) {
                allBasePlans.add(planJson.getName());
            }

            // Retrieve available products (addons) for that base product
            final List<PlanDetail> availableAddons = killBillClient.getAvailableAddons(productJson.getName());
            final Set<String> availableAddonsNames = new HashSet<String>();
            for (final PlanDetail planDetailJson : availableAddons) {
                availableAddonsNames.add(planDetailJson.getProduct());
            }
            Assert.assertEquals(availableAddonsNames, new HashSet<String>(productJson.getAvailable()));
        }

        // Verify base plans endpoint
        final List<PlanDetail> basePlans = killBillClient.getBasePlans();
        final Set<String> foundBasePlans = new HashSet<String>();
        for (final PlanDetail planDetailJson : basePlans) {
            foundBasePlans.add(planDetailJson.getPlan());
        }
        Assert.assertEquals(foundBasePlans, allBasePlans);
    }

    @Test(groups = "slow", description = "Try to retrieve a json version of the catalog with an invalid date",
            expectedExceptions = KillBillClientException.class,
            expectedExceptionsMessageRegExp = "There is no catalog version that applies for the given date.*")
    public void testCatalogInvalidDate() throws Exception {
        final Catalog catalogJson = killBillClient.getJSONCatalog(DateTime.parse("2008-01-01"));
        Assert.fail();
    }

}
