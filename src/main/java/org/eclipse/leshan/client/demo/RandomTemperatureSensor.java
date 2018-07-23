package org.eclipse.leshan.client.demo;

import java.lang.reflect.Field;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Date;
import java.util.UUID;
import java.lang.*;
import java.text.SimpleDateFormat;

import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import java.nio.charset.StandardCharsets;

import io.swagger.client.ApiException;
import io.swagger.client.api.*;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.VersionApi;
import io.swagger.client.api.DeviceApi;
import io.swagger.client.model.*;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.util.NamedThreadFactory;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomTemperatureSensor extends BaseInstanceEnabler {

    private static final String UNIT_CELSIUS = "cel";
    private static final int SENSOR_VALUE = 5700;
    private static final int UNITS = 5701;
    private static final int MAX_MEASURED_VALUE = 5602;
    private static final int MIN_MEASURED_VALUE = 5601;
    private static final int RESET_MIN_MAX_MEASURED_VALUES = 5605;
    private final ScheduledExecutorService scheduler;
    private final Random rng = new Random();
    private float currentTemp = 20;
    private float minMeasuredValue = currentTemp;
    private float maxMeasuredValue = currentTemp;
    private int min = 20;
    private int max = 50;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private Date timeStamp = new Date();
    private static String deviceStatus = "UNKNOWN";
    private static final Logger LOG = LoggerFactory.getLogger(RandomTemperatureSensor.class);

    public RandomTemperatureSensor(final String deviceId, final String token, final String httpsURL, final String URLPath, final String xDeviceNetwork) {        

        String baseApiUrl = httpsURL + "/" + URLPath;
        UUID endpoint = UUID.fromString(deviceId); // String | Device endpoint
        String v3Token = token;
        int connectTimeout = 60000;
        int readTimeout = 120000;


        this.scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Temperature Sensor"));
        scheduler.scheduleAtFixedRate(new Runnable() {
            private float temp = 1.0f;
            @Override
            public void run() {

                ApiClient client = new ApiClient();
                String authorization = v3Token; // String | Basic Access Authentication
                client.setBasePath(baseApiUrl);
                client.setDebugging(false);
 
                String smartObjectId = " ";
                // Send a random temperatre vale
                temp = (ThreadLocalRandom.current().nextInt(min, max + 1))/1.0f;
                adjustTemperature(temp);
                LOG.info("Temperature value " + getTwoDigitValue(temp) + " was send");
                // Wait for a while for the measurement to be sent
                try {
                    // thread to sleep for x milliseconds
                    Thread.sleep(3000);
                } catch (Exception e) {
                        LOG.error("Sleep failed: " + e);
                }

                //Read the device info
                DeviceApi deviceApiInstance = new DeviceApi(client);
                LOG.info("Reading back the temperature");
                SigmaSensationDTODeviceDeviceRequest deviceRequest = new SigmaSensationDTODeviceDeviceRequest();
                try {
                    SigmaSensationDTODeviceDeviceResponse result = deviceApiInstance.deviceGet_0(endpoint, xDeviceNetwork, authorization);

                    if (result != null) {
                        if ((client.getStatusCode() == 200) || (client.getStatusCode() == 204) ) {
                                for (SigmaSensationDTOSmartObjectSmartObjectListResponse g: result.getSmartObjects()) {
                                    if (g.getName().equals("Temperature")) {
                                        smartObjectId = g.getId().toString();
                                    }
                                }
                        } else {
                            deviceStatus = "Not OK";
                            LOG.error("Could not read SmartObject id, Error code: " + client.getStatusCode());
                            LOG.debug("Response from IoTA is: " + result);
                            System.exit(0);
                            return;
                        }
                    }

                } catch (ApiException e) {
                    deviceStatus = "Not OK";
                    LOG.error("Error reading the device: " + endpoint);
                    System.exit(0);
                    //e.printStackTrace();
                }

                // Read SmartObject
                if (smartObjectId.equals(" ")){
                    LOG.error("No Temperature device found");
                    System.exit(0);
                    return;
                }
                try {
                    // thread to sleep for 1000 milliseconds
                    Thread.sleep(3000);
                } catch (Exception e) {
                        LOG.error("Sleep failed: " + e);
                }

                SmartObjectApi readInstance = new SmartObjectApi(client);
                try {
                    readInstance.smartObjectReadResources(UUID.fromString(smartObjectId), xDeviceNetwork, authorization);
                } catch (ApiException e) {
                    LOG.error("Error: Unable to perform a read on smart object" + smartObjectId);
                    //e.printStackTrace();
                }

                // Fetch SmartObject Value
                try {
                    // thread to sleep for 3000 milliseconds to stabilize system
                    Thread.sleep(3000);
                } catch (Exception e) {
                        LOG.error("Sleep failed: " + e);
                }

                SmartObjectApi fetchInstance = new SmartObjectApi(client);
                try {
                    SigmaSensationDTOSmartObjectSmartObjectResponse result = fetchInstance.smartObjectGetLatestMeasurementForResourcesAsync(UUID.fromString(smartObjectId), xDeviceNetwork, authorization);
                    timeStamp = new Date();
                    if (result != null) {
                        if ((client.getStatusCode() == 200) || (client.getStatusCode() == 204) ) {
                            for (SigmaSensationDTOResourceResourceResponse g: result.getResources()) {
                                if (g.getName().equals("Sensor Value")) {
                                     SigmaSensationDTOGrainCommunicationSignalRMeasurementMessage measurement = g.getLatestMeasurement();
                                     LOG.debug("Temperature Read: " + measurement.getV());
                                     if (temp != measurement.getV()) {
                                         deviceStatus = "Not OK";                                                  
                                         LOG.debug("Measurement failed. The smartobject is " + smartObjectId + ".  Read value = " + measurement.getV() + " should have been " + temp);
                                         System.exit(0);
                                     } else {
                                         deviceStatus = "OK";
                                         LOG.info("Measurement OK");
                                         System.exit(0);
                                     }
                                }
                            }
                        } else {
                            deviceStatus = "Not OK";
                            LOG.error("Could not read the value from SmartObject, Error code: " + client.getStatusCode());
                            LOG.debug("Response from IoTA is: " + result);
                            System.exit(0);
                            return;
                        }
                    }
                } catch (ApiException e) {
                    deviceStatus = "Not OK";
                    LOG.error("Error reading the value from the device: " + endpoint);
                    System.exit(0);
                    //e.printStackTrace();
                }

            }
        }, 2, 2, TimeUnit.SECONDS);
        // De-register on shutdown and stop client.
/*
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                LOG.info("Deleting Record and closing connection");
                try {
                    DeleteResult deleteResult = devices.deleteOne(eq("deviceid", deviceId));
                    LOG.info("Deleted records: " + deleteResult.getDeletedCount());
                    mongo.close();
                } catch (Exception e) {
                    LOG.error("Mongo DB not properly closed: " + e);
                }
            }
        });
*/

    }

    @Override
    public synchronized ReadResponse read(int resourceId) {
        switch (resourceId) {
        case MIN_MEASURED_VALUE:
            return ReadResponse.success(resourceId, getTwoDigitValue(minMeasuredValue));
        case MAX_MEASURED_VALUE:
            return ReadResponse.success(resourceId, getTwoDigitValue(maxMeasuredValue));
        case SENSOR_VALUE:
            return ReadResponse.success(resourceId, getTwoDigitValue(currentTemp));
        //case UNITS:
        //    return ReadResponse.success(resourceId, UNIT_CELSIUS);
        default:
            return super.read(resourceId);
        }
    }

    @Override
    public synchronized ExecuteResponse execute(int resourceId, String params) {
        switch (resourceId) {
        case RESET_MIN_MAX_MEASURED_VALUES:
            resetMinMaxMeasuredValues();
            return ExecuteResponse.success();
        default:
            return super.execute(resourceId, params);
        }
    }

    private float getTwoDigitValue(final float value) {
        BigDecimal toBeTruncated = BigDecimal.valueOf(value);
        return toBeTruncated.setScale(2, RoundingMode.HALF_UP).floatValue();
    }

    private synchronized void adjustTemperature(Float temp) {
        currentTemp = temp;
        Integer changedResource = adjustMinMaxMeasuredValue(currentTemp);
        if (changedResource != null) {
            fireResourcesChange(SENSOR_VALUE, changedResource);
        } else {
            fireResourcesChange(SENSOR_VALUE);
        }
    }

    private Integer adjustMinMaxMeasuredValue(float newTemperature) {

        if (newTemperature > maxMeasuredValue) {
            maxMeasuredValue = newTemperature;
            return MAX_MEASURED_VALUE;
        } else if (newTemperature < minMeasuredValue) {
            minMeasuredValue = newTemperature;
            return MIN_MEASURED_VALUE;
        } else {
            return null;
        }
    }

    private void resetMinMaxMeasuredValues() {
        minMeasuredValue = currentTemp;
        maxMeasuredValue = currentTemp;
    }
}
