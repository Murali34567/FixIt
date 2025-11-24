package uk.ac.tees.mad.fixit.domain.util

import android.net.Uri
import uk.ac.tees.mad.fixit.data.model.IssueLocation

object ValidationHelper {

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationError>
    )

    sealed class ValidationError(
        val field: String,
        val message: String
    ) {
        object NoImage : ValidationError("image", "Please capture or select an image")
        object DescriptionEmpty : ValidationError("description", "Description is required")
        object DescriptionTooShort : ValidationError("description", "Description must be at least 10 characters")
        object DescriptionTooLong : ValidationError("description", "Description must be less than 500 characters")
        object NoLocation : ValidationError("location", "Please get your current location")
        object InvalidLocation : ValidationError("location", "Please provide a valid location")
    }

    fun validateReport(
        imageUri: Uri?,
        description: String,
        location: IssueLocation?
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Image validation
        if (imageUri == null) {
            errors.add(ValidationError.NoImage)
        }

        // Description validation
        when {
            description.isBlank() -> errors.add(ValidationError.DescriptionEmpty)
            description.length < 10 -> errors.add(ValidationError.DescriptionTooShort)
            description.length > 500 -> errors.add(ValidationError.DescriptionTooLong)
        }

        // Location validation
        when {
            location == null -> errors.add(ValidationError.NoLocation)
            location.latitude == 0.0 && location.longitude == 0.0 ->
                errors.add(ValidationError.InvalidLocation)
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    fun getFieldErrors(errors: List<ValidationError>, field: String): List<String> {
        return errors.filter { it.field == field }.map { it.message }
    }
}