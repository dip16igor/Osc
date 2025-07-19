/* main.c - STM32Cube HAL implementation for STM32F401CC oscilloscope */
#include "main.h"
#include <string.h>
#include <stdlib.h>

#define ADC_BUF_MAX 8192 * 2
#define ADC_BUF_MIN 1024
#define ADC_BUF_DEF 2048
#define SAMPLE_RATE_MIN 1000
#define SAMPLE_RATE_MAX 1000000
#define SAMPLE_RATE_DEF 10000
#define UART_BaudRate 460800

ADC_HandleTypeDef hadc1;
UART_HandleTypeDef huart1;
DMA_HandleTypeDef hdma_usart1_tx;
TIM_HandleTypeDef htim2;
SPI_HandleTypeDef hspi1;

uint16_t adcBuffer[ADC_BUF_MAX];
volatile uint32_t sampleCount = ADC_BUF_DEF;
volatile uint32_t sampleRate = SAMPLE_RATE_DEF;
volatile uint16_t triggerLevel = 2048;
volatile uint8_t triggerAuto = 1;
volatile uint8_t sampling = 0;
volatile uint32_t adcIndex = 0;

void SystemClock_Config(void);
static void MX_GPIO_Init(void);
static void MX_DMA_Init(void);
static void MX_ADC1_Init(void);
static void MX_USART1_UART_Init(void);
static void MX_TIM2_Init(void);
static void MX_SPI1_Init(void);

void process_uart_command(void);
void start_sampling(void);
void stop_sampling(void);
void send_buffer_uart(void);
void send_buffer_spi(void);
void check_trigger(void);

int main(void)
{
    HAL_Init();
    SystemClock_Config();
    MX_GPIO_Init();
    MX_DMA_Init();
    MX_ADC1_Init();
    MX_USART1_UART_Init();
    MX_TIM2_Init();
    MX_SPI1_Init();

    // HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_RESET); // Включить светодиод
    // HAL_Delay(300);
    // HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_SET); // Выключить светодиод
    // HAL_Delay(300);
    // HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_RESET); // Включить светодиод
    // HAL_Delay(300);
    // HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_SET); // Выключить светодиод

    // // HAL_UART_Transmit(&huart1, (uint8_t *)"STM32F401CC Oscilloscope Ready\r\n", 32, 100);

    // HAL_Delay(1000);

    __enable_irq(); // Включение глобальных прерываний

    // Запуск таймера с разрешением прерывания
    // HAL_TIM_Base_Start_IT(&htim2);
    // start_sampling();
    while (1)
    {
        // HAL_Delay(500);
        // sampling = 1;
        // HAL_TIM_Base_Start_IT(&htim2);

        process_uart_command();
        if (!sampling && !triggerAuto)
        {
            check_trigger();
        }
        // запуск start_sampling() в цикле один раз  в секунду
        static uint32_t last_tick = 0;
        if (HAL_GetTick() - last_tick >= 500) // 150
        {
            last_tick = HAL_GetTick();
            start_sampling();
        }
    }
}

void start_sampling(void)
{
    // HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_RESET); // Включить светодиод
    //  HAL_Delay(50);

    adcIndex = 0;
    sampling = 1;
    HAL_TIM_Base_Start_IT(&htim2);
    // HAL_ADC_Start_IT(&hadc1); // Запуск АЦП в прерываниях
    //  HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_RESET); // Включить светодиод
    //  HAL_Delay(500);
    //  HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_SET); // Выключить светодиод
}

void stop_sampling(void)
{
    sampling = 0;
    HAL_TIM_Base_Stop_IT(&htim2);
    HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_SET); // Выключить светодиод
}

void send_buffer_uart(void)
{
    HAL_UART_Transmit_DMA(&huart1, (uint8_t *)adcBuffer, sampleCount * 2);
}

void send_buffer_spi(void)
{
    HAL_GPIO_WritePin(GPIOA, GPIO_PIN_4, GPIO_PIN_RESET); // CS low
    HAL_SPI_Transmit_DMA(&hspi1, (uint8_t *)adcBuffer, sampleCount * 2);
    // CS will be set high in transfer complete callback
}

