package com.examples.android.blescanlegatt;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    private final static HashMap<String, String> attributes = new HashMap<>();
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    // M5Stamp pico example UUIDs
    public static String M5STACK_SAMPLE_SERVICE = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
    public static String M5STACK_SAMPLE_CHARACTERISTIC = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
    public static String GENERIC_ACCESS = "00001800-0000-1000-8000-00805f9b34fb";
    public static String GENERIC_ATTRIBUTE = "00001801-0000-1000-8000-00805f9b34fb";

    static {
        // Sample Services.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");

        // Sample Services.
        attributes.put(M5STACK_SAMPLE_SERVICE, "M5Stamp BLE Service");
        // Sample Characteristics.
        attributes.put(M5STACK_SAMPLE_CHARACTERISTIC, "M5Stamp Characteristic1");
        // M5Stamp pico
        attributes.put(GENERIC_ACCESS, "Generic Access");
        attributes.put(GENERIC_ATTRIBUTE, "Generic Attribute");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
