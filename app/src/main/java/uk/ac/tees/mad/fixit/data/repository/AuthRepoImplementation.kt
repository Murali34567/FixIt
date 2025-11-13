//package uk.ac.tees.mad.fixit.data.repository
//
//import com.google.firebase.auth.FirebaseAuth
//import kotlinx.coroutines.channels.awaitClose
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.callbackFlow
//import uk.ac.tees.mad.fixit.domain.model.User
//import uk.ac.tees.mad.fixit.domain.repository.AuthRepository
//
//class AuthRepoImpl(
//    private val firebaseAuth: FirebaseAuth
//) : AuthRepository {
//
//    override fun login(email: String, password: String): Flow<Result<User>> {
//        return callbackFlow {
//            awaitClose {
//                firebaseAuth.signInWithEmailAndPassword(email, password)
//                    .addOnSuccessListener { user ->
//                        user.user?.let {
//                            trySend(
//                                Result.success(
//                                    User(
//                                        uid = it.uid,
//                                        email = it.email.orEmpty(),
//                                        displayName = it.displayName.orEmpty()
//                                    )
//                                )
//                            )
//                        }
//                    }
//                    .addOnFailureListener { error ->
//                        trySend(Result.failure(error))
//                    }
//            }
//        }
//    }
//
//    override fun signup(email: String, password: String): Flow<Result<User>> {
//        return callbackFlow {
//            awaitClose {
//                firebaseAuth
//                    .createUserWithEmailAndPassword(email, password)
//                    .addOnSuccessListener { user ->
//                        user.user?.let {
//                            trySend(
//                                Result.success(
//                                    User(
//                                        uid = it.uid,
//                                        email = it.email.orEmpty(),
//                                        displayName = it.displayName.orEmpty()
//                                    )
//                                )
//                            )
//                        }
//                    }
//                    .addOnFailureListener { error ->
//                        trySend(Result.failure(error))
//                    }
//            }
//        }
//    }
//
//    override suspend fun logout() {
//        firebaseAuth.signOut()
//    }
//
//    override suspend fun getCurrentUser(): User? {
//        return firebaseAuth.currentUser?.let {
//            User(
//                uid = it.uid,
//                email = it.email.orEmpty(),
//                displayName = it.displayName.orEmpty()
//            )
//        }
//    }
//}