void process_uart_command(void)
{
    static char rx_buf[32];
    static uint8_t idx = 0;
    uint8_t ch;
    if (HAL_UART_Receive(&huart1, &ch, 1, 0) == HAL_OK)
    {
        if (ch == '\n' || ch == '\r')
        {
            rx_buf[idx] = 0;
            if (strncmp(rx_buf, "START", 5) == 0)
            {
                start_sampling();
            }
            else if (strncmp(rx_buf, "LEN:", 4) == 0)
            {
                uint32_t len = atoi(&rx_buf[4]);
                if (len >= ADC_BUF_MIN && len <= ADC_BUF_MAX && (len & (len - 1)) == 0)
                {
                    sampleCount = len;
                    HAL_UART_Transmit(&huart1, (uint8_t *)"OK LEN\r\n", 8, 100);
                }
                else
                {
                    HAL_UART_Transmit(&huart1, (uint8_t *)"ERR LEN\r\n", 9, 100);
                }
            }
            else if (strncmp(rx_buf, "SRATE:", 6) == 0)
            {
                uint32_t rate = atoi(&rx_buf[6]);
                if (rate >= SAMPLE_RATE_MIN && rate <= SAMPLE_RATE_MAX)
                {
                    sampleRate = rate;
                    // TODO: update timer period
                    HAL_UART_Transmit(&huart1, (uint8_t *)"OK SRATE\r\n", 11, 100);
                }
                else
                {
                    HAL_UART_Transmit(&huart1, (uint8_t *)"ERR SRATE\r\n", 12, 100);
                }
            }
            else if (strncmp(rx_buf, "TRIGLVL:", 8) == 0)
            {
                triggerLevel = atoi(&rx_buf[8]);
                HAL_UART_Transmit(&huart1, (uint8_t *)"OK TRIGLVL\r\n", 13, 100);
            }
            else if (strcmp(rx_buf, "TRIG:WAIT") == 0)
            {
                triggerAuto = 0;
                HAL_UART_Transmit(&huart1, (uint8_t *)"OK TRIG WAIT\r\n", 15, 100);
            }
            else if (strcmp(rx_buf, "TRIG:AUTO") == 0)
            {
                triggerAuto = 1;
                HAL_UART_Transmit(&huart1, (uint8_t *)"OK TRIG AUTO\r\n", 15, 100);
            }
            else
            {
                HAL_UART_Transmit(&huart1, (uint8_t *)"ERR CMD\r\n", 10, 100);
            }
            idx = 0;
        }
        else if (idx < sizeof(rx_buf) - 1)
        {
            rx_buf[idx++] = ch;
        }
    }
}

void check_trigger(void)
{
    ADC_ChannelConfTypeDef sConfig = {0};
    sConfig.Channel = ADC_CHANNEL_0;
    sConfig.Rank = 1;
    sConfig.SamplingTime = ADC_SAMPLETIME_15CYCLES;
    HAL_ADC_ConfigChannel(&hadc1, &sConfig);
    HAL_ADC_Start(&hadc1);
    if (HAL_ADC_PollForConversion(&hadc1, 10) == HAL_OK)
    {
        uint16_t val = HAL_ADC_GetValue(&hadc1);
        if (val > triggerLevel)
        {
            start_sampling();
        }
    }
    HAL_ADC_Stop(&hadc1);
}

