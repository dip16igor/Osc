// main.cpp
// Прошивка для STM32F401CC (Arduino framework)
// Реализация цифрового осциллографа по ТЗ
#include <Arduino.h>
#include <HardwareSerial.h>
#include <STM32ADC.h>
#include <DMAChannel.h>
#include <Timer.h>

// --- Настройки по умолчанию ---
#define ADC_PIN PA0
#define UART_TX_PIN PA6
#define UART_RX_PIN PA7
#define UART_BAUD 115200
#define ADC_RESOLUTION 12
#define DMA_BUFFER_MAX 32768
#define DMA_BUFFER_MIN 1024
#define DMA_BUFFER_DEF 4096
#define SAMPLE_RATE_MIN 1000
#define SAMPLE_RATE_MAX 1000000
#define SAMPLE_RATE_DEF 10000

// --- Глобальные переменные ---
volatile uint16_t adcBuffer[DMA_BUFFER_MAX];
volatile uint32_t sampleCount = DMA_BUFFER_DEF;
volatile uint32_t sampleRate = SAMPLE_RATE_DEF;
volatile uint16_t triggerLevel = 2048; // середина диапазона
volatile bool triggerAuto = true;
volatile bool triggered = false;
volatile bool sampling = false;

STM32ADC myADC(ADC_PIN);
DMAChannel dma;
Timer timer;
volatile uint32_t adcIndex = 0;

HardwareSerial SerialUART(1);

// --- Прототипы функций ---
void setupADC();
void setupTimer(uint32_t rate);
void setupUART();
void setupDMA();
void startSampling();
void stopSampling();
void processUART();
void sendBufferUART();
void checkTrigger();
void onTimerISR();
void onDMACompleteISR();

// --- Настройка ---
void setup()
{
    pinMode(ADC_PIN, INPUT_ANALOG);
    setupUART();
    setupADC();
    setupTimer(sampleRate);
    setupDMA();
    SerialUART.println("STM32F401CC Oscilloscope Ready");
}

// --- Главный цикл ---
void loop()
{
    processUART();
    if (!sampling && !triggerAuto)
    {
        checkTrigger();
    }
}

// --- Реализация функций ---
void setupUART()
{
    SerialUART.begin(UART_BAUD, SERIAL_8N1, UART_RX_PIN, UART_TX_PIN);
}

void setupADC()
{
    myADC.setResolution(ADC_RESOLUTION);
    myADC.setSampleRate(ADC_SMPR_15); // Среднее время выборки
    myADC.attachInterrupt([](uint16_t value)
                          {
        if (sampling && adcIndex < sampleCount) {
            adcBuffer[adcIndex++] = value;
            if (adcIndex >= sampleCount) {
                stopSampling();
                sendBufferUART();
            }
        } });
}

void setupTimer(uint32_t rate)
{
    timer.pause();
    timer.setPeriod(1000000UL / rate); // период в мкс
    timer.attachInterrupt(onTimerISR);
    timer.refresh();
    if (sampling)
        timer.resume();
}

void setupDMA()
{
    // Настройка DMA для передачи по UART
    dma.disable();
    dma.sourceBuffer((uint16_t *)adcBuffer, sampleCount);
    dma.destination((void *)&(USART1->DR));
    dma.attachInterrupt(onDMACompleteISR);
}

void startSampling()
{
    adcIndex = 0;
    sampling = true;
    triggered = false;
    myADC.startConversion();
    timer.resume();
}

void stopSampling()
{
    sampling = false;
    timer.pause();
    myADC.stopConversion();
}

void processUART()
{
    if (SerialUART.available())
    {
        String cmd = SerialUART.readStringUntil('\n');
        cmd.trim();
        if (cmd.startsWith("START"))
        {
            startSampling();
        }
        else if (cmd.startsWith("LEN:"))
        {
            uint32_t len = cmd.substring(4).toInt();
            if (len >= DMA_BUFFER_MIN && len <= DMA_BUFFER_MAX && (len & (len - 1)) == 0)
            {
                sampleCount = len;
                SerialUART.println("OK LEN");
            }
            else
            {
                SerialUART.println("ERR LEN");
            }
        }
        else if (cmd.startsWith("SRATE:"))
        {
            uint32_t rate = cmd.substring(6).toInt();
            if (rate >= SAMPLE_RATE_MIN && rate <= SAMPLE_RATE_MAX)
            {
                sampleRate = rate;
                setupTimer(sampleRate);
                SerialUART.println("OK SRATE");
            }
            else
            {
                SerialUART.println("ERR SRATE");
            }
        }
        else if (cmd.startsWith("TRIGLVL:"))
        {
            uint16_t lvl = cmd.substring(9).toInt();
            triggerLevel = lvl;
            SerialUART.println("OK TRIGLVL");
        }
        else if (cmd.startsWith("TRIG:WAIT"))
        {
            triggerAuto = false;
            SerialUART.println("OK TRIG WAIT");
        }
        else if (cmd.startsWith("TRIG:AUTO"))
        {
            triggerAuto = true;
            SerialUART.println("OK TRIG AUTO");
        }
        else
        {
            SerialUART.println("ERR CMD");
        }
    }
}

void checkTrigger()
{
    uint16_t val = analogRead(ADC_PIN);
    if (val > triggerLevel)
    {
        startSampling();
    }
}

void sendBufferUART()
{
    dma.sourceBuffer((uint16_t *)adcBuffer, sampleCount);
    dma.destination((void *)&(USART1->DR));
    dma.enable();
}

// --- Прерывания ---
void onTimerISR()
{
    if (sampling && adcIndex < sampleCount)
    {
        myADC.startConversion();
    }
}

void onDMACompleteISR()
{
    dma.disable();
    SerialUART.println("DMA DONE");
}