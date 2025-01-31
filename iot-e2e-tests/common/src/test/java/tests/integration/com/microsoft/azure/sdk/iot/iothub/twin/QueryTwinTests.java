/*
 *  Copyright (c) Microsoft. All rights reserved.
 *  Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package tests.integration.com.microsoft.azure.sdk.iot.iothub.twin;

import com.azure.core.credential.AzureSasCredential;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.microsoft.azure.sdk.iot.deps.twin.TwinConnectionState;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.exceptions.ModuleClientException;
import com.microsoft.azure.sdk.iot.service.IotHubConnectionString;
import com.microsoft.azure.sdk.iot.service.IotHubConnectionStringBuilder;
import com.microsoft.azure.sdk.iot.service.auth.AuthenticationType;
import com.microsoft.azure.sdk.iot.service.auth.IotHubServiceSasToken;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwin;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwinClientOptions;
import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceTwinDevice;
import com.microsoft.azure.sdk.iot.service.devicetwin.Pair;
import com.microsoft.azure.sdk.iot.service.devicetwin.Query;
import com.microsoft.azure.sdk.iot.service.devicetwin.QueryCollection;
import com.microsoft.azure.sdk.iot.service.devicetwin.QueryCollectionResponse;
import com.microsoft.azure.sdk.iot.service.devicetwin.QueryOptions;
import com.microsoft.azure.sdk.iot.service.devicetwin.SqlQuery;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubUnathorizedException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import tests.integration.com.microsoft.azure.sdk.iot.helpers.ClientType;
import tests.integration.com.microsoft.azure.sdk.iot.helpers.CorrelationDetailsLoggingAssert;
import tests.integration.com.microsoft.azure.sdk.iot.helpers.IntegrationTest;
import tests.integration.com.microsoft.azure.sdk.iot.helpers.SasTokenTools;
import tests.integration.com.microsoft.azure.sdk.iot.helpers.TestConstants;
import tests.integration.com.microsoft.azure.sdk.iot.helpers.Tools;
import tests.integration.com.microsoft.azure.sdk.iot.helpers.annotations.ContinuousIntegrationTest;
import tests.integration.com.microsoft.azure.sdk.iot.helpers.annotations.IotHubTest;
import tests.integration.com.microsoft.azure.sdk.iot.helpers.annotations.StandardTierHubOnlyTest;
import tests.integration.com.microsoft.azure.sdk.iot.iothub.setup.DeviceTwinCommon;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.microsoft.azure.sdk.iot.device.IotHubClientProtocol.HTTPS;
import static com.microsoft.azure.sdk.iot.service.auth.AuthenticationType.SAS;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.*;

/**
 * Test class containing all tests to be run on JVM and android pertaining to Queries.
 */
@Slf4j
@IotHubTest
@RunWith(Parameterized.class)
public class QueryTwinTests extends DeviceTwinCommon
{
    public static final int QUERY_TIMEOUT_MILLISECONDS = 4 * 60 * 1000; // 4 minutes

    public QueryTwinTests(IotHubClientProtocol protocol, AuthenticationType authenticationType, ClientType clientType) throws IOException
    {
        super(protocol, authenticationType, clientType);
    }

    // Override the input parameters that are defined in DeviceTwinCommon since these query tests are strictly service client tests.
    // No need to parameterize these tests on device client options.
    @Parameterized.Parameters(name = "{0}_{1}_{2}")
    public static Collection inputs()
    {
        iotHubConnectionString = Tools.retrieveEnvironmentVariableValue(TestConstants.IOT_HUB_CONNECTION_STRING_ENV_VAR_NAME);
        IntegrationTest.isBasicTierHub = Boolean.parseBoolean(Tools.retrieveEnvironmentVariableValue(TestConstants.IS_BASIC_TIER_HUB_ENV_VAR_NAME));
        IntegrationTest.isPullRequest = Boolean.parseBoolean(Tools.retrieveEnvironmentVariableValue(TestConstants.IS_PULL_REQUEST));

        return Arrays.asList(
                    new Object[][]
                            {
                                    //Query is only supported over http and only with sas based authentication
                                    {HTTPS, SAS, ClientType.DEVICE_CLIENT},
                            });
    }

