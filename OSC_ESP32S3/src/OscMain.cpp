// Беспроводной осциллограф
// Связь через итернет с MQTT брокером или через локальную сеть с андроид устройством и отправка выборки

#include <Arduino.h>
#include <vector>
#include <WiFi.h> // Для ESP32
// #include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <WebSocketsServer.h>
#include <FastLED.h>
#include <U8g2lib.h> // текст и графика, с видеобуфером
#include "esp32-hal.h"
#include <HardwareSerial.h>
#include <SPI.h>
#include "driver/spi_slave.h"
#include "driver/spi_common.h"
#include <esp_heap_caps.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>

#define LED_PIN 48 // Пин, к которому подключена лента WS2812
#define NUM_LEDS 1 // Количество светодиодов в ленте
#define LED_TYPE WS2812
#define COLOR_ORDER GRB

const char *ssidList[] = {"lmvlmv2011", "dip16", "HUAWEI-041D4V"};    // список SSID
const char *passwordList[] = {"dip16dip16", "0496a6596", "barsik74"}; // список паролей

// const char *mqtt_server = "46.8.233.146"; // Finland
const char *mqtt_server = "80.211.205.234"; // Chez
#define login "dip16"
#define pass "nirvana7"

#define analogPin 1
// const int numReadings = 20; // Количество отсчетов для усреднения

bool WiFiFail = 1;
String WiFi_SSID = "-----";

String ipString = "----";
const char *ipString1;

WiFiClient espClient;
PubSubClient client(espClient);

WebSocketsServer webSocket = WebSocketsServer(81); // Порт 81 для WebSocket-сервера

unsigned long lastMsg = 0;
#define MSG_BUFFER_SIZE (50)
// char msg[MSG_BUFFER_SIZE];
// char msg1[MSG_BUFFER_SIZE];
int value = 0;
int triggerRi = 0;
// char Buf[100];
int i;
int j;
float Voltage;
int Reset = 1;
int TimeToSend;

long rssi;
float Vbat;
unsigned long previousMillis = 0; // хранит время последнего вызова функции
const long interval = 2000;       // интервал в миллисекундах (5 секунд)

#define CommMQTT 0
#define CommWSS 1
int CommMode = CommMQTT;

#define MQTTStreamOff 0
#define MQTTStreamOn 1
int MQTTStreamMode = MQTTStreamOn;

int analogValue;
const float referenceVoltage = 1.10; // Опорное напряжение в вольтах
const int numReadings = 20;          // Количество отсчетов для усреднения
int readings[numReadings];           // Массив для хранения значений
int readIndex = 0;                   // Индекс текущего значения
float total = 0;                     // Переменная для хранения суммы значений
float averageValue = 0;              // Переменная для хранения среднего значения

int sampleSize = 2048; // Размер массива 32757 макс
int oldSampleSize = 0;
float samplingFrequency = 2000; // New variable for sampling frequency in Hz

// Объявляем векторы
std::vector<uint16_t> samples;
std::vector<uint8_t> data;
std::vector<uint16_t> sineWave; // new vector for sinewave

// uint16_t samples[sampleSize]; // Массив для хранения отсчетов
// uint8_t data[sampleSize * 2];

float frequency = 200.8823; // Частота синусоиды
int oldSignalFrequency = 0;
const float dc_part = 2047;
float amplitude = 2040;   // 4095/2;     // Амплитуда (максимальное значение 4095 / 2)
float noiseAmplitude = 1; // Амплитуда шума

// char payload[sampleSize * 5]; // Строка для хранения данных в формате const char*
// int payloadIndex = 0;

unsigned long startGen;
unsigned long finishGen;

unsigned long startConv;
unsigned long finishConv;

unsigned long startTX;
unsigned long finishTX;

unsigned long startPingReq;
unsigned long finishPingReq;
#define PingReqTimeOut 60000 // 15 сек

#define UART_RX 18
#define UART_TX 17
#define UART_BAUD 460800
#define MAX_UART_BUFFER 16384

HardwareSerial SerialPort(2); // Use UART2
uint8_t *uartBuffer;
size_t uartBufferIndex = 0;
bool uartDataReady = false;

// OLED SSD1306
U8G2_SSD1306_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, /* reset=*/U8X8_PIN_NONE); // настройки OLEDsdfsdf

CRGB leds[NUM_LEDS];

// SPI pins
#define SPI_MOSI 11
#define SPI_MISO 13
#define SPI_SCK 12
#define SPI_CS 10
#define DEBUG_GPIO 4 // GPIO4 for SPI timing debugging

// Режим работы - UART или SPI
#define COMM_MODE_UART 0
#define COMM_MODE_SPI 1
uint8_t dataMode = COMM_MODE_SPI;

// Буфер для SPI
static const size_t MAX_SPI_BUFFER = 16384;
uint8_t *spiBuffer = nullptr;
size_t spiBufferIndex = 0;
bool spiDataReady = false;
volatile bool spiTransferComplete = false;

// SPI slave driver structures and DMA buffer
static spi_slave_transaction_t *spiSlaveTransaction = nullptr;
static uint8_t *spiDMABuffer = nullptr;
const size_t DMA_BUFFER_SIZE = 4096;
static volatile bool spiTransactionInProgress = false;

// Функция для изменения размера массивов
void resizeArrays(int newSampleSize)
{
  sampleSize = newSampleSize;
  samples.resize(sampleSize);  // Изменяем размер вектора samples
  sineWave.resize(sampleSize); // Изменяем размер вектора sineWave
  data.resize(sampleSize * 2); // Изменяем размер вектора data
  oldSampleSize = 0;           // flag to regenarate the sinewave
}

void generateSamples();

void createPayload();

