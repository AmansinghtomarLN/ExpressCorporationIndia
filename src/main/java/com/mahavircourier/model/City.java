package com.mahavircourier.model;

import com.mahavircourier.service.CityCategory;

public class City {

    private Long id;
    private String cityName;
    private String state;
    private String category = CityCategory.REST;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCategoryLabel() {
        return CityCategory.displayName(category);
    }

    public String getLabel() {
        if (state == null || state.isBlank()) {
            return cityName;
        }
        return cityName + " — " + state;
    }
}