    @Test
    @StandardTierHubOnlyTest
    public void testRawQueryTwinWithConnectionString() throws IOException, InterruptedException, IotHubException, GeneralSecurityException, URISyntaxException, ModuleClientException
    {
        testInstance.twinServiceClient = new DeviceTwin(iotHubConnectionString);
        testRawQueryTwin();
    }

    @Test
    @StandardTierHubOnlyTest
    public void testRawQueryTwinWithAzureSasCredential() throws IOException, InterruptedException, IotHubException, GeneralSecurityException, URISyntaxException, ModuleClientException
    {
        IotHubConnectionString iotHubConnectionStringObj =
                IotHubConnectionStringBuilder.createIotHubConnectionString(iotHubConnectionString);

        IotHubServiceSasToken serviceSasToken = new IotHubServiceSasToken(iotHubConnectionStringObj);
        AzureSasCredential sasCredential = new AzureSasCredential(serviceSasToken.toString());
        testInstance.twinServiceClient = new DeviceTwin(iotHubConnectionStringObj.getHostName(), sasCredential);

        testRawQueryTwin();
    }

    @Test
    @StandardTierHubOnlyTest
    public void rawQueryTokenRenewalWithAzureSasCredential() throws IOException, InterruptedException, IotHubException, GeneralSecurityException, URISyntaxException, ModuleClientException
    {
        IotHubConnectionString iotHubConnectionStringObj =
            IotHubConnectionStringBuilder.createIotHubConnectionString(iotHubConnectionString);

        IotHubServiceSasToken serviceSasToken = new IotHubServiceSasToken(iotHubConnectionStringObj);

        AzureSasCredential sasCredential = new AzureSasCredential(serviceSasToken.toString());
        testInstance.twinServiceClient = new DeviceTwin(iotHubConnectionStringObj.getHostName(), sasCredential);

        // test that the service client can query before the shared access signature expires
        testRawQueryTwin();

        // deliberately expire the SAS token to provoke a 401 to ensure that the registry manager is using the shared
        // access signature that is set here.
        sasCredential.update(SasTokenTools.makeSasTokenExpired(serviceSasToken.toString()));

        try
        {
            testRawQueryTwin();
            fail("Expected query call to throw unauthorized exception since an expired SAS token was used, but no exception was thrown");
        }
        catch (IotHubUnathorizedException e)
        {
            log.debug("IotHubUnauthorizedException was thrown as expected, continuing test");
        }

        // Renew the expired shared access signature
        serviceSasToken = new IotHubServiceSasToken(iotHubConnectionStringObj);
        sasCredential.update(serviceSasToken.toString());

        // test that the service client can query after renewing the shared access signature expires
        testRawQueryTwin();
    }