void convertToInt8();

void screen(void);

void handleUartData(void);

void setupSPI(void);

void handleSPIData(void);

void IRAM_ATTR spi_post_setup_callback(spi_slave_transaction_t *trans);

void IRAM_ATTR spi_post_trans_callback(spi_slave_transaction_t *trans);

void cleanupSPI(void);

// Обработка событий WebSocket
void webSocketEvent(uint8_t num, WStype_t type, uint8_t *payload, size_t length)
{
  switch (type)
  {
  case WStype_DISCONNECTED:
    Serial.printf("\nWSS клиент %u отключен\n", num);
    // Serial.println();
    CommMode = CommMQTT;
    client.publish("Osc/CommMode", "MQTT");
    leds[0] = CRGB::BlueViolet; // Устанавливаем цвет первого светодиода на красный
    FastLED.show();
    break;
  case WStype_CONNECTED:
    Serial.printf("\nWSS клиент %u подключен\n", num);
    // Serial.println();
    webSocket.sendTXT(num, "Hello!");
    CommMode = CommWSS;
    client.publish("Osc/CommMode", "WSS");
    leds[0] = CRGB::Green; // Устанавливаем цвет первого светодиода на красный
    FastLED.show();
    break;

  case WStype_PING:
    // Serial.printf("Клиент %u PING", num);
    break;

  case WStype_TEXT:
    // Serial.printf("Получено сообщение от клиента %u: %s\n", num, payload);
    //  Отправка ответа клиенту
    //  webSocket.sendTXT(num, "Сообщение получено");
    if (length == 4 && strncmp((const char *)payload, "PING", length) == 0)
    {
      webSocket.sendTXT(num, "PONG");
    }
    break;
  case WStype_FRAGMENT_TEXT_START:
    Serial.printf("Получено FRAGMENT_TEXT_START от клиента %u: %s\n", num, payload);
    break;
  }
}

void setup_wifi()
{
  int count = 0;

  delay(10);
  // We start by connecting to a WiFi network
  Serial.println();

  // Подключение к первой доступной Wi-Fi сети из списка
  Serial.println("Scanning available networks...");
  int numNetworks = WiFi.scanNetworks();
  /*
    String ssid[numNetworks];
    int rssi[numNetworks];

    // Заполняем массивы
    for (int i = 0; i < numNetworks; i++)
    {
      ssid[i] = WiFi.SSID(i);
      rssi[i] = WiFi.RSSI(i);
    }
    // Сортируем сети по RSSI (по убыванию)
    for (int i = 0; i < numNetworks - 1; i++)
    {
      for (int j = 0; j < numNetworks - 1 - i; j++)
      {
        if (rssi[j] < rssi[j + 1])
        {
          // Меняем местами RSSI
          int tempRssi = rssi[j];
          rssi[j] = rssi[j + 1];
          rssi[j + 1] = tempRssi;
          // Меняем местами SSID
          String tempSsid = ssid[j];
          ssid[j] = ssid[j + 1];
          ssid[j + 1] = tempSsid;
        }
      }
    }

    // Выводим отсортированные сети
    Serial.println("Отсортированные сети по уровню сигнала:");
    for (int i = 0; i < numNetworks; i++)
    {
      Serial.print("RSSI: ");
      Serial.print(rssi[i]);
      Serial.print(" dBm");
      Serial.print("\tSSID: ");
      Serial.println(ssid[i]);
    }
  */
  for (int i = 0; i < numNetworks; i++)
  {
    Serial.print(WiFi.RSSI(i));
    Serial.print(" dBm SSID: ");
    Serial.println(WiFi.SSID(i));
  }

  for (int i = 0; i < numNetworks; i++)
  {
    for (int j = 0; j < sizeof(ssidList) / sizeof(ssidList[0]); j++)
    {
      if (WiFi.SSID(i) == ssidList[j])
      {
        Serial.print("Connecting to: ");
        Serial.print(ssidList[j]);

        u8g2.clearBuffer();
        u8g2.setCursor(0, 15);
        u8g2.print("Connect to");
        u8g2.setCursor(0, 28);
        u8g2.print(ssidList[j]);
        u8g2.sendBuffer();

        WiFi.begin(ssidList[j], passwordList[j]);

        u8g2.setCursor(0, 63);
        while (WiFi.status() != WL_CONNECTED)
        {
          u8g2.print(".");
          u8g2.sendBuffer();

          Serial.print(".");
          leds[0] = CRGB::Red; // Устанавливаем цвет первого светодиода на красный
          FastLED.show();
          delay(200);
          leds[0] = CRGB::Black; // Устанавливаем цвет первого светодиода на красный
          FastLED.show();
          delay(200);

          if (count++ >= 16)
          {
            u8g2.setCursor(0, 40);
            u8g2.print("Reset!    ");
            u8g2.sendBuffer();
            delay(1000);
            ESP.restart();
          }
        }
        WiFiFail = 0;
        Serial.println(" Connected!");
        WiFi_SSID = ssidList[j];
        rssi = WiFi.RSSI();
      }
    }
  }
  if (WiFiFail)
  {
    u8g2.setCursor(0, 40);
    u8g2.print("WiFi Fail!");
    u8g2.sendBuffer();
    Serial.println("WiFi fail. Restart!");
    delay(2000);
    ESP.restart();
  }

  while (WiFi.status() != WL_CONNECTED)
  {
    // TestGreenKey();
    // digitalWrite(LEDREDLow, 1);
    delay(250);
    // digitalWrite(LEDREDLow, 0);
    delay(250);
    Serial.print(".");
    // digitalWrite(LED1, HIGH); // гасим синий светодиод
  }
  // digitalWrite(LEDREDLow, 1);

  u8g2.setCursor(0, 40);
  u8g2.print(WiFi.localIP());
  u8g2.sendBuffer();

  randomSeed(micros());

  // Получаем IP-адрес
  // IPAddress ip = WiFi.localIP();
  // Преобразуем IP-адрес в строку
  // ipString = String(ip[0]) + "." + String(ip[1]) + "." + String(ip[2]) + "." + String(ip[3]);
  ipString = WiFi.localIP().toString();
  ipString1 = ipString.c_str();
  Serial.print("IP address: ");
  Serial.println(ipString);
  // Serial.println(WiFi.localIP());
  //  digitalWrite(LED1, LOW); // зажигаем синий светодиод
  // Запуск WebSocket-сервера
  webSocket.begin();
  webSocket.onEvent(webSocketEvent);

  Serial.println("webSocket.begin()");
}

