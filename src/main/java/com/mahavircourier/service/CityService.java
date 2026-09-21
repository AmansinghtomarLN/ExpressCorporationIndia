package com.mahavircourier.service;

import com.mahavircourier.dao.CityDao;
import com.mahavircourier.model.City;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class CityService {

    private static final Logger log = LoggerFactory.getLogger(CityService.class);
    private static final String CATALOG = "data/india-cities.csv";
    private static final String DEFAULT_LOCAL_CITY = "Indore";

    private final CityDao cityDao;

    public CityService(CityDao cityDao) {
        this.cityDao = cityDao;
    }

    public List<City> findAll() {
        return cityDao.findAll();
    }

    public List<City> search(String query, String state, String category) {
        String cat = StringUtils.hasText(category) ? CityCategory.normalize(category) : null;
        return cityDao.search(query, state, cat);
    }

    public java.util.Optional<City> findById(Long id) {
        return cityDao.findById(id);
    }

    @Transactional
    public City create(City incoming) {
        City city = normalize(incoming, null);
        if (cityDao.exists(city.getCityName(), city.getState(), null)) {
            throw new IllegalArgumentException(city.getCityName() + ", " + city.getState() + " is already in the catalog");
        }
        Long id = cityDao.save(city);
        city.setId(id);
        return city;
    }

    @Transactional
    public City update(City incoming) {
        City existing = cityDao.findById(incoming.getId())
                .orElseThrow(() -> new IllegalArgumentException("City not found"));
        City city = normalize(incoming, existing.getId());
        city.setId(existing.getId());
        if (cityDao.exists(city.getCityName(), city.getState(), city.getId())) {
            throw new IllegalArgumentException(city.getCityName() + ", " + city.getState() + " is already in the catalog");
        }
        cityDao.update(city);
        return city;
    }

    @Transactional
    public void delete(Long id) {
        cityDao.findById(id).orElseThrow(() -> new IllegalArgumentException("City not found"));
        cityDao.deleteById(id);
    }

    /**
     * Loads the India city catalog into {@code cities} on startup.
     * Existing rows keep their category so admin edits are not overwritten.
     */
    @Transactional
    public int importCatalog() {
        List<String[]> rows = readCatalog();
        int inserted = 0;
        for (String[] row : rows) {
            String cityName = row[0];
            String state = row[1];
            if (cityDao.exists(cityName, state, null)) {
                continue;
            }
            City city = new City();
            city.setCityName(cityName);
            city.setState(state);
            city.setCategory(seedCategory(cityName, state));
            cityDao.save(city);
            inserted++;
        }
        log.info("City catalog import finished. inserted={}, total={}", inserted, cityDao.count());
        return inserted;
    }

    public String manifestOptionsJson(String localCity) {
        String local = StringUtils.hasText(localCity) ? localCity.trim() : DEFAULT_LOCAL_CITY;
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (City city : cityDao.findAll()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            String group = displayGroup(city, local);
            json.append("{\"city\":\"").append(jsonEscape(city.getCityName())).append('"')
                    .append(",\"state\":\"").append(jsonEscape(city.getState())).append('"')
                    .append(",\"category\":\"").append(jsonEscape(group)).append('"')
                    .append(",\"group\":\"").append(jsonEscape(CityCategory.displayNameForDropdown(group))).append('"')
                    .append(",\"label\":\"").append(jsonEscape(city.getLabel())).append("\"}");
        }
        return json.append(']').toString();
    }

    private String displayGroup(City city, String localCity) {
        if (CityCategory.sameCity(city.getCityName(), localCity)) {
            return CityCategory.LOCAL;
        }
        if (CityCategory.LOCAL.equalsIgnoreCase(city.getCategory())) {
            return CityCategory.fromState(city.getState());
        }
        try {
            return CityCategory.normalize(city.getCategory());
        } catch (IllegalArgumentException ex) {
            return CityCategory.fromState(city.getState());
        }
    }

    private City normalize(City incoming, Long excludeId) {
        if (incoming == null || !StringUtils.hasText(incoming.getCityName())) {
            throw new IllegalArgumentException("City is required");
        }
        if (!StringUtils.hasText(incoming.getState())) {
            throw new IllegalArgumentException("State is required");
        }
        City city = new City();
        city.setId(excludeId);
        city.setCityName(incoming.getCityName().trim());
        city.setState(incoming.getState().trim());
        if (StringUtils.hasText(incoming.getCategory())) {
            city.setCategory(CityCategory.normalize(incoming.getCategory()));
        } else {
            city.setCategory(CityCategory.fromState(city.getState()));
        }
        return city;
    }

    private String seedCategory(String cityName, String state) {
        if (CityCategory.sameCity(cityName, DEFAULT_LOCAL_CITY)) {
            return CityCategory.LOCAL;
        }
        return CityCategory.fromState(state);
    }

    private List<String[]> readCatalog() {
        List<String[]> rows = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(CATALOG);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                if (!StringUtils.hasText(line) || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(",", 2);
                if (parts.length < 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
                    continue;
                }
                rows.add(new String[]{parts[0].trim(), parts[1].trim()});
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read city catalog " + CATALOG, ex);
        }
        return rows;
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c");
    }
}