    public void testRawQueryTwin() throws IOException, InterruptedException, IotHubException, GeneralSecurityException, URISyntaxException, ModuleClientException
    {
        addMultipleDevices(MAX_DEVICES, false);
        Gson gson = new GsonBuilder().enableComplexMapKeySerialization().serializeNulls().create();

        // Add same desired on multiple devices
        final String queryProperty = PROPERTY_KEY_QUERY + UUID.randomUUID().toString();
        final String queryPropertyValue = PROPERTY_VALUE_QUERY + UUID.randomUUID().toString();
        final int expectedNumberOfDevices = MAX_DEVICES;

        setDesiredProperties(queryProperty, queryPropertyValue, MAX_DEVICES);

        Thread.sleep(DESIRED_PROPERTIES_PROPAGATION_TIME_MILLISECONDS);

        // Raw Query for multiple devices having same property
        final String select = "properties.desired." + queryProperty + " AS " + queryProperty + "," + " COUNT() AS numberOfDevices";
        final String groupBy = "properties.desired." + queryProperty;
        final SqlQuery sqlQuery = SqlQuery.createSqlQuery(select, SqlQuery.FromType.DEVICES, null, groupBy);

        boolean querySucceeded = false;
        long startTime = System.currentTimeMillis();
        while (!querySucceeded)
        {
            Query rawTwinQuery = testInstance.rawTwinQueryClient.query(sqlQuery.getQuery(), PAGE_SIZE);

            while (testInstance.rawTwinQueryClient.hasNext(rawTwinQuery))
            {
                String result = testInstance.rawTwinQueryClient.next(rawTwinQuery);
                assertNotNull(result);
                Map map = gson.fromJson(result, Map.class);
                if (map.containsKey("numberOfDevices") && map.containsKey(queryProperty))
                {
                    // Casting as a double first to get the value from the map, but then casting to an int because the
                    // number of devices should always be an integer
                    int actualNumberOfDevices = (int) (double) map.get("numberOfDevices");
                    if (actualNumberOfDevices == expectedNumberOfDevices)
                    {
                        // Due to propagation delays, there will be times when the query is executed and only a
                        // subset of the expected devices are queryable. This test will loop until all of them are queryable
                        // to avoid this issue.
                        querySucceeded = true;
                    }
                    else
                    {
                        log.info("Expected device count not correct, re-running query");
                        Thread.sleep(200);
                    }
                }
            }

            if (System.currentTimeMillis() - startTime > QUERY_TIMEOUT_MILLISECONDS)
            {
                fail("Timed out waiting for query results to match expectations");
            }
        }
    }

