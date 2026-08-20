package com.example.tester.config;

public enum PayloadMode {
    TEXT,
    BASE64,
    HEX,
    FRUIT_ORANGE,
    FRUIT_BANANA,
    WEATHER_TEMPERATURE_READING,
    PING,
    CANDY,
    RADA_STATUS,
    RADA_EXTENDED_STATUS,
    RADA_EXTENDED_STATUS_LITTLE_ENDIAN,
    RADA_EXTENDED_STATUS_MRS,
    RADA_TRACKS_EXTENDED,
    /** POST /pets against a REST-protocol interface (see swagger/pets-demo.yml's createPet operation) - requires target.transport: REST. */
    PETS_CREATE,
    /** GET /pets/{petId} against a REST-protocol interface (see swagger/pets-demo.yml's getPet operation) - requires target.transport: REST. */
    PETS_GET
}
