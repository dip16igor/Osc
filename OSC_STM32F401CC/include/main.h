#ifndef __MAIN_H
#define __MAIN_H

#include "stm32f4xx_hal.h"

extern ADC_HandleTypeDef hadc1;
extern UART_HandleTypeDef huart1;
extern DMA_HandleTypeDef hdma_usart1_tx;
extern TIM_HandleTypeDef htim2;

void Error_Handler(void);

#endif