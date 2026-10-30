package com.studenthub.modules.location.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.modules.location.controller.dto.LocationPlaceResponse;
import com.studenthub.modules.location.controller.dto.LocationReverseResponse;
import com.studenthub.modules.location.integration.AmapLocationClient;
import com.studenthub.modules.location.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {
    private final AmapLocationClient client;

    @Override
    public List<LocationPlaceResponse> nearby(double latitude, double longitude, String keyword, int radius) {
        JsonNode root = client.around(latitude, longitude, keyword, radius);
        List<LocationPlaceResponse> places = new ArrayList<>();
        for (JsonNode poi : root.path("pois")) {
            String location = poi.path("location").asText("");
            String[] parts = location.split(",");
            if (parts.length != 2) continue;
            double lng;
            double lat;
            try {
                lng = Double.parseDouble(parts[0]);
                lat = Double.parseDouble(parts[1]);
            } catch (NumberFormatException exception) {
                continue;
            }
            places.add(new LocationPlaceResponse(
                    poi.path("id").asText(),
                    poi.path("name").asText(),
                    poi.path("address").asText(),
                    lat,
                    lng,
                    parseDouble(poi.path("distance").asText("0")),
                    poi.path("type").asText(),
                    poi.path("adname").asText()));
        }
        return places;
    }

    @Override
    public LocationReverseResponse reverse(double latitude, double longitude) {
        JsonNode root = client.reverse(latitude, longitude);
        JsonNode regeocode = root.path("regeocode");
        String formatted = regeocode.path("formatted_address").asText("");
        String district = "";
        String city = "";
        JsonNode component = regeocode.path("addressComponent");
        if (component.isObject()) {
            district = component.path("district").asText("");
            city = component.path("city").asText("");
            if (city.isEmpty()) city = component.path("province").asText("");
        }
        return new LocationReverseResponse(formatted, formatted, latitude, longitude, district, city);
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
