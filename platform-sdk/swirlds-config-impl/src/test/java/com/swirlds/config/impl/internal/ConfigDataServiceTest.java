package com.swirlds.config.impl.internal;

import com.swirlds.config.api.converter.ConfigConverter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigDataServiceTest {

    private ConverterService converterService;

    @BeforeEach
    public void setUp() {
        converterService = new ConverterService();
        converterService.init();
    }

    @Test
    public void converterReturnsNotNull() {
        // given
        ConverterService cs = converterService;

        //then:
        Assertions.assertNotNull(cs.getConverterForType(PlainTestEnum.class));
    }

    @Test
    public void converterConvertsValueToPlainTestEnum() {
        // given
        ConverterService cs = converterService;

        //then:
        Assertions.assertEquals(PlainTestEnum.UNO, cs.convert("UNO", PlainTestEnum.class));
    }

    @Test
    public void converterConvertsValueToComplexTestEnum() {
        // given
        ConverterService cs = converterService;

        //then:
        Assertions.assertEquals(ComplexTestEnum.UNO, cs.convert("UNO", ComplexTestEnum.class));
    }

    @Test
    public void converterConvertsValueToComplexTestEnumEvenHavingASimilarEnum() {
        // given
        ConverterService cs = converterService;
        cs.getConverterForType(PlainTestEnum.class);

        //then:
        Assertions.assertEquals(ComplexTestEnum.UNO, cs.convert("UNO", ComplexTestEnum.class));
    }


    @Test
    public void itDoesntCreateANewInstanceForAlreadyAskedConverters() {
        // given
        ConverterService cs = converterService;
        ConfigConverter<PlainTestEnum> converterForTypePlainTestEnum = cs.getConverterForType(PlainTestEnum.class);

        //then:
        Assertions.assertEquals(converterForTypePlainTestEnum, cs.getConverterForType(PlainTestEnum.class));
    }

    @Test
    public void converterThrowsIllegalArgumentIfDoesntMatch() {
        // given
        ConverterService cs = converterService;

        //then:
        Assertions.assertThrows(IllegalArgumentException.class, ()-> cs.convert("DOS", PlainTestEnum.class));
    }

    enum PlainTestEnum {
        UNO

    }


    enum ComplexTestEnum {
        UNO(1);
        final int value;

        ComplexTestEnum(int value) {
            this.value = value;
        }

    }
}