// Обработчик прерывания таймера TIM2
void TIM2_IRQHandler(void)
{
    HAL_TIM_IRQHandler(&htim2); // Обработка прерывания таймера
}
// Таймерное прерывание: оцифровка и запись в буфер
void HAL_TIM_PeriodElapsedCallback(TIM_HandleTypeDef *htim)
{
    if (htim->Instance == TIM2 && sampling && adcIndex < sampleCount)
    {
        HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_RESET); // Включить светодиод

        // HAL_GPIO_TogglePin(GPIOC, GPIO_PIN_13); // Переключение состояния светодио
        //  Запуск АЦП в режиме прерываний
        HAL_ADC_Start_IT(&hadc1);
        // if (HAL_ADC_PollForConversion(&hadc1, 5) == HAL_OK)
        // {
        //     adcBuffer[adcIndex++] = HAL_ADC_GetValue(&hadc1);
        //     if (adcIndex >= sampleCount)
        //     {
        //         stop_sampling();
        //         send_buffer_uart();
        //     }
        // }
        // HAL_ADC_Stop(&hadc1);
    }
}
// Обработчик прерывания для АЦП
void ADC_IRQHandler(void)
{
    HAL_ADC_IRQHandler(&hadc1); // Обработка прерывания АЦП
}
// Обработчик завершения конверсии АЦП
void HAL_ADC_ConvCpltCallback(ADC_HandleTypeDef *hadc)
{
    // HAL_GPIO_TogglePin(GPIOC, GPIO_PIN_13); // Переключение состояния светодио
    if (hadc->Instance == ADC1)
    {
        if (adcIndex < sampleCount)
        {
            // adcBuffer[adcIndex++] = HAL_ADC_GetValue(hadc);
            adcBuffer[adcIndex++] = (HAL_ADC_GetValue(hadc) >> 8) | (HAL_ADC_GetValue(hadc) << 8);
            if (adcIndex >= sampleCount)
            {
                stop_sampling();
                send_buffer_spi();
            }
        }
    }
    HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_SET); // Выключить светодиод
}

// DMA завершение передачи
void HAL_UART_TxCpltCallback(UART_HandleTypeDef *huart)
{
    if (huart->Instance == USART1)
    {
        const char done[] = "DMA DONE\r\n";
        // HAL_UART_Transmit(&huart1, (uint8_t *)done, sizeof(done) - 1, 100);
    }
}

void HAL_SPI_TxCpltCallback(SPI_HandleTypeDef *hspi)
{
    if (hspi->Instance == SPI1)
    {
        HAL_GPIO_WritePin(GPIOA, GPIO_PIN_4, GPIO_PIN_SET); // CS high
    }
}

// void SystemClock_Config(void) {}
// void MX_GPIO_Init(void) {}
// void MX_DMA_Init(void) {}
// void MX_ADC1_Init(void) {}
// void MX_USART1_UART_Init(void) {}
// void MX_TIM2_Init(void) {}

/**
 * @brief System Clock Configuration
 * @retval None
 */
void SystemClock_Config(void)
{
    RCC_OscInitTypeDef RCC_OscInitStruct = {0};
    RCC_ClkInitTypeDef RCC_ClkInitStruct = {0};

    /** Configure the main internal regulator output voltage
     */
    __HAL_RCC_PWR_CLK_ENABLE();
    __HAL_PWR_VOLTAGESCALING_CONFIG(PWR_REGULATOR_VOLTAGE_SCALE2);

    /** Initializes the RCC Oscillators according to the specified parameters
     * in the RCC_OscInitTypeDef structure.
     */
    RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSE;
    RCC_OscInitStruct.HSEState = RCC_HSE_ON;
    RCC_OscInitStruct.PLL.PLLState = RCC_PLL_ON;
    RCC_OscInitStruct.PLL.PLLSource = RCC_PLLSOURCE_HSE;
    RCC_OscInitStruct.PLL.PLLM = 25;
    RCC_OscInitStruct.PLL.PLLN = 168;
    RCC_OscInitStruct.PLL.PLLP = RCC_PLLP_DIV2;
    RCC_OscInitStruct.PLL.PLLQ = 4;
    if (HAL_RCC_OscConfig(&RCC_OscInitStruct) != HAL_OK)
    {
        Error_Handler();
    }

    /** Initializes the CPU, AHB and APB buses clocks
     */
    RCC_ClkInitStruct.ClockType = RCC_CLOCKTYPE_HCLK | RCC_CLOCKTYPE_SYSCLK | RCC_CLOCKTYPE_PCLK1 | RCC_CLOCKTYPE_PCLK2;
    RCC_ClkInitStruct.SYSCLKSource = RCC_SYSCLKSOURCE_PLLCLK;
    RCC_ClkInitStruct.AHBCLKDivider = RCC_SYSCLK_DIV1;
    RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV2;
    RCC_ClkInitStruct.APB2CLKDivider = RCC_HCLK_DIV1;

    if (HAL_RCC_ClockConfig(&RCC_ClkInitStruct, FLASH_LATENCY_2) != HAL_OK)
    {
        Error_Handler();
    }
}
// Инициализация АЦП
static void MX_ADC1_Init(void)
{
    __HAL_RCC_ADC1_CLK_ENABLE(); // Включение тактирования АЦП
    ADC_ChannelConfTypeDef sConfig = {0};
    hadc1.Instance = ADC1;
    hadc1.Init.ClockPrescaler = ADC_CLOCK_SYNC_PCLK_DIV4;
    hadc1.Init.Resolution = ADC_RESOLUTION_12B;
    hadc1.Init.ScanConvMode = DISABLE;
    hadc1.Init.ContinuousConvMode = DISABLE;
    hadc1.Init.DiscontinuousConvMode = DISABLE;
    hadc1.Init.ExternalTrigConvEdge = ADC_EXTERNALTRIGCONVEDGE_NONE; // Без внешнего триггера
    hadc1.Init.ExternalTrigConv = ADC_SOFTWARE_START;                // Запуск по программному обеспечению
    hadc1.Init.DataAlign = ADC_DATAALIGN_RIGHT;
    hadc1.Init.NbrOfConversion = 1;
    HAL_ADC_Init(&hadc1);
    // Настройка канала АЦП
    sConfig.Channel = ADC_CHANNEL_0; // Выбор канала 0
    sConfig.Rank = 1;
    sConfig.SamplingTime = ADC_SAMPLETIME_56CYCLES;
    HAL_ADC_ConfigChannel(&hadc1, &sConfig);
    // Настройка прерывания для АЦП
    HAL_NVIC_SetPriority(ADC_IRQn, 1, 0);
    HAL_NVIC_EnableIRQ(ADC_IRQn);
}
/**
 * @brief ADC1 Initialization Function
 * @param None
 * @retval None
 */
