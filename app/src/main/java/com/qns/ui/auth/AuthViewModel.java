package com.qns.ui.auth;
import androidx.lifecycle.*;
import com.qns.data.repository.AuthRepository;
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
    private final CompositeDisposable bag = new CompositeDisposable();

    public final MutableLiveData<Boolean> isLoggedIn  = new MutableLiveData<>(false);
    public final MutableLiveData<String>  userRole    = new MutableLiveData<>("user");
    public final MutableLiveData<Boolean> isLoading   = new MutableLiveData<>(false);
    public final MutableLiveData<String>  error       = new MutableLiveData<>();
    public final MutableLiveData<Boolean> loginSuccess= new MutableLiveData<>();

    @Inject
    public AuthViewModel(LoginUseCase lu, RegisterUseCase ru, AuthRepository repo) {
        this.loginUC = lu; this.regUC = ru; this.repo = repo;
        bag.add(repo.observeLoggedIn().observeOn(AndroidSchedulers.mainThread()).subscribe(isLoggedIn::setValue));
        bag.add(repo.observeRole()    .observeOn(AndroidSchedulers.mainThread()).subscribe(userRole::setValue));
    }
    public void login(String u, String p) {
        isLoading.setValue(true);
        bag.add(loginUC.execute(u,p).observeOn(AndroidSchedulers.mainThread())
            .subscribe(r->{ isLoading.setValue(false); loginSuccess.setValue(true); },
                       e->{ isLoading.setValue(false); error.setValue(e.getMessage()); }));
    }
    public void register(String u, String p) {
        isLoading.setValue(true);
        bag.add(regUC.execute(u,p).observeOn(AndroidSchedulers.mainThread())
            .subscribe(r->{ isLoading.setValue(false); loginSuccess.setValue(true); },
                       e->{ isLoading.setValue(false); error.setValue(e.getMessage()); }));
    }
    public void logout() { bag.add(repo.logout().observeOn(AndroidSchedulers.mainThread()).subscribe()); }
    @Override protected void onCleared() { super.onCleared(); bag.clear(); }
}
