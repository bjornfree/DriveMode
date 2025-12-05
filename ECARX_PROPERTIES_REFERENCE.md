# ECARX Property ID Reference для Geely Coolray (Binyue L)

## 🔍 Как определить назначение property:

1. **Название** указано в дампе:
   ```
   Property:0x2140a377,Property name:AC_AMBIENT_TEMP
   ```

2. **Значение** в секции событий:
   ```
   event count:3, lastEvent:Property:0x2140a377,int32Values: [116]
   ```

3. **Логика конвертации**:
   - Температура: `integer / 10` (116 → 11.6°C)
   - Топливо: `integer / 100` (1988 → 19.88L)
   - Давление шин: в kPa (172 → 1.72 bar)
   - Напряжение: `integer / 10` (122 → 12.2V)

---

## 📋 Структура ECARX Property ID

Формат: **0xXXYYZZZZ**

- **XX** = Категория (21-27, 2c)
- **YY** = Подсистема (40-4a, 00-0f)
- **ZZZZ** = Конкретный property ID

---

## 🎯 ТОП полезных ECARX Properties

### ✅ Температура (проверено работает!)
```kotlin
0x2140a377  // AC_AMBIENT_TEMP - температура снаружи (Integer/10)
0x2140a379  // AC_INSIDE_TEMP - температура в салоне (Integer/10)
0x21408017  // ENGINE_COOLANT_TEMP - охлаждающая жидкость (Integer/10)
0x2140101d  // HVAC_IN_OUT_TEMP - температура HVAC
0x21404515  // HU_ENVIRONMENTAL_TEMP - температура окружающей среды
```

### ⛽ Топливо и батарея
```kotlin
0x2140a693  // MCU_DISPLAY_TEMP_VAL - УРОВЕНЬ ТОПЛИВА! (Integer/100 = литры) ✅
            // Внимание: название вводит в заблуждение, но это точно топливо!
            // Пример: 1988 = 19.88L (435 events в дампе)
0x2140a692  // MCU_DISPLAY_HIGH_TEMP_WARN - предупреждение низкого топлива (0=OK, 1=LOW)
0x21408006  // BODY_BATTERY_U_BATT_INFO - напряжение батареи (Integer/10 = вольты)
            // Пример: 122 = 12.2V
```

### 🚗 Давление и температура шин (TPMS)
```kotlin
0x2140a456  // TPMSPRESSURE_FL - давление передняя левая (kPa)
0x2140a457  // TPMSPRESSURE_FR - давление передняя правая
0x2140a458  // TPMSPRESSURE_RL - давление задняя левая
0x2140a459  // TPMSPRESSURE_RR - давление задняя правая

0x2140a460  // TPMSTEMP_FL - температура шины передняя левая
0x2140a461  // TPMSTEMP_FR
0x2140a462  // TPMSTEMP_RL
0x2140a463  // TPMSTEMP_RR
```

### 🎮 Режимы вождения
```kotlin
// ЗАПИСЬ режима (используются в коде, но НЕ найдены в дампе properties):
0x2E70002C   // VEHICLE_PROPERTY_CHANGWEI_DRIVE_MODE (decimal: 779092012) - для записи
0x2E70002D   // VEHICLE_PROPERTY_CHANGWEI_SWITCH_DRIVER_MODE (decimal: 779092013) - для записи

// ЧТЕНИЕ режима (возможные кандидаты из дампа):
0x21403000   // AP_DRIVE_MODE_SET_STATUS - статус установленного режима от приложения?
0x21402006   // DRIVE_POWER_OUT_PUT - выходная мощность (может меняться по режиму)
0x2140200e   // DRIVE_EPS_TORQ_MODE - режим крутящего момента руля
0x2140200f   // DRIVE_PWR_TRAIN_STATUS - статус силовой установки
0x21402002   // DRIVE_SWC_EPS_MODE - режим рулевого управления
0x21402005   // DRIVE_BRAKE_MODE_SET - режим тормозов

// Режимы определяются через logcat по значениям:
// realValue 139 = SPORT
// realValue 138 = COMFORT
// realValue 137 = ECO
// realValue 201 = ADAPTIVE
```

