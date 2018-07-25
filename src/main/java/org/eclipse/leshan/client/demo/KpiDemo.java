/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Zebra Technologies - initial API and implementation
 *     Sierra Wireless, - initial API and implementation
 *     Bosch Software Innovations GmbH, - initial API and implementation
 *******************************************************************************/

package org.eclipse.leshan.client.demo;

import static org.eclipse.leshan.LwM2mId.*;
import static org.eclipse.leshan.client.object.Security.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.Arrays;
import java.util.logging.Level;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.CaliforniumLogger;
import org.eclipse.californium.scandium.ScandiumLogger;
import org.eclipse.leshan.LwM2m;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.util.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class KpiDemo {

    private static final Logger LOG = LoggerFactory.getLogger(KpiDemo.class);
    static {
        CaliforniumLogger.initialize();
        CaliforniumLogger.setLevel(Level.WARNING);
        ScandiumLogger.initialize();
        ScandiumLogger.setLevel(Level.OFF);
    }

    private final static String[] modelPaths = new String[] { "3303.xml" };

    private static final int OBJECT_ID_TEMPERATURE_SENSOR = 3303;
    private final static String DEFAULT_ENDPOINT = "LeshanClientDemo";
    private final static String USAGE = "java -jar leshan-client-demo.jar [OPTION]";
    private final static String DEFAULT_TOKEN = "TestToken";

    private static UUID idOne = UUID.randomUUID();
    private static UUID idTwo = UUID.randomUUID();
    private static String httpsURL = null;
    private static String URLPath = null; 

    private static RandomTemperatureSensor temperatureInstance;
    private static String token = DEFAULT_TOKEN;
    private static String serverURI = null;
    private static String XDeviceNetwork = null;
    private static int connectTimeout = 0;
    private static int readTimeout = 0;
    private static String pskKeyString = null;
    private static String pskIdentityString = null;
    private static byte[] pskIdentity = null;
    private static byte[] pskKey = null;
    private static String endpoint = null;
    private static boolean LOCAL = false;
    private static boolean USE_BOOTSTRAP = false;
    private static final String SECRETS_DIR = "/run/secrets/";

    public static void main(final String[] args) {

        String deviceId = null;
        Options options = new Options();

        options.addOption("h", "help", false, "Display help information.");
        options.addOption("l", false, "If present use bootstrap.");
        HelpFormatter formatter = new HelpFormatter();
        formatter.setOptionComparator(null);

        // Parse arguments
        CommandLine cl;
        try {
            cl = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println("Parsing failed.  Reason: " + e.getMessage());
            formatter.printHelp(USAGE, options);
            return;
        }

        // Use local configuration
        if (cl.hasOption("l")) {
            LOCAL = true;
        } else {
            LOCAL = false;
        }

        // Print help
        if (cl.hasOption("help")) {
            formatter.printHelp(USAGE, options);
            return;
        }

        // Abort if unexpected options
        if (cl.getArgs().length > 0) {
            System.err.println("Unexpected option or arguments : " + cl.getArgList());
            formatter.printHelp(USAGE, options);
            return;
        }

        //pskIdentity = endpoint.getBytes();
        JSONObject confObject = readConfig();
        deviceId = generateClient(endpoint, pskIdentity, pskKey, confObject);
        if (deviceId != null) {
            createAndStartClient(endpoint, USE_BOOTSTRAP, serverURI, token, deviceId, 2.64d, httpsURL, URLPath, readTimeout, connectTimeout);
        } else {
            LOG.error("Error in registrating device. Aborting");
            System.exit(0);
        }
    }

    public static JSONObject readConfig() {
        String fileName = null;
        //secretsConfiguration = new SecretsConfig();
        String deviceId = null;
        String pskKey = null;
        JSONParser fileparser = new JSONParser();
        JSONObject configObject = new JSONObject();
        if (LOCAL) {
            fileName = "secret/configuration.json";
        } else {
            fileName = SECRETS_DIR + "configuration.json";
        } 
        try {
            Object file = fileparser.parse(new FileReader(fileName));
            configObject =  (JSONObject) file;
            token = configObject.get("Authorization").toString();
            configObject.remove("Authorization");
            httpsURL = configObject.get("httpsURL").toString();
            configObject.remove("httpsURL");
            URLPath = configObject.get("URLPath").toString();
            configObject.remove("URLPath");
            serverURI = "coaps://" + configObject.get("serverURI").toString();
            configObject.remove("serverURI");
            XDeviceNetwork = configObject.get("X-DeviceNetwork").toString();
            configObject.remove("X-DeviceNetwork");
            endpoint = configObject.get("endpoint").toString() + "-" + idOne;
            configObject.remove("endpoint");
            configObject.put("DeviceIdentifier", endpoint);
            configObject.put("Name", endpoint);
            pskIdentityString = endpoint;
            connectTimeout = (int) (long) configObject.get("ConnectTimeout");
            configObject.remove("ConnectTimeout");
            readTimeout = (int) (long) configObject.get("ReadTimeout");
            configObject.remove("ReadTimeout");
            pskKeyString = configObject.get("pskKey").toString();
            configObject.remove("pskKey");
            JSONArray SettingsCat = new JSONArray();
            JSONObject SettingsCat1 = new JSONObject();
            JSONArray Settings = new JSONArray();
            JSONObject identity = new JSONObject();
            JSONObject key = new JSONObject();
            key.put("DataType", "String");
            key.put("Key", "preSharedKey");
            key.put("Value", pskKeyString);
            identity.put("DataType", "String");
            identity.put("Key", "identity");
            identity.put("Value", endpoint);
            Settings.add(identity);
            Settings.add(key);
            SettingsCat1.put("Name", "PSK");
            SettingsCat1.put("Settings", Settings);
            SettingsCat.add(SettingsCat1);
            configObject.put("SettingCategories", SettingsCat);

        } catch (IOException | org.json.simple.parser.ParseException e) {
            e.printStackTrace();
            System.exit(0);
        }
    return configObject;
    }

    public static String generateClient(String endpoint, byte[] pskIdentity, byte[] pskKey, JSONObject configObject) {

        String deviceId = null;
        HttpClient httpClient = HttpClientBuilder.create().build();
        try {
            String PATH = "devices";
            HttpPost request = new HttpPost(httpsURL + "/" + URLPath +"/api/v3/" + PATH);
            StringEntity params =new StringEntity(configObject.toString());
            LOG.info("Device is to be created");
            request.addHeader("content-type", "application/json");
            request.addHeader("X-DeviceNetwork", XDeviceNetwork);
            request.addHeader("Authorization", token);
            request.setEntity(params);
            HttpResponse response = httpClient.execute(request);
            if (response != null) {
                if (response.getStatusLine().getStatusCode() == 201) {
                    InputStream in = response.getEntity().getContent(); //Get the data in the entity
                    String result = IOUtils.toString(in, StandardCharsets.UTF_8);
                    JSONParser jsonParser = new JSONParser();
                    JSONObject jsonObject = (JSONObject) jsonParser.parse(result);
                    deviceId = (String) jsonObject.get("Id");
                    LOG.info("Device with Id " + deviceId + " has been created");
                    return deviceId;
                } else {
                    LOG.error("Error in responce code from IoTA: " + response.getStatusLine().getStatusCode());
                    InputStream in = response.getEntity().getContent(); //Get the data in the entity
                    String result = IOUtils.toString(in, StandardCharsets.UTF_8);
                    LOG.error("Response from IoTA is: " + result);
                    System.exit(0);
                }
            }
        } catch (Exception ex) {

            System.out.println("Error in Communication to IoTA " + ex);
            System.exit(0);
            return null;
        } finally {
            //httpClient.releaseConnection();;
        }
        return null;
    }

    public static void createAndStartClient(String endpoint, boolean needBootstrap, String serverURI, final String token, final String deviceId, Double temp, final String httpsURL, final String URLPath, int readTimeout, int connectTimeout) {

        byte [] pskIdentity = pskIdentityString.getBytes();
        byte[] pskKey = pskKeyString.getBytes();
        int localPort = 0;
        String localAddress = null;
        int secureLocalPort = 0;
        String secureLocalAddress = null;
        //locationInstance = new MyLocation(latitude, longitude, scaleFactor);
        LOG.debug("X-deviceNetwork is " +  XDeviceNetwork);
        temperatureInstance = new RandomTemperatureSensor(deviceId, token, httpsURL, URLPath, XDeviceNetwork, readTimeout, connectTimeout);

        // Initialize model
        List<ObjectModel> models = ObjectLoader.loadDefault();
        models.addAll(ObjectLoader.loadDdfResources("/models", modelPaths));

        // Initialize object list
        ObjectsInitializer initializer = new ObjectsInitializer(new LwM2mModel(models));
        if (needBootstrap) {
            if (pskIdentity == null)
                initializer.setInstancesForObject(SECURITY, noSecBootstap(serverURI));
            else
                initializer.setInstancesForObject(SECURITY, pskBootstrap(serverURI, pskIdentity, pskKey));
        } else {
            if (pskIdentity == null) {
                initializer.setInstancesForObject(SECURITY, noSec(serverURI, 123));
                initializer.setInstancesForObject(SERVER, new Server(123, 30, BindingMode.U, false));
            } else {
                initializer.setInstancesForObject(SECURITY, psk(serverURI, 123, pskIdentity, pskKey));
                initializer.setInstancesForObject(SERVER, new Server(123, 30, BindingMode.U, false));
            }
        }
        //initializer.setClassForObject(DEVICE, MyDevice.class);
        initializer.setInstancesForObject(OBJECT_ID_TEMPERATURE_SENSOR, temperatureInstance);
        //List<LwM2mObjectEnabler> enablers = initializer.create(SECURITY, SERVER, DEVICE, LOCATION,
        //        OBJECT_ID_TEMPERATURE_SENSOR);
        List<LwM2mObjectEnabler> enablers = initializer.create(SECURITY, SERVER,
                OBJECT_ID_TEMPERATURE_SENSOR);


        // Create CoAP Config
        NetworkConfig coapConfig;
        File configFile = new File(NetworkConfig.DEFAULT_FILE_NAME);
        if (configFile.isFile()) {
            coapConfig = new NetworkConfig();
            coapConfig.load(configFile);
        } else {
            coapConfig = LeshanClientBuilder.createDefaultNetworkConfig();
            coapConfig.store(configFile);
        }

        // Create client
        LeshanClientBuilder builder = new LeshanClientBuilder(endpoint);
        builder.setLocalAddress(localAddress, localPort);
        builder.setLocalSecureAddress(secureLocalAddress, secureLocalPort);
        builder.setObjects(enablers);
        builder.setCoapConfig(coapConfig);
        // if we don't use bootstrap, client will always use the same unique endpoint
        // so we can disable the other one.
        if (!needBootstrap) {
            if (pskIdentity == null)
                builder.disableSecuredEndpoint();
            else
                builder.disableUnsecuredEndpoint();
        }
        final LeshanClient client = builder.build();

        // Start the client
        client.start();

        // De-register on shutdown and stop client.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    // thread to sleep for 3000 milliseconds
                    Thread.sleep(3000);
                } catch (Exception e) {
                        LOG.error("Sleep failed: " + e);
                }
                client.destroy(true); // send de-registration request before destroy
                HttpClient httpClient = HttpClientBuilder.create().build();
                try {
                    String COMMAND = "devices";
                    HttpDelete delete = new HttpDelete(httpsURL + "/" + URLPath +"/api/v3/" + COMMAND + "/" + deviceId);
                    //StringEntity params =new StringEntity(obj.toString());
                    delete.addHeader("X-DeviceNetwork", XDeviceNetwork);
                    delete.addHeader("Authorization", token);
                    //delete.setEntity(params);
                    HttpResponse response = httpClient.execute(delete);
                    if (response != null) {
                        String status = response.getStatusLine().toString();
                        LOG.info("Response from delete: " + status);
                    }
                } catch (Exception ex) {
                    System.out.println("Error in deleting Device " + deviceId + " with error code " + ex);

                } finally {
                    //httpClient.releaseConnection();;
                }
            }
        });

        // Change the location through the Console
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNext()) {
                String nextMove = scanner.next();
                //locationInstance.moveLocation(nextMove);
            }
        }
    }
}