static void MX_ADC1_Init1(void)
{

    /* USER CODE BEGIN ADC1_Init 0 */

    /* USER CODE END ADC1_Init 0 */

    ADC_ChannelConfTypeDef sConfig = {0};

    /* USER CODE BEGIN ADC1_Init 1 */

    /* USER CODE END ADC1_Init 1 */

    /** Configure the global features of the ADC (Clock, Resolution, Data Alignment and number of conversion)
     */
    hadc1.Instance = ADC1;
    hadc1.Init.ClockPrescaler = ADC_CLOCK_SYNC_PCLK_DIV4;
    hadc1.Init.Resolution = ADC_RESOLUTION_12B;
    hadc1.Init.ScanConvMode = DISABLE;
    hadc1.Init.ContinuousConvMode = DISABLE;
    hadc1.Init.DiscontinuousConvMode = DISABLE;
    hadc1.Init.ExternalTrigConvEdge = ADC_EXTERNALTRIGCONVEDGE_RISING;
    hadc1.Init.ExternalTrigConv = ADC_EXTERNALTRIGCONV_T2_CC2;
    hadc1.Init.DataAlign = ADC_DATAALIGN_RIGHT;
    hadc1.Init.NbrOfConversion = 1;
    hadc1.Init.DMAContinuousRequests = DISABLE;
    hadc1.Init.EOCSelection = ADC_EOC_SINGLE_CONV;
    if (HAL_ADC_Init(&hadc1) != HAL_OK)
    {
        Error_Handler();
    }

    /** Configure for the selected ADC regular channel its corresponding rank in the sequencer and its sample time.
     */
    sConfig.Channel = ADC_CHANNEL_0;
    sConfig.Rank = 1;
    sConfig.SamplingTime = ADC_SAMPLETIME_3CYCLES;
    if (HAL_ADC_ConfigChannel(&hadc1, &sConfig) != HAL_OK)
    {
        Error_Handler();
    }
    /* USER CODE BEGIN ADC1_Init 2 */
    HAL_NVIC_SetPriority(ADC_IRQn, 1, 0); // Установка приоритета прерывания для АЦП
    HAL_NVIC_EnableIRQ(ADC_IRQn);         // Включение прерывания для АЦП
    /* USER CODE END ADC1_Init 2 */
}

/**
 * @brief TIM2 Initialization Function
 * @param None
 * @retval None
 */