### 🏎️ Скорость и руль
```kotlin
0x11600207  // Speed (Float)
0x2140a141  // VSTEERWHEELINFO_ANGLE_DIRECTION
0x2140a142  // VSTEERWHEELINFO_ANGLE_VALUE
0x21408016  // BODY_STEERING_WHEEL_STEERING_ANGLE_SPEED
```

---

## 📚 Полный каталог по категориям

### 0x2140aXXX - MCU/System/Sensors (основная система)

**Качество воздуха:**
- `0x2140a348` - AC_AIRINQLEVEL (качество воздуха внутри)
- `0x2140a349` - AC_AIROUTQLEVEL (качество воздуха снаружи)
- `0x2c40103b` - HVAC_AIR_QUALITY_IN_OUT_CAR

**Климат:**
- `0x2140a355` - AC_PARKINGCLIMATESET
- `0x2140a356` - AC_PARKINGCLIMATE_FAIL_STS
- `0x2140a359` - AC_ECOCLIMATESTS
- `0x2140a376` - AC_AMBIENT_TEMP_INVALID (флаг валидности)
- `0x2140a378` - AC_INSIDE_TEMP_INVALID (флаг валидности)

**Парковочные радары:**
- `0x2140a040` - RADARWARN_WARN_TYPE
- `0x2140a171` - RADARINFO_SENSOR_NUM_FRONT
- `0x2140a172` - RADARINFO_SENSOR_NUM_REAR
- `0x2140a173-180` - RADARWARN_OBSTACLE_DETECT_FL/FLM/FRM/FR/RL/RLM/RRM/RR

**PDC (камеры):**
- `0x2140a181` - PDC_UART_REQUEST
- `0x2140a182` - PDC_VOLUME_REQUEST
- `0x2140a183` - BLIND_CAMERA_SWITCH
- `0x2140a184` - RVC_VIEW_DISPLAY_SWITCH

**Wireless Charging:**
- `0x2140a100` - WIRELESS_WIRELESS_CMD
- `0x2140a101` - WPC_RESPONSE_ERROR
- `0x2140a102` - WPC_ERROR_RES

**Ароматизатор салона:**
- `0x2140a103` - FRAGRANT_TASTE
- `0x2140a104` - FRAGRANT_CONCENTRATION
- `0x2140a105` - FRAGRANT_CH1_TYPE
- `0x2140a106-112` - различные ошибки ароматизатора

**Настройки автомобиля:**
- `0x2140a113` - VEHIC_RLS (Rain Light Sensor)
- `0x2140a114` - VEHIC_RKE_LOCK_FEEDBACK
- `0x2140a115` - VEHIC_DAY_RUNNING_LAMP
- `0x2140a116` - VEHIC_WINDOW_VENTILATE
- `0x2140a117` - VEHIC_AUTO_UNLOCK_WHEN_NEAR_DOOR
- `0x2140a118` - VEHIC_WELCOME_LIGHT_SET
- `0x2140a119` - VEHIC_ANY_DOOR_LOCK_CAR_WARNING
- `0x2140a120` - VEHIC_WINDOW_CLAMP_WARNING
- `0x2140a121` - VEHIC_BACK_DOOR_AUTO_UNLOCK_DISTANCE
- `0x2140a122` - VEHIC_FOLLOWME_HOME_TIMESELECT
- `0x2140a123` - VEHIC_CORNREING_MODE
- `0x2140a124` - VEHIC_LOCK_BATTORY_ON
- `0x2140a125` - VEHIC_KEYINREMINDER_OPTION
- `0x2140a126` - VEHIC_REARWIPERAUTOACTIVEATREVERSER
- `0x2140a127` - VEHIC_FOLLOWME_HOME_CLOSEFUNC
- `0x2140a128` - VEHIC_SAILINGSWITCHSTS
- `0x2140a129` - VEHIC_MMRYSCSINDREQ
- `0x2140a130` - VEHIC_MEMORYSET
- `0x2140a131` - VEHIC_IGNOFFUNLOCKDOOR
- `0x2140a185` - VEHIC_EXT_LIGHT_OUTPUT
- `0x2140a186` - VEHIC_MUSIC_RHYTHM

