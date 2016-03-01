/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.control.plugin.api.OnFailurePaymentControlResult;
import org.killbill.billing.control.plugin.api.OnSuccessPaymentControlResult;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.payment.retry.DefaultFailureCallResult;
import org.killbill.billing.payment.retry.DefaultOnSuccessPaymentControlResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class TestPaymentApiWithControl extends PaymentTestSuiteWithEmbeddedDB {

    private static final PaymentOptions PAYMENT_OPTIONS = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return false;
        }

        @Override
        public List<String> getPaymentControlPluginNames() {
            return ImmutableList.of(TestPaymentControlPluginApi.PLUGIN_NAME);
        }
    };

    @Inject
    private OSGIServiceRegistration<PaymentControlPluginApi> controlPluginRegistry;

    private Account account;
    private TestPaymentControlPluginApi testPaymentControlPluginApi;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        account = testHelper.createTestAccount("bobo@gmail.com", true);

        testPaymentControlPluginApi = new TestPaymentControlPluginApi();
        controlPluginRegistry.registerService(new OSGIServiceDescriptor() {
                                                  @Override
                                                  public String getPluginSymbolicName() {
                                                      return null;
                                                  }

                                                  @Override
                                                  public String getPluginName() {
                                                      return TestPaymentControlPluginApi.PLUGIN_NAME;
                                                  }

                                                  @Override
                                                  public String getRegistrationName() {
                                                      return TestPaymentControlPluginApi.PLUGIN_NAME;
                                                  }
                                              },
                                              testPaymentControlPluginApi);
    }

    // Verify Payment control API can be used to change the paymentMethodId on the fly and this is reflected in the created Payment.
    @Test(groups = "slow")
    public void testCreateAuthWithControl() throws PaymentApiException {
        final PaymentMethodPlugin paymentMethodInfo = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), false, null);
        final UUID newPaymentMethodId = paymentApi.addPaymentMethod(account, paymentMethodInfo.getExternalPaymentMethodId(), MockPaymentProviderPlugin.PLUGIN_NAME, false, paymentMethodInfo, ImmutableList.<PluginProperty>of(), callContext);
        testPaymentControlPluginApi.setNewPaymentMethodId(newPaymentMethodId);

        final Payment payment = paymentApi.createAuthorizationWithPaymentControl(account, account.getPaymentMethodId(), null, BigDecimal.TEN, Currency.USD, UUID.randomUUID().toString(),
                                                                                 UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getPaymentMethodId(), newPaymentMethodId);
    }

    @Test(groups = "slow")
    public void testCreateAuthWithControlCaptureNoControl() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;

        Payment payment = paymentApi.createAuthorizationWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, UUID.randomUUID().toString(),
                                                                           UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        payment = paymentApi.createCapture(account, payment.getId(), payment.getAuthAmount(), payment.getCurrency(), UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
    }

    @Test(groups = "slow")
    public void testCreateAuthNoControlCaptureWithControl() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;

        Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, UUID.randomUUID().toString(),
                                                         UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        payment = paymentApi.createCaptureWithPaymentControl(account, payment.getId(), payment.getAuthAmount(), payment.getCurrency(), UUID.randomUUID().toString(),
                                                             ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
    }

    public static class TestPaymentControlPluginApi implements PaymentControlPluginApi {

        public static final String PLUGIN_NAME = "TEST_CONTROL_API_PLUGIN_NAME";

        private UUID newPaymentMethodId;

        public void setNewPaymentMethodId(final UUID newPaymentMethodId) {
            this.newPaymentMethodId = newPaymentMethodId;
        }

        @Override
        public PriorPaymentControlResult priorCall(final PaymentControlContext context, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
            return new PriorPaymentControlResult() {
                @Override
                public boolean isAborted() {
                    return false;
                }

                @Override
                public BigDecimal getAdjustedAmount() {
                    return null;
                }

                @Override
                public Currency getAdjustedCurrency() {
                    return null;
                }

                @Override
                public UUID getAdjustedPaymentMethodId() {
                    return newPaymentMethodId;
                }

                @Override
                public Iterable<PluginProperty> getAdjustedPluginProperties() {
                    return ImmutableList.of();
                }
            };
        }

        @Override
        public OnSuccessPaymentControlResult onSuccessCall(final PaymentControlContext context, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
            return new DefaultOnSuccessPaymentControlResult();
        }

        @Override
        public OnFailurePaymentControlResult onFailureCall(final PaymentControlContext context, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
            return new DefaultFailureCallResult(null);
        }
    }
}