void MQTTcallback(char *topic, byte *payload, unsigned int length)
{
  // Serial.print("Message arrived [");
  // Serial.print(topic);
  // Serial.print("] ");
  // Serial.print("[");
  // for (int i = 0; i < length; i++)
  // {
  //   Serial.print((char)payload[i]);
  // }
  // Serial.print("] ");
  // Serial.println();

  String message = "";
  for (int i = 0; i < length; i++)
  {
    message += (char)payload[i];
  }

  if (strcmp(topic, "Osc/Cmd") == 0)
  {
    // char ch = (char)payload[0];
    if (message == "IP?")
    {
      ipString1 = ipString.c_str();
      client.publish("Osc/IP", ipString1);
      // client.publish("Osc/System", "test");
      // Serial.print("Osc/IP => ");
      // Serial.println(ipString1);
    }
    if (message == "SSID?")
    {
      ipString1 = ipString.c_str();
      client.publish("Osc/SSID", WiFi_SSID.c_str());
      // client.publish("Osc/System", "test");
      // Serial.print("Osc/SSID => ");
      // Serial.println(WiFi_SSID.c_str());
    }
  }

  if (strcmp(topic, "Osc/PingRequest") == 0)
  {
    // char ch = (char)payload[0];
    if (message == "PING")
    {
      ipString1 = ipString.c_str();
      client.publish("Osc/PingAsk", "PONG");
      //  client.publish("Osc/System", "test");
      //  Serial.print("Osc/PingAsk => ");
      //  Serial.println("PONG");
      startPingReq = millis();
    }
  }

  if (strcmp(topic, "Osc/Setup") == 0)
  {
    // char ch = (char)payload[0];
    // int time2 = (int)ch - 0x30;
  }

  if (strcmp(topic, "Osc/SetSSize") == 0)
  {
    int newSize = message.toInt();
    if (newSize > 0)
    {
      resizeArrays(newSize);                     // Изменяем размер массивов
      client.setBufferSize(sampleSize * 2 + 20); // устанавливаем новый размер буфера
      Serial.print("New sampleSize = ");
      Serial.println(sampleSize);
    }
    // samples = new uint16_t[sampleSize];
  }

  if (strcmp(topic, "Osc/SetSFreq") == 0)
  {
    float newSFreq = message.toFloat();
    if (newSFreq > 0.0)
    {
      samplingFrequency = newSFreq;
      Serial.print("New Sampling Freq = ");
      Serial.println(samplingFrequency);
      oldSampleSize = 0; // update flag for sine regenaration.
    }
  }

  if (strcmp(topic, "Osc/SetFreq") == 0)
  {
    float newFreq = message.toFloat();
    if (newFreq > 0.0)
    {
      frequency = newFreq;
      Serial.print("New Signal Freq = ");
      Serial.println(frequency);
      oldSampleSize = 0; // update flag for sine regenaration.
    }
  }

  if (strcmp(topic, "Osc/SetAmpl") == 0)
  {
    float newAmpl = message.toFloat();
    if (newAmpl > 0.0)
    {
      amplitude = newAmpl;
      Serial.print("New Signal Ampl = ");
      Serial.println(amplitude);
      oldSampleSize = 0; // update flag for sine regenaration.
    }
  }
  if (strcmp(topic, "Osc/SetNoise") == 0)
  {
    float newAmpl = message.toFloat();
    if (newAmpl >= 0.0)
    {
      noiseAmplitude = newAmpl;
      Serial.print("New Noise Ampl = ");
      Serial.println(noiseAmplitude);
      // oldSampleSize = 0; // update flag for sine regenaration.
    }
  }

  if (strcmp(topic, "Osc/SetMode") == 0)
  {
    if (message == "UART")
    {
      dataMode = COMM_MODE_UART;
      Serial.println("Switched to UART mode");
    }
    else if (message == "SPI")
    {
      dataMode = COMM_MODE_SPI;
      Serial.println("Switched to SPI mode");
    }
  }
}