**PHEV/Hybrid (если применимо):**
- `0x2140a140` - EPTREADY
- `0x2140a143` - CHARGE_CONNECTOR_STS
- `0x2140a144` - VEH_CHARGE_STS
- `0x2140a145` - VEH_PRE_CHARGE_STS
- `0x2140a146` - NOPLUGIN_MODE
- `0x2140a147` - SAVE_Mode
- `0x2140a150` - PHEV_NODE_STS
- `0x2140a151` - PARKING_AUXILIARY_SWITCH
- `0x2140a163` - HIGH_VOLTAGE_BATTERY_PACK_WORK_STATUS
- `0x2140a164` - PURE_ELECTRIC_MILEAGE
- `0x2140a165` - AC_CHARGING_REMAINING_TIME
- `0x2140a166` - BATTERY_CURR
- `0x2140a167` - EXTERNAL_DISCHARGE
- `0x2140a168` - BATTERY_THERMAL_RUNAWAY

**Индикация и предупреждения:**
- `0x2140a574` - IPKWARN_AIRBAG
- `0x2140a578` - IPKINFO_COOLAN_TEMP
- `0x2140a593` - IPKWARN_TCU_OVER_TEMP_LEVEL
- `0x2140a596` - IPK_ENGINE_COOLANT_TEMP_VALIDITY
- `0x2140a692` - MCU_DISPLAY_HIGH_TEMP_WARN
- `0x2140a693` - MCU_DISPLAY_TEMP_VAL

**MCU System:**
- `0x2140a010` - MCU system
- `0x2140a017` - MCU_POWER_WORK_MODE_ATS
- `0x2140a043` - MCU_CONFIG_DID
- `0x2140a045` - MCU_CONFIG_F102
- `0x2140a046` - MCU_CAN_BODY
- `0x2140a048` - MCU_CAN_NETWORK
- `0x2140a049` - MCU_CAN_CTRL
- `0x2140a050` - MCU_UPGRADE_STATE
- `0x2140a051` - MCU_UPGRADE_FILE
- `0x2140a052` - MCU_UPGRADE_DATA

**DVR (видеорегистратор):**
- `0x2140a187` - DVR_RESOLUTION
- `0x2140a188` - DVR_RESTORE_FACTORY_SETTING
- `0x2140a189` - DVR_UPDATE_MODE
- `0x2140a190` - DVR_RETURN_TO_MAIN_MENU
- `0x2140a191` - DVR_BROWSE_INSTRUCTION
- `0x2140a192` - DVR_INSTRUCTION_LIST
- `0x2140a193` - DVR_ENTRY_AND_EXIT_RESPONSE
- `0x2140a194` - DVR_SYSTEM_STATE
- `0x2140a195` - DVR_SDCARD_STATE
- `0x2140a196` - DVR_MESSAGE_NODE_LOST
- `0x2140a197` - DVR_REQUEST_TO_ENTER_DVR
- `0x2140a198` - DVR_SPEED_STATE
- `0x2140a199` - DVR_SYNC_INFO

### 0x21401XXX - HVAC (климат-контроль)
- `0x21401002` - HVAC_AIR_DRYIONG_ON
- `0x2140100f` - HVAC_ENG_HEAT_TEMP_CFG
- `0x2140101d` - HVAC_IN_OUT_TEMP
- `0x21401021` - HVAC_TEMPERATURE_SET
- `0x21601022` - HVAC_TEMPERATURE_LV_SET
- `0x25401028` - HVAC_SEAT_TEMPERATURE
- `0x25401036` - HVAC_TEMPERATURE_ADJUST

### 0x21402XXX - Body Controls (кузов)
- `0x2140204c` - BODY_SUNROOF_STATUS
- `0x21402017` - BODY_WINDOW_SUNROOFRAIN_DETECTCLOSE_ON
- `0x214020c7` - ?

