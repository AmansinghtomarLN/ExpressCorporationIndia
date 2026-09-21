package com.mahavircourier.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class CityCatalogLoader implements ApplicationRunner {

    private final CityService cityService;

    public CityCatalogLoader(CityService cityService) {
        this.cityService = cityService;
    }

    @Override
    public void run(ApplicationArguments args) {
        cityService.importCatalog();
    }
}
