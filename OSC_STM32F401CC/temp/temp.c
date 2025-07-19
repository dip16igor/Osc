#include "main.h"
// Объявление функций
void SystemClock_Config(void);
static void MX_GPIO_Init(void);
static void MX_TIM2_Init(void);
TIM_HandleTypeDef htim2;
int main(void)
{
    // Инициализация HAL
    HAL_Init();

    // Настройка системного тактирования
    SystemClock_Config();

    // Инициализация GPIO
    MX_GPIO_Init();

    // Инициализация таймера
    MX_TIM2_Init();

    // Разрешение глобальных прерываний
    __enable_irq();
    // Запуск таймера с разрешением прерывания
    HAL_TIM_Base_Start_IT(&htim2);

    // Основной цикл
    while (1)
    {
        // Здесь можно добавить основной код, если необходимо
    }
}
// Настройка системного тактирования
void SystemClock_Config(void)
{
    RCC_OscInitTypeDef RCC_OscInitStruct = {0};
    RCC_ClkInitTypeDef RCC_ClkInitStruct = {0};
    // Включение тактирования
    __HAL_RCC_PWR_CLK_ENABLE();
    __HAL_PWR_VOLTAGESCALING_CONFIG(PWR_REGULATOR_VOLTAGE_SCALE2);
    RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSE;
    RCC_OscInitStruct.HSEState = RCC_HSE_ON;
    RCC_OscInitStruct.PLL.PLLState = RCC_PLL_ON;
    RCC_OscInitStruct.PLL.PLLSource = RCC_PLLSOURCE_HSE;
    RCC_OscInitStruct.PLL.PLLM = 25;
    RCC_OscInitStruct.PLL.PLLN = 168;
    RCC_OscInitStruct.PLL.PLLP = RCC_PLLP_DIV2;
    RCC_OscInitStruct.PLL.PLLQ = 4;
    HAL_RCC_OscConfig(&RCC_OscInitStruct);
    RCC_ClkInitStruct.ClockType = RCC_CLOCKTYPE_HCLK | RCC_CLOCKTYPE_SYSCLK | RCC_CLOCKTYPE_PCLK1 | RCC_CLOCKTYPE_PCLK2;
    RCC_ClkInitStruct.SYSCLKSource = RCC_SYSCLKSOURCE_PLLCLK;
    RCC_ClkInitStruct.AHBCLKDivider = RCC_SYSCLK_DIV1;
    RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV2;
    RCC_ClkInitStruct.APB2CLKDivider = RCC_HCLK_DIV1;
    HAL_RCC_ClockConfig(&RCC_ClkInitStruct, FLASH_LATENCY_2);
}
// Инициализация GPIO
static void MX_GPIO_Init(void)
{
    __HAL_RCC_GPIOC_CLK_ENABLE(); // Включение тактирования порта C
    GPIO_InitTypeDef GPIO_InitStruct = {0};
    GPIO_InitStruct.Pin = GPIO_PIN_13;           // Пин C13
    GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;  // Режим: выход Push-Pull
    GPIO_InitStruct.Pull = GPIO_NOPULL;          // Без подтягивающего резистора
    GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW; // Низкая скорость
    HAL_GPIO_Init(GPIOC, &GPIO_InitStruct);      // Инициализация пина
}
// Инициализация таймера TIM2
static void MX_TIM2_Init(void)
{
    __HAL_RCC_TIM2_CLK_ENABLE(); // Включение тактирования таймера 2
    htim2.Instance = TIM2;
    htim2.Init.Prescaler = 8399;                       // Предделитель (при 84 МГц будет 10 кГц)
    htim2.Init.CounterMode = TIM_COUNTERMODE_UP;       // Режим: счет вверх
    htim2.Init.Period = 9999;                          // Период (10000 тактов = 1 секунда)
    htim2.Init.ClockDivision = TIM_CLOCKDIVISION_DIV1; // Деление тактов
    HAL_TIM_Base_Init(&htim2);                         // Инициализация таймера
                               // Настройка NVIC
    HAL_NVIC_SetPriority(TIM2_IRQn, 1, 0); // Установка приоритета прерывания
    HAL_NVIC_EnableIRQ(TIM2_IRQn);         // Включение прерывания
}
// Обработчик прерывания таймера TIM2
void TIM2_IRQHandler(void)
{
    HAL_TIM_IRQHandler(&htim2); // Обработка прерывания таймера
}
// Обработчик переполнения таймера
void HAL_TIM_PeriodElapsedCallback(TIM_HandleTypeDef *htim)
{
    if (htim->Instance == TIM2) // Проверка, что это TIM2
    {
        HAL_GPIO_TogglePin(GPIOC, GPIO_PIN_13); // Переключение состояния светодиода на C13
    }
}