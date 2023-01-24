#include <M5Stack.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>


#define LED_WATING_PIN 25     // Blink Red
#define LED_CONNECTED_PIN 19  // Green

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;
uint32_t value = 0;

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      Serial.println("Callback.onConnect.");
      deviceConnected = true;
      BLEDevice::startAdvertising();
    };

    void onDisconnect(BLEServer* pServer) {
      Serial.println("Callback.onDisconnect.");
      deviceConnected = false;
    }
};

void setup() {
  Serial.begin(115200);
  while (!Serial);

  pinMode(LED_WATING_PIN, OUTPUT);
  digitalWrite(LED_WATING_PIN, LOW);
  pinMode(LED_CONNECTED_PIN, OUTPUT);
  digitalWrite(LED_CONNECTED_PIN, LOW);
  Serial.print("LED pin:");
  Serial.println(LED_WATING_PIN);

  // Create the BLE Device
  BLEDevice::init("M5Stamp-pico");

  // Create the BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create the BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Create a BLE Characteristic
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ   |
                      BLECharacteristic::PROPERTY_WRITE  |
                      BLECharacteristic::PROPERTY_NOTIFY |
                      BLECharacteristic::PROPERTY_INDICATE
                    );

  // https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
  // Create a BLE Descriptor
  // pCharacteristic->addDescriptor(new BLE2902());
  //+ADD code Start ++++++++++++++++++++++++++++++
  BLE2902 *pDescriptor = new BLE2902();
  // Characteristic notification enable -> Android BluetoothGattApp auto value updating.
  pDescriptor->setNotifications(true);
  pCharacteristic->addDescriptor(pDescriptor);
  //+ADD code End ++++++++++++++++++++++++++++++++

  // Start the service
  pService->start();

  // Start advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(false);
  pAdvertising->setMinPreferred(0x0);  // set value to 0x00 to not advertise this parameter
  BLEDevice::startAdvertising();
  Serial.println("Waiting a client connection to notify...");
}

void loop() {
  if (!deviceConnected) {
    digitalWrite(LED_WATING_PIN, HIGH);
    digitalWrite(LED_CONNECTED_PIN, LOW);
  } else {
    digitalWrite(LED_WATING_PIN, LOW);
    digitalWrite(LED_CONNECTED_PIN, HIGH);
  }
  
  // notify changed value
  if (deviceConnected) {
      pCharacteristic->setValue((uint8_t*)&value, 4);
      pCharacteristic->notify();
      value++;
      delay(10); // bluetooth stack will go into congestion, if too many packets are sent, in 6 hours test i was able to go as low as 3ms
  }
  // disconnecting
  if (!deviceConnected && oldDeviceConnected) {
      delay(500); // give the bluetooth stack the chance to get things ready
      pServer->startAdvertising(); // restart advertising
      Serial.println("start advertising");
      oldDeviceConnected = deviceConnected;
  }
  // connecting
  if (deviceConnected && !oldDeviceConnected) {
      // do stuff here on connecting
      oldDeviceConnected = deviceConnected;
  }

  delay(1000);
  // Blink Wating LED
  if (!deviceConnected) {
    digitalWrite(LED_WATING_PIN, LOW);
  }
  delay(1000);
}
