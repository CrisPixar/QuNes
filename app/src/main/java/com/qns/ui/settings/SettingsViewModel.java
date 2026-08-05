package com.qns.ui.settings;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.qns.data.remote.model.SessionInfo;
import com.qns.data.repository.AuthRepository;
import com.qns.ui.theme.ThemeRepository;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

@HiltViewModel
public class SettingsViewModel extends ViewModel {
    private final AuthRepository repository;
    private final ThemeRepository themeRepository;
    private final CompositeDisposable bag = new CompositeDisposable();
    public final MutableLiveData<Boolean> loggedOut = new MutableLiveData<>();
    public final MutableLiveData<String> themeMode;
    public final MutableLiveData<List<SessionInfo>> sessions = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<Boolean> sessionsLoading = new MutableLiveData<>(false);
    public final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public SettingsViewModel(AuthRepository repository, ThemeRepository themeRepository) {
        this.repository = repository;
        this.themeRepository = themeRepository;
        this.themeMode = themeRepository.mode;
    }

    public void setTheme(String mode) {
        themeRepository.setMode(mode);
    }

    public void logout() {
        bag.add(repository.logout()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(() -> loggedOut.setValue(true), value -> error.setValue(value.getMessage())));
    }

    public void loadSessions() {
        sessionsLoading.setValue(true);
        bag.add(repository.getSessions()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(value -> {
                sessions.setValue(value);
                sessionsLoading.setValue(false);
            }, value -> {
                sessionsLoading.setValue(false);
                error.setValue(value.getMessage());
            }));
    }

    public void revokeSession(String id) {
        bag.add(repository.revokeSession(id)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::loadSessions, value -> error.setValue(value.getMessage())));
    }

    public void revokeAllSessions() {
        bag.add(repository.revokeAllSessions()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::loadSessions, value -> error.setValue(value.getMessage())));
    }

    @Override
    protected void onCleared() {
        bag.clear();
        super.onCleared();
    }
}