    @Test
    @StandardTierHubOnlyTest
    @ContinuousIntegrationTest
    public void testRawQueryMultipleInParallelTwin() throws IOException, InterruptedException, IotHubException, GeneralSecurityException, URISyntaxException, ModuleClientException
    {
        addMultipleDevices(MAX_DEVICES, false);
        final Gson gson = new GsonBuilder().enableComplexMapKeySerialization().serializeNulls().create();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Add same desired on multiple devices
        final String queryProperty = PROPERTY_KEY_QUERY + UUID.randomUUID().toString();
        final String queryPropertyValue = PROPERTY_VALUE_QUERY + UUID.randomUUID().toString();
        final int expectedNumberOfDevices = MAX_DEVICES;

        final String queryPropertyEven = PROPERTY_KEY_QUERY + UUID.randomUUID().toString();
        final String queryPropertyValueEven = PROPERTY_VALUE_QUERY + UUID.randomUUID().toString();
        int noOfEvenDevices = 0;

        for (int i = 0; i < MAX_DEVICES; i++)
        {
            Set<Pair> desiredProperties = new HashSet<>();
            desiredProperties.add(new Pair(queryProperty, queryPropertyValue));
            if (i % 2 == 0)
            {
                desiredProperties.add(new Pair(queryPropertyEven, queryPropertyValueEven));
                noOfEvenDevices++;
            }
            testInstance.devicesUnderTest[i].sCDeviceForTwin.setDesiredProperties(desiredProperties);

            testInstance.twinServiceClient.updateTwin(testInstance.devicesUnderTest[i].sCDeviceForTwin);
            testInstance.devicesUnderTest[i].sCDeviceForTwin.clearTwin();
        }

        executor.submit(() -> {
            try
            {
                // Raw Query for multiple devices having same property
                final String select = "properties.desired." + queryProperty + " AS " + queryProperty + "," + " COUNT() AS numberOfDevices";
                final String groupBy = "properties.desired." + queryProperty;
                final SqlQuery sqlQuery = SqlQuery.createSqlQuery(select, SqlQuery.FromType.DEVICES, null, groupBy);

                boolean querySucceeded = false;
                long startTime = System.currentTimeMillis();
                while (!querySucceeded)
                {
                    Query rawTwinQuery = testInstance.rawTwinQueryClient.query(sqlQuery.getQuery(), PAGE_SIZE);

                    while (testInstance.rawTwinQueryClient.hasNext(rawTwinQuery))
                    {
                        String result = testInstance.rawTwinQueryClient.next(rawTwinQuery);
                        assertNotNull(result);
                        Map map = gson.fromJson(result, Map.class);
                        if (map.containsKey("numberOfDevices") && map.containsKey(queryProperty))
                        {
                            // Casting as a double first to get the value from the map, but then casting to an int because the
                            // number of devices should always be an integer
                            int actualNumberOfDevices = (int) (double) map.get("numberOfDevices");
                            if (actualNumberOfDevices == expectedNumberOfDevices)
                            {
                                querySucceeded = true;
                            }
                            else
                            {
                                log.info("Expected device count not correct, re-running query");
                                Thread.sleep(200);
                            }
                        }
                    }

                    if (System.currentTimeMillis() - startTime > QUERY_TIMEOUT_MILLISECONDS)
                    {
                        fail("Timed out waiting for query results to match expectations");
                    }
                }
            }
            catch (Exception e)
            {
                fail(e.getMessage());
            }

        });

        final double expectedNumberOfDevicesEven = noOfEvenDevices;
        executor.submit(() -> {
            try
            {
                // Raw Query for multiple devices having same property
                final String select = "properties.desired." + queryPropertyEven + " AS " + queryPropertyEven + "," + " COUNT() AS numberOfDevices";
                final String groupBy = "properties.desired." + queryPropertyEven;
                final SqlQuery sqlQuery = SqlQuery.createSqlQuery(select, SqlQuery.FromType.DEVICES, null, groupBy);

                boolean querySucceeded = false;
                long startTime = System.currentTimeMillis();
                while (!querySucceeded)
                {
                    Query rawTwinQuery = testInstance.rawTwinQueryClient.query(sqlQuery.getQuery(), PAGE_SIZE);

                    while (testInstance.rawTwinQueryClient.hasNext(rawTwinQuery))
                    {
                        String result = testInstance.rawTwinQueryClient.next(rawTwinQuery);
                        assertNotNull(result);
                        Map map = gson.fromJson(result, Map.class);
                        if (map.containsKey("numberOfDevices") && map.containsKey(queryPropertyEven))
                        {
                            // Casting as a double first to get the value from the map, but then casting to an int because the
                            // number of devices should always be an integer
                            int actualNumberOfDevices = (int) (double) map.get("numberOfDevices");
                            if (actualNumberOfDevices == expectedNumberOfDevicesEven)
                            {
                                querySucceeded = true;
                            }
                            else
                            {
                                log.info("Expected device count not correct, re-running query");
                                Thread.sleep(200);
                            }
                        }
                    }

                    if (System.currentTimeMillis() - startTime > QUERY_TIMEOUT_MILLISECONDS)
                    {
                        fail("Timed out waiting for query results to match expectations");
                    }
                }
            }
            catch (Exception e)
            {
                fail(e.getMessage());
            }
        });

        executor.shutdown();
        if (!executor.awaitTermination(MULTITHREADED_WAIT_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS))
        {
            executor.shutdownNow();
        }
    }

    @Test
    @StandardTierHubOnlyTest
    public void testQueryTwinWithConnectionString() throws IOException, InterruptedException, IotHubException, GeneralSecurityException, URISyntaxException, ModuleClientException
    {
        testInstance.twinServiceClient = new DeviceTwin(iotHubConnectionString);
        testQueryTwin();
    }

    @Test
    @StandardTierHubOnlyTest
    public void testQueryTwinWithAzureSasCredential() throws IOException, InterruptedException, IotHubException, GeneralSecurityException, URISyntaxException, ModuleClientException
    {
        testInstance.twinServiceClient = buildDeviceTwinClientWithAzureSasCredential();
        testQueryTwin();
    }