uint8_t reconnect()
{
  uint8_t retries = 3; // три попытки соедениться, потом зависание
  // Loop until we're reconnected
  while (!client.connected())
  {
    // TestGreenKey();
    Serial.print("Attempting MQTT connection...");
    // Create a random client ID
    String clientId = "ESP32S3-OSC";
    // clientId += String(random(0xffff), HEX);
    //  Attempt to connect
    if (client.connect(clientId.c_str(), login, pass))
    {
      // TestGreenKey();
      // digitalWrite(LEDGREENLow, 1);
      Serial.println(" Connected!");
      if (CommMode == CommMQTT)
      {
        leds[0] = CRGB::BlueViolet; // Устанавливаем цвет первого светодиода на фиолетовый
        FastLED.show();
      }
      // Once connected, publish an announcement...
      if (Reset == 1)
      {
        client.publish("Osc/System", "Reset!");
        Reset = 0;
      }
      else
        client.publish("Osc/System", "Reconnect");
      // ... and resubscribe
      ipString1 = ipString.c_str();

      client.subscribe("Osc/Setup");
      client.subscribe("Osc/Cmd");
      client.subscribe("Osc/PingRequest");
      client.subscribe("Osc/SetSSize");
      client.subscribe("Osc/SetSFreq");
      client.subscribe("Osc/SetFreq");
      client.subscribe("Osc/SetAmpl");
      client.subscribe("Osc/SetNoise");
      client.subscribe("Osc/SetMode");

      client.publish("Osc/IP", ipString1);
      client.publish("Osc/SSID", WiFi_SSID.c_str());
      client.publish("Osc/CommMode", "MQTT");
      client.publish("Osc/CmdAsk", "?");
    }
    else
    {
      // TestGreenKey();
      // digitalWrite(LEDGREENLow, 0);
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      // Wait 5 seconds before retrying
      // delay(5000);
      for (i = 0; i < 10; i++)
      {
        // TestGreenKey();
        // digitalWrite(LEDGREENLow, 1);
        delay(250);
        // digitalWrite(LEDGREENLow, 0);
        delay(250);
      }
      retries--;
      if (retries == 0)
      {
        return 0;
        Serial.println(" WHILE(1); ");
        while (1) // зависание с перезагрузкой
          ;
      }
    }
  }
  return 1;
}

void setup()
{
  Serial.begin(115200);
  while (!Serial)
  {
    ; // Ожидание подключения последовательного порта
  }

  // Clean up any existing SPI configuration first
  spi_slave_free(SPI2_HOST); // Clean up any previous SPI configuration
  delay(100);                // Short delay to ensure cleanup is complete

  // Initialize debug GPIO pin
  pinMode(DEBUG_GPIO, OUTPUT);
  digitalWrite(DEBUG_GPIO, LOW);

  // Initialize SPI with DMA support
  setupSPI();

  // Настройка UART
  SerialPort.begin(UART_BAUD, SERIAL_8N1, UART_RX, UART_TX);
  uartBuffer = (uint8_t *)malloc(MAX_UART_BUFFER);
  if (!uartBuffer)
  {
    Serial.println("Failed to allocate UART buffer!");
  }
  // Initialize SPI with DMA support
  setupSPI();

  // Вывод информации о чипе
  Serial.println("Информация о чипе:");
  Serial.printf("Модель: %s\n", ESP.getChipModel());
  Serial.printf("Версия: %d\n", ESP.getChipRevision());
  Serial.printf("FLASH mode: %X\n", ESP.getFlashChipMode());
  Serial.printf("FLASH size: %d Byte\n", ESP.getFlashChipSize());
  Serial.printf("Количество ядер: %d\n", ESP.getChipCores());
  Serial.printf("Частота: %d MHz\n", ESP.getCpuFreqMHz());

  // Проверка наличия PSRAM
  if (esp_spiram_get_size() > 0)
  {
    Serial.println("PSRAM доступен:");
    Serial.printf("Размер PSRAM: %d байт\n", esp_spiram_get_size());
  }
  else
  {
    Serial.println("PSRAM недоступен.");
  }

  uartBuffer = (uint8_t *)malloc(MAX_UART_BUFFER);
  if (!uartBuffer)
  {
    Serial.println("Failed to allocate UART buffer!");
  }
  // Изначально задаём размеры
  resizeArrays(sampleSize);

  analogReadResolution(12); // Разрешение 12 бит
  analogSetPinAttenuation(analogPin, ADC_0db);

  float total1 = 0; // Переменная для хранения суммы значений
  // Считываем значения 20 раз
  for (int i = 0; i < numReadings; i++)
  {
    int analogValue = analogRead(analogPin); // Считываем значение
    total1 += analogValue;                   // Добавляем к общей сумме
    delay(10);                               // Небольшая задержка между считываниями (по желанию)
  }
  // Вычисляем среднее значение
  float averageValue = total1 / numReadings;
  // Преобразование в напряжение
  float Vbat = (averageValue / 4095.0) * referenceVoltage * 3.6295;
  char buffer1[10]; // Буфер для хранения строки
  // Преобразуем число с плавающей запятой в строку с 2 знаками после запятой
  dtostrf(Vbat, 4, 2, buffer1); // 4 - минимальная ширина, 2 - количество знаков после запятой

  // Инициализация светодиодов
  FastLED.addLeds<LED_TYPE, LED_PIN, COLOR_ORDER>(leds, NUM_LEDS);
  FastLED.setBrightness(50); // Установка яркости (0-255)

  leds[0] = CRGB::Red; // Устанавливаем цвет первого светодиода на красный
  FastLED.show();

  u8g2.begin();
  u8g2.setFont(u8g2_font_9x18B_tr);
  u8g2.clearBuffer();
  u8g2.setCursor(0, 12);

  u8g2.print("Reset!");
  u8g2.sendBuffer();
  delay(300);

  u8g2.setCursor(0, 24);
  u8g2.print("Vbat: ");
  u8g2.print(buffer1);
  u8g2.print(" V");
  u8g2.sendBuffer();

  u8g2.setCursor(0, 36);
  u8g2.print("scaning WiFi..");
  u8g2.sendBuffer();

  setup_wifi();

  u8g2.clearBuffer();
  u8g2.setCursor(0, 12);
  u8g2.print("Connect to");
  u8g2.setCursor(0, 24);
  u8g2.print("MQTT brocker");
  u8g2.setCursor(0, 36);
  u8g2.print(mqtt_server);
  u8g2.sendBuffer();

  client.setServer(mqtt_server, 1883);
  client.setCallback(MQTTcallback);
  client.setBufferSize(sampleSize * 2 + 20);

  u8g2.setCursor(0, 48);
  if (reconnect() == 1)
  {
    // MQTT_available = true;
    u8g2.print("OK! ");
  }
  else
  {
    // MQTT_available = false;
    u8g2.print("ERROR! ");
  }
  u8g2.sendBuffer();

  u8g2.setCursor(0, 60);
  u8g2.print(rssi);
  u8g2.print(" dBm");
  u8g2.sendBuffer();
  // Инициализация SPI
  setupSPI();

  generateSamples();
  convertToInt8();

  // Инициализация массива значений
  for (int i = 0; i < numReadings; i++)
  {
    readings[i] = analogRead(analogPin); //(int)averageValue; // Заполнение массива нулями
  }

  total = numReadings * analogRead(analogPin);

  delay(1000);
}

