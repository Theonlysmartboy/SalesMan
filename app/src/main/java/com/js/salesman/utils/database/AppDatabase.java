package com.js.salesman.utils.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.js.salesman.interfaces.CustomerVisitDao;
import com.js.salesman.interfaces.ProductDao;
import com.js.salesman.interfaces.SyncDao;
import com.js.salesman.interfaces.TrackingDao;
import com.js.salesman.models.Converter;
import com.js.salesman.models.CustomerVisit;
import com.js.salesman.models.Product;
import com.js.salesman.models.TrackingRecord;

@Database(
        entities = {
                Product.class,
                SyncMetadata.class,
                TrackingRecord.class,
                CustomerVisit.class
        },
        version = 4,
        exportSchema = false
)
@TypeConverters({Converter.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract ProductDao productDao();
    public abstract SyncDao syncDao();
    public abstract TrackingDao trackingDao();
    public abstract CustomerVisitDao customerVisitDao();
    private static volatile AppDatabase INSTANCE;
    /**
     * Migration from database version 1 to version 2.
     * Creates the existing tracking_records table.
     */
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

    /**
     * Migration from database version 2 to version 3.
     * Creates customer_visits without deleting existing data.
     */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `customer_visits` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`visit_id` TEXT NOT NULL, " +
                            "`user_id` TEXT NOT NULL, " +
                            "`customer_id` TEXT, " +
                            "`customer_type` TEXT NOT NULL, " +
                            "`business_name` TEXT, " +
                            "`started_at` INTEGER NOT NULL, " +
                            "`ended_at` INTEGER, " +
                            "`duration_seconds` INTEGER NOT NULL DEFAULT 0, " +
                            "`start_latitude` REAL NOT NULL, " +
                            "`start_longitude` REAL NOT NULL, " +
                            "`end_latitude` REAL, " +
                            "`end_longitude` REAL, " +
                            "`visit_status` TEXT NOT NULL, " +
                            "`visit_source` TEXT NOT NULL, " +
                            "`notes` TEXT, " +
                            "`sync_status` TEXT NOT NULL DEFAULT 'PENDING', " +
                            "`created_at` INTEGER NOT NULL, " +
                            "`updated_at` INTEGER" +
                            ")");

            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "`index_customer_visits_visit_id` " +
                            "ON `customer_visits` (`visit_id`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                            "`index_customer_visits_user_id` " +
                            "ON `customer_visits` (`user_id`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                            "`index_customer_visits_customer_id` " +
                            "ON `customer_visits` (`customer_id`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                            "`index_customer_visits_started_at` " +
                            "ON `customer_visits` (`started_at`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                            "`index_customer_visits_visit_status` " +
                            "ON `customer_visits` (`visit_status`)");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                            "`index_customer_visits_sync_status` " +
                            "ON `customer_visits` (`sync_status`)");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `tracking_records` " +
                            "ADD COLUMN `visit_id` TEXT");

            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                            "`index_tracking_records_visit_id` " +
                            "ON `tracking_records` (`visit_id`)");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class,
                                    "salesman_room.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .fallbackToDestructiveMigration(true)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}