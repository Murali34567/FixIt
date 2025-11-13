//package uk.ac.tees.mad.fixit.di
//
//import com.google.firebase.auth.FirebaseAuth
//import dagger.Module
//import dagger.Provides
//import dagger.hilt.InstallIn
//import dagger.hilt.components.SingletonComponent
//import uk.ac.tees.mad.fixit.data.repository.AuthRepoImpl
//import uk.ac.tees.mad.fixit.domain.repository.AuthRepository
//import uk.ac.tees.mad.fixit.domain.useCase.GetCurrentUserUseCase
//import uk.ac.tees.mad.fixit.domain.useCase.LoginUseCase
//import uk.ac.tees.mad.fixit.domain.useCase.LogoutUseCase
//import uk.ac.tees.mad.fixit.domain.useCase.SignupUseCase
//import javax.inject.Singleton
//
//@InstallIn(SingletonComponent::class)
//@Module
//object AuthModule {
//
//    @Singleton
//    @Provides
//    fun provideFirebaseAuth() : FirebaseAuth{
//        return FirebaseAuth.getInstance()
//    }
//
//    @Provides
//    fun provideAuthRepository(auth: FirebaseAuth) : AuthRepository{
//        return AuthRepoImpl(auth)
//    }
//
//    // use cases
//    @Provides
//    fun provideGetCurrentUserUseCase(authRepository: AuthRepository): GetCurrentUserUseCase{
//        return GetCurrentUserUseCase(authRepository)
//    }
//
//    @Provides
//    fun provideLoginUseCase(authRepository: AuthRepository): LoginUseCase{
//        return LoginUseCase(authRepository)
//    }
//
//    @Provides
//    fun provideSignupUseCase(authRepository: AuthRepository): SignupUseCase {
//        return SignupUseCase(authRepository)
//    }
//
//    @Provides
//    fun provideLogoutUseCase(authRepository: AuthRepository): LogoutUseCase {
//        return LogoutUseCase(authRepository)
//    }
//
//}