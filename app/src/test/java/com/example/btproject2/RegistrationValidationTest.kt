package com.example.btproject2

import com.example.btproject2.utils.RegistrationValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for RegistrationValidator covering field completeness, format validation,
 * disposable email filtering, password length, and password matching.
 */
class RegistrationValidationTest {

    @Test
    fun testFullNameValidation() {
        // Blank or whitespace-only
        assertTrue(RegistrationValidator.validateFullName("") is RegistrationValidator.FieldResult.Invalid)
        assertTrue(RegistrationValidator.validateFullName("   ") is RegistrationValidator.FieldResult.Invalid)

        // Too short (< 2 characters)
        val shortResult = RegistrationValidator.validateFullName("A")
        assertTrue(shortResult is RegistrationValidator.FieldResult.Invalid)
        assertEquals("Full name must be at least 2 characters.", (shortResult as RegistrationValidator.FieldResult.Invalid).errorMessage)

        // Invalid characters (digits, illegal symbols)
        val symbolResult = RegistrationValidator.validateFullName("John123")
        assertTrue(symbolResult is RegistrationValidator.FieldResult.Invalid)

        // Valid names
        assertTrue(RegistrationValidator.validateFullName("John Doe") is RegistrationValidator.FieldResult.Valid)
        assertTrue(RegistrationValidator.validateFullName("Mary-Jane O'Connor") is RegistrationValidator.FieldResult.Valid)
        assertTrue(RegistrationValidator.validateFullName("José Silva") is RegistrationValidator.FieldResult.Valid)
    }

    @Test
    fun testEmailValidation() {
        // Blank or whitespace
        val emptyResult = RegistrationValidator.validateEmail("")
        assertTrue(emptyResult is RegistrationValidator.FieldResult.Invalid)
        assertEquals("Email address is required.", (emptyResult as RegistrationValidator.FieldResult.Invalid).errorMessage)

        assertTrue(RegistrationValidator.validateEmail("   ") is RegistrationValidator.FieldResult.Invalid)

        // Malformed syntax
        assertTrue(RegistrationValidator.validateEmail("plainaddress") is RegistrationValidator.FieldResult.Invalid)
        assertTrue(RegistrationValidator.validateEmail("@missinguser.com") is RegistrationValidator.FieldResult.Invalid)
        assertTrue(RegistrationValidator.validateEmail("user@domain") is RegistrationValidator.FieldResult.Invalid)
        assertTrue(RegistrationValidator.validateEmail("user@.com") is RegistrationValidator.FieldResult.Invalid)
        assertTrue(RegistrationValidator.validateEmail("user@domain..com") is RegistrationValidator.FieldResult.Invalid)

        // Blocked disposable / burner domains
        val disposable1 = RegistrationValidator.validateEmail("spammer@tempmail.com")
        assertTrue(disposable1 is RegistrationValidator.FieldResult.Invalid)
        assertEquals("Disposable email addresses are not permitted.", (disposable1 as RegistrationValidator.FieldResult.Invalid).errorMessage)

        val disposable2 = RegistrationValidator.validateEmail("user@mailinator.com")
        assertTrue(disposable2 is RegistrationValidator.FieldResult.Invalid)
        assertEquals("Disposable email addresses are not permitted.", (disposable2 as RegistrationValidator.FieldResult.Invalid).errorMessage)

        val disposable3 = RegistrationValidator.validateEmail("test@10minutemail.com")
        assertTrue(disposable3 is RegistrationValidator.FieldResult.Invalid)

        // Valid emails
        assertTrue(RegistrationValidator.validateEmail("user@example.com") is RegistrationValidator.FieldResult.Valid)
        assertTrue(RegistrationValidator.validateEmail("firstname.lastname+tag@company.co.uk") is RegistrationValidator.FieldResult.Valid)
        assertTrue(RegistrationValidator.validateEmail("john_doe123@gmail.com") is RegistrationValidator.FieldResult.Valid)
    }

    @Test
    fun testPasswordValidation() {
        // Empty
        val emptyResult = RegistrationValidator.validatePassword("")
        assertTrue(emptyResult is RegistrationValidator.FieldResult.Invalid)
        assertEquals("Password is required.", (emptyResult as RegistrationValidator.FieldResult.Invalid).errorMessage)

        // Length < 6
        val shortResult = RegistrationValidator.validatePassword("12345")
        assertTrue(shortResult is RegistrationValidator.FieldResult.Invalid)
        assertEquals("Password must be at least 6 characters.", (shortResult as RegistrationValidator.FieldResult.Invalid).errorMessage)

        // Valid length >= 6
        assertTrue(RegistrationValidator.validatePassword("123456") is RegistrationValidator.FieldResult.Valid)
        assertTrue(RegistrationValidator.validatePassword("SecurePass2026!") is RegistrationValidator.FieldResult.Valid)
    }

    @Test
    fun testConfirmPasswordValidation() {
        // Empty confirmation
        val emptyConfirm = RegistrationValidator.validateConfirmPassword("Password123", "")
        assertTrue(emptyConfirm is RegistrationValidator.FieldResult.Invalid)
        assertEquals("Please confirm your password.", (emptyConfirm as RegistrationValidator.FieldResult.Invalid).errorMessage)

        // Mismatch
        val mismatch = RegistrationValidator.validateConfirmPassword("Password123", "Password456")
        assertTrue(mismatch is RegistrationValidator.FieldResult.Invalid)
        assertEquals("Passwords do not match.", (mismatch as RegistrationValidator.FieldResult.Invalid).errorMessage)

        // Case difference
        val caseDiff = RegistrationValidator.validateConfirmPassword("Password123", "password123")
        assertTrue(caseDiff is RegistrationValidator.FieldResult.Invalid)
        assertEquals("Passwords do not match.", (caseDiff as RegistrationValidator.FieldResult.Invalid).errorMessage)

        // Match
        assertTrue(RegistrationValidator.validateConfirmPassword("Password123", "Password123") is RegistrationValidator.FieldResult.Valid)
    }

    @Test
    fun testValidateFormCompleteFlow() {
        // All fields empty
        val allEmpty = RegistrationValidator.validateForm("", "", "", "")
        assertFalse(allEmpty.isValid)
        assertTrue(allEmpty.fullNameResult is RegistrationValidator.FieldResult.Invalid)
        assertTrue(allEmpty.emailResult is RegistrationValidator.FieldResult.Invalid)
        assertTrue(allEmpty.passwordResult is RegistrationValidator.FieldResult.Invalid)
        assertTrue(allEmpty.confirmPasswordResult is RegistrationValidator.FieldResult.Invalid)

        // Valid form
        val validForm = RegistrationValidator.validateForm(
            fullName = "Maria Clara",
            email = "maria.clara@example.com",
            password = "SecretPassword123",
            confirmPassword = "SecretPassword123"
        )
        assertTrue(validForm.isValid)
    }
}