static void MX_TIM2_Init(void)
{

    /* USER CODE BEGIN TIM2_Init 0 */

    /* USER CODE END TIM2_Init 0 */

    // TIM_ClockConfigTypeDef sClockSourceConfig = {0};
    // TIM_MasterConfigTypeDef sMasterConfig = {0};
    // TIM_OC_InitTypeDef sConfigOC = {0};

    /* USER CODE BEGIN TIM2_Init 1 */

    /* USER CODE END TIM2_Init 1 */
    htim2.Instance = TIM2;
    htim2.Init.Prescaler = 1;
    htim2.Init.CounterMode = TIM_COUNTERMODE_UP;
    htim2.Init.Period = 419; // 4294967295
    htim2.Init.ClockDivision = TIM_CLOCKDIVISION_DIV1;
    // htim2.Init.AutoReloadPreload = TIM_AUTORELOAD_PRELOAD_DISABLE;
    if (HAL_TIM_Base_Init(&htim2) != HAL_OK)
    {
        Error_Handler();
    }
    // sClockSourceConfig.ClockSource = TIM_CLOCKSOURCE_INTERNAL;
    // if (HAL_TIM_ConfigClockSource(&htim2, &sClockSourceConfig) != HAL_OK)
    // {
    //     Error_Handler();
    // }
    // if (HAL_TIM_OC_Init(&htim2) != HAL_OK)
    // {
    //     Error_Handler();
    // }
    // sMasterConfig.MasterOutputTrigger = TIM_TRGO_RESET;
    // sMasterConfig.MasterSlaveMode = TIM_MASTERSLAVEMODE_DISABLE;
    // if (HAL_TIMEx_MasterConfigSynchronization(&htim2, &sMasterConfig) != HAL_OK)
    // {
    //     Error_Handler();
    // }
    // sConfigOC.OCMode = TIM_OCMODE_TIMING;
    // sConfigOC.Pulse = 0;
    // sConfigOC.OCPolarity = TIM_OCPOLARITY_HIGH;
    // sConfigOC.OCFastMode = TIM_OCFAST_DISABLE;
    // if (HAL_TIM_OC_ConfigChannel(&htim2, &sConfigOC, TIM_CHANNEL_2) != HAL_OK)
    // {
    //     Error_Handler();
    // }
    /* USER CODE BEGIN TIM2_Init 2 */
    HAL_NVIC_SetPriority(TIM2_IRQn, 1, 0); // Установите приоритет (1 - уровень приоритета, 0 - подуровень)
    HAL_NVIC_EnableIRQ(TIM2_IRQn);         // Включите прерывание для TIM2
    /* USER CODE END TIM2_Init 2 */
}

/**
 * @brief USART1 Initialization Function
 * @param None
 * @retval None
 */
static void MX_USART1_UART_Init(void)
{

    /* USER CODE BEGIN USART1_Init 0 */

    /* USER CODE END USART1_Init 0 */

    /* USER CODE BEGIN USART1_Init 1 */

    /* USER CODE END USART1_Init 1 */
    huart1.Instance = USART1;
    huart1.Init.BaudRate = UART_BaudRate;
    huart1.Init.WordLength = UART_WORDLENGTH_8B;
    huart1.Init.StopBits = UART_STOPBITS_1;
    huart1.Init.Parity = UART_PARITY_NONE;
    huart1.Init.Mode = UART_MODE_TX_RX;
    huart1.Init.HwFlowCtl = UART_HWCONTROL_NONE;
    huart1.Init.OverSampling = UART_OVERSAMPLING_16;
    if (HAL_UART_Init(&huart1) != HAL_OK)
    {
        Error_Handler();
    }
    /* USER CODE BEGIN USART1_Init 2 */

    /* USER CODE END USART1_Init 2 */
}

/**
 * @brief SPI1 Initialization Function
 * @param None
 * @retval None
 */