    public void testQueryTwin() throws InterruptedException, ModuleClientException, IOException, GeneralSecurityException, IotHubException, URISyntaxException
    {
        addMultipleDevices(MAX_DEVICES, false);

        // Add same desired on multiple devices
        final String queryProperty = PROPERTY_KEY_QUERY + UUID.randomUUID().toString();
        final String queryPropertyValue = PROPERTY_VALUE_QUERY + UUID.randomUUID().toString();

        setDesiredProperties(queryProperty, queryPropertyValue, MAX_DEVICES);

        // Query multiple devices having same property
        final String where = "is_defined(properties.desired." + queryProperty + ")";
        SqlQuery sqlQuery = SqlQuery.createSqlQuery("*", SqlQuery.FromType.DEVICES, where, null);
        Thread.sleep(MAXIMUM_TIME_FOR_IOTHUB_PROPAGATION_BETWEEN_DEVICE_SERVICE_CLIENTS);
        Query twinQuery = testInstance.twinServiceClient.queryTwin(sqlQuery.getQuery(), PAGE_SIZE);

        for (int i = 0; i < MAX_DEVICES; i++)
        {
            if (testInstance.twinServiceClient.hasNextDeviceTwin(twinQuery))
            {
                DeviceTwinDevice d = testInstance.twinServiceClient.getNextDeviceTwin(twinQuery);
                assertNotNull(d.getVersion());

                assertEquals(TwinConnectionState.DISCONNECTED.toString(), d.getConnectionState());

                for (Pair dp : d.getDesiredProperties())
                {
                    Assert.assertEquals("Unexpected desired property key, expected " + queryProperty + " but was " + dp.getKey(), queryProperty, dp.getKey());
                    Assert.assertEquals("Unexpected desired property value, expected " + queryPropertyValue + " but was " + dp.getValue(), queryPropertyValue, dp.getValue());
                }
            }
        }
        assertFalse(testInstance.twinServiceClient.hasNextDeviceTwin(twinQuery));
    }

