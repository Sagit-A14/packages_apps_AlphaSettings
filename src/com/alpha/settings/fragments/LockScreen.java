/*
 * Copyright (C) 2016-2024 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alpha.settings.fragments;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import com.alpha.settings.fragments.lockscreen.doze.DozeSettings;
import com.alpha.settings.fragments.lockscreen.udfps.UdfpsAnimation;
import com.alpha.settings.fragments.lockscreen.udfps.UdfpsIconPicker;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.OmniJawsClient;
import com.android.internal.util.crdroid.Utils;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import lineageos.providers.LineageSettings;

@SearchIndexable
public class LockScreen extends SettingsPreferenceFragment
            implements Preference.OnPreferenceChangeListener  {

    public static final String TAG = "LockScreen";

    private static final String LOCKSCREEN_INTERFACE_CATEGORY = "lockscreen_interface_category";
    private static final String LOCKSCREEN_GESTURES_CATEGORY = "lockscreen_gestures_category";
    private static final String KEY_RIPPLE_EFFECT = "enable_ripple_effect";
    private static final String KEY_WEATHER = "lockscreen_weather_enabled";
    private static final String KEY_UDFPS_ANIMATIONS = "udfps_recognizing_animation_preview";
    private static final String KEY_UDFPS_ICONS = "udfps_icon_picker";
    private static final String SCREEN_OFF_UDFPS_ENABLED = "screen_off_udfps_enabled";

    private Preference mUdfpsAnimations;
    private Preference mUdfpsIcons;
    private Preference mRippleEffect;
    private Preference mWeather;
    private Preference mScreenOffUdfps;

    private Preference mDepthWallpaperCustomImagePicker;

    private OmniJawsClient mWeatherClient;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.alpha_settings_lockscreen);

        mDepthWallpaperCustomImagePicker = findPreference("depth_wallpaper_subject_image_uri");

        PreferenceCategory gestCategory = (PreferenceCategory) findPreference(LOCKSCREEN_GESTURES_CATEGORY);

        FingerprintManager mFingerprintManager = (FingerprintManager)
                getActivity().getSystemService(Context.FINGERPRINT_SERVICE);
        mUdfpsAnimations = (Preference) findPreference(KEY_UDFPS_ANIMATIONS);
        mUdfpsIcons = (Preference) findPreference(KEY_UDFPS_ICONS);
        mRippleEffect = (Preference) findPreference(KEY_RIPPLE_EFFECT);
        mScreenOffUdfps = (Preference) findPreference(SCREEN_OFF_UDFPS_ENABLED);

        if (mFingerprintManager == null || !mFingerprintManager.isHardwareDetected()) {
            gestCategory.removePreference(mUdfpsAnimations);
            gestCategory.removePreference(mUdfpsIcons);
            gestCategory.removePreference(mRippleEffect);
            gestCategory.removePreference(mScreenOffUdfps);
        } else {
            if (!Utils.isPackageInstalled(getContext(), "com.alpha.udfps.animations")) {
                gestCategory.removePreference(mUdfpsAnimations);
            }
            if (!Utils.isPackageInstalled(getContext(), "com.alpha.udfps.icons")) {
                gestCategory.removePreference(mUdfpsIcons);
            }
            Resources resources = getResources();
            boolean screenOffUdfpsAvailable = resources.getBoolean(
                    com.android.internal.R.bool.config_supportScreenOffUdfps) ||
                    !TextUtils.isEmpty(resources.getString(
                        com.android.internal.R.string.config_dozeUdfpsLongPressSensorType));
            if (!screenOffUdfpsAvailable)
                gestCategory.removePreference(mScreenOffUdfps);
        }

        mWeather = (Preference) findPreference(KEY_WEATHER);
        mWeatherClient = new OmniJawsClient(getContext());
        updateWeatherSettings();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.Secure.putIntForUser(resolver,
                Settings.Secure.SCREEN_OFF_UDFPS_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_BATTERY_INFO, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.DOUBLE_TAP_SLEEP_LOCKSCREEN, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.ENABLE_RIPPLE_EFFECT, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.FP_ERROR_VIBRATE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.FP_SUCCESS_VIBRATE, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_ENABLE_POWER_MENU, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_WEATHER_ENABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_WEATHER_LOCATION, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.LOCKSCREEN_WEATHER_TEXT, 1, UserHandle.USER_CURRENT);
        UdfpsAnimation.reset(mContext);
        UdfpsIconPicker.reset(mContext);
    }

    private void updateWeatherSettings() {
        if (mWeatherClient == null || mWeather == null) return;

        boolean weatherEnabled = mWeatherClient.isOmniJawsEnabled();
        mWeather.setEnabled(weatherEnabled);
        mWeather.setSummary(weatherEnabled ? R.string.lockscreen_weather_summary :
            R.string.lockscreen_weather_enabled_info);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateWeatherSettings();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.ALPHA;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mDepthWallpaperCustomImagePicker) {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, 10001);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent result) {
        if (requestCode == 10001) {
            if (resultCode != Activity.RESULT_OK) {
                return;
            }

            final Uri imgUri = result.getData();
            if (imgUri != null) {
                String savedImagePath = saveImageToInternalStorage(getContext(), imgUri);
                if (savedImagePath != null) {
                    ContentResolver resolver = getContext().getContentResolver();
                    Settings.System.putStringForUser(resolver, "depth_wallpaper_subject_image_uri", savedImagePath, UserHandle.USER_CURRENT);
                }
            }
        }
    }

    private String saveImageToInternalStorage(Context context, Uri imgUri) {
        try {
            InputStream inputStream;
            if (imgUri.toString().startsWith("content://com.google.android.apps.photos.contentprovider")) {
                List<String> segments = imgUri.getPathSegments();
                if (segments.size() > 2) {
                    String mediaUriString = URLDecoder.decode(segments.get(2), StandardCharsets.UTF_8.name());
                    Uri mediaUri = Uri.parse(mediaUriString);
                    inputStream = context.getContentResolver().openInputStream(mediaUri);
                } else {
                    throw new FileNotFoundException("Failed to parse Google Photos content URI");
                }
            } else {
                inputStream = context.getContentResolver().openInputStream(imgUri);
            }
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String imageFileName = "DEPTH_WALLPAPER_SUBJECT_" + timeStamp + ".png";
            File directory = new File("/sdcard/depthwallpaper");
            if (!directory.exists() && !directory.mkdirs()) {
                return null;
            }
            File[] files = directory.listFiles((dir, name) -> name.startsWith("DEPTH_WALLPAPER_SUBJECT_") && name.endsWith(".png"));
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            File file = new File(directory, imageFileName);
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.alpha_settings_lockscreen) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    FingerprintManager mFingerprintManager = (FingerprintManager)
                            context.getSystemService(Context.FINGERPRINT_SERVICE);
                    if (mFingerprintManager == null || !mFingerprintManager.isHardwareDetected()) {
                        keys.add(KEY_UDFPS_ANIMATIONS);
                        keys.add(KEY_UDFPS_ICONS);
                        keys.add(KEY_RIPPLE_EFFECT);
                        keys.add(SCREEN_OFF_UDFPS_ENABLED);
                    } else {
                        if (!Utils.isPackageInstalled(context, "com.alpha.udfps.animations")) {
                            keys.add(KEY_UDFPS_ANIMATIONS);
                        }
                        if (!Utils.isPackageInstalled(context, "com.alpha.udfps.icons")) {
                            keys.add(KEY_UDFPS_ICONS);
                        }
                        Resources resources = context.getResources();
                        boolean screenOffUdfpsAvailable = resources.getBoolean(
                            com.android.internal.R.bool.config_supportScreenOffUdfps) ||
                            !TextUtils.isEmpty(resources.getString(
                                com.android.internal.R.string.config_dozeUdfpsLongPressSensorType));
                        if (!screenOffUdfpsAvailable)
                            keys.add(SCREEN_OFF_UDFPS_ENABLED);
                        }
                    return keys;
                }
            };
}
