package com.qns.domain.usecase;

import com.qns.data.remote.model.AuthResponse;
import com.qns.data.repository.AuthRepository;
import javax.inject.Inject;
import io.reactivex.rxjava3.core.Single;

public class LoginUseCase {
    private final AuthRepository repo;
    @Inject public LoginUseCase(AuthRepository repo) { this.repo = repo; }

    public Single<AuthResponse> execute(String username, String password) {
        if (username == null || username.length() < 3)
            return Single.error(new IllegalArgumentException("Username too short"));
        if (password == null || password.length() < 8)
            return Single.error(new IllegalArgumentException("Password too short"));
        return repo.login(username.trim(), password);
    }
}
