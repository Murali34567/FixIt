package uk.ac.tees.mad.fixit.data.model

/**
 * User profile data model
 */
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val photoUrl: String = "",
    val notificationsEnabled: Boolean = true,
    val emailNotifications: Boolean = true,
    val pushNotifications: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "uid" to uid,
            "email" to email,
            "name" to name,
            "photoUrl" to photoUrl,
            "notificationsEnabled" to notificationsEnabled,
            "emailNotifications" to emailNotifications,
            "pushNotifications" to pushNotifications,
            "createdAt" to createdAt,
            "lastUpdated" to lastUpdated
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): UserProfile {
            return UserProfile(
                uid = map["uid"] as? String ?: "",
                email = map["email"] as? String ?: "",
                name = map["name"] as? String ?: "",
                photoUrl = map["photoUrl"] as? String ?: "",
                notificationsEnabled = map["notificationsEnabled"] as? Boolean ?: true,
                emailNotifications = map["emailNotifications"] as? Boolean ?: true,
                pushNotifications = map["pushNotifications"] as? Boolean ?: true,
                createdAt = map["createdAt"] as? Long ?: System.currentTimeMillis(),
                lastUpdated = map["lastUpdated"] as? Long ?: System.currentTimeMillis()
            )
        }
    }
}