package com.studenthub.modules.location.service;

import com.studenthub.modules.location.controller.dto.LocationPlaceResponse;
import com.studenthub.modules.location.controller.dto.LocationReverseResponse;

import java.util.List;

public interface LocationService {
    List<LocationPlaceResponse> nearby(double latitude, double longitude, String keyword, int radius);
    LocationReverseResponse reverse(double latitude, double longitude);
}