    @Test
    @StandardTierHubOnlyTest
    @ContinuousIntegrationTest
    public void testQueryTwinWithContinuationToken() throws IOException, InterruptedException, IotHubException, GeneralSecurityException, URISyntaxException, ModuleClientException
    {
        addMultipleDevices(PAGE_SIZE + 1, false);

        // Add same desired on multiple devices so that they can be queried
        final String queryProperty = PROPERTY_KEY_QUERY + UUID.randomUUID().toString();
        final String queryPropertyValue = PROPERTY_VALUE_QUERY + UUID.randomUUID().toString();
        setDesiredProperties(queryProperty, queryPropertyValue, PAGE_SIZE + 1);

        // Query multiple devices having same property
        final String where = "is_defined(properties.desired." + queryProperty + ")";

        SqlQuery sqlQuery;
        if (this.testInstance.clientType == ClientType.MODULE_CLIENT)
        {
            sqlQuery = SqlQuery.createSqlQuery("*", SqlQuery.FromType.MODULES, where, null);
        }
        else
        {
            sqlQuery = SqlQuery.createSqlQuery("*", SqlQuery.FromType.DEVICES, where, null);
        }


        // There is some propagation delay between when all the devices are created and have their twins set, and when
        // they become queryable. This test assumes that eventually, the query result will have multiple pages. To
        // avoid querying too soon, this test repeatedly queries until the continuation token is present in the return value
        // as expected or until a timeout is hit.
        String continuationToken = null;
        Collection<DeviceTwinDevice> queriedDeviceTwinDeviceCollection = null;
        long startTime = System.currentTimeMillis();
        while (continuationToken == null)
        {
            QueryCollection twinQueryCollection = testInstance.twinServiceClient.queryTwinCollection(sqlQuery.getQuery(), PAGE_SIZE);

            // Run a query and save the continuation token for the second page of results
            QueryCollectionResponse<DeviceTwinDevice> queryCollectionResponse = testInstance.twinServiceClient.next(twinQueryCollection);
            queriedDeviceTwinDeviceCollection = queryCollectionResponse.getCollection();
            continuationToken = queryCollectionResponse.getContinuationToken();

            if (continuationToken == null)
            {
                log.info("No continuation token detected yet, re-running the query");
                Thread.sleep(200);
            }

            if (System.currentTimeMillis() - startTime > QUERY_TIMEOUT_MILLISECONDS)
            {
                fail("Timed out waiting for query to return a continuation token");
            }
        }


        // Re-run the same query using the saved continuation token. The results can be predicted since this test caused them
        QueryOptions options = new QueryOptions();
        options.setContinuationToken(continuationToken);
        options.setPageSize(PAGE_SIZE);
        QueryCollection twinQueryToReRun = testInstance.twinServiceClient.queryTwinCollection(sqlQuery.getQuery());
        Collection<DeviceTwinDevice> continuedDeviceTwinDeviceQuery = testInstance.twinServiceClient.next(twinQueryToReRun, options).getCollection();

        // Assert
        assertEquals((long) PAGE_SIZE, queriedDeviceTwinDeviceCollection.size());
        assertEquals(1, continuedDeviceTwinDeviceQuery.size());

        // since order is not guaranteed, we cannot check that the third updated deviceTwinDevice is the third queried.
        // Instead, all we can check is that each updated device twin identity is in either the initial query or the continued query.
        ArrayList<String> expectedDeviceIds = new ArrayList<>();
        for (int deviceTwinDeviceIndex = 0; deviceTwinDeviceIndex < PAGE_SIZE + 1; deviceTwinDeviceIndex++)
        {
            expectedDeviceIds.add(testInstance.devicesUnderTest[deviceTwinDeviceIndex].sCDeviceForTwin.getDeviceId());
        }

        Collection<DeviceTwinDevice> allQueriedDeviceTwinDevices = new ArrayList<>(continuedDeviceTwinDeviceQuery);
        continuedDeviceTwinDeviceQuery.addAll(queriedDeviceTwinDeviceCollection);

        for (DeviceTwinDevice deviceTwinDevice : allQueriedDeviceTwinDevices)
        {
            if (!expectedDeviceIds.contains(deviceTwinDevice.getDeviceId()))
            {
                fail("Missing deviceTwinDevice: continuation token did not continue query where expected");
            }
        }
    }

    @Test
    @StandardTierHubOnlyTest
    @ContinuousIntegrationTest
    public void queryCollectionCanReturnEmptyQueryResults() throws IOException, IotHubException
    {
        String fullQuery = "select * from devices where deviceId='nonexistantdevice'";
        DeviceTwin twinClient = new DeviceTwin(iotHubConnectionString, DeviceTwinClientOptions.builder().httpReadTimeout(HTTP_READ_TIMEOUT).build());
        QueryCollection twinQuery = twinClient.queryTwinCollection(fullQuery);
        QueryOptions options = new QueryOptions();
        QueryCollectionResponse<DeviceTwinDevice> response = twinClient.next(twinQuery, options);

        assertNull(response.getContinuationToken());
        assertTrue(response.getCollection().isEmpty());
    }

    @Test
    @StandardTierHubOnlyTest
    @ContinuousIntegrationTest
    public void testMultipleQueryTwinInParallel() throws IOException, InterruptedException, IotHubException, GeneralSecurityException, URISyntaxException, ModuleClientException
    {
        addMultipleDevices(MAX_DEVICES, false);

        // Add same desired on multiple devices
        final String queryProperty = PROPERTY_KEY_QUERY + UUID.randomUUID().toString();
        final String queryPropertyEven = PROPERTY_KEY_QUERY + UUID.randomUUID().toString();
        final String queryPropertyValue = PROPERTY_VALUE_QUERY + UUID.randomUUID().toString();
        final String queryPropertyValueEven = PROPERTY_VALUE_QUERY + UUID.randomUUID().toString();
        int noOfEvenDevices = 0;
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < MAX_DEVICES; i++)
        {
            Set<Pair> desiredProperties = new HashSet<>();
            desiredProperties.add(new Pair(queryProperty, queryPropertyValue));
            if (i % 2 == 0)
            {
                desiredProperties.add(new Pair(queryPropertyEven, queryPropertyValueEven));
                noOfEvenDevices++;
            }
            testInstance.devicesUnderTest[i].sCDeviceForTwin.setDesiredProperties(desiredProperties);

            testInstance.twinServiceClient.updateTwin(testInstance.devicesUnderTest[i].sCDeviceForTwin);
            testInstance.devicesUnderTest[i].sCDeviceForTwin.clearTwin();
        }