static void MX_SPI1_Init(void)
{
    /* Enable SPI1 clock */
    __HAL_RCC_SPI1_CLK_ENABLE();

    /* SPI1 parameter configuration*/
    hspi1.Instance = SPI1;
    hspi1.Init.Mode = SPI_MODE_MASTER;
    hspi1.Init.Direction = SPI_DIRECTION_2LINES;
    hspi1.Init.DataSize = SPI_DATASIZE_8BIT;
    hspi1.Init.CLKPolarity = SPI_POLARITY_LOW;
    hspi1.Init.CLKPhase = SPI_PHASE_1EDGE;
    hspi1.Init.NSS = SPI_NSS_SOFT;
    hspi1.Init.BaudRatePrescaler = SPI_BAUDRATEPRESCALER_64; // ~10MHz at 84MHz APB2
    hspi1.Init.FirstBit = SPI_FIRSTBIT_MSB;
    hspi1.Init.TIMode = SPI_TIMODE_DISABLE;
    hspi1.Init.CRCCalculation = SPI_CRCCALCULATION_DISABLE;
    hspi1.Init.CRCPolynomial = 10;
    if (HAL_SPI_Init(&hspi1) != HAL_OK)
    {
        Error_Handler();
    }
}

/**
 * Enable DMA controller clock
 */
static void MX_DMA_Init(void)
{
    /* DMA controller clock enable */
    __HAL_RCC_DMA2_CLK_ENABLE();

    /* DMA interrupt init */
    /* DMA2_Stream7_IRQn interrupt configuration for UART1 TX */
    HAL_NVIC_SetPriority(DMA2_Stream7_IRQn, 0, 0);
    HAL_NVIC_EnableIRQ(DMA2_Stream7_IRQn);

    /* DMA2_Stream3_IRQn interrupt configuration for SPI1 TX */
    HAL_NVIC_SetPriority(DMA2_Stream3_IRQn, 0, 1);
    HAL_NVIC_EnableIRQ(DMA2_Stream3_IRQn);
}

/**
 * @brief GPIO Initialization Function
 * @param None
 * @retval None
 */
static void MX_GPIO_Init(void)
{
    /* USER CODE BEGIN MX_GPIO_Init_1 */

    /* USER CODE END MX_GPIO_Init_1 */

    /* GPIO Ports Clock Enable */
    __HAL_RCC_GPIOH_CLK_ENABLE();
    __HAL_RCC_GPIOA_CLK_ENABLE();

    /* USER CODE BEGIN MX_GPIO_Init_2 */
    __HAL_RCC_GPIOC_CLK_ENABLE();

    // Configure LED pin (PC13)
    GPIO_InitTypeDef GPIO_InitStruct = {0};
    GPIO_InitStruct.Pin = GPIO_PIN_13;
    GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
    GPIO_InitStruct.Pull = GPIO_NOPULL;
    GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
    HAL_GPIO_Init(GPIOC, &GPIO_InitStruct);
    HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_SET);

    // Configure SPI1 pins (PA5-SCK, PA6-MISO, PA7-MOSI)
    GPIO_InitStruct.Pin = GPIO_PIN_5 | GPIO_PIN_6 | GPIO_PIN_7;
    GPIO_InitStruct.Mode = GPIO_MODE_AF_PP;
    GPIO_InitStruct.Pull = GPIO_NOPULL;
    GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_VERY_HIGH;
    GPIO_InitStruct.Alternate = GPIO_AF5_SPI1;
    HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);

    // Configure SPI1 CS pin (PA4)
    GPIO_InitStruct.Pin = GPIO_PIN_4;
    GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
    GPIO_InitStruct.Pull = GPIO_NOPULL;
    GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_HIGH;
    HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);
    HAL_GPIO_WritePin(GPIOA, GPIO_PIN_4, GPIO_PIN_SET); // CS inactive high

    /* USER CODE END MX_GPIO_Init_2 */
}

/**
 * @brief  This function is executed in case of error occurrence.
 * @retval None
 */
void Error_Handler(void)
{
    /* USER CODE BEGIN Error_Handler_Debug */
    /* User can add his own implementation to report the HAL error return state */
    __disable_irq();
    while (1)
    {
        HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_RESET); // Включить светодиод
        HAL_Delay(50);
        HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_SET); // Выключить светодиод
        HAL_Delay(50);
        HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_RESET); // Включить светодиод
        HAL_Delay(50);
        HAL_GPIO_WritePin(GPIOC, GPIO_PIN_13, GPIO_PIN_SET); // Выключить светодиод
        HAL_Delay(50);
    }
    /* USER CODE END Error_Handler_Debug */
}