// Функция для плавного перехода цветов
void rainbowCycle(uint8_t wait)
{
  static uint8_t hue = 0;
  for (int i = 0; i < NUM_LEDS; i++)
  {
    leds[i] = CHSV(hue + (i * 256 / NUM_LEDS), 255, 255);
  }
  FastLED.show();
  hue++;
  delay(wait);
}

void loop()
{
  // delay(1000);
  //  rainbowCycle(10); // Вызываем функцию для плавного перехода цветов
  if (dataMode == COMM_MODE_UART)
  {
    handleUartData();
    if (uartDataReady)
    {
      boolean result = false;

      if (CommMode == CommWSS)
      {
        startTX = millis();

        for (int i = 0; i < webSocket.connectedClients(); i++)
        {
          result = webSocket.sendBIN(i, &data[0], data.size());
        }

        finishTX = millis();
      }
      else if (CommMode == CommMQTT && MQTTStreamMode == MQTTStreamOn)
      {
        startTX = millis();
        result = client.publish("Osc/Data", &data[0], data.size());
        finishTX = millis();
      }

      TimeToSend = finishTX - startTX;
      uartDataReady = false;
    }
  }
  else if (dataMode == COMM_MODE_SPI)
  {
    handleSPIData();
    if (spiDataReady)
    {
      digitalWrite(DEBUG_GPIO, HIGH); // Set GPIO4 high when SPI data is ready
      boolean result = false;

      if (CommMode == CommWSS)
      {
        startTX = millis();
        for (int i = 0; i < webSocket.connectedClients(); i++)
        {
          result = webSocket.sendBIN(i, &data[0], data.size());
        }
        finishTX = millis();
      }
      else if (CommMode == CommMQTT && MQTTStreamMode == MQTTStreamOn)
      {
        startTX = millis();
        result = client.publish("Osc/Data", &data[0], data.size());
        finishTX = millis();
      }

      TimeToSend = finishTX - startTX;
      spiDataReady = false;
      digitalWrite(DEBUG_GPIO, LOW); // Set GPIO4 low after processing is complete
    }
  }

  webSocket.loop();

  if (!client.connected())
  {
    // digitalWrite(LED1, 0); // зажигаем светодиод LED2 COM
    reconnect();
    // digitalWrite(LED1, 1); // гасим светодиод LED2 COM
  }
  client.loop();

  unsigned long now = millis();
  // if (now - lastMsg > 500)
  // {
  // startGen = millis();
  // generateSamples();
  // finishGen = millis();
  // // Serial.println(finishGen - startGen);

  // startConv = millis();
  // // createPayload();
  // convertToInt8();
  // finishConv = millis();

  // Serial.println(finishConv - startConv);
  //   Serial.println(payload); // Печать payload для проверки
  boolean result = false;
  // boolean result = client.publish("Osc/Data", payload);
  /*
    if (CommMode == CommMQTT)
    {
      if (webSocket.connectedClients() > 0)
      {
        CommMode = CommWSS;
        Serial.print("\n WSS mode \n");
      }
    }

    // webSocket.sendTXT(0, "Test ..");
    if (CommMode == CommWSS)
    {
      // digitalWrite(LED1, 0); // зажигаем светодиод LED2 COM
      startTX = millis();

      for (int i = 0; i < webSocket.connectedClients(); i++)
        result = webSocket.sendBIN(i, &data[0], data.size());

      finishTX = millis();

      TimeToSend = finishTX - startTX;

      // Создаем строку шкалы
      String scale = "";
      for (int j = 0; j <= 70; j++)
      {
        if (j < TimeToSend)
        {
          scale += "█"; // Заполненная часть
        }
        else
        {
          scale += "░"; // Пустая часть
        }
      }
      // scale += "] ";

      Serial.print("\r"); // Возврат каретки
      Serial.print(scale);
      // digitalWrite(LED1, 1); // гасим светодиод LED2 COM
      Serial.print(result ? " webSocket. Время: " : "Ошибка webSocket. Время: ");
      Serial.print(TimeToSend);
      Serial.print(" ms           ");

      if (!result)
      {
        webSocket.disconnect(0);
        CommMode = CommMQTT;
        client.publish("Osc/CommMode", "MQTT");
        Serial.print("\n MQTT mode \n");
      }
    }
    else if (CommMode == CommMQTT && MQTTStreamMode == MQTTStreamOn)
    {
      // digitalWrite(LED1, 0); // зажигаем светодиод LED2 COM
      startTX = millis();

      result = client.publish("Osc/Data", &data[0], data.size());

      finishTX = millis();

      TimeToSend = finishTX - startTX;
      // Создаем строку шкалы
      String scale = "";
      for (int j = 0; j <= 70; j++)
      {
        if (j < TimeToSend)
        {
          scale += "█"; // Заполненная часть
        }
        else
        {
          scale += "▒"; // Пустая часть
        }
      }
      // scale += "] ";

      Serial.print("\r"); // Возврат каретки
      Serial.print(scale);
      // digitalWrite(LED1, 1); // гасим светодиод LED2 COM
      Serial.print(result ? " MQTT. Время: " : "Ошибка webSocket. Время: ");
      Serial.print(TimeToSend);
      Serial.print(" ms           ");

      // Преобразование int в строку
      char ttsString[10]; // Достаточно места для хранения long
      snprintf(ttsString, sizeof(ttsString), "%ld", TimeToSend);
      client.publish("Osc/TTS", ttsString);
    }
    // Serial.println(ESP.getFreeHeap());
    //  size_t freeHeap = ESP.getFreeHeap();
    //
    //    lastMsg = now;

    //++value;
  */
  lastMsg = now;

  finishPingReq = millis();
  if (finishPingReq - startPingReq > PingReqTimeOut)
  {
    if (MQTTStreamMode == MQTTStreamOn)
    {
      MQTTStreamMode = MQTTStreamOff;
      client.publish("Osc/StreamMode", "MQTTStreamOff");
      Serial.print("\n MQTTStreamOff \n");
    }
    // MQTTStreamMode = MQTTStreamOff;
    //  Serial.println("MQTTStreamOff");
    rainbowCycle(10); // Вызываем функцию для плавного перехода цветов
  }
  else
  {
    if (MQTTStreamMode == MQTTStreamOff)
    {
      MQTTStreamMode = MQTTStreamOn;
      client.publish("Osc/StreamMode", "MQTTStreamOn");
      Serial.print("\n MQTTStreamOn \n");
    }
    // Serial.println("MQTTStreamOn");
  }

  unsigned long currentMillis = millis(); // получаем текущее время
  // Проверяем, прошло ли 5 секунд
  if (currentMillis - previousMillis >= interval)
  {
    previousMillis = currentMillis; // обновляем время последнего вызова
    rssi = WiFi.RSSI();
    // Преобразование long в строку
    char rssiString[10]; // Достаточно места для хранения long
    snprintf(rssiString, sizeof(rssiString), "%ld", rssi);
    client.publish("Osc/Rssi", rssiString);

    // Удаляем старое значение из суммы
    total -= readings[readIndex];
    // Считываем новое значение
    readings[readIndex] = analogRead(analogPin);
    // Добавляем новое значение к сумме
    total += readings[readIndex];
    // Переходим к следующему индексу
    readIndex++;
    // Если индекс превышает размер массива, сбрасываем его
    if (readIndex >= numReadings)
    {
      readIndex = 0;
    }
    // Вычисляем среднее значение
    averageValue = total / numReadings;
    // Преобразование в напряжение
    Vbat = (averageValue / 4095.0) * referenceVoltage * 3.6295;

    // analogValue = analogRead(analogPin);

    // Vbat = (analogValue / 4095.0) * referenceVoltage * 3.6295;

    char VbatString[10]; // Достаточно места для хранения long
    // Форматирование значения Vbat в строку
    snprintf(VbatString, sizeof(VbatString), "%.2f", Vbat); // 2 знака после запятой
                                                            // Публикация значения в MQTT
    client.publish("Osc/Vbat", VbatString);

    // Serial.print(" Vbat:");
    // Serial.print(analogValue);
    // Serial.print(" or ");
    // Serial.print(VbatString);
    // Serial.println(" V");

    // screen();
  }
}

