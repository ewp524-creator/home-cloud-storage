package com.filehome.user;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filehome.common.ApiException;
import com.filehome.user.UserSettingsDtos.PreferenceView;
import com.filehome.user.UserSettingsDtos.SettingsView;
import com.filehome.user.UserSettingsDtos.UpdatePreferencesRequest;

@Service
public class UserSettingsService {

    private static final Set<String> VIEWS = Set.of("GRID", "LIST");
    private static final Set<String> SORTS = Set.of(
            "modified-desc",
            "modified-asc",
            "name-asc",
            "size-desc");

    private final UserService userService;
    private final UserPreferenceRepository preferenceRepository;

    public UserSettingsService(
            UserService userService,
            UserPreferenceRepository preferenceRepository) {
        this.userService = userService;
        this.preferenceRepository = preferenceRepository;
    }

    public SettingsView get(UUID userId) {
        UserAccount account = userService.require(userId);
        UserPreference preference = preferenceRepository.findById(userId)
                .orElseGet(() -> UserPreference.defaults(userId));
        return view(account, preference);
    }

    @Transactional
    public SettingsView update(
            UUID userId,
            UpdatePreferencesRequest request) {
        UserAccount account = userService.require(userId);
        String view = request.defaultView().trim().toUpperCase();
        String sort = request.defaultSort().trim();
        if (!VIEWS.contains(view)) {
            throw ApiException.badRequest(
                    "INVALID_DEFAULT_VIEW",
                    "\u9ed8\u8ba4\u89c6\u56fe\u4e0d\u5408\u6cd5");
        }
        if (!SORTS.contains(sort)) {
            throw ApiException.badRequest(
                    "INVALID_DEFAULT_SORT",
                    "\u9ed8\u8ba4\u6392\u5e8f\u4e0d\u5408\u6cd5");
        }
        if (request.uploadConcurrency() < 1
                || request.uploadConcurrency() > 4) {
            throw ApiException.badRequest(
                    "INVALID_UPLOAD_CONCURRENCY",
                    "\u4e0a\u4f20\u5e76\u53d1\u6570\u9700\u8981\u5728 1-4 \u4e4b\u95f4");
        }
        UserPreference preference = new UserPreference(
                userId,
                view,
                sort,
                request.reduceMotion(),
                request.uploadConcurrency(),
                java.time.Instant.now());
        preferenceRepository.upsert(preference);
        return view(account, preference);
    }

    private SettingsView view(
            UserAccount account,
            UserPreference preference) {
        return new SettingsView(
                UserView.from(account),
                new PreferenceView(
                        preference.defaultView(),
                        preference.defaultSort(),
                        preference.reduceMotion(),
                        preference.uploadConcurrency()));
    }
}