### 0x21403XXX - Instrument Panel (приборная панель)
- `0x21403017` - AP_PHONE_STATE
- `0x21403043` - IP_REQ_SCREENSHOTS_RESULT
- `0x21403044` - IP_ADAPTIVE_DIMMING_LEVEL
- `0x21403045` - IP_LCM_DIMMER_LEVEL
- `0x2140304e` - IP_DISPLAY_THEME_STATE
- `0x2140304f` - IP_NAVI_STATE
- `0x21403050` - IP_DISPLAY_MODE

### 0x21404XXX - ADAS (ассистенты водителя)
- `0x21404006` - ADAS_LANE_CHANGE_STYLE
- `0x21404017` - DRIVE_ACC_FCW_OVERSPEED_INFO
- `0x21404040` - ADAS_ACC_ELK_SWITCH
- `0x21404041` - ADAS_FCTA_SWITCH
- `0x21404042` - ADAS_RCTB_SWITCH
- `0x21404043` - ADAS_FCTB_SWITCH
- `0x21404044` - ADAS_LCATTC_SETTING
- `0x21404045` - ADAS_LCDAL_RCW_SOUND_ENABLE
- `0x21404515` - HU_ENVIRONMENTAL_TEMP

### 0x21405XXX - AVM/Cameras (камеры кругового обзора)
- `0x21405017` - DVR_LANGUAGE_SET
- `0x21405040` - AVM_DISPLAY_SWITCH
- `0x21405041` - AVM_LINE_SWITCH
- `0x21405042` - AVM_MOD_ENABLE
- `0x21405043` - AVM_TURNLIGHT_TRIGGER_SWITCH
- `0x21405044` - AVM_RADAR_TRIGGER_SWITCH
- `0x21405045` - AVM_BODY_COLOR_STATUS
- `0x21405046` - AVM_WHEEL_HUB_STYLE
- `0x21405047` - AVM_LICENSE_NUMBER_SET
- `0x21405048` - SCREEN_INFO
- `0x21405049` - AVM_DISPLAY_FORM
- `0x2140504a` - AVM_TRANSPARENT_CAR_SWITCH
- `0x2140504b` - AVM_3D_HOR_ANGLE
- `0x2140504c` - AVM_DETECTION_REQUEST
- `0x2140504d` - AVM_TOPLOOK_DOWN_SWITCH
- `0x2140504e` - APA_REMOTE_DISP_STS
- `0x2140504f` - TBOX_AVM_DISPLAY_FORM
- `0x21405050` - AVM_INNER_SET
- `0x21405051` - AVM_SPEED_PANORAMIC_SELECT
- `0x21405054` - DVR_REQUEST

### 0x21408XXX - Engine/Powertrain (двигатель)
- `0x21408006` - BODY_BATTERY_U_BATT_INFO (напряжение батареи)
- `0x21408015` - ?
- `0x21408016` - BODY_STEERING_WHEEL_STEERING_ANGLE_SPEED
- `0x21408017` - ENGINE_COOLANT_TEMPERATURE (охлаждающая жидкость)
- `0x2140800b` - ?
- `0x2140800c` - ?
- `0x2140800e` - ?
- `0x2140800f` - ?

### 0x21409XXX - Audio/Vendor/PCP (звук)
- `0x21409017` - VENDOR_BCALL_REQ
- `0x21409045` - VENDOR_SAVE_SE_WORK_STATUS
- `0x21409046` - VENDOR_SAVE_SIGN_STATUS
- `0x21409049` - PCP_SOUND_ADJUST_TYPE
- `0x2140904a` - PCP_SOUND_DISPLAY_TYPE
- `0x2140904b` - PCP_VOLUME_MUTE
- `0x2140904c` - PCP_VOLUME_STATUS
- `0x2140904d` - PCP_VOLUME_ADJUST
- `0x2140904e` - PCP_VOLUME_MUTE_SET
- `0x2140904f` - PCP_VOL_ADJUST_SENSTIVE_STATUS
- `0x21409050` - AP_USER_STATE
- `0x21409051` - VENDOR_BLEU_USER_ID

