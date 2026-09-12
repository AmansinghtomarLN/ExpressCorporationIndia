package com.mahavircourier.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Component
public class StringToBigDecimalConverter implements Converter<String, BigDecimal> {

    @Override
    public BigDecimal convert(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        return new BigDecimal(source.trim());
    }
}
