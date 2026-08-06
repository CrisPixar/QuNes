package com.qns.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.qns.data.local.dao.ChatDao;
import com.qns.data.local.dao.MessageDao;
import com.qns.data.local.dao.RatchetSessionDao;
import com.qns.data.local.entity.ChatEntity;
import com.qns.data.local.entity.MessageEntity;
import com.qns.data.local.entity.RatchetSessionEntity;

import net.sqlcipher.database.SupportFactory;

@Database(
    entities = { ChatEntity.class, MessageEntity.class, RatchetSessionEntity.class },
    version = 2,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract ChatDao chatDao();
    public abstract MessageDao messageDao();
    public abstract RatchetSessionDao ratchetSessionDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE chats ADD COLUMN otherUserScamReason TEXT");
            db.execSQL("ALTER TABLE chats ADD COLUMN otherUserVerified INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE messages ADD COLUMN clientMessageId TEXT");
            db.execSQL("ALTER TABLE messages ADD COLUMN protocolVersion INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE messages ADD COLUMN decryptionError TEXT");
            db.execSQL("ALTER TABLE ratchet_sessions ADD COLUMN remoteUserId TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE ratchet_sessions ADD COLUMN fingerprint TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE ratchet_sessions ADD COLUMN protocolVersion INTEGER NOT NULL DEFAULT 2");
            db.execSQL("ALTER TABLE ratchet_sessions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context, byte[] passphrase) {
        if (INSTANCE == null) synchronized (AppDatabase.class) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "qns.db")
                    .openHelperFactory(new SupportFactory(passphrase))
                    .addMigrations(MIGRATION_1_2)
                    .build();
            }
        }
        return INSTANCE;
    }
}