### 0x2110XXXX - AP (Application Processor)
- `0x21100104` - ?
- `0x21103015` - AP_MEDIA_PLAYING_SOURCE_MESSAGE
- `0x21103016` - AP_MEDIA_SINGER_NAME
- `0x21103018` - AP_PHONE_CONTACT_INFO
- `0x2110301a` - AP_PHONE_NUMBER
- `0x2110901d` - TUID_INFO
- `0x21109000` - VENDOR_HW_VERSION_ACK
- `0x2110a001` - MCU_SYS_SN

### 0x2170XXXX - BLE/Connectivity (Bluetooth)
- `0x2170a070` - MCU_BLE_SOC_BLE
- `0x2170a071` - MCU_BLE_MCU_BLE
- `0x2170a072` - SOC_SYNC_MCU
- `0x2170a62e` - ? (bytes data)

---

## 💡 Как использовать

### Чтение Integer property:
```kotlin
val intValue = carPropertyManager.getIntProperty(0x2140a377, 0) // area ID = 0
val temperature = intValue / 10f // конвертация в °C
```

### Чтение Float property:
```kotlin
val floatValue = carPropertyManager.getFloatProperty(0x11600207, 0)
val speed = floatValue // скорость
```

### Запись property:
```kotlin
carPropertyManager.setIntProperty(0x25401028, 1, 3) // установка обогрева сидения водителя на уровень 3
```

---

## 📝 Примечания

- **Всего ECARX properties: ~1334**
- Префиксы `0x21-0x27` и `0x2c` указывают на ECARX custom properties
- Стандартные AOSP properties начинаются с `0x11`, `0x15`, `0x16`
- На Geely Coolray с Flyme Auto многие AOSP properties работают в demo mode
- Используй ECARX properties для получения реальных данных

---

## 🔧 Особенности и заметки

### Почему `MCU_DISPLAY_TEMP_VAL` = топливо?

**Название вводит в заблуждение!** Анализ показал:

1. **Частота обновлений**: 435 событий (очень активное)
2. **Значение**: 1988
3. **Логика для топлива**: 1988 / 100 = **19.88L** → 44% от бака 45L ✅
4. **Логика для температуры**: 1988 / 10 = 198.8°C ❌ (нереально)
5. **Парный property**: `0x2140a692` (предупреждение) идет вместе
6. **Стандартные AOSP fuel properties** не работают (demo mode)

**Вывод:** Это единственный работающий property для чтения топлива на Geely Coolray!

### Как читать текущий режим вождения?

Properties `0x2E70002C` и `0x2E70002D` **только для записи** (не в списке getPropertyList).

**Текущее решение:** Парсинг logcat:
```kotlin
// logcat содержит:
// QSCarPropertyManager: handleDriveModeChange realValue 139 → SPORT
// QSCarPropertyManager: handleDriveModeChange realValue 138 → COMFORT
// QSCarPropertyManager: handleDriveModeChange realValue 137 → ECO
// QSCarPropertyManager: handleDriveModeChange realValue 201 → ADAPTIVE
```

**Альтернатива для тестирования:**
```kotlin
// Попробуй эти properties в машине:
val candidates = listOf(
    0x21403000,  // AP_DRIVE_MODE_SET_STATUS
    0x21402006,  // DRIVE_POWER_OUT_PUT
    0x2140200e,  // DRIVE_EPS_TORQ_MODE
    0x2140200f   // DRIVE_PWR_TRAIN_STATUS
)

for (propId in candidates) {
    try {
        val value = carPropertyManager.getIntProperty(propId, 0)
        Log.d("DriveMode", "Property 0x${propId.toString(16)} = $value")
    } catch (e: Exception) {
        // Property не поддерживается
    }
}
```

### Почему некоторые AOSP properties не работают?

**Demo Mode:** В `build.prop` найдено:
```
android.car.hvac.demo=true
```

Это означает что стандартные AOSP properties возвращают статические "демо" значения:
- `ENV_OUTSIDE_TEMPERATURE` → 25.0°C (всегда)
- `FUEL_LEVEL` → 15.0L (всегда)

**Решение:** Использовать ECARX custom properties!

---

**Источник данных:** dumpsys car_service на Geely Coolray (Binyue L) с ECARX/Flyme Auto
**Всего ECARX properties:** ~1334
**Дата анализа:** 2025-01-26
