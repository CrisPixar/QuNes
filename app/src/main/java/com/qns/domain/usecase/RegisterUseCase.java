package com.qns.domain.usecase;

import com.qns.data.remote.model.AuthResponse;
import com.qns.data.repository.AuthRepository;
import javax.inject.Inject;
import io.reactivex.rxjava3.core.Single;

public class RegisterUseCase {
    private final AuthRepository repo;
    @Inject public RegisterUseCase(AuthRepository repo) { this.repo = repo; }

    public Single<AuthResponse> execute(String username, String password) {
        if (username == null || username.length() < 3)
            return Single.error(new IllegalArgumentException("Username: min 3 chars"));
        if (password == null || password.length() < 8)
            return Single.error(new IllegalArgumentException("Password: min 8 chars"));
        if (!username.matches("[a-zA-Z0-9_\\-]+"))
            return Single.error(new IllegalArgumentException("Username: letters, digits, _ - only"));
        return repo.register(username.trim(), password);
    }
}
