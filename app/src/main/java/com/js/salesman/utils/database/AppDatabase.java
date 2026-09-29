package com.js.salesman.utils.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.js.salesman.interfaces.ProductDao;
import com.js.salesman.interfaces.SyncDao;
import com.js.salesman.interfaces.TrackingDao;
import com.js.salesman.models.Converter;
import com.js.salesman.models.Product;
import com.js.salesman.models.TrackingRecord;

@Database(entities = {Product.class, SyncMetadata.class, TrackingRecord.class}, version = 3, exportSchema = false)
@TypeConverters({Converter.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract ProductDao productDao();
    public abstract SyncDao syncDao();
    public abstract TrackingDao trackingDao();
    private static volatile AppDatabase INSTANCE;

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `tracking_records` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`tracking_id` TEXT NOT NULL, " +
                    "`user_id` TEXT, " +
                    "`latitude` REAL NOT NULL, " +
                    "`longitude` REAL NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`retry_count` INTEGER NOT NULL DEFAULT 0, " +
                    "`last_error` TEXT" +
                    ")");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `tracking_records` ADD COLUMN `sync_started_at` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `tracking_records` ADD COLUMN `lease_expires_at` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `tracking_records` ADD COLUMN `batch_id` TEXT DEFAULT NULL");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_records_status_timestamp` ON `tracking_records` (`status`, `timestamp`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_records_status_created_at` ON `tracking_records` (`status`, `created_at`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_records_tracking_id` ON `tracking_records` (`tracking_id`)");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "salesman_room.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .fallbackToDestructiveMigration(true)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}