void setupSPI()
{
  // Clean up any existing SPI configuration first
  cleanupSPI();
  delay(100); // Allow time for cleanup to complete

  esp_err_t ret;
  Serial.println("Initializing SPI slave interface...");

  // Выделяем память под буфер SPI с проверкой выравнивания для DMA
  spiBuffer = (uint8_t *)heap_caps_aligned_alloc(16, MAX_SPI_BUFFER, MALLOC_CAP_DMA);
  if (!spiBuffer)
  {
    Serial.println("ERROR: Failed to allocate aligned SPI buffer!");
    return;
  }
  memset(spiBuffer, 0, MAX_SPI_BUFFER);
  Serial.printf("SPI buffer allocated: %d bytes\n", MAX_SPI_BUFFER);

  // Initialize DMA buffer with proper alignment
  spiDMABuffer = (uint8_t *)heap_caps_aligned_alloc(16, DMA_BUFFER_SIZE, MALLOC_CAP_DMA);
  if (!spiDMABuffer)
  {
    Serial.println("ERROR: Failed to allocate aligned DMA buffer!");
    cleanupSPI();
    return;
  }
  memset(spiDMABuffer, 0, DMA_BUFFER_SIZE);
  Serial.printf("DMA buffer allocated: %d bytes\n", DMA_BUFFER_SIZE);

  // Allocate and configure transaction structure
  spiSlaveTransaction = (spi_slave_transaction_t *)heap_caps_malloc(sizeof(spi_slave_transaction_t), MALLOC_CAP_DMA);
  if (!spiSlaveTransaction)
  {
    Serial.println("ERROR: Failed to allocate transaction structure!");
    cleanupSPI();
    return;
  }
  Serial.println("Transaction structure allocated");

  // Configure SPI bus with explicit settings
  spi_bus_config_t buscfg{};
  buscfg.mosi_io_num = SPI_MOSI;
  buscfg.miso_io_num = SPI_MISO;
  buscfg.sclk_io_num = SPI_SCK;
  buscfg.quadwp_io_num = -1;
  buscfg.quadhd_io_num = -1;
  buscfg.max_transfer_sz = DMA_BUFFER_SIZE;
  buscfg.flags = SPICOMMON_BUSFLAG_SLAVE;
  buscfg.intr_flags = 0; // Removed ESP_INTR_FLAG_IRAM flag

  // Configure SPI slave interface with explicit settings
  spi_slave_interface_config_t slvcfg{};
  slvcfg.mode = 0;
  slvcfg.spics_io_num = SPI_CS;
  slvcfg.queue_size = 3;
  slvcfg.flags = 0;
  slvcfg.post_setup_cb = spi_post_setup_callback;
  slvcfg.post_trans_cb = spi_post_trans_callback;

  // Initialize SPI slave interface with retry mechanism
  int retry_count = 0;
  const int MAX_RETRIES = 3;
  bool success = false;

  while (retry_count < MAX_RETRIES && !success)
  {
    ret = spi_slave_initialize(SPI2_HOST, &buscfg, &slvcfg, SPI_DMA_CH_AUTO);
    if (ret == ESP_OK)
    {
      success = true;
      break;
    }
    Serial.printf("SPI initialization attempt %d failed with error: %d\n", retry_count + 1, ret);
    delay(100); // Short delay between retries
    spi_slave_free(SPI2_HOST);
    retry_count++;
  }

  if (!success)
  {
    Serial.printf("ERROR: SPI slave initialization failed after %d attempts!\n", MAX_RETRIES);
    cleanupSPI();
    return;
  }

  // Configure transaction structure
  memset(spiSlaveTransaction, 0, sizeof(spi_slave_transaction_t));
  spiSlaveTransaction->length = DMA_BUFFER_SIZE * 8; // Length in bits
  spiSlaveTransaction->rx_buffer = spiDMABuffer;
  spiSlaveTransaction->tx_buffer = NULL; // We're only receiving in slave mode
  spiTransactionInProgress = false;
  spiBufferIndex = 0;
  spiDataReady = false;

  Serial.println("SPI slave successfully initialized with DMA support");
}

