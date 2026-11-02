package com.fadcam.kcam.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProfileStoreTest {

    @Test
    public void securityKeysAreNeverTakenFromProfiles() {
        assertTrue(ProfileStore.isSecurityKey("remote_auth_password_hash"));
        assertTrue(ProfileStore.isSecurityKey("applock_enabled"));
        assertTrue(ProfileStore.isSecurityKey("cloud_stream_token"));
        assertTrue(ProfileStore.isSecurityKey("user_pin_hash"));
    }

    @Test
    public void normalSettingsAreProfileable() {
        assertFalse(ProfileStore.isSecurityKey("video_resolution"));
        assertFalse(ProfileStore.isSecurityKey("watermark_option"));
        assertFalse(ProfileStore.isSecurityKey("pref_ae_lock")); // camera exposure lock, not security
        assertFalse(ProfileStore.isSecurityKey("camera_selection"));
    }

    @Test
    public void idIsFileSystemSafe() {
        assertEquals("car_night", ProfileStore.idFor("  Car / Night! "));
        assertEquals("profile", ProfileStore.idFor("***"));
    }
}
