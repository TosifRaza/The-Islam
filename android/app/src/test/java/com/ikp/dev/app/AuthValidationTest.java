package com.ikp.dev.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ikp.dev.app.ui.auth.AuthViewModel;

import org.junit.Test;

/** Client-side auth validation rules (mirror the backend's VAL rules). */
public class AuthValidationTest {

    @Test
    public void acceptsValidEmails() {
        assertTrue(AuthViewModel.isValidEmail("dev-reader@ikp.test"));
        assertTrue(AuthViewModel.isValidEmail("  user@example.org  "));
    }

    @Test
    public void rejectsInvalidEmails() {
        assertFalse(AuthViewModel.isValidEmail(null));
        assertFalse(AuthViewModel.isValidEmail(""));
        assertFalse(AuthViewModel.isValidEmail("no-at-sign"));
        assertFalse(AuthViewModel.isValidEmail("trailing@"));
        assertFalse(AuthViewModel.isValidEmail("a@"));
    }

    @Test
    public void passwordMustBeAtLeastEightChars() {
        assertTrue(AuthViewModel.isValidPassword("Dev#Reader-1234"));
        assertFalse(AuthViewModel.isValidPassword(null));
        assertFalse(AuthViewModel.isValidPassword("short1!"));
        assertTrue(AuthViewModel.isValidPassword("longenough1"));
    }
}