// SPI transaction completed callbacks with error tracking
void IRAM_ATTR spi_post_setup_callback(spi_slave_transaction_t *trans)
{
  spiTransactionInProgress = true;
#ifdef DEBUG_SPI_IRQ
  Serial.println("SPI transaction setup complete");
#endif
}

void IRAM_ATTR spi_post_trans_callback(spi_slave_transaction_t *trans)
{
  // Only update state if we have a valid transaction
  if (trans != NULL && trans == spiSlaveTransaction)
  {
    spiTransactionInProgress = false;
  }
}

// Handle SPI data reception with DMA
void handleSPIData()
{
  static int error_count = 0;
  const int MAX_ERRORS = 3;
  const int RECOVERY_DELAY = 100;                       // ms
  const size_t MIN_PACKET_SIZE = 64;                    // Expected minimum size for a valid packet
  const size_t PROCESS_THRESHOLD = DMA_BUFFER_SIZE / 2; // Process when buffer is half full

  if (!spiDMABuffer || !spiSlaveTransaction)
  {
    Serial.println("ERROR: SPI not properly initialized!");
    return;
  }

  // If we have enough data, process it before continuing
  if (spiBufferIndex >= PROCESS_THRESHOLD || spiDataReady)
  {
    spiDataReady = true;
    return;
  }

  // Start new transaction if none is in progress
  if (!spiTransactionInProgress)
  {
    // Clear DMA buffer before new transaction
    memset(spiDMABuffer, 0, DMA_BUFFER_SIZE);

    esp_err_t ret = spi_slave_queue_trans(SPI2_HOST, spiSlaveTransaction, portMAX_DELAY);
    if (ret != ESP_OK)
    {
      Serial.printf("ERROR: Failed to queue SPI transaction: %d\n", ret);
      error_count++;
      if (error_count >= MAX_ERRORS)
      {
        Serial.println("Too many queueing errors, attempting recovery...");
        cleanupSPI();
        delay(RECOVERY_DELAY);
        setupSPI();
        error_count = 0;
      }
      return;
    }
    spiTransactionInProgress = true;
    error_count = 0; // Reset error count on successful queue
    return;
  }

  // Check if transaction is complete
  spi_slave_transaction_t *completedTrans = NULL;
  esp_err_t ret = spi_slave_get_trans_result(SPI2_HOST, &completedTrans, 0);

  if (ret == ESP_OK && completedTrans)
  {
    size_t bytesReceived = (completedTrans->trans_len + 7) / 8;

    if (bytesReceived >= MIN_PACKET_SIZE && bytesReceived <= DMA_BUFFER_SIZE)
    {
      // Need to buffer the data or process it?
      if (spiBufferIndex + bytesReceived > MAX_SPI_BUFFER)
      {
        // Buffer would overflow, reset buffer index
        spiBufferIndex = 0;
      }

      // Copy new data
      memcpy(spiBuffer + spiBufferIndex, spiDMABuffer, bytesReceived);
      spiBufferIndex += bytesReceived;

      // Mark as ready if we have accumulated enough data
      if (spiBufferIndex >= PROCESS_THRESHOLD)
      {
        spiDataReady = true;
      }
    }

    spiTransactionInProgress = false;
    error_count = 0;
  }
  else if (ret != ESP_ERR_TIMEOUT)
  {
    Serial.printf("ERROR: Failed to get transaction result: %d\n", ret);
    error_count++;

    if (error_count >= MAX_ERRORS)
    {
      Serial.println("Too many transaction errors, attempting recovery...");
      cleanupSPI();
      delay(RECOVERY_DELAY);
      setupSPI();
      error_count = 0;
    }
  }
}