        // Query multiple devices having same property

        executor.submit(() -> {
            try
            {
                final String where = "is_defined(properties.desired." + queryProperty + ")";
                SqlQuery sqlQuery = SqlQuery.createSqlQuery("*", SqlQuery.FromType.DEVICES, where, null);
                final Query twinQuery = testInstance.twinServiceClient.queryTwin(sqlQuery.getQuery(), PAGE_SIZE);

                for (int i = 0; i < MAX_DEVICES; i++)
                {
                    try
                    {
                        if (testInstance.twinServiceClient.hasNextDeviceTwin(twinQuery))
                        {
                            DeviceTwinDevice d = testInstance.twinServiceClient.getNextDeviceTwin(twinQuery);

                            assertNotNull(d.getVersion());
                            for (Pair dp : d.getDesiredProperties())
                            {
                                assertEquals(dp.getKey(), queryProperty);
                                assertEquals(dp.getValue(), queryPropertyValue);
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        fail(e.getMessage());
                    }

                    assertFalse(testInstance.twinServiceClient.hasNextDeviceTwin(twinQuery));
                }
            }
            catch (Exception e)
            {
                fail(e.getMessage());
            }
        });

        final int maximumEvenDevices = noOfEvenDevices;
        executor.submit(() -> {
            try
            {
                final String whereEvenDevices = "is_defined(properties.desired." + queryPropertyEven + ")";
                SqlQuery sqlQueryEvenDevices = SqlQuery.createSqlQuery("*", SqlQuery.FromType.DEVICES, whereEvenDevices, null);
                final Query twinQueryEven = testInstance.twinServiceClient.queryTwin(sqlQueryEvenDevices.getQuery(), PAGE_SIZE);

                for (int i = 0; i < maximumEvenDevices; i++)
                {
                    try
                    {
                        if (testInstance.twinServiceClient.hasNextDeviceTwin(twinQueryEven))
                        {
                            DeviceTwinDevice d = testInstance.twinServiceClient.getNextDeviceTwin(twinQueryEven);

                            assertNotNull(d.getVersion());
                            for (Pair dp : d.getDesiredProperties())
                            {
                                assertEquals(dp.getKey(), queryPropertyEven);
                                assertEquals(dp.getValue(), queryPropertyValueEven);
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        fail(e.getMessage());
                    }

                    assertFalse(testInstance.twinServiceClient.hasNextDeviceTwin(twinQueryEven));
                }
            }
            catch (Exception e)
            {
                fail(e.getMessage());
            }
        });

        executor.shutdown();
        if (!executor.awaitTermination(MULTITHREADED_WAIT_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS))
        {
            executor.shutdownNow();
        }
    }

    public void setDesiredProperties(String queryProperty, String queryPropertyValue, int numberOfDevices) throws IOException, IotHubException
    {
        for (int i = 0; i < numberOfDevices; i++)
        {
            Set<Pair> desiredProperties = new HashSet<>();
            desiredProperties.add(new Pair(queryProperty, queryPropertyValue));
            testInstance.devicesUnderTest[i].sCDeviceForTwin.setDesiredProperties(desiredProperties);

            testInstance.twinServiceClient.updateTwin(testInstance.devicesUnderTest[i].sCDeviceForTwin);
            testInstance.devicesUnderTest[i].sCDeviceForTwin.clearTwin();
        }
    }
}
