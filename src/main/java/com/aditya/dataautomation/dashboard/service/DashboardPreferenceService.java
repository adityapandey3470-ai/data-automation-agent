package com.aditya.dataautomation.dashboard.service;

public interface DashboardPreferenceService {

    String getPreferences(Long userId);

    String savePreferences(Long userId, String preferencesJson);

    String resetPreferences(Long userId);
}
