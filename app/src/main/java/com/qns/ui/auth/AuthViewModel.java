package com.qns.ui.auth;
import androidx.lifecycle.*;
import com.qns.data.remote.AuthEvents;
import com.qns.data.repository.AuthRepository;
import com.qns.utils.ErrorMapper;
import com.qns.domain.usecase.*;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.*;

@HiltViewModel
public class AuthViewModel extends ViewModel {
    private final LoginUseCase    loginUC;
    private final RegisterUseCase regUC;
    private final AuthRepository  repo;
    private final AuthEvents      authEvents;
    private final CompositeDisposable bag = new CompositeDisposable();

    public final MutableLiveData<Boolean> isLoggedIn  = new MutableLiveData<>(false);
    public final MutableLiveData<String>  userRole    = new MutableLiveData<>("user");
    public final MutableLiveData<Boolean> isBetaTester = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> isLoading   = new MutableLiveData<>(false);
    public final MutableLiveData<String>  error       = new MutableLiveData<>();
    public final MutableLiveData<Boolean> loginSuccess= new MutableLiveData<>();
    public final MutableLiveData<Boolean> forcedLogout = new MutableLiveData<>(false);

    @Inject
    public AuthViewModel(LoginUseCase lu, RegisterUseCase ru, AuthRepository repo, AuthEvents authEvents) {
        this.loginUC = lu; this.regUC = ru; this.repo = repo; this.authEvents = authEvents;
        bag.add(repo.observeLoggedIn().observeOn(AndroidSchedulers.mainThread()).subscribe(isLoggedIn::setValue));
        bag.add(repo.observeRole().observeOn(AndroidSchedulers.mainThread()).subscribe(userRole::setValue));
        bag.add(repo.observeBetaTester().observeOn(AndroidSchedulers.mainThread()).subscribe(isBetaTester::setValue));
        bag.add(repo.restoreSession().subscribe(() -> {}, ignored -> {}));
        bag.add(authEvents.forcedLogout().observeOn(AndroidSchedulers.mainThread()).subscribe(ignored -> handleForcedLogout()));
    }

    private void handleForcedLogout() {
        error.setValue("Сессия истекла. Войдите снова.");
        bag.add(repo.logout().observeOn(AndroidSchedulers.mainThread())
            .subscribe(() -> forcedLogout.setValue(true), e -> forcedLogout.setValue(true)));
    }

    /** Сбрасывает флаг после того, как NavGraph обработал переход на экран входа. */
    public void consumeForcedLogout() {
        if (Boolean.TRUE.equals(forcedLogout.getValue())) forcedLogout.setValue(false);
    }
    public void login(String u, String p) {
        error.setValue(null);
        loginSuccess.setValue(false);
        isLoading.setValue(true);
        bag.add(loginUC.execute(u,p).observeOn(AndroidSchedulers.mainThread())
            .subscribe(r->{ isLoading.setValue(false); loginSuccess.setValue(true); },
                       e->{ isLoading.setValue(false); error.setValue(ErrorMapper.messageForAuth(e)); }));
    }
    public void register(String u, String p) {
        error.setValue(null);
        loginSuccess.setValue(false);
        isLoading.setValue(true);
        bag.add(regUC.execute(u,p).observeOn(AndroidSchedulers.mainThread())
            .subscribe(r->{ isLoading.setValue(false); loginSuccess.setValue(true); },
                       e->{ isLoading.setValue(false); error.setValue(ErrorMapper.messageForAuth(e)); }));
    }
    public void logout() { bag.add(repo.logout().observeOn(AndroidSchedulers.mainThread()).subscribe()); }
    @Override protected void onCleared() { super.onCleared(); bag.clear(); }
}
