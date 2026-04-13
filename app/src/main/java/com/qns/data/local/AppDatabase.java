package com.qns.data.local;

import android.content.Context;
import androidx.room.*;
import com.qns.data.local.dao.*;
import com.qns.data.local.entity.*;
import net.sqlcipher.database.SupportFactory;

@Database(
    entities = { ChatEntity.class, MessageEntity.class, RatchetSessionEntity.class },
    version  = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract ChatDao    chatDao();
    public abstract MessageDao messageDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context ctx, byte[] passphrase) {
        if (INSTANCE == null) synchronized (AppDatabase.class) {
            if (INSTANCE == null)
                INSTANCE = Room.databaseBuilder(ctx.getApplicationContext(), AppDatabase.class, "qns.db")
                    .openHelperFactory(new SupportFactory(passphrase))
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return INSTANCE;
    }
}
