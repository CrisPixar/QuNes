package com.qns.ui.admin;
import androidx.lifecycle.*;
import com.qns.data.remote.ApiService;
import java.util.*; import java.util.ArrayList;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.*;

@HiltViewModel
public class AdminViewModel extends ViewModel {
    private final ApiService api;
    private final CompositeDisposable bag = new CompositeDisposable();
    public final MutableLiveData<List<AdminUser>> users   = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<AdminStats>      stats   = new MutableLiveData<>();
    public final MutableLiveData<Boolean>         loading = new MutableLiveData<>(false);
    public final MutableLiveData<String>          error   = new MutableLiveData<>();

    @Inject public AdminViewModel(ApiService api) { this.api = api; }

    public void loadData() {
        loading.setValue(true);
        bag.add(api.getAdminStats().observeOn(AndroidSchedulers.mainThread()).subscribe(m -> {
            stats.setValue(new AdminStats(n(m,"totalUsers"),n(m,"totalMessages"),n(m,"scamUsers"),n(m,"activeSessions"),n(m,"activeWs")));
        }, e -> error.setValue(e.getMessage())));
        bag.add(api.getAdminUsers("",1).observeOn(AndroidSchedulers.mainThread()).subscribe(m -> {
            @SuppressWarnings("unchecked") List<Map<String,Object>> raw = (List<Map<String,Object>>) m.get("users");
            List<AdminUser> list = new ArrayList<>();
            if (raw != null) for (Map<String,Object> u : raw)
                list.add(new AdminUser(s(u,"id"),s(u,"username"),s(u,"role"),Boolean.TRUE.equals(u.get("isScam")),s(u,"last_ip"),n(u,"active_sessions")));
            users.setValue(list); loading.setValue(false);
        }, e -> { loading.setValue(false); error.setValue(e.getMessage()); }));
    }
    public void deleteUser(String uid) { bag.add(api.deleteAdminUser(uid).observeOn(AndroidSchedulers.mainThread()).subscribe(r->loadData(), e->error.setValue(e.getMessage()))); }
    public void toggleScam(String uid, boolean scam, String reason) { bag.add(api.setScam(uid, Map.of("isScam",scam,"reason",reason!=null?reason:"")).observeOn(AndroidSchedulers.mainThread()).subscribe(r->loadData(), e->error.setValue(e.getMessage()))); }
    public void revokeUserSessions(String uid) { bag.add(api.revokeSessions(uid).observeOn(AndroidSchedulers.mainThread()).subscribe(r->loadData(), e->error.setValue(e.getMessage()))); }
    public void deleteAllMessages(String chatId) { bag.add(api.deleteAllMessages(chatId).observeOn(AndroidSchedulers.mainThread()).subscribe()); }
    @Override protected void onCleared() { super.onCleared(); bag.clear(); }

    private static long   n(Map<String,Object> m, String k) { Object v=m.get(k); return v instanceof Number?((Number)v).longValue():0; }
    private static String s(Map<String,Object> m, String k) { Object v=m.get(k); return v instanceof String?(String)v:v!=null?v.toString():null; }

    public static class AdminUser { public String id,username,role,lastIp; public boolean isScam; public long activeSessions;
        public AdminUser(String i,String u,String r,boolean sc,String ip,long s){id=i;username=u;role=r;isScam=sc;lastIp=ip;activeSessions=s;} }
    public static class AdminStats { public long totalUsers,totalMessages,scamUsers,activeSessions,activeWs;
        public AdminStats(long u,long m,long sc,long as_,long ws){totalUsers=u;totalMessages=m;scamUsers=sc;activeSessions=as_;activeWs=ws;} }
}
