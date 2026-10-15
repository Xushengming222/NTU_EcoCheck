package com.ntu.electricity.payment

/** A snapshot of the home form. Credentials stay in memory and are never put in a URL. */
data class PaymentRequest(
    val studentId: String,
    val password: String,
    val campusId: String,
    val buildingId: String,
    val roomId: String,
    val campusName: String,
    val buildingName: String,
    val roomName: String
) {
    val roomLabel: String get() = "$campusName $buildingName $roomName"
}