// Cleanup function for SPI resources
void cleanupSPI()
{
  // First cleanup any pending transactions
  if (spiTransactionInProgress)
  {
    spi_slave_transaction_t *t;
    spi_slave_get_trans_result(SPI2_HOST, &t, 0);
    spiTransactionInProgress = false;
  }

  // Free the SPI driver
  spi_slave_free(SPI2_HOST);
  delay(50); // Short delay to ensure cleanup completes

  // Free allocated buffers with proper checks
  if (spiBuffer)
  {
    heap_caps_free(spiBuffer);
    spiBuffer = nullptr;
    spiBufferIndex = 0;
  }

  if (spiDMABuffer)
  {
    heap_caps_free(spiDMABuffer);
    spiDMABuffer = nullptr;
  }

  if (spiSlaveTransaction)
  {
    heap_caps_free(spiSlaveTransaction);
    spiSlaveTransaction = nullptr;
  }

  // Reset state variables
  spiDataReady = false;
  spiTransactionInProgress = false;

  Serial.println("SPI resources cleaned up");
}

void generateSineWave()
{
  for (int i = 0; i < sampleSize; i++)
  {
    // Вычисляем синусоиду
    float calculatedSineValue = dc_part + amplitude * sin(TWO_PI * frequency * (i / samplingFrequency));
    // Ограничиваем снизу нулем, если значение стало отрицательным
    sineWave[i] = (uint16_t)(calculatedSineValue > 0 ? calculatedSineValue : 0);
  }
}

void generateSamples()
{
  if (oldSampleSize != sampleSize)
  {
    // generateSineWave();
    oldSampleSize = sampleSize;
  }
  generateSineWave();

  if ((int)noiseAmplitude > 0)
  {
    // Serial.print("1");
    for (int i = 0; i < sampleSize; i++)
    {
      // Добавляем шум
      float noise = random(-noiseAmplitude, noiseAmplitude);
      // Формируем окончательное значение
      samples[i] = constrain((int)(sineWave[i] + noise), 0, 4095);
    }
  }
  else if ((int)noiseAmplitude == 0)
  {
    for (int i = 0; i < sampleSize; i++)
    {
      // Добавляем шум
      // float noise = random(-noiseAmplitude, noiseAmplitude);
      // Формируем окончательное значение
      samples[i] = constrain((int)(sineWave[i]), 0, 4095);
    }

    // Serial.print("0");
  }
}
// void generateSamples()
// {
//   for (int i = 0; i < sampleSize; i++)
//   {
//     // Вычисляем синусоиду
//     float sineValue = dc_part + amplitude * sin(TWO_PI * frequency * (i / (float)sampleSize));
//     // Добавляем шум
//     float noise = random(-noiseAmplitude, noiseAmplitude);
//     // Формируем окончательное значение
//     samples[i] = constrain((int)(sineValue + noise), 0, 4095);
//     // Serial.print(samples[i]);
//     // Serial.print(" ");
//   }
// }

void createPayload()
{
  // payloadIndex = 0; // Сбрасываем индекс
  // for (int i = 0; i < sampleSize; i++)
  // {
  //   // Добавляем отсчет в payload
  //   payloadIndex += snprintf(payload + payloadIndex, sizeof(payload) - payloadIndex, "%d", samples[i]);

  //   // Добавляем запятую, если это не последний элемент
  //   if (i < sampleSize - 1)
  //   {
  //     payloadIndex += snprintf(payload + payloadIndex, sizeof(payload) - payloadIndex, ",");
  //   }

  //   // Serial.print(payload[i]);
  // }
}

void convertToInt8()
{
  // Преобразование uint16_t в uint8_t
  for (int i = 0; i < sampleSize; i++)
  {
    data[i * 2] = (samples[i] >> 8) & 0xFF; // Старший байт
    data[i * 2 + 1] = samples[i] & 0xFF;    // Младший байт
  }
}

void screen(void)
{
  u8g2.clearBuffer();
  u8g2.setFont(u8g2_font_9x18B_tr);
  u8g2.setCursor(0, 12);
  // u8g2.print("SSID:");
  u8g2.print(WiFi_SSID);

  u8g2.setCursor(0, 25);
  u8g2.print("RSSI:");
  u8g2.print(rssi);
  u8g2.print(" dBm");

  u8g2.setCursor(0, 38);
  u8g2.print(WiFi.localIP());

  u8g2.setCursor(0, 51);
  if (CommMode == CommMQTT)
  {
    u8g2.print("MQTT");
    if (MQTTStreamMode == MQTTStreamOn)
      u8g2.print(" >");
    else
      u8g2.print("  ");
  }
  else
    u8g2.print("WSS >");
  u8g2.print(" t:");
  u8g2.print(TimeToSend);
  u8g2.print(" ms");

  u8g2.setCursor(0, 64);
  u8g2.print("Vbat: ");
  u8g2.print(Vbat);
  u8g2.print(" V");

  u8g2.sendBuffer();
}

void handleUartData()
{
  static uint32_t lastByte = 0;

  while (SerialPort.available())
  {
    if (uartBufferIndex >= MAX_UART_BUFFER)
    {
      uartBufferIndex = 0; // Reset if buffer full
    }

    uartBuffer[uartBufferIndex++] = SerialPort.read();
    lastByte = millis();
  }

  // Check if we have complete data (timeout based)
  if (uartBufferIndex > 0 && (millis() - lastByte) > 20)
  {
    // Assume data complete after 50ms of no new data
    if (uartBufferIndex % 2 == 0)
    { // Check if we have complete 16-bit samples
      // Resize data vector if needed
      data.resize(uartBufferIndex);

      // Copy received data
      memcpy(&data[0], uartBuffer, uartBufferIndex);

      // Signal that new data is ready
      uartDataReady = true;

      // Print debug info
      Serial.printf("Received %d bytes from UART\n", uartBufferIndex);
    }

    // Reset buffer
    uartBufferIndex = 0;
  